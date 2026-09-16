package com.example.domain.gamification

import com.example.core.model.BadgeId
import com.example.core.model.EventType
import com.example.core.model.GamificationSnapshot
import com.example.core.model.LearningEvent
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Gamification engine — pure, deterministic, ADDITIVE.
 *
 * Derives XP, levels, streaks and badges from the existing immutable event
 * log. No Android dependencies: safe for plain JVM unit tests.
 */
object GamificationEngine {

    /** Minutes of study credited per learning event (documented heuristic). */
    const val MINUTES_PER_EVENT: Int = 2

    /** Maximum minutes credited per day (prevents degenerate farming). */
    const val MAX_MINUTES_PER_DAY: Int = 180

    // ------------------------------------------------------------------
    // Tiny payload readers (regex-based so this stays pure-JVM testable).
    // ------------------------------------------------------------------

    private val TRUE_PATTERNS = listOf(
        Regex("\"isCorrect\"\\s*:\\s*true"),
        Regex("\"passed\"\\s*:\\s*true"),
        Regex("\"isValid\"\\s*:\\s*true")
    )

    private fun payloadIsSuccess(payloadJson: String): Boolean =
        TRUE_PATTERNS.any { it.containsMatchIn(payloadJson) }

    // ------------------------------------------------------------------
    // XP rules
    // ------------------------------------------------------------------

    /**
     * XP awarded for a single event. Correct/valid/passing outcomes earn a
     * bonus so effort is rewarded and accuracy is celebrated.
     */
    fun xpForEvent(event: LearningEvent): Int {
        val success = payloadIsSuccess(event.payloadJson)
        return when (event.eventType) {
            EventType.DIAGNOSTIC_ITEM_SUBMITTED -> if (success) 20 else 10
            EventType.SYMBOLIC_VERIFICATION_COMPLETED -> if (success) 15 else 5
            EventType.TRANSFER_TASK_EVALUATED -> if (success) 20 else 8
            EventType.CONCEPT_ASSESSED -> 8
            EventType.MASTERY_UPDATED -> 8
            EventType.DIAGNOSTIC_COMPLETED -> 30
            EventType.MEDIA_GENERATED -> 5
            EventType.MEDIA_CONSUMED -> 6
            EventType.TRANSFER_TASK_ATTEMPTED -> 4
            EventType.QUESTION_ATTEMPTED -> 6
            EventType.QUESTION_PRESENTED -> 1
            EventType.PLAN_CREATED -> 10
            EventType.PLAN_REVISED -> 6
            EventType.PLAN_STARTED -> 4
            EventType.PLAN_EXECUTED -> 8
            EventType.PLAN_DEVIATION_DETECTED -> 2
            EventType.MISCONCEPTION_DETECTED -> 4
            EventType.STUDENT_ONBOARDED -> 25
            EventType.COURSE_PACKAGE_INSTALLED -> 10
            EventType.DIAGNOSTIC_STARTED -> 3
            // Non-learning / infra events earn nothing.
            EventType.SYNC_BATCH_MERGED,
            EventType.CLOCK_ADVANCED_SIMULATED,
            EventType.CRYPTOGRAPHIC_AUDIT_PURGE -> 0
        }
    }

    /** Events that count toward streaks / daily goals (excludes infra noise). */
    fun isLearningEvent(event: LearningEvent): Boolean = when (event.eventType) {
        EventType.SYNC_BATCH_MERGED,
        EventType.CLOCK_ADVANCED_SIMULATED,
        EventType.CRYPTOGRAPHIC_AUDIT_PURGE,
        EventType.COURSE_PACKAGE_INSTALLED -> false
        else -> true
    }

    // ------------------------------------------------------------------
    // Level curve: level L needs 100 + (L-1)*50 XP to complete.
    // ------------------------------------------------------------------

    fun xpNeededForLevel(level: Int): Int = 100 + (level.coerceAtLeast(1) - 1) * 50

    /** Returns Triple(level, xpIntoLevel, xpForNextLevel). */
    fun levelForXp(totalXp: Int): Triple<Int, Int, Int> {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        var need = xpNeededForLevel(level)
        while (remaining >= need) {
            remaining -= need
            level += 1
            need = xpNeededForLevel(level)
        }
        return Triple(level, remaining, need)
    }

