package com.example.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * State representing discovery and refresh of the Noorix published content index.
 */
sealed class CatalogSyncState {
    object Idle : CatalogSyncState()
    object Refreshing : CatalogSyncState()
    data class Success(val catalog: PublishedContentCatalog, val refreshedFromNetwork: Boolean) : CatalogSyncState()
    data class Outage(val message: String, val cachedCatalog: PublishedContentCatalog?, val isRetryable: Boolean) : CatalogSyncState()
    data class AccessDenied(val message: String) : CatalogSyncState()
    data class Error(val message: String, val cachedCatalog: PublishedContentCatalog?) : CatalogSyncState()
}

/**
 * Central repository and discovery provider for the Noorix cloud content index (Google Drive or public index)
 * and YouTube channel association.
 */
class CloudContentRepository(
    private val context: Context,
    private var config: CloudContentConfig = CloudContentPrefs.load(context),
    private val client: OkHttpClient = defaultClient(),
    private val roomRepository: CatalogRoomRepository = CatalogRoomRepository(context),
    private val retrofitClient: com.example.data.cloud.network.CatalogRetrofitClient = com.example.data.cloud.network.CatalogRetrofitClient(client),
    private val downloader: CloudArtifactDownloader = CloudArtifactDownloader(context, client, roomRepository),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val syncMutex = Mutex()
    private val cacheFile = File(context.filesDir, "noorix_cached_catalog.json")
    private val metaFile = File(context.filesDir, "noorix_cached_catalog.meta")

    private val _syncState = MutableStateFlow<CatalogSyncState>(CatalogSyncState.Idle)
    val syncState: StateFlow<CatalogSyncState> = _syncState.asStateFlow()

    private val _activeCatalog = MutableStateFlow<PublishedContentCatalog?>(null)
    val activeCatalog: StateFlow<PublishedContentCatalog?> = _activeCatalog.asStateFlow()

    // Holds newly detected downloadable items for the student's class pending user approval
    private val _pendingNewContentForClass = MutableStateFlow<List<Pair<CloudTopicRecord, CloudArtifactRecord>>>(emptyList())
    val pendingNewContentForClass: StateFlow<List<Pair<CloudTopicRecord, CloudArtifactRecord>>> = _pendingNewContentForClass.asStateFlow()

    private val _lastSeenCatalogVersion = MutableStateFlow<String?>(null)
    val lastSeenCatalogVersion: StateFlow<String?> = _lastSeenCatalogVersion.asStateFlow()

    init {
        // Load offline cached catalog immediately on startup without blocking
        loadCachedCatalogSync()
    }

    /**
     * Updates configuration dynamically (e.g. from System Settings UI).
     */
    fun updateConfig(newConfig: CloudContentConfig) {
        this.config = newConfig
        CloudContentPrefs.save(context, newConfig)
    }

    fun getConfig(): CloudContentConfig = config

    fun getDownloader(): CloudArtifactDownloader = downloader

    fun getRoomRepository(): CatalogRoomRepository = roomRepository

    fun getRetrofitClient(): com.example.data.cloud.network.CatalogRetrofitClient = retrofitClient

    /**
     * Loads the last valid cached catalog synchronously from Room (with file fallback) for immediate offline startup.
     */
    private fun loadCachedCatalogSync() {
        // 1. Try loading from Room-based cache first
        runCatching {
            val cached = kotlinx.coroutines.runBlocking(ioDispatcher) {
                roomRepository.getCachedCatalog()
            }
            if (cached != null) {
                _activeCatalog.value = cached
                _syncState.value = CatalogSyncState.Success(cached, refreshedFromNetwork = false)
                return
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to load cached catalog from Room: ${e.message}")
        }

        // 2. Legacy fallback to file-based cache
        if (cacheFile.exists() && cacheFile.length() > 0) {
            runCatching {
                val json = cacheFile.readText(Charsets.UTF_8)
                val etag = if (metaFile.exists()) metaFile.readText(Charsets.UTF_8).trim() else null
                val parsed = PublishedContentCatalog.parse(json, etag)
                _activeCatalog.value = parsed
                _syncState.value = CatalogSyncState.Success(parsed, refreshedFromNetwork = false)
                // Migrate to Room
                kotlinx.coroutines.runBlocking(ioDispatcher) {
                    runCatching {
                        roomRepository.saveCatalog(json, etag, config.contentIndexUrl)
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to load cached catalog from file: ${e.message}")
            }
        }
    }

    /**
     * Refreshes the content catalog from the configured cloud source using Retrofit with conditional GET (ETag).
     * Retains previously valid cached index in Room if network or cloud provider is unavailable.
     */
    suspend fun refreshCatalog(force: Boolean = false): CatalogSyncState = withContext(ioDispatcher) {
        syncMutex.withLock {
            _syncState.value = CatalogSyncState.Refreshing

            val currentCatalog = _activeCatalog.value ?: roomRepository.getCachedCatalog()

            // If not connected to network, return outage with cached index if available
            if (!isNetworkConnected()) {
                val state = CatalogSyncState.Outage(
                    message = "No internet connection. Using local offline cache.",
                    cachedCatalog = currentCatalog,
                    isRetryable = true
                )
                _syncState.value = state
                return@withContext state
            }

            try {
                val ifNoneMatch = if (!force) (currentCatalog?.etag ?: roomRepository.getCachedEtag()) else null

                val response = retrofitClient.apiService.getCatalogRaw(
                    url = config.contentIndexUrl,
                    ifNoneMatch = ifNoneMatch
                )
                val code = response.code()

                when {
                    code == 304 -> {
                        // HTTP Not Modified: Cached catalog is fresh
                        val cached = currentCatalog ?: roomRepository.getCachedCatalog()
                            ?: throw IllegalStateException("304 received without cached catalog")
                        val freshState = CatalogSyncState.Success(
                            catalog = cached,
                            refreshedFromNetwork = false
                        )
                        _syncState.value = freshState
                        return@withContext freshState
                    }

                    code == 401 || code == 403 -> {
                        val accessState = CatalogSyncState.AccessDenied(
                            "Access denied (HTTP $code) reading content index at ${config.contentIndexUrl}"
                        )
                        _syncState.value = accessState
                        return@withContext accessState
                    }

                    !response.isSuccessful -> {
                        val outageState = CatalogSyncState.Outage(
                            message = "Cloud provider unavailable (HTTP $code: ${response.message()})",
                            cachedCatalog = currentCatalog,
                            isRetryable = code in setOf(408, 429, 500, 502, 503, 504)
                        )
                        _syncState.value = outageState
                        return@withContext outageState
                    }

                    else -> {
                        val responseBody = response.body()?.string()
                            ?: throw IllegalStateException("Empty response body from catalog index")

                        val newEtag = response.headers()["ETag"]

                        // Save to Room-based repository layer
                        val newCatalog = if (config.allowOfflineCache) {
                            roomRepository.saveCatalog(responseBody, newEtag, config.contentIndexUrl)
                        } else {
                            PublishedContentCatalog.parse(responseBody, newEtag)
                        }

                        // Also update legacy file cache as fallback
                        if (config.allowOfflineCache) {
                            runCatching {
                                cacheFile.writeText(responseBody, Charsets.UTF_8)
                                if (newEtag != null) {
                                    metaFile.writeText(newEtag, Charsets.UTF_8)
                                }
                            }
                        }

                        _activeCatalog.value = newCatalog
                        val successState = CatalogSyncState.Success(newCatalog, refreshedFromNetwork = true)
                        _syncState.value = successState
                        return@withContext successState
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing catalog index", e)
                val outageState = CatalogSyncState.Outage(
                    message = "Could not reach content provider: ${e.message ?: "Network error"}",
                    cachedCatalog = currentCatalog,
                    isRetryable = true
                )
                _syncState.value = outageState
                return@withContext outageState
            }
        }
    }

    /**
     * Resolves the current availability state and artifact record for a topic and artifact type.
     * Conforms to Section 6: Distinguishes 'coming_soon' from provider outages or offline states.
     */
    fun resolveArtifactStatus(
        topicId: String,
        type: NoorixArtifactType
    ): Pair<ArtifactAvailabilityState, CloudArtifactRecord?> {
        val catalog = _activeCatalog.value
        val sync = _syncState.value

        // Check if catalog contains an explicit record
        val record = catalog?.findArtifact(topicId, type)

        if (record != null) {
            // If registered as AVAILABLE, check network/provider reachability
            if (record.status == ArtifactAvailabilityState.AVAILABLE) {
                // If downloadable file is already cached offline, it's immediately AVAILABLE
                val cachedFile = downloader.getCachedFile(record)
                if (cachedFile != null) {
                    return Pair(ArtifactAvailabilityState.AVAILABLE, record)
                }

                // If not cached and provider is down or offline:
                if (sync is CatalogSyncState.Outage || !isNetworkConnected()) {
                    return Pair(ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE, record)
                }
                if (sync is CatalogSyncState.AccessDenied) {
                    return Pair(ArtifactAvailabilityState.ACCESS_DENIED, record)
                }
            }
            return Pair(record.status, record)
        }

        // If no catalog loaded yet
        if (catalog == null) {
            return if (sync is CatalogSyncState.Outage || !isNetworkConnected()) {
                Pair(ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE, null)
            } else {
                Pair(ArtifactAvailabilityState.COMING_SOON, null)
            }
        }

        // Valid catalog loaded, but topic or artifact not registered -> COMING_SOON
        return Pair(ArtifactAvailabilityState.COMING_SOON, null)
    }

    /**
     * Resolves all available resources for a specific topic ID.
     */
    fun resolveAllTopicResources(topicId: String): Map<NoorixArtifactType, Pair<ArtifactAvailabilityState, CloudArtifactRecord?>> {
        return NoorixArtifactType.values().associateWith { type ->
            resolveArtifactStatus(topicId, type)
        }
    }

    /**
     * Inspects the newly loaded catalog against local cache and student's class,
     * identifying newly available downloadable items for the student's class.
     * YouTube videos update instantly and are skipped from bulk downloads.
     */
    fun checkForNewContentForClass(studentClass: String): List<Pair<CloudTopicRecord, CloudArtifactRecord>> {
        val catalog = _activeCatalog.value ?: return emptyList()
        val classTopics = catalog.getTopicsForClass(studentClass)
        val newDownloadables = mutableListOf<Pair<CloudTopicRecord, CloudArtifactRecord>>()

        for (topic in classTopics) {
            for ((type, record) in topic.artifacts) {
                // Videos update instantly through IFrame; only downloadable assets are synced/cached
                if (type == NoorixArtifactType.VIDEO) continue

                if (record.status == ArtifactAvailabilityState.AVAILABLE && record.downloadUrl.isNotBlank()) {
                    val isCached = downloader.getCachedFile(record) != null
                    if (!isCached) {
                        newDownloadables.add(Pair(topic, record))
                    }
                }
            }
        }
        _pendingNewContentForClass.value = newDownloadables
        return newDownloadables
    }

    /**
     * Clears or dismisses the pending new content list.
     */
    fun dismissPendingNewContent() {
        _pendingNewContentForClass.value = emptyList()
    }

    /**
     * Sets a parsed catalog directly (used for unit testing or offline bundled fallbacks).
     */
    fun setCatalogForTesting(catalog: PublishedContentCatalog) {
        _activeCatalog.value = catalog
        _syncState.value = CatalogSyncState.Success(catalog, refreshedFromNetwork = false)
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "CloudContentRepo"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
