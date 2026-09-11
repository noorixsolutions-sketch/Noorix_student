package com.example.domain.bubble

/**
 * Bubble visual-state mapping — pure, deterministic, ADDITIVE.
 *
 * Implements §3 of the Goal Architecture: the 56dp bubble's avatar color
 * reflects student state at a glance. Priority (highest first): focus mode,
 * confusion, streak-at-risk, then idle. Mock-week (🎯) and parent-message
 * (💛) badges are independent overlays.
 */
enum class BubbleVisualState {
    IDLE,
    FOCUS,
    STREAK_AT_RISK,
    CONFUSED
}

data class BubblePresentation(
    val state: BubbleVisualState,
    /** True while a Pomodoro/focus block is active. */
    val focusMode: Boolean = false,
    /** Amber halo with 1.2s pulse when the streak may break today. */
    val haloPulse: Boolean = false,
    /** 🎯 badge: a mock/exam is within 14 days. */
    val showTargetBadge: Boolean = false,
    /** 💛 badge: a parent encouragement is waiting. */
    val showHeartBadge: Boolean = false
)

object BubbleStateMapper {

    /**
     * @param streakDays current consecutive-day streak.
     * @param studiedToday whether any learning event exists today.
     * @param focusMode Pomodoro/focus block active.
     * @param confusionDetected struggle signals (repeated wrong answers, verifier failures).
     * @param mockWithin14Days a mock/exam is within 14 days.
     * @param parentMessageWaiting a parent encouragement is waiting.
     */
    fun map(
        streakDays: Int,
        studiedToday: Boolean,
        focusMode: Boolean = false,
        confusionDetected: Boolean = false,
        mockWithin14Days: Boolean = false,
        parentMessageWaiting: Boolean = false
    ): BubblePresentation {
        val state = when {
            focusMode -> BubbleVisualState.FOCUS
            confusionDetected -> BubbleVisualState.CONFUSED
            streakDays > 0 && !studiedToday -> BubbleVisualState.STREAK_AT_RISK
            else -> BubbleVisualState.IDLE
        }
        return BubblePresentation(
            state = state,
            focusMode = focusMode,
            haloPulse = state == BubbleVisualState.STREAK_AT_RISK,
            showTargetBadge = mockWithin14Days,
            showHeartBadge = parentMessageWaiting
        )
    }

    /** Convenience: streak is at risk when there is a live streak but no study today. */
    fun isStreakAtRisk(streakDays: Int, studiedToday: Boolean): Boolean =
        streakDays > 0 && !studiedToday
}
