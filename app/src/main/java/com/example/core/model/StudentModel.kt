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

/**
 * Multi-factor student profile calibration based on student class/grade, age, target goals,
 * cognitive reasoning priors, and psychometric signals.
 */
data class StudentProfileCalibration(
    val studentGrade: String = "O-Level Year 1 (Grade 10)",
    val studentAge: Int = 15,
    val targetGrade: String = "A*",
    val targetExamSession: String = "May/June 2027",
    val dailyTargetMinutes: Int = 45,
    val priorTheta: Double = 0.50,
    val baselineVelocity: Double = 0.16, // expected mastery gain per session
    val cognitivePriors: Map<String, Double> = mapOf(
        "workingMemory" to 0.65,
        "spatialReasoning" to 0.60,
        "logicalReasoning" to 0.70,
        "processingEfficiency" to 0.68
    ),
    val psychometricPriors: Map<String, Double> = mapOf(
        "persistence" to 0.70,
        "confidence" to 0.65,
        "frustrationResponse" to 0.60,
        "metacognition" to 0.75
    )
)

/**
 * Attribution classification for closed-loop calibration discrepancies.
 */
enum class RecalibrationAttribution {
    ON_TRACK,
    CONTENT_DEFICIENCY,           // Content/pedagogy failed to meet student need (Plan Error)
    DIAGNOSTIC_STATE_MISESTIMATION, // Baseline diagnostic failed to capture prerequisite state/capability (Assessment Error)
    PACING_FATIGUE,                // Engagement duration/pacing issue (Execution Issue)
    ACCELERATED_MASTERY            // Student learning velocity exceeds baseline projection
}

/**
 * Audit record of an automated or manual recalibration event.
 */
data class RecalibrationRecord(
    val recalibrationId: String,
    val timestampMs: Long,
    val triggerSource: String, // "POST_STUDY_QUIZ", "SYMBOLIC_TUTOR", "RETENTION_DECAY", "MANUAL_CHECK"
    val conceptId: String,
    val previousIntervention: String,
    val newIntervention: String,
    val attribution: RecalibrationAttribution,
    val observedMasteryGain: Double,
    val expectedMasteryGain: Double,
    val observedAccuracy: Double,
    val expectedAccuracy: Double,
    val diagnosisNote: String,
    val actionTaken: String
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
    val lastUpdatedHlc: String = "0:0:local",
    val profileCalibration: StudentProfileCalibration = StudentProfileCalibration(),
    val recalibrationHistory: List<RecalibrationRecord> = emptyList()
)
