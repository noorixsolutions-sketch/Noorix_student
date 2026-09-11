package com.example.domain.studytools

import android.content.Context
import com.example.core.model.Bookmark
import com.example.core.model.BookmarkKind
import com.example.core.model.FlashcardReview
import com.example.core.model.ReviewGrade
import com.example.core.model.StudyNote
import com.example.data.local.BookmarkEntity
import com.example.data.local.FlashcardProgressEntity
import com.example.data.local.StudyNoteEntity
import com.example.data.local.StudyToolsDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repository for study tools — ADDITIVE.
 *
 * Owns the standalone `study_tools.db`; the core [com.example.domain.repository.LearningRepository]
 * is never modified by bookmarks, notes or review scheduling.
 */
class StudyToolsRepository(context: Context) {

    private val db = StudyToolsDatabase.getInstance(context)
    private val bookmarkDao = db.bookmarkDao()
    private val noteDao = db.studyNoteDao()
    private val progressDao = db.flashcardProgressDao()

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    val bookmarksFlow: Flow<List<Bookmark>> = bookmarkDao.allFlow().map { list ->
        list.mapNotNull { it.toModel() }
    }

    fun bookmarksForConcept(conceptId: String): Flow<List<Bookmark>> =
        bookmarkDao.forConceptFlow(conceptId).map { list -> list.mapNotNull { it.toModel() } }

    /** Toggles a bookmark; returns true when the item is bookmarked afterwards. */
    suspend fun toggleBookmark(
        kind: BookmarkKind,
        refId: String,
        conceptId: String,
        title: String
    ): Boolean {
        val id = Bookmark.idFor(kind, refId)
        return if (bookmarkDao.exists(id) > 0) {
            bookmarkDao.delete(id)
            false
        } else {
            bookmarkDao.upsert(
                BookmarkEntity(
                    bookmarkId = id,
                    kind = kind.name,
                    refId = refId,
                    conceptId = conceptId,
                    title = title.ifBlank { refId },
                    createdAtMs = System.currentTimeMillis()
                )
            )
            true
        }
    }

    // ------------------------------------------------------------------
    // Notes
    // ------------------------------------------------------------------

    fun notesForConcept(conceptId: String): Flow<List<StudyNote>> =
        noteDao.forConceptFlow(conceptId).map { list -> list.map { it.toModel() } }

    suspend fun saveNote(
        noteId: String? = null,
        conceptId: String,
        conceptName: String = "",
        title: String,
        body: String
    ): StudyNote {
        val now = System.currentTimeMillis()
        val entity = StudyNoteEntity(
            noteId = noteId ?: "note-" + UUID.randomUUID().toString().take(8),
            conceptId = conceptId,
            conceptName = conceptName,
            title = title.ifBlank { "Untitled note" },
            body = body,
            updatedAtMs = now
        )
        noteDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun deleteNote(noteId: String) {
        noteDao.delete(noteId)
    }

    // ------------------------------------------------------------------
    // Flashcard spaced repetition
    // ------------------------------------------------------------------

    val reviewProgressFlow: Flow<Map<String, FlashcardReview>> = progressDao.allFlow().map { list ->
        list.map { it.toModel() }.associateBy { it.cardId }
    }

    suspend fun gradeFlashcard(
        cardId: String,
        conceptId: String,
        grade: ReviewGrade,
        nowMs: Long = System.currentTimeMillis()
    ): FlashcardReview {
        val previous = progressDao.forCard(cardId)?.toModel()
        val updated = FlashcardReviewEngine.grade(previous, cardId, conceptId, grade, nowMs)
        progressDao.upsert(updated.toEntity())
        return updated
    }

    // ------------------------------------------------------------------
    // Mappers
    // ------------------------------------------------------------------

    private fun BookmarkEntity.toModel(): Bookmark? {
        val kind = runCatching { BookmarkKind.valueOf(kind) }.getOrNull() ?: return null
        return Bookmark(
            bookmarkId = bookmarkId,
            kind = kind,
            refId = refId,
            conceptId = conceptId,
            title = title,
            createdAtMs = createdAtMs
        )
    }

    private fun StudyNoteEntity.toModel(): StudyNote = StudyNote(
        noteId = noteId,
        conceptId = conceptId,
        conceptName = conceptName,
        title = title,
        body = body,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun FlashcardProgressEntity.toModel(): FlashcardReview = FlashcardReview(
        cardId = cardId,
        conceptId = conceptId,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        repetitions = repetitions,
        nextDueMs = nextDueMs,
        lastGrade = lastGrade?.let { runCatching { ReviewGrade.valueOf(it) }.getOrNull() },
        lastReviewedMs = lastReviewedMs,
        totalReviews = totalReviews
    )

    private fun FlashcardReview.toEntity(): FlashcardProgressEntity = FlashcardProgressEntity(
        cardId = cardId,
        conceptId = conceptId,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        repetitions = repetitions,
        nextDueMs = nextDueMs,
        lastGrade = lastGrade?.name,
        lastReviewedMs = lastReviewedMs,
        totalReviews = totalReviews
    )
}
