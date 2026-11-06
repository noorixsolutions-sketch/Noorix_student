package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity caching the complete published catalog JSON along with ETag and metadata.
 * Provides offline access to the curriculum structure when network is unavailable.
 */
@Entity(tableName = "cached_catalog")
data class CachedCatalogEntity(
    @PrimaryKey val id: String = PRIMARY_KEY,
    val catalogJson: String,
    val catalogVersion: String,
    val curriculumId: String,
    val etag: String?,
    val sourceUrl: String,
    val cachedAtMs: Long = System.currentTimeMillis(),
    val publishedAt: String? = null
) {
    companion object {
        const val PRIMARY_KEY = "primary_catalog"
    }
}

/**
 * Room entity storing metadata of downloaded learning artifacts (PDF notes, study packs, quizzes).
 * Tracks local file path, checksum, file size, download time, and verification status.
 */
@Entity(
    tableName = "downloaded_artifact_metadata",
    indices = [
        Index(value = ["topicId"]),
        Index(value = ["subjectId"]),
        Index(value = ["status"])
    ]
)
data class DownloadedArtifactMetadataEntity(
    @PrimaryKey val artifactId: String,
    val topicId: String,
    val subjectId: String = "",
    val curriculumId: String = "",
    val artifactType: String, // e.g. "STUDY_PACK", "QUIZ", "NOTES", "WORKSHEET"
    val format: String, // e.g. "pdf", "json"
    val version: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val downloadUrl: String,
    val title: String = "",
    val downloadedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedAtMs: Long = System.currentTimeMillis(),
    val status: String = STATUS_COMPLETED // COMPLETED, FAILED, DOWNLOADING
) {
    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
    }
}

/**
 * Room entity indexing curriculum topics from the cloud catalog for fast offline SQL queries.
 */
@Entity(
    tableName = "cached_curriculum_topics",
    indices = [
        Index(value = ["topicId"], unique = true),
        Index(value = ["classId"]),
        Index(value = ["subjectId"])
    ]
)
data class CachedCurriculumTopicEntity(
    @PrimaryKey val topicId: String,
    val topicName: String,
    val subjectId: String,
    val subjectName: String,
    val classId: String,
    val className: String,
    val availableArtifactTypes: String, // Comma-separated e.g. "STUDY_PACK,QUIZ,VIDEO"
    val comingSoonArtifactTypes: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)
