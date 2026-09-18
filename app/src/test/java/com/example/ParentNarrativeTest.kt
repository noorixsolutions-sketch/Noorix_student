package com.example

import com.example.domain.parent.ParentNarrativeBuilder
import com.example.domain.parent.ParentWeekStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for [ParentNarrativeBuilder] (§11 weekly note). */
class ParentNarrativeTest {

    private fun stats() = ParentWeekStats(
        studentName = "Hamza Khan",
        weekMinutes = 402,
        previousWeekMinutes = 260,
        weekEvents = 23,
        accuracyPercent = 74,
        weakestConceptName = "Circular motion",
        weakestConceptMastery = 0.42,
        strongestConceptName = "Factorisation",
        streakDays = 47,
        activeDaysThisWeek = 6,
        targetExamSession = "May/June 2027"
    )

    @Test
    fun `note leads with time and delta`() {
        val narrative = ParentNarrativeBuilder.build(stats())
        assertTrue(
            "note should open with study time, was: ${narrative.note}",
            narrative.note.startsWith("Hamza studied 6h 42m this week, up from 4h 20m last week.")
        )
    }

    @Test
    fun `note names weakest concept accuracy and streak`() {
        val narrative = ParentNarrativeBuilder.build(stats())
        assertTrue(narrative.note.contains("Circular motion"))
        assertTrue(narrative.note.contains("74%"))
        assertTrue(narrative.note.contains("47-day streak"))
        assertTrue(narrative.note.contains("May/June 2027"))
    }

    @Test
    fun `attention flags sub sixty mastery`() {
        val narrative = ParentNarrativeBuilder.build(stats())
        assertTrue(narrative.attention.any { it.contains("Circular motion") && it.contains("60%") })
    }

    @Test
    fun `quiet week produces a gentle nudge`() {
        val narrative = ParentNarrativeBuilder.build(stats().copy(weekMinutes = 0, weekEvents = 0, previousWeekMinutes = 0))
        assertTrue(narrative.note.contains("hasn't logged study time"))
        assertTrue(narrative.attention.any { it.contains("10-minute") })
        assertEquals("Next focus: a 10-minute daily habit to start the streak.", narrative.nextFocus)
    }

    @Test
    fun `next focus targets the weakest concept`() {
        val narrative = ParentNarrativeBuilder.build(stats())
        assertTrue(narrative.nextFocus.contains("Circular motion"))
    }

    @Test
    fun `note never exceeds five sentences`() {
        val narrative = ParentNarrativeBuilder.build(stats())
        val sentences = narrative.note.split(". ").size
        assertTrue("expected <= 5 sentences, got $sentences", sentences <= 5)
    }
}
