package com.example

import com.example.core.model.BadgeId
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.domain.gamification.GamificationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Pure-JVM tests for [GamificationEngine] — no Android/Robolectric needed.
 * All day math runs in UTC for determinism.
 */
class GamificationEngineTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val dayMs = 86_400_000L

    /** A fixed UTC noon so +/- N days always land in distinct UTC day buckets. */
    private val todayNoonUtc = (1_700_000_000_000L / dayMs) * dayMs + 12 * 3_600_000L

    private var idCounter = 0

    private fun event(
        type: EventType,
        daysAgo: Int = 0,
        payload: String = "{}"
    ): LearningEvent {
        idCounter += 1
        val ts = todayNoonUtc - daysAgo * dayMs
        return LearningEvent(
            eventId = "evt-$idCounter",
            eventType = type,
            studentId = "student-101",
            courseVersionId = "course-version:mathematics-4024:2025-2027",
            hlcTimestamp = "$ts:0:test",
            physicalMs = ts,
            payloadJson = payload
        )
    }

    @Test
    fun `xp rewards correct answers more than attempts`() {
        val correct = event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":true}""")
        val wrong = event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":false}""")
        assertEquals(20, GamificationEngine.xpForEvent(correct))
        assertEquals(10, GamificationEngine.xpForEvent(wrong))
    }

    @Test
    fun `xp rules cover proofs transfers and milestones`() {
        assertEquals(
            15,
            GamificationEngine.xpForEvent(
                event(EventType.SYMBOLIC_VERIFICATION_COMPLETED, payload = """{"isValid":true}""")
            )
        )
        assertEquals(
            20,
            GamificationEngine.xpForEvent(
                event(EventType.TRANSFER_TASK_EVALUATED, payload = """{"passed":true}""")
            )
        )
        assertEquals(30, GamificationEngine.xpForEvent(event(EventType.DIAGNOSTIC_COMPLETED)))
        assertEquals(0, GamificationEngine.xpForEvent(event(EventType.SYNC_BATCH_MERGED)))
        assertEquals(0, GamificationEngine.xpForEvent(event(EventType.CLOCK_ADVANCED_SIMULATED)))
    }

    @Test
    fun `level curve progresses through thresholds`() {
        assertEquals(Triple(1, 0, 100), GamificationEngine.levelForXp(0))
        assertEquals(Triple(1, 99, 100), GamificationEngine.levelForXp(99))
        assertEquals(Triple(2, 0, 150), GamificationEngine.levelForXp(100))
        assertEquals(Triple(2, 50, 150), GamificationEngine.levelForXp(150))
        // 100 + 150 = 250 completes level 2 -> level 3 needs 200.
        assertEquals(Triple(3, 0, 200), GamificationEngine.levelForXp(250))
    }

    @Test
    fun `streak counts consecutive active days`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 0),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 1),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 2)
        )
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertEquals(3, snap.streakDays)
        assertEquals(3, snap.longestStreakDays)
    }

    @Test
    fun `streak survives when today is quiet but yesterday was active`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 1),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 2)
        )
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertEquals(2, snap.streakDays)
    }

    @Test
    fun `streak resets after a gap`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 0),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 5)
        )
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertEquals(1, snap.streakDays)
        assertEquals(1, snap.longestStreakDays)
    }

    @Test
    fun `first steps and xp badges unlock at thresholds`() {
        // 5 correct answers = 100 XP exactly.
        val events = List(5) {
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":true}""")
        }
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertEquals(100, snap.totalXp)
        assertTrue(snap.earnedBadges.contains(BadgeId.FIRST_STEPS))
        assertTrue(snap.earnedBadges.contains(BadgeId.XP_100))
        assertTrue(!snap.earnedBadges.contains(BadgeId.XP_500))
    }

    @Test
    fun `sharpshooter needs ten correct answers`() {
        val events = List(10) {
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":true}""")
        }
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertTrue(snap.earnedBadges.contains(BadgeId.SHARPSHOOTER))
    }

    @Test
    fun `math prover needs five valid proofs`() {
        val events = List(5) {
            event(EventType.SYMBOLIC_VERIFICATION_COMPLETED, payload = """{"isValid":true}""")
        }
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertTrue(snap.earnedBadges.contains(BadgeId.MATH_PROVER))
    }

    @Test
    fun `comeback badge unlocks after a long break`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 6),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 0)
        )
        val snap = GamificationEngine.computeSnapshot(events, nowMs = todayNoonUtc, timeZone = utc)
        assertTrue(snap.earnedBadges.contains(BadgeId.COMEBACK))
    }

    @Test
    fun `concept master badge follows projected mastery count`() {
        val events = listOf(event(EventType.DIAGNOSTIC_ITEM_SUBMITTED))
        val without = GamificationEngine.computeSnapshot(
            events, masteredConceptCount = 0, nowMs = todayNoonUtc, timeZone = utc
        )
        val with = GamificationEngine.computeSnapshot(
            events, masteredConceptCount = 1, nowMs = todayNoonUtc, timeZone = utc
        )
        assertTrue(!without.earnedBadges.contains(BadgeId.CONCEPT_MASTER))
        assertTrue(with.earnedBadges.contains(BadgeId.CONCEPT_MASTER))
    }

    @Test
    fun `empty log returns zero snapshot`() {
        val snap = GamificationEngine.computeSnapshot(emptyList(), nowMs = todayNoonUtc, timeZone = utc)
        assertEquals(0, snap.totalXp)
        assertEquals(1, snap.level)
        assertEquals(0, snap.streakDays)
        assertTrue(snap.earnedBadges.isEmpty())
    }

    @Test
    fun `daily goal progress reflects estimated minutes`() {
        // 3 events * 2 min = 6 min of a 45 min goal.
        val events = List(3) { event(EventType.DIAGNOSTIC_ITEM_SUBMITTED) }
        val snap = GamificationEngine.computeSnapshot(
            events, dailyTargetMinutes = 45, nowMs = todayNoonUtc, timeZone = utc
        )
        assertEquals(6, snap.todayMinutesEstimated)
        assertEquals(6f / 45f, snap.dailyGoalProgress, 0.001f)
        assertTrue(!snap.dailyGoalMet)
    }
}