    // ------------------------------------------------------------------
    // Day buckets (device-local midnight boundaries).
    // ------------------------------------------------------------------

    fun dayIndexOf(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }

    // ------------------------------------------------------------------
    // Snapshot
    // ------------------------------------------------------------------

    fun emptySnapshot(dailyTargetMinutes: Int = 45): GamificationSnapshot =
        GamificationSnapshot(dailyTargetMinutes = dailyTargetMinutes)

    /**
     * Computes the full snapshot from the event log.
     *
     * @param masteredConceptCount number of concepts at >=85% effective mastery
     *        (passed in from the projected StudentModel to keep this pure).
     */
    fun computeSnapshot(
        events: List<LearningEvent>,
        dailyTargetMinutes: Int = 45,
        masteredConceptCount: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): GamificationSnapshot {
        if (events.isEmpty()) return emptySnapshot(dailyTargetMinutes)

        val totalXp = events.sumOf { xpForEvent(it) }
        val (level, intoLevel, forNext) = levelForXp(totalXp)

        val learningEvents = events.filter { isLearningEvent(it) }
        val byDay: Map<Long, List<LearningEvent>> =
            learningEvents.groupBy { dayIndexOf(it.physicalMs, timeZone) }

        val todayIdx = dayIndexOf(nowMs, timeZone)
        val sortedDays = byDay.keys.sorted()

        // Current streak: count back from today (with yesterday grace).
        var streak = 0
        var cursor = todayIdx
        if (!byDay.containsKey(cursor)) cursor -= 1 // grace: streak alive if active yesterday
        while (byDay.containsKey(cursor)) {
            streak += 1
            cursor -= 1
        }

        // Longest streak anywhere in the log.
        var longest = 0
        var run = 0
        var prev: Long? = null
        for (day in sortedDays) {
            run = if (prev != null && day == prev + 1) run + 1 else 1
            longest = maxOf(longest, run)
            prev = day
        }

        val todayEvents = byDay[todayIdx].orEmpty()
        val todayXp = todayEvents.sumOf { xpForEvent(it) }
        val todayMinutes = (todayEvents.size * MINUTES_PER_EVENT).coerceAtMost(MAX_MINUTES_PER_DAY)

        val correctAnswers = events.count {
            it.eventType == EventType.DIAGNOSTIC_ITEM_SUBMITTED && payloadIsSuccess(it.payloadJson)
        }
        val validProofs = events.count {
            it.eventType == EventType.SYMBOLIC_VERIFICATION_COMPLETED && payloadIsSuccess(it.payloadJson)
        }

        // Comeback: any gap of 3+ inactive days followed by activity.
        var comeback = false
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] - sortedDays[i - 1] >= 4) {
                comeback = true
                break
            }
        }

        val badges = buildList {
            if (learningEvents.isNotEmpty()) add(BadgeId.FIRST_STEPS)
            if (longest >= 3 || streak >= 3) add(BadgeId.DAILY_GRIND)
            if (longest >= 7 || streak >= 7) add(BadgeId.WEEK_WARRIOR)
            if (totalXp >= 100) add(BadgeId.XP_100)
            if (totalXp >= 500) add(BadgeId.XP_500)
            if (totalXp >= 1500) add(BadgeId.XP_1500)
            if (correctAnswers >= 10) add(BadgeId.SHARPSHOOTER)
            if (validProofs >= 5) add(BadgeId.MATH_PROVER)
            if (masteredConceptCount > 0) add(BadgeId.CONCEPT_MASTER)
            if (comeback) add(BadgeId.COMEBACK)
        }

        return GamificationSnapshot(
            totalXp = totalXp,
            level = level,
            xpIntoLevel = intoLevel,
            xpForNextLevel = forNext,
            streakDays = streak,
            longestStreakDays = longest,
            earnedBadges = badges,
            todayXp = todayXp,
            todayMinutesEstimated = todayMinutes,
            dailyTargetMinutes = dailyTargetMinutes
        )
    }
}
