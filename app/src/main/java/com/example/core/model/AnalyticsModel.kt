package com.example.core.model

/**
 * Progress-analytics model — ADDITIVE layer.
 *
 * Derived from the existing event log + projected [StudentModel]; no new
 * event types or schema changes required.
 */

/** One day bucket of the trailing-7-day activity chart. */
data class WeeklyActivityDay(
    /** Short day label, e.g. "Mon". */
    val dayLabel: String,
    /** Number of learning events that day. */
    val eventCount: Int,
    /** XP earned that day. */
    val xp: Int,
    /** Estimated study minutes that day. */
    val minutesEstimated: Int,
    /** True when this bucket is today. */
    val isToday: Boolean = false
)

/** Per-concept mastery row for the dashboard breakdown. */
data class ConceptMasteryRow(
    val conceptId: String,
    val conceptName: String,
    /** 0..1 effective (decayed) mastery. */
    val mastery: Double,
    val status: MasteryStatus,
    val evidenceCount: Int,
    /**
     * BKT P(L) — probability the student has learned the concept (§7 Goal
     * Architecture). Defaults to [mastery] when no graded attempts exist.
     */
    val bktMastery: Double = mastery
)

/** Aggregated snapshot rendered by the Dashboard progress sections. */
data class AnalyticsSnapshot(
    val weeklyActivity: List<WeeklyActivityDay> = emptyList(),
    val eventsThisWeek: Int = 0,
    val xpThisWeek: Int = 0,
    val minutesThisWeekEstimated: Int = 0,
    val activeDaysThisWeek: Int = 0,
    /** 0..100 blended accuracy across quizzes, transfer tasks and proofs. */
    val accuracyPercent: Int = 0,
    val questionsAnswered: Int = 0,
    val questionsCorrect: Int = 0,
    val proofsVerified: Int = 0,
    val transferPassed: Int = 0,
    val conceptRows: List<ConceptMasteryRow> = emptyList(),
    /** 0..1 average effective mastery across assessed concepts. */
    val averageMastery: Double = 0.0
)
