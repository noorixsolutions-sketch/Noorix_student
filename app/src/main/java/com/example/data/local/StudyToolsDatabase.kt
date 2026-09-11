package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Standalone database for study tools (bookmarks, notes, flashcard review
 * scheduling) — ADDITIVE.
 *
 * Kept separate from [AppDatabase] on purpose: bookmarks/notes are
 * student-owned conveniences, not part of the signed evidence chain, and a
 * fresh v1 database needs no migration from the core store.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        StudyNoteEntity::class,
        FlashcardProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudyToolsDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun flashcardProgressDao(): FlashcardProgressDao

    companion object {
        @Volatile
        private var INSTANCE: StudyToolsDatabase? = null

        fun getInstance(context: Context): StudyToolsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyToolsDatabase::class.java,
                    "study_tools.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
