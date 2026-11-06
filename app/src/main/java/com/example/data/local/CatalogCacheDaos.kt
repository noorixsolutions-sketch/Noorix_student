package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for persisting and observing the cached published catalog JSON.
 */
@Dao
interface CatalogCacheDao {

    @Query("SELECT * FROM cached_catalog WHERE id = :id LIMIT 1")
    fun observeCachedCatalog(id: String = CachedCatalogEntity.PRIMARY_KEY): Flow<CachedCatalogEntity?>

    @Query("SELECT * FROM cached_catalog WHERE id = :id LIMIT 1")
    suspend fun getCachedCatalog(id: String = CachedCatalogEntity.PRIMARY_KEY): CachedCatalogEntity?

    @Query("SELECT catalogJson FROM cached_catalog WHERE id = :id LIMIT 1")
    suspend fun getCatalogJson(id: String = CachedCatalogEntity.PRIMARY_KEY): String?

    @Query("SELECT etag FROM cached_catalog WHERE id = :id LIMIT 1")
    suspend fun getCatalogEtag(id: String = CachedCatalogEntity.PRIMARY_KEY): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalog(catalog: CachedCatalogEntity)

    @Query("DELETE FROM cached_catalog WHERE id = :id")
    suspend fun deleteCatalog(id: String = CachedCatalogEntity.PRIMARY_KEY)
}

/**
 * Data Access Object for tracking downloaded artifact metadata and local storage usage.
 */
@Dao
interface DownloadedArtifactDao {

    @Query("SELECT * FROM downloaded_artifact_metadata ORDER BY downloadedAtMs DESC")
    fun observeAllArtifacts(): Flow<List<DownloadedArtifactMetadataEntity>>

    @Query("SELECT * FROM downloaded_artifact_metadata WHERE artifactId = :artifactId LIMIT 1")
    suspend fun getArtifactById(artifactId: String): DownloadedArtifactMetadataEntity?

    @Query("SELECT * FROM downloaded_artifact_metadata WHERE topicId = :topicId")
    fun observeArtifactsForTopic(topicId: String): Flow<List<DownloadedArtifactMetadataEntity>>

    @Query("SELECT * FROM downloaded_artifact_metadata WHERE topicId = :topicId")
    suspend fun getArtifactsForTopic(topicId: String): List<DownloadedArtifactMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(artifact: DownloadedArtifactMetadataEntity)

    @Query("DELETE FROM downloaded_artifact_metadata WHERE artifactId = :artifactId")
    suspend fun deleteArtifact(artifactId: String)

    @Query("SELECT SUM(fileSizeBytes) FROM downloaded_artifact_metadata WHERE status = 'COMPLETED'")
    fun observeTotalStorageBytes(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM downloaded_artifact_metadata WHERE status = 'COMPLETED'")
    fun observeDownloadedCount(): Flow<Int>
}

/**
 * Data Access Object for querying offline curriculum topics structure.
 */
@Dao
interface CachedCurriculumTopicDao {

    @Query("SELECT * FROM cached_curriculum_topics ORDER BY subjectId, topicId")
    fun observeAllTopics(): Flow<List<CachedCurriculumTopicEntity>>

    @Query("SELECT * FROM cached_curriculum_topics WHERE topicId = :topicId LIMIT 1")
    suspend fun getTopicById(topicId: String): CachedCurriculumTopicEntity?

    @Query("SELECT * FROM cached_curriculum_topics WHERE classId = :classId OR className LIKE '%' || :classId || '%'")
    suspend fun getTopicsForClass(classId: String): List<CachedCurriculumTopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<CachedCurriculumTopicEntity>)

    @Query("DELETE FROM cached_curriculum_topics")
    suspend fun clearTopics()
}
