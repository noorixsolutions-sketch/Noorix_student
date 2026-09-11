package com.example.core.model

/**
 * Study-tools model — ADDITIVE layer (bookmarks, notes, spaced repetition).
 *
 * Persisted in a separate `study_tools.db` Room database so the core
 * `olevel_runtime.db` schema and its migrations stay untouched.
 */

enum class BookmarkKind {
    VIDEO,
    FLASHCARD,
    QUIZ,
    CONCEPT
}

data class Bookmark(
    val bookmarkId: String,
    val kind: BookmarkKind,
    /** Referenced content id (video/flashcard/quiz id or concept id). */
    val refId: String,
    val conceptId: String,
    val title: String,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        fun idFor(kind: BookmarkKind, refId: String): String = "${kind.name}:$refId"
    }
}

data class StudyNote(
    val noteId: String,
    val conceptId: String,
    val conceptName: String = "",
    val title: String,
    val body: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

/** Self-grade given after revealing a flashcard in Review Mode. */
enum class ReviewGrade {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

/**
 * Spaced-repetition state per flashcard (SM-2 lite).
 *
 * @param easeFactor SM-2 easiness factor (>= 1.3).
 * @param intervalDays current scheduling interval in days.
 * @param repetitions consecutive successful recalls.
 * @param nextDueMs epoch-ms when the card becomes due again.
 */
data class FlashcardReview(
    val cardId: String,
    val conceptId: String,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextDueMs: Long = 0L,
    val lastGrade: ReviewGrade? = null,
    val lastReviewedMs: Long = 0L,
    val totalReviews: Int = 0
) {
    /** Cards never reviewed (nextDueMs == 0) are always due. */
    fun isDue(nowMs: Long): Boolean = nextDueMs <= nowMs
}
