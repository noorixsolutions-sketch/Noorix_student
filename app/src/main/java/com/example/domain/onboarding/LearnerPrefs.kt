package com.example.domain.onboarding

import android.content.Context

/**
 * On-device persistence for the learner's mode and tiny profile
 * (docs/two-mode-experience.md §4.1).
 *
 * Deliberately simple SharedPreferences — this is a UX convenience (never replay
 * onboarding), not a trust boundary. The evidence of record stays in the event
 * log / audit chain, and the STUDENT_ONBOARDED event keeps its payload contract
 * with ProjectionEngine.
 */
object LearnerPrefs {

    private const val FILE = "learner_profile"
    private const val KEY_COMPLETE = "onboarding_complete"
    private const val KEY_MODE = "learner_mode"
    private const val KEY_NAME = "learner_name"
    private const val KEY_AGE = "learner_age"
    private const val KEY_STAGE = "learner_stage"
    private const val KEY_TARGET_GRADE = "target_grade"
    private const val KEY_EXAM_SESSION = "target_exam_session"
    private const val KEY_DAILY_MINUTES = "daily_minutes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, state: OnboardingState) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETE, state.isComplete)
            .putString(KEY_MODE, (state.mode ?: LearnerMode.EXPLORE).name)
            .putString(KEY_NAME, state.learnerName)
            .putInt(KEY_AGE, state.age ?: -1)
            .putString(KEY_STAGE, state.stage)
            .putString(KEY_TARGET_GRADE, state.targetGrade)
            .putString(KEY_EXAM_SESSION, state.targetExamSession)
            .putInt(KEY_DAILY_MINUTES, state.dailyTargetMinutes)
            .apply()
    }

    /** Returns the completed state for a returning learner, or null. */
    fun load(context: Context): OnboardingState? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_COMPLETE, false)) return null
        return OnboardingState(
            phase = OnboardingPhase.COMPLETE,
            mode = runCatching { LearnerMode.valueOf(p.getString(KEY_MODE, LearnerMode.EXPLORE.name)!!) }
                .getOrDefault(LearnerMode.EXPLORE),
            learnerName = p.getString(KEY_NAME, "").orEmpty(),
            age = p.getInt(KEY_AGE, -1).takeIf { it >= 0 },
            stage = p.getString(KEY_STAGE, "").orEmpty(),
            targetGrade = p.getString(KEY_TARGET_GRADE, "").orEmpty(),
            targetExamSession = p.getString(KEY_EXAM_SESSION, "").orEmpty(),
            dailyTargetMinutes = p.getInt(KEY_DAILY_MINUTES, 45),
            isComplete = true
        )
    }

    /** True once a learner has completed onboarding in either mode. */
    fun isComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETE, false)

    /** True when name/age/stage were captured (lets a returning learner skip the profile step). */
    fun hasProfile(context: Context): Boolean {
        val p = prefs(context)
        return p.getInt(KEY_AGE, -1) >= 0 && (p.getString(KEY_STAGE, "").orEmpty().isNotBlank())
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
