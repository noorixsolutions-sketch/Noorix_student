package com.example.core.model

/**
 * Gamification model — ADDITIVE layer.
 *
 * All values are derived deterministically from the existing immutable
 * [LearningEvent] log, so XP / streaks / badges never need their own tables
 * and can never drift from the student's real learning history.
 */

/** Stable badge identifiers. */
enum class BadgeId {
    FIRST_STEPS,
    DAILY_GRIND,
    WEEK_WARRIOR,
    XP_100,
    XP_500,
    XP_1500,
    SHARPSHOOTER,
    MATH_PROVER,
    CONCEPT_MASTER,
    COMEBACK
}

/** Static badge metadata (presentation-safe copy lives in the UI layer). */
data class BadgeDef(
    val id: BadgeId,
    val title: String,
    val description: String
)

object BadgeCatalog {
    val all: List<BadgeDef> = listOf(
        BadgeDef(BadgeId.FIRST_STEPS, "First Steps", "Log your first learning activity"),
        BadgeDef(BadgeId.DAILY_GRIND, "Daily Grind", "Study 3 days in a row"),
        BadgeDef(BadgeId.WEEK_WARRIOR, "Week Warrior", "Study 7 days in a row"),
        BadgeDef(BadgeId.XP_100, "Rising Star", "Earn 100 XP"),
        BadgeDef(BadgeId.XP_500, "Scholar", "Earn 500 XP"),
        BadgeDef(BadgeId.XP_1500, "Cambridge Ace", "Earn 1,500 XP"),
        BadgeDef(BadgeId.SHARPSHOOTER, "Sharpshooter", "Answer 10 questions correctly"),
        BadgeDef(BadgeId.MATH_PROVER, "Math Prover", "Verify 5 proofs in Math Lab"),
        BadgeDef(BadgeId.CONCEPT_MASTER, "Concept Master", "Master any concept (85%+)"),
        BadgeDef(BadgeId.COMEBACK, "Comeback", "Study again after a 3+ day break")
    )

    fun titleFor(id: BadgeId): String = all.firstOrNull { it.id == id }?.title ?: id.name
}

/**
 * Point-in-time gamification snapshot for one student.
 *
 * @param totalXp lifetime XP earned from the event log.
 * @param level current level (1-based).
 * @param xpIntoLevel XP earned toward the current level.
 * @param xpForNextLevel XP required to complete the current level.
 * @param streakDays current consecutive-day study streak.
 * @param longestStreakDays best streak observed in the log.
 * @param earnedBadges badges unlocked so far.
 * @param todayXp XP earned today (device-local day).
 * @param todayMinutesEstimated estimated study minutes today.
 * @param dailyTargetMinutes daily goal from onboarding calibration.
 */
data class GamificationSnapshot(
    val totalXp: Int = 0,
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100,
    val streakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val earnedBadges: List<BadgeId> = emptyList(),
    val todayXp: Int = 0,
    val todayMinutesEstimated: Int = 0,
    val dailyTargetMinutes: Int = 45
) {
    /** 0..1 progress toward the daily study goal. */
    val dailyGoalProgress: Float
        get() = if (dailyTargetMinutes <= 0) 0f
        else (todayMinutesEstimated.toFloat() / dailyTargetMinutes.toFloat()).coerceIn(0f, 1f)

    /** 0..1 progress toward the next level. */
    val levelProgress: Float
        get() = if (xpForNextLevel <= 0) 0f
        else (xpIntoLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)

    val dailyGoalMet: Boolean get() = todayMinutesEstimated >= dailyTargetMinutes && dailyTargetMinutes > 0
}
