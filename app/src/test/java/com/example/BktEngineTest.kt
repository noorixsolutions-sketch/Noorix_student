package com.example

import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.domain.mastery.BktEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for [BktEngine] (§7 Bayesian Knowledge Tracing). */
class BktEngineTest {

    @Test
    fun `single update matches the BKT equations`() {
        // prior 0.5, correct: post = 0.46/0.56 = 0.8214, +transition = 0.8429
        assertEquals(0.843, BktEngine.update(0.5, true), 0.001)
        // prior 0.5, incorrect: post = 0.04/0.44 = 0.0909, +transition = 0.2
        assertEquals(0.2, BktEngine.update(0.5, false), 0.001)
    }

    @Test
    fun `output stays within probability bounds`() {
        assertTrue(BktEngine.update(0.0, true) in 0.0..1.0)
        assertTrue(BktEngine.update(1.0, false) in 0.0..1.0)
        assertTrue(BktEngine.update(-3.0, true) in 0.0..1.0)
        assertTrue(BktEngine.update(99.0, false) in 0.0..1.0)
    }

    @Test
    fun `correct streaks raise PL and wrong streaks lower it`() {
        val up = BktEngine.tracePL(listOf(true, true, true))
        val down = BktEngine.tracePL(listOf(false, false, false))
        assertTrue(up > BktEngine.DEFAULT_PARAMS.pL0)
        assertTrue(down < BktEngine.DEFAULT_PARAMS.pL0)
        assertTrue(up > down)
    }

    @Test
    fun `empty trace returns the prior`() {
        assertEquals(BktEngine.DEFAULT_PARAMS.pL0, BktEngine.tracePL(emptyList()), 0.0)
    }

    private var idCounter = 0

    private fun attempt(conceptId: String, correct: Boolean, ts: Long): LearningEvent {
        idCounter += 1
        return LearningEvent(
            eventId = "evt-$idCounter",
            eventType = EventType.DIAGNOSTIC_ITEM_SUBMITTED,
            studentId = "student-101",
            courseVersionId = "course-version:mathematics-4024:2025-2027",
            hlcTimestamp = "$ts:0:test",
            physicalMs = ts,
            payloadJson = """{"conceptId":"$conceptId","isCorrect":$correct}"""
        )
    }

    @Test
    fun `plForConcept folds chronological attempts per concept`() {
        val events = listOf(
            attempt("c:strong", true, 1000L),
            attempt("c:strong", true, 2000L),
            attempt("c:weak", false, 1500L),
            attempt("c:weak", false, 2500L)
        )
        val strong = BktEngine.plForConcept(events, "c:strong")!!
        val weak = BktEngine.plForConcept(events, "c:weak")!!
        assertTrue(strong > weak)
        assertTrue(strong > BktEngine.DEFAULT_PARAMS.pL0)
    }

    @Test
    fun `plForConcept returns null without graded attempts`() {
        val events = listOf(
            LearningEvent(
                eventId = "e1",
                eventType = EventType.PLAN_CREATED,
                studentId = "s",
                courseVersionId = "c",
                hlcTimestamp = "0:0:t",
                physicalMs = 1L,
                payloadJson = """{"conceptId":"c:x"}"""
            )
        )
        assertNull(BktEngine.plForConcept(events, "c:x"))
    }

    @Test
    fun `recentWrongCount looks only at the latest attempts`() {
        val events = listOf(
            attempt("c:a", false, 1000L),
            attempt("c:a", false, 2000L),
            attempt("c:a", true, 3000L),
            attempt("c:a", true, 4000L),
            attempt("c:a", true, 5000L),
            attempt("c:a", false, 6000L)
        )
        assertEquals(1, BktEngine.recentWrongCount(events, n = 3))
        assertEquals(3, BktEngine.recentWrongCount(events, n = 10))
    }

    @Test
    fun `plForAllConcepts keys every attempted concept`() {
        val events = listOf(
            attempt("c:a", true, 1000L),
            attempt("c:b", false, 2000L)
        )
        val all = BktEngine.plForAllConcepts(events)
        assertEquals(setOf("c:a", "c:b"), all.keys)
        assertTrue(all.getValue("c:a") > all.getValue("c:b"))
    }
}
