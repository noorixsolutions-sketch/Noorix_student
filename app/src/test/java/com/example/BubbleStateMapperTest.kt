package com.example

import com.example.domain.bubble.BubbleStateMapper
import com.example.domain.bubble.BubbleVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for [BubbleStateMapper] (§3 bubble state contract). */
class BubbleStateMapperTest {

    @Test
    fun `idle by default`() {
        val p = BubbleStateMapper.map(streakDays = 0, studiedToday = false)
        assertEquals(BubbleVisualState.IDLE, p.state)
        assertFalse(p.haloPulse)
        assertFalse(p.showTargetBadge)
        assertFalse(p.showHeartBadge)
    }

    @Test
    fun `live streak without study today pulses amber`() {
        val p = BubbleStateMapper.map(streakDays = 47, studiedToday = false)
        assertEquals(BubbleVisualState.STREAK_AT_RISK, p.state)
        assertTrue(p.haloPulse)
    }

    @Test
    fun `studied today keeps the bubble calm`() {
        val p = BubbleStateMapper.map(streakDays = 47, studiedToday = true)
        assertEquals(BubbleVisualState.IDLE, p.state)
        assertFalse(p.haloPulse)
    }

    @Test
    fun `focus mode wins over streak risk`() {
        val p = BubbleStateMapper.map(streakDays = 5, studiedToday = false, focusMode = true)
        assertEquals(BubbleVisualState.FOCUS, p.state)
        assertTrue(p.focusMode)
    }

    @Test
    fun `confusion wins over streak risk but not focus`() {
        val confused = BubbleStateMapper.map(streakDays = 5, studiedToday = false, confusionDetected = true)
        assertEquals(BubbleVisualState.CONFUSED, confused.state)

        val both = BubbleStateMapper.map(streakDays = 5, studiedToday = false, focusMode = true, confusionDetected = true)
        assertEquals(BubbleVisualState.FOCUS, both.state)
    }

    @Test
    fun `badges are independent overlays`() {
        val p = BubbleStateMapper.map(
            streakDays = 3,
            studiedToday = true,
            mockWithin14Days = true,
            parentMessageWaiting = true
        )
        assertEquals(BubbleVisualState.IDLE, p.state)
        assertTrue(p.showTargetBadge)
        assertTrue(p.showHeartBadge)
    }
}
