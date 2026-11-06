package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for indexing and browsing the K-12 curriculum JSON files
 * (Bands, Grades, Subjects, Topics, Index Metadata, Topic Mastery, and Immutable
 * Curriculum Interaction Events).
 */
@Database(
    entities = [
        CurriculumIndexMetaEntity::class,
        CurriculumBandEntity::class,
        CurriculumGradeEntity::class,
        CurriculumSubjectEntity::class,
        CurriculumTopicEntity::class,
        CurriculumTopicMasteryEntity::class,
        CurriculumInteractionEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CurriculumDatabase : RoomDatabase() {

    abstract fun curriculumDao(): CurriculumDao

    companion object {
        private const val DB_NAME = "k12_curriculum_index.db"

        @Volatile
        private var INSTANCE: CurriculumDatabase? = null

        fun getInstance(context: Context): CurriculumDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CurriculumDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun setInstanceForTesting(database: CurriculumDatabase?) {
            synchronized(this) {
                INSTANCE = database
            }
        }
    }
}
