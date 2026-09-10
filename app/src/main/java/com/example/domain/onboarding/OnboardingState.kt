package com.example.domain.onboarding

enum class OnboardingPhase {
    WELCOME,
    PROFILE_SETUP,
    TEST_A_PSYCHOMETRIC,
    TEST_B_COGNITIVE,
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
    val testAIndex: Int = 0,
    val testBIndex: Int = 0,
    val testAResponses: List<Int> = emptyList(),
    val testBResponses: List<Boolean> = emptyList(),
    val psychometricSignals: Map<String, Float>? = null,
    val cognitiveSignals: Map<String, Float>? = null,
    val isComplete: Boolean = false
)

data class PsychometricQuestion(
    val id: String,
    val trait: String,
    val text: String
)

data class CognitiveQuestion(
    val id: String,
    val trait: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int
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

    val testA = listOf(
        PsychometricQuestion("p1", "persistence", "I keep working on a math problem even when I feel stuck."),
        PsychometricQuestion("p2", "confidence", "I believe I can master difficult mathematical concepts."),
        PsychometricQuestion("p3", "frustration_response", "I quickly get frustrated when I get an answer wrong."),
        PsychometricQuestion("p4", "metacognition", "I think about my own thinking process when solving problems.")
    )

    val testB = listOf(
        CognitiveQuestion("c1", "pattern_reasoning", "Which number comes next: 2, 4, 8, 16, ...", listOf("24", "32", "64", "20"), 1),
        CognitiveQuestion("c2", "logical_reasoning", "If all bloops are razzies and all razzies are lazzies, are all bloops lazzies?", listOf("Yes", "No", "Cannot be determined", "Sometimes"), 0),
        CognitiveQuestion("c3", "working_memory", "Remember this sequence: 7, 2, 9, 1. What was the third number?", listOf("7", "2", "9", "1"), 2),
        CognitiveQuestion("c4", "spatial_reasoning", "If you rotate a 'd' 180 degrees clockwise, what letter does it look like?", listOf("b", "p", "q", "d"), 1)
    )
}
