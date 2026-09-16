package com.example

import com.example.core.model.Flashcard
import com.example.core.model.FlashcardReview
import com.example.core.model.ReviewGrade
import com.example.domain.studytools.FlashcardReviewEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for [FlashcardReviewEngine] (SM-2 lite scheduling). */
class FlashcardReviewEngineTest {

    private val now = 1_700_000_000_000L
    private val dayMs = FlashcardReviewEngine.DAY_MS

    private fun card(id: String) = Flashcard(
        id = id,
        conceptId = "concept:4024:factorisation",
        front = "front $id",
        back = "back $id"
    )

    @Test
    fun `again resets progress and returns card tomorrow`() {
        val prev = FlashcardReview(
            cardId = "c1",
            conceptId = "concept:4024:factorisation",
            easeFactor = 2.5,
            intervalDays = 6,
            repetitions = 3
        )
        val next = FlashcardReviewEngine.grade(prev, "c1", prev.conceptId, ReviewGrade.AGAIN, now)
        assertEquals(0, next.repetitions)
        assertEquals(0, next.intervalDays)
        assertEquals(now + dayMs, next.nextDueMs)
        assertEquals(1, next.totalReviews)
        assertTrue(next.easeFactor >= FlashcardReviewEngine.MIN_EASE)
    }

    @Test
    fun `good grades build an expanding schedule`() {
        val first = FlashcardReviewEngine.grade(null, "c1", "concept:x", ReviewGrade.GOOD, now)
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)

        val second = FlashcardReviewEngine.grade(first, "c1", "concept:x", ReviewGrade.GOOD, now + dayMs)
        assertEquals(2, second.repetitions)
        assertEquals(3, second.intervalDays)

        val third = FlashcardReviewEngine.grade(second, "c1", "concept:x", ReviewGrade.GOOD, now + 4 * dayMs)
        assertEquals(3, third.repetitions)
        assertTrue(third.intervalDays >= 3)
    }

    @Test
    fun `easy stretches the second interval further than good`() {
        val first = FlashcardReviewEngine.grade(null, "c1", "concept:x", ReviewGrade.GOOD, now)
        val easySecond = FlashcardReviewEngine.grade(first, "c1", "concept:x", ReviewGrade.EASY, now + dayMs)
        assertEquals(4, easySecond.intervalDays)
    }

    @Test
    fun `ease factor never drops below minimum`() {
        var state: FlashcardReview? = null
        repeat(10) {
            state = FlashcardReviewEngine.grade(state, "c1", "concept:x", ReviewGrade.AGAIN, now)
        }
        assertTrue(state!!.easeFactor >= FlashcardReviewEngine.MIN_EASE)
    }

    @Test
    fun `session orders overdue cards before fresh ones`() {
        val overdue = card("a-overdue")
        val fresh = card("b-fresh")
        val progress = mapOf(
            "a-overdue" to FlashcardReview(
                cardId = "a-overdue",
                conceptId = "concept:x",
                nextDueMs = now - 5 * dayMs
            )
        )
        val ordered = FlashcardReviewEngine.sessionOrder(listOf(fresh, overdue), progress, now)
        assertEquals(listOf(overdue, fresh), ordered)
    }

    @Test
    fun `due cards include never reviewed and overdue`() {
        val cards = listOf(card("a"), card("b"), card("c"))
        val progress = mapOf(
            "a" to FlashcardReview(cardId = "a", conceptId = "concept:x", nextDueMs = now - dayMs),
            "b" to FlashcardReview(cardId = "b", conceptId = "concept:x", nextDueMs = now + 10 * dayMs)
        )
        val due = FlashcardReviewEngine.dueCards(cards, progress, now).map { it.id }.toSet()
        assertEquals(setOf("a", "c"), due)
    }

    @Test
    fun `due labels are student friendly`() {
        val dueNow = FlashcardReview(cardId = "a", conceptId = "concept:x", nextDueMs = now - 1)
        val tomorrow = FlashcardReview(cardId = "b", conceptId = "concept:x", nextDueMs = now + dayMs)
        val later = FlashcardReview(cardId = "c", conceptId = "concept:x", nextDueMs = now + 6 * dayMs)
        assertEquals("due now", FlashcardReviewEngine.dueLabel(dueNow, now))
        assertEquals("tomorrow", FlashcardReviewEngine.dueLabel(tomorrow, now))
        assertEquals("in 6 days", FlashcardReviewEngine.dueLabel(later, now))
    }
}
