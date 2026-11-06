package com.example.data.cloud

import android.content.Context
import com.example.data.local.CachedCatalogEntity
import com.example.data.local.CachedCurriculumTopicEntity
import com.example.data.local.CatalogCacheDatabase
import com.example.data.local.DownloadedArtifactMetadataEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Room-based repository layer caching the published content catalog JSON,
 * storing downloaded artifact metadata, and providing offline access to the
 * curriculum structure when the network is unavailable.
 */
class CatalogRoomRepository(
    private val database: CatalogCacheDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        database = CatalogCacheDatabase.getInstance(context),
        ioDispatcher = ioDispatcher
    )

    private val catalogDao = database.catalogCacheDao()
    private val artifactDao = database.downloadedArtifactDao()
    private val topicDao = database.cachedCurriculumTopicDao()

    /**
     * Reactive Flow observing the currently cached catalog from Room, automatically parsed into domain model.
     */
    val cachedCatalogFlow: Flow<PublishedContentCatalog?> = catalogDao.observeCachedCatalog().map { entity ->
        if (entity != null && entity.catalogJson.isNotBlank()) {
            runCatching {
                PublishedContentCatalog.parse(entity.catalogJson, entity.etag)
            }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Reactive Flow of all downloaded artifact records.
     */
    val downloadedArtifactsFlow: Flow<List<DownloadedArtifactMetadataEntity>> = artifactDao.observeAllArtifacts()

    /**
     * Total storage bytes consumed by downloaded artifacts.
     */
    val totalStorageBytesFlow: Flow<Long?> = artifactDao.observeTotalStorageBytes()

    /**
     * Count of downloaded offline artifacts.
     */
    val downloadedArtifactsCountFlow: Flow<Int> = artifactDao.observeDownloadedCount()

    /**
     * Retrieves the cached catalog parsed as domain model directly from Room.
     */
    suspend fun getCachedCatalog(): PublishedContentCatalog? = withContext(ioDispatcher) {
        val entity = catalogDao.getCachedCatalog() ?: return@withContext null
        runCatching {
            PublishedContentCatalog.parse(entity.catalogJson, entity.etag)
        }.getOrNull()
    }

    /**
     * Retrieves the raw cached catalog JSON string from Room.
     */
    suspend fun getCachedCatalogJson(): String? = withContext(ioDispatcher) {
        catalogDao.getCatalogJson()
    }

    /**
     * Retrieves the stored ETag for conditional GET refreshes.
     */
    suspend fun getCachedEtag(): String? = withContext(ioDispatcher) {
        catalogDao.getCatalogEtag()
    }

    /**
     * Retrieves the raw cached catalog entity.
     */
    suspend fun getCachedCatalogEntity(): CachedCatalogEntity? = withContext(ioDispatcher) {
        catalogDao.getCachedCatalog()
    }

    /**
     * Saves raw catalog JSON into Room, extracts and caches the curriculum structure topics,
     * and returns the parsed domain catalog.
     */
    suspend fun saveCatalog(
        catalogJson: String,
        etag: String?,
        sourceUrl: String
    ): PublishedContentCatalog = withContext(ioDispatcher) {
        val parsedCatalog = PublishedContentCatalog.parse(catalogJson, etag)

        // 1. Cache the raw catalog JSON and metadata
        val entity = CachedCatalogEntity(
            id = CachedCatalogEntity.PRIMARY_KEY,
            catalogJson = catalogJson,
            catalogVersion = parsedCatalog.catalogVersion,
            curriculumId = parsedCatalog.curriculumId,
            etag = etag,
            sourceUrl = sourceUrl,
            cachedAtMs = System.currentTimeMillis(),
            publishedAt = parsedCatalog.publishedAt
        )
        catalogDao.upsertCatalog(entity)

        // 2. Cache denormalized curriculum topics for immediate offline SQL queries
        val topicEntities = mutableListOf<CachedCurriculumTopicEntity>()
        parsedCatalog.classes.forEach { cls ->
            cls.subjects.forEach { subj ->
                subj.topics.forEach { top ->
                    val availableTypes = top.artifacts.filterValues { it.status == ArtifactAvailabilityState.AVAILABLE }
                        .keys.joinToString(",") { it.name }
                    val comingSoonTypes = top.artifacts.filterValues { it.status == ArtifactAvailabilityState.COMING_SOON }
                        .keys.joinToString(",") { it.name }

                    topicEntities.add(
                        CachedCurriculumTopicEntity(
                            topicId = top.topicId,
                            topicName = top.topicName,
                            subjectId = subj.subjectId,
                            subjectName = subj.subjectName,
                            classId = cls.classId,
                            className = cls.className,
                            availableArtifactTypes = availableTypes,
                            comingSoonArtifactTypes = comingSoonTypes,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        // If there were top-level subjects without classes
        if (parsedCatalog.classes.isEmpty()) {
            parsedCatalog.subjects.forEach { subj ->
                subj.topics.forEach { top ->
                    val availableTypes = top.artifacts.filterValues { it.status == ArtifactAvailabilityState.AVAILABLE }
                        .keys.joinToString(",") { it.name }
                    val comingSoonTypes = top.artifacts.filterValues { it.status == ArtifactAvailabilityState.COMING_SOON }
                        .keys.joinToString(",") { it.name }

                    topicEntities.add(
                        CachedCurriculumTopicEntity(
                            topicId = top.topicId,
                            topicName = top.topicName,
                            subjectId = subj.subjectId,
                            subjectName = subj.subjectName,
                            classId = top.gradeId,
                            className = top.className,
                            availableArtifactTypes = availableTypes,
                            comingSoonArtifactTypes = comingSoonTypes,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        if (topicEntities.isNotEmpty()) {
            topicDao.insertTopics(topicEntities)
        }

        parsedCatalog
    }

    /**
     * Stores downloaded artifact metadata into Room.
     */
    suspend fun recordDownloadedArtifact(
        record: CloudArtifactRecord,
        file: File,
        topicId: String = "",
        subjectId: String = "",
        curriculumId: String = ""
    ): DownloadedArtifactMetadataEntity = withContext(ioDispatcher) {
        val metadata = DownloadedArtifactMetadataEntity(
            artifactId = record.artifactId,
            topicId = topicId,
            subjectId = subjectId,
            curriculumId = curriculumId,
            artifactType = record.artifactType.name,
            format = record.format,
            version = record.version,
            localFilePath = file.absolutePath,
            fileSizeBytes = file.length(),
            sha256Checksum = record.sha256,
            downloadUrl = record.downloadUrl,
            title = record.title,
            downloadedAtMs = System.currentTimeMillis(),
            lastAccessedAtMs = System.currentTimeMillis(),
            status = DownloadedArtifactMetadataEntity.STATUS_COMPLETED
        )
        artifactDao.insertOrUpdate(metadata)
        metadata
    }

    /**
     * Retrieves downloaded artifact metadata by artifactId.
     */
    suspend fun getDownloadedArtifact(artifactId: String): DownloadedArtifactMetadataEntity? = withContext(ioDispatcher) {
        artifactDao.getArtifactById(artifactId)
    }

    /**
     * Retrieves all downloaded artifacts associated with a topic.
     */
    suspend fun getDownloadedArtifactsForTopic(topicId: String): List<DownloadedArtifactMetadataEntity> = withContext(ioDispatcher) {
        artifactDao.getArtifactsForTopic(topicId)
    }

    /**
     * Removes an artifact metadata record from Room.
     */
    suspend fun removeDownloadedArtifact(artifactId: String) = withContext(ioDispatcher) {
        artifactDao.deleteArtifact(artifactId)
    }

    /**
     * Checks if an artifact is downloaded and present on disk with valid size.
     */
    suspend fun isArtifactDownloaded(record: CloudArtifactRecord): Boolean = withContext(ioDispatcher) {
        val meta = artifactDao.getArtifactById(record.artifactId) ?: return@withContext false
        val file = File(meta.localFilePath)
        file.exists() && file.length() > 0L
    }

    /**
     * Queries offline curriculum topics, optionally filtered by student class.
     */
    suspend fun getOfflineCurriculumTopics(classFilter: String? = null): List<CachedCurriculumTopicEntity> = withContext(ioDispatcher) {
        if (classFilter.isNullOrBlank()) {
            topicDao.observeAllTopics().map { it }.let {
                // If direct synchronous fetch needed:
                topicDao.getTopicsForClass("")
            }
        } else {
            topicDao.getTopicsForClass(classFilter)
        }
    }
}
