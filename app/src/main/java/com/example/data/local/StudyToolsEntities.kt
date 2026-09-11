package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Study-tools tables — ADDITIVE.
 *
 * These live in a dedicated `study_tools.db` (see [StudyToolsDatabase]) so
 * the core `olevel_runtime.db` schema, migrations and audit guarantees are
 * never touched by bookmarks / notes / review scheduling.
 */

@Entity(tableName = "study_bookmarks")
data class BookmarkEntity(
    @PrimaryKey val bookmarkId: String,
    val kind: String, // BookmarkKind.name
    val refId: String,
    val conceptId: String,
    val title: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey val noteId: String,
    val conceptId: String,
    val conceptName: String = "",
    val title: String,
    val body: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcard_progress")
data class FlashcardProgressEntity(
    @PrimaryKey val cardId: String,
    val conceptId: String,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextDueMs: Long = 0L,
    val lastGrade: String? = null, // ReviewGrade.name
    val lastReviewedMs: Long = 0L,
    val totalReviews: Int = 0
)
