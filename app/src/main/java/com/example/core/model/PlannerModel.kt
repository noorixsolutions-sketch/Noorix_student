package com.example.core.model

enum class PlanStatus {
    DRAFT,
    ACTIVE,
    EXECUTING,
    OBSERVING,
    EVALUATED,
    REVISED,
    COMPLETED
}

enum class PlanClassification {
    ON_TRACK,
    ASSESSMENT_ERROR,
    PLAN_ERROR,
    EXECUTION_ISSUE
}

data class PlanPrediction(
    val expectedMasteryGain: Double = 0.18,
    val expectedAccuracy: Double = 0.80,
    val expectedDurationMinutes: Int = 25,
    val expectedCompletionProbability: Double = 0.78
)

data class PlanObservation(
    val observedMasteryGain: Double = 0.0,
    val observedAccuracy: Double = 0.0,
    val observedDurationMinutes: Int = 0,
    val completion: Boolean = true
)

data class PlanDeviation(
    val deltaMasteryGain: Double = 0.0,
    val deltaAccuracy: Double = 0.0,
    val deltaDurationMinutes: Int = 0
)

data class LearningPlan(
    val planId: String,
    val studentId: String,
    val courseVersionId: String,
    val targetConceptId: String,
    val targetConceptName: String,
    val objectiveIds: List<String>,
    val interventionType: String, // WORKED_EXAMPLES, RETRIEVAL_PRACTICE, VISUAL_EXPLANATION, TRANSFER_PRACTICE
    val predictions: PlanPrediction,
    val observation: PlanObservation? = null,
    val deviation: PlanDeviation? = null,
    val classification: PlanClassification? = null,
    val diagnosisNote: String = "",
    val confidence: Double = 0.75,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdAtHlc: String = ""
)

data class InterventionEfficacy(
    val interventionType: String,
    val conceptId: String,
    val attempts: Int,
    val successRate: Double,
    val averageMasteryGain: Double
)
