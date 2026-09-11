package com.example.domain.analytics

import com.example.core.model.AnalyticsSnapshot
import com.example.core.model.ConceptMasteryRow
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.core.model.StudentModel
import com.example.core.model.WeeklyActivityDay
import com.example.domain.gamification.GamificationEngine
import com.example.domain.mastery.BktEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Progress-analytics engine — pure, deterministic, ADDITIVE.
 *
 * Builds the trailing-7-day activity chart, blended accuracy and per-concept
 * mastery rows from existing data. No Android dependencies.
 */
object LearningAnalyticsEngine {

    const val WEEK_DAYS: Int = 7

    private val SUCCESS_PATTERNS = listOf(
        Regex("\"isCorrect\"\\s*:\\s*true"),
        Regex("\"passed\"\\s*:\\s*true"),
        Regex("\"isValid\"\\s*:\\s*true")
    )

    private fun isSuccess(payloadJson: String): Boolean =
        SUCCESS_PATTERNS.any { it.containsMatchIn(payloadJson) }

    private fun startOfDayMs(timestampMs: Long, timeZone: TimeZone): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun computeWeeklyActivity(
        events: List<LearningEvent>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<WeeklyActivityDay> {
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        labelFmt.timeZone = timeZone
        val todayStart = startOfDayMs(nowMs, timeZone)
        val dayMs = 86_400_000L

        return (WEEK_DAYS - 1 downTo 0).map { back ->
            val bucketStart = todayStart - back * dayMs
            val bucketEnd = bucketStart + dayMs
            val bucketEvents = events.filter {
                GamificationEngine.isLearningEvent(it) &&
                    it.physicalMs >= bucketStart && it.physicalMs < bucketEnd
            }
            WeeklyActivityDay(
                dayLabel = labelFmt.format(Date(bucketStart)),
                eventCount = bucketEvents.size,
                xp = bucketEvents.sumOf { GamificationEngine.xpForEvent(it) },
                minutesEstimated = (bucketEvents.size * GamificationEngine.MINUTES_PER_EVENT)
                    .coerceAtMost(GamificationEngine.MAX_MINUTES_PER_DAY),
                isToday = back == 0
            )
        }
    }

    /**
     * Blended accuracy across diagnostic answers, transfer tasks and
     * symbolic proofs. Returns Pair(answered, correct).
     */
    fun computeAccuracy(events: List<LearningEvent>): Pair<Int, Int> {
        var answered = 0
        var correct = 0
        for (event in events) {
            when (event.eventType) {
                EventType.DIAGNOSTIC_ITEM_SUBMITTED,
                EventType.TRANSFER_TASK_EVALUATED,
                EventType.SYMBOLIC_VERIFICATION_COMPLETED -> {
                    answered += 1
                    if (isSuccess(event.payloadJson)) correct += 1
                }
                else -> Unit
            }
        }
        return answered to correct
    }

    fun computeConceptRows(
        model: StudentModel,
        limit: Int = 6,
        events: List<LearningEvent> = emptyList()
    ): List<ConceptMasteryRow> {
        val bktByConcept = if (events.isEmpty()) emptyMap() else BktEngine.plForAllConcepts(events)
        return model.academicStates.values
            .sortedByDescending { it.effectiveMastery }
            .take(limit.coerceAtLeast(1))
            .map { state ->
                ConceptMasteryRow(
                    conceptId = state.conceptId,
                    conceptName = state.conceptName.ifEmpty {
                        state.conceptId.substringAfterLast(":")
                            .replace("-", " ")
                            .replaceFirstChar { c -> c.uppercase() }
                    },
                    mastery = state.effectiveMastery,
                    status = state.status,
                    evidenceCount = state.evidenceCount,
                    bktMastery = bktByConcept[state.conceptId] ?: state.effectiveMastery
                )
            }
    }

    fun computeSnapshot(
        events: List<LearningEvent>,
        model: StudentModel,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AnalyticsSnapshot {
        val weekly = computeWeeklyActivity(events, nowMs, timeZone)
        val (answered, correct) = computeAccuracy(events)
        val rows = computeConceptRows(model, events = events)
        val avg = if (model.academicStates.isNotEmpty()) {
            model.academicStates.values.map { it.effectiveMastery }.average()
        } else 0.0
        return AnalyticsSnapshot(
            weeklyActivity = weekly,
            eventsThisWeek = weekly.sumOf { it.eventCount },
            xpThisWeek = weekly.sumOf { it.xp },
            minutesThisWeekEstimated = weekly.sumOf { it.minutesEstimated },
            activeDaysThisWeek = weekly.count { it.eventCount > 0 },
            accuracyPercent = if (answered == 0) 0 else ((correct * 100) / answered).coerceIn(0, 100),
            questionsAnswered = events.count { it.eventType == EventType.DIAGNOSTIC_ITEM_SUBMITTED },
            questionsCorrect = events.count {
                it.eventType == EventType.DIAGNOSTIC_ITEM_SUBMITTED && isSuccess(it.payloadJson)
            },
            proofsVerified = events.count {
                it.eventType == EventType.SYMBOLIC_VERIFICATION_COMPLETED && isSuccess(it.payloadJson)
            },
            transferPassed = events.count {
                it.eventType == EventType.TRANSFER_TASK_EVALUATED && isSuccess(it.payloadJson)
            },
            conceptRows = rows,
            averageMastery = avg
        )
    }
}
