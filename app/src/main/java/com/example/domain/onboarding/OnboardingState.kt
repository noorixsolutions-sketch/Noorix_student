package com.example.domain.onboarding

enum class OnboardingPhase {
    WELCOME,
    PROFILE_SETUP,
    TEST_C_CURRICULUM,
    COMPLETE
}

data class OnboardingState(
    val phase: OnboardingPhase = OnboardingPhase.WELCOME,
    val candidateName: String = "Cambridge Candidate",
    val candidateGrade: String = "O-Level Year 1 (Grade 10)",
    val candidateAge: Int = 15,
    val targetGrade: String = "A*",
    val targetExamSession: String = "May/June 2027",
    val dailyTargetMinutes: Int = 45,
    val isComplete: Boolean = false
)

object OnboardingData {
    val gradeOptions = listOf(
        "Pre-O-Level (Grade 9)",
        "O-Level Year 1 (Grade 10)",
        "O-Level Year 2 (Grade 11)",
        "Private Candidate"
    )

    val ageOptions = listOf(13, 14, 15, 16, 17, 18)

    val targetGradeOptions = listOf("A*", "A", "B", "C")

    val examSessionOptions = listOf(
        "May/June 2027",
        "Oct/Nov 2026",
        "May/June 2026",
        "Oct/Nov 2027"
    )

    val dailyMinutesOptions = listOf(30, 45, 60, 90)
}
