package com.example.domain.studytools

import com.example.core.model.Flashcard
import com.example.core.model.FlashcardReview
import com.example.core.model.ReviewGrade

/**
 * Spaced-repetition engine (SM-2 lite) — pure, deterministic, ADDITIVE.
 *
 * Powers "Review Mode" in the Learning Hub: cards the student struggles
 * with resurface tomorrow, while mastered cards stretch out over weeks.
 */
object FlashcardReviewEngine {

    const val DAY_MS: Long = 86_400_000L
    const val MIN_EASE: Double = 1.3

    /**
     * Applies one self-grade to a card's scheduling state (SM-2 update).
     */
    fun grade(
        previous: FlashcardReview?,
        cardId: String,
        conceptId: String,
        grade: ReviewGrade,
        nowMs: Long = System.currentTimeMillis()
    ): FlashcardReview {
        val prev = previous ?: FlashcardReview(cardId = cardId, conceptId = conceptId)
        return when (grade) {
            ReviewGrade.AGAIN -> prev.copy(
                repetitions = 0,
                intervalDays = 0,
                // Keep ease (slightly penalised) so the card returns tomorrow.
                easeFactor = (prev.easeFactor - 0.15).coerceAtLeast(MIN_EASE),
                nextDueMs = nowMs + DAY_MS,
                lastGrade = grade,
                lastReviewedMs = nowMs,
                totalReviews = prev.totalReviews + 1
            )
            ReviewGrade.HARD,
            ReviewGrade.GOOD,
            ReviewGrade.EASY -> {
                val quality = when (grade) {
                    ReviewGrade.HARD -> 3
                    ReviewGrade.GOOD -> 4
                    else -> 5
                }
                val ease = (prev.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
                    .coerceAtLeast(MIN_EASE)
                val repetitions = prev.repetitions + 1
                val interval = when {
                    repetitions == 1 -> 1
                    repetitions == 2 -> if (grade == ReviewGrade.EASY) 4 else 3
                    else -> (prev.intervalDays * ease).toInt().coerceAtLeast(1)
                }
                prev.copy(
                    easeFactor = (ease * 100).toInt() / 100.0,
                    intervalDays = interval,
                    repetitions = repetitions,
                    nextDueMs = nowMs + interval * DAY_MS,
                    lastGrade = grade,
                    lastReviewedMs = nowMs,
                    totalReviews = prev.totalReviews + 1
                )
            }
        }
    }

    /**
     * Orders a review session: overdue cards first (most overdue at the
     * front), then never-reviewed cards, each group shuffled deterministically
     * by card id so sessions are stable and testable.
     */
    fun sessionOrder(
        cards: List<Flashcard>,
        progressByCardId: Map<String, FlashcardReview>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Flashcard> {
        val (due, fresh) = cards.partition { card ->
            val progress = progressByCardId[card.id]
            progress == null || progress.isDue(nowMs)
        }
        val overdueFirst = due.sortedWith(
            compareBy(
                { progressByCardId[it.id]?.nextDueMs ?: Long.MIN_VALUE },
                { it.id }
            )
        )
        return overdueFirst + fresh.sortedBy { it.id }
    }

    /** Cards currently due for review (including never-reviewed). */
    fun dueCards(
        cards: List<Flashcard>,
        progressByCardId: Map<String, FlashcardReview>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Flashcard> = cards.filter { card ->
        val progress = progressByCardId[card.id]
        progress == null || progress.isDue(nowMs)
    }

    /** Human label for when a card returns, e.g. "tomorrow" / "in 6 days". */
    fun dueLabel(review: FlashcardReview, nowMs: Long = System.currentTimeMillis()): String {
        if (review.nextDueMs <= nowMs) return "due now"
        val days = ((review.nextDueMs - nowMs + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
        return when (days) {
            1 -> "tomorrow"
            else -> "in $days days"
        }
    }
}
