package com.example

import com.example.core.model.AcademicState
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.core.model.MasteryStatus
import com.example.core.model.StudentModel
import com.example.domain.analytics.LearningAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Pure-JVM tests for [LearningAnalyticsEngine] (UTC day buckets). */
class LearningAnalyticsEngineTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val dayMs = 86_400_000L
    private val todayNoonUtc = (1_700_000_000_000L / dayMs) * dayMs + 12 * 3_600_000L

    private var idCounter = 0

    private fun event(
        type: EventType,
        daysAgo: Int = 0,
        payload: String = "{}"
    ): LearningEvent {
        idCounter += 1
        return LearningEvent(
            eventId = "evt-$idCounter",
            eventType = type,
            studentId = "student-101",
            courseVersionId = "course-version:mathematics-4024:2025-2027",
            hlcTimestamp = "0:0:test",
            physicalMs = todayNoonUtc - daysAgo * dayMs,
            payloadJson = payload
        )
    }

    @Test
    fun `weekly activity always has seven buckets ending today`() {
        val weekly = LearningAnalyticsEngine.computeWeeklyActivity(
            emptyList(), nowMs = todayNoonUtc, timeZone = utc
        )
        assertEquals(7, weekly.size)
        assertTrue(weekly.last().isToday)
        assertTrue(weekly.dropLast(1).none { it.isToday })
    }

    @Test
    fun `events land in the correct day buckets`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 0),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 0),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, daysAgo = 3)
        )
        val weekly = LearningAnalyticsEngine.computeWeeklyActivity(
            events, nowMs = todayNoonUtc, timeZone = utc
        )
        assertEquals(2, weekly.last().eventCount) // today
        assertEquals(1, weekly[weekly.size - 1 - 3].eventCount) // 3 days ago
        assertEquals(0, weekly.first().eventCount)
    }

    @Test
    fun `infra events are excluded from activity buckets`() {
        val events = listOf(
            event(EventType.SYNC_BATCH_MERGED, daysAgo = 0),
            event(EventType.CLOCK_ADVANCED_SIMULATED, daysAgo = 0)
        )
        val weekly = LearningAnalyticsEngine.computeWeeklyActivity(
            events, nowMs = todayNoonUtc, timeZone = utc
        )
        assertTrue(weekly.all { it.eventCount == 0 })
    }

    @Test
    fun `accuracy blends quizzes transfers and proofs`() {
        val events = listOf(
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":true}"""),
            event(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload = """{"isCorrect":false}"""),
            event(EventType.TRANSFER_TASK_EVALUATED, payload = """{"passed":true}"""),
            event(EventType.SYMBOLIC_VERIFICATION_COMPLETED, payload = """{"isValid":false}""")
        )
        val (answered, correct) = LearningAnalyticsEngine.computeAccuracy(events)
        assertEquals(4, answered)
        assertEquals(2, correct)

        val snap = LearningAnalyticsEngine.computeSnapshot(events, StudentModel())
        assertEquals(50, snap.accuracyPercent)
        assertEquals(2, snap.questionsAnswered)
        assertEquals(1, snap.questionsCorrect)
        assertEquals(0, snap.proofsVerified)
        assertEquals(1, snap.transferPassed)
    }

    @Test
    fun `concept rows sort by mastery descending`() {
        val model = StudentModel(
            academicStates = mapOf(
                "c1" to AcademicState(
                    conceptId = "c1",
                    conceptName = "Weak",
                    baseMastery = 0.3,
                    effectiveMastery = 0.3,
                    status = MasteryStatus.EMERGING,
                    evidenceCount = 2
                ),
                "c2" to AcademicState(
                    conceptId = "c2",
                    conceptName = "Strong",
                    baseMastery = 0.9,
                    effectiveMastery = 0.9,
                    status = MasteryStatus.MASTERED,
                    evidenceCount = 5
                )
            )
        )
        val rows = LearningAnalyticsEngine.computeConceptRows(model)
        assertEquals(2, rows.size)
        assertEquals("c2", rows[0].conceptId)
        assertEquals("c1", rows[1].conceptId)

        val snap = LearningAnalyticsEngine.computeSnapshot(emptyList(), model)
        assertEquals(0.6, snap.averageMastery, 0.001)
    }

    @Test
    fun `concept rows fall back to readable names`() {
        val model = StudentModel(
            academicStates = mapOf(
                "concept:4024:simultaneous-equations" to AcademicState(
                    conceptId = "concept:4024:simultaneous-equations",
                    effectiveMastery = 0.5,
                    status = MasteryStatus.DEVELOPING
                )
            )
        )
        val rows = LearningAnalyticsEngine.computeConceptRows(model)
        assertEquals("Simultaneous equations", rows[0].conceptName)
    }
}
