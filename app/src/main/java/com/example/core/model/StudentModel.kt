package com.example.core.model

enum class MasteryStatus {
    UNKNOWN,
    EMERGING,
    DEVELOPING,
    MASTERED,
    REGRESSED,
    UNCERTAIN
}

data class AcademicState(
    val conceptId: String,
    val conceptName: String = "",
    val baseMastery: Double = 0.0,
    val effectiveMastery: Double = 0.0,
    val confidence: Double = 0.5,
    val evidenceCount: Int = 0,
    val lastAssessedPhysicalMs: Long = 0L,
    val status: MasteryStatus = MasteryStatus.UNKNOWN,
    val difficultyLambda: Double = 0.05 // λ decay param per day
)

data class CapabilityState(
    val reasoning: Double = 0.70,
    val workingMemory: Double = 0.65,
    val processingEfficiency: Double = 0.68,
    val verbalReasoning: Double = 0.72,
    val spatialReasoning: Double = 0.60
)

data class BehaviourState(
    val persistence: Double = 0.70,
    val confidence: Double = 0.65,
    val frustrationResponse: Double = 0.60,
    val helpSeeking: Double = 0.80,
    val completionConsistency: Double = 0.75
)

data class StudentModel(
    val studentId: String = "student-101",
    val studentName: String = "Cambridge Candidate",
    val activeCourseId: String = "course:mathematics-4024",
    val courseVersionId: String = "course-version:mathematics-4024:2025-2027",
    val academicStates: Map<String, AcademicState> = emptyMap(),
    val capability: CapabilityState = CapabilityState(),
    val behaviour: BehaviourState = BehaviourState(),
    val activeMisconceptions: List<String> = emptyList(),
    val lastUpdatedHlc: String = "0:0:local"
)
