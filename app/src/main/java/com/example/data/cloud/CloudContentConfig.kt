package com.example.data.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

/**
 * Operating mode for the Noorix cloud content source.
 */
enum class ContentSourceMode {
    /**
     * Standard mode: Reads published read-only JSON index from a public HTTPS URL
     * (e.g. Google Drive direct download link or public CDN/hosting) and approved assets.
     */
    PUBLIC_INDEX,

    /**
     * Authenticated Google Drive API discovery mode using Drive root folder ID.
     */
    AUTHENTICATED_DRIVE
}

/**
 * User preference policy for loading/downloading new learning content discovered on app start.
 */
enum class ContentSyncPolicy {
    /**
     * Notify first (default): When new content for student's class is discovered on start,
     * notify student/parent and ask whether they want to load/sync that content.
     */
    NOTIFY_FIRST,

    /**
     * Automatic: Automatically load and cache new study packs and quizzes for the student's class.
     */
    AUTOMATIC,

    /**
     * Manual only: Discover schema on start but do not download anything until explicitly requested per item.
     */
    MANUAL_ONLY
}

/**
 * Configuration options for the Noorix Cloud Content Source and Noorix YouTube Channel.
 */
data class CloudContentConfig(
    val contentIndexUrl: String,
    val driveRootFolderId: String = "",
    val contentSourceMode: ContentSourceMode = ContentSourceMode.PUBLIC_INDEX,
    val youtubeChannelId: String = DEFAULT_YOUTUBE_CHANNEL_ID,
    val youtubeApiKey: String = "",
    val catalogRefreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    val allowOfflineCache: Boolean = true,
    val contentSyncPolicy: ContentSyncPolicy = ContentSyncPolicy.NOTIFY_FIRST,
    val notifyOnNewContent: Boolean = true,
    val allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS
) {
    companion object {
        const val DEFAULT_INDEX_URL = "https://drive.google.com/uc?export=download&id=published_catalog_json"
        const val DEFAULT_YOUTUBE_CHANNEL_ID = "UC_NOORIX_OFFICIAL_CHANNEL"
        const val DEFAULT_REFRESH_INTERVAL_MS = 60 * 60 * 1000L // 1 hour

        val DEFAULT_ALLOWED_HOSTS: Set<String> = setOf(
            "drive.google.com",
            "drive.usercontent.google.com",
            "doc-0s-9g-docs.googleusercontent.com",
            "docs.google.com",
            "storage.googleapis.com",
            "content.googleapis.com",
            "www.googleapis.com",
            "youtube.com",
            "www.youtube.com",
            "img.youtube.com",
            "i.ytimg.com",
            "raw.githubusercontent.com"
        )
    }
}

/**
 * Preferences manager for persisting and loading Noorix Cloud & YouTube configuration.
 */
object CloudContentPrefs {
    private const val PREFS_NAME = "noorix_cloud_content_prefs"
    private const val KEY_INDEX_URL = "cloud_content_index_url"
    private const val KEY_DRIVE_FOLDER_ID = "cloud_drive_root_folder_id"
    private const val KEY_SOURCE_MODE = "cloud_content_source_mode"
    private const val KEY_YT_CHANNEL_ID = "cloud_youtube_channel_id"
    private const val KEY_YT_API_KEY = "cloud_youtube_api_key"
    private const val KEY_REFRESH_INTERVAL = "cloud_catalog_refresh_interval_ms"
    private const val KEY_ALLOW_OFFLINE_CACHE = "cloud_allow_offline_cache"
    private const val KEY_SYNC_POLICY = "cloud_content_sync_policy"
    private const val KEY_NOTIFY_NEW = "cloud_notify_on_new_content"

    fun load(context: Context): CloudContentConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val modeStr = prefs.getString(KEY_SOURCE_MODE, null)
        val mode = if (modeStr != null) {
            runCatching { ContentSourceMode.valueOf(modeStr) }.getOrDefault(ContentSourceMode.PUBLIC_INDEX)
        } else {
            ContentSourceMode.PUBLIC_INDEX
        }

        val policyStr = prefs.getString(KEY_SYNC_POLICY, null)
        val policy = if (policyStr != null) {
            runCatching { ContentSyncPolicy.valueOf(policyStr) }.getOrDefault(ContentSyncPolicy.NOTIFY_FIRST)
        } else {
            ContentSyncPolicy.NOTIFY_FIRST
        }

        return CloudContentConfig(
            contentIndexUrl = prefs.getString(KEY_INDEX_URL, null)
                ?: CloudContentConfig.DEFAULT_INDEX_URL,
            driveRootFolderId = prefs.getString(KEY_DRIVE_FOLDER_ID, "") ?: "",
            contentSourceMode = mode,
            youtubeChannelId = prefs.getString(KEY_YT_CHANNEL_ID, null)
                ?: CloudContentConfig.DEFAULT_YOUTUBE_CHANNEL_ID,
            youtubeApiKey = prefs.getString(KEY_YT_API_KEY, "") ?: "",
            catalogRefreshIntervalMs = prefs.getLong(
                KEY_REFRESH_INTERVAL,
                CloudContentConfig.DEFAULT_REFRESH_INTERVAL_MS
            ),
            allowOfflineCache = prefs.getBoolean(KEY_ALLOW_OFFLINE_CACHE, true),
            contentSyncPolicy = policy,
            notifyOnNewContent = prefs.getBoolean(KEY_NOTIFY_NEW, true)
        )
    }

    fun save(context: Context, config: CloudContentConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_INDEX_URL, config.contentIndexUrl)
            .putString(KEY_DRIVE_FOLDER_ID, config.driveRootFolderId)
            .putString(KEY_SOURCE_MODE, config.contentSourceMode.name)
            .putString(KEY_YT_CHANNEL_ID, config.youtubeChannelId)
            .putString(KEY_YT_API_KEY, config.youtubeApiKey)
            .putLong(KEY_REFRESH_INTERVAL, config.catalogRefreshIntervalMs)
            .putBoolean(KEY_ALLOW_OFFLINE_CACHE, config.allowOfflineCache)
            .putString(KEY_SYNC_POLICY, config.contentSyncPolicy.name)
            .putBoolean(KEY_NOTIFY_NEW, config.notifyOnNewContent)
            .apply()
    }
}
