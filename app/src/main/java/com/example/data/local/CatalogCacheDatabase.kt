package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for caching the published catalog JSON, storing
 * downloaded artifact metadata, and maintaining offline access to the curriculum structure.
 */
@Database(
    entities = [
        CachedCatalogEntity::class,
        DownloadedArtifactMetadataEntity::class,
        CachedCurriculumTopicEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CatalogCacheDatabase : RoomDatabase() {

    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun downloadedArtifactDao(): DownloadedArtifactDao
    abstract fun cachedCurriculumTopicDao(): CachedCurriculumTopicDao

    companion object {
        private const val DB_NAME = "noorix_catalog_cache.db"

        @Volatile
        private var INSTANCE: CatalogCacheDatabase? = null

        fun getInstance(context: Context): CatalogCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CatalogCacheDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun setInstanceForTesting(database: CatalogCacheDatabase?) {
            synchronized(this) {
                INSTANCE = database
            }
        }
    }
}
