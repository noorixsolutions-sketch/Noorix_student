package com.example.domain.planner

import com.example.core.model.AcademicState
import com.example.core.model.InterventionEfficacy
import com.example.core.model.LearningPlan
import com.example.core.model.PlanClassification
import com.example.core.model.PlanDeviation
import com.example.core.model.PlanObservation
import com.example.core.model.PlanPrediction
import com.example.core.model.PlanStage
import com.example.core.model.PlanStatus
import com.example.core.model.RecalibrationAttribution
import com.example.core.model.RecalibrationRecord
import com.example.core.model.StudentProfileCalibration
import java.util.UUID

/**
 * Adaptive Planner Engine.
 * Formulates plans with multi-factor profile priors, evaluates observed student evidence,
 * performs closed-loop discrepancy detection (Content Deficiency vs Diagnostic Misestimation),
 * and dynamic plan recalibration.
 */
object AdaptivePlannerEngine {

    // Empirical Efficacy Table: intervention_type -> historical records
    private val empiricalEfficacy = mutableMapOf(
        "WORKED_EXAMPLES" to InterventionEfficacy("WORKED_EXAMPLES", "concept:4024:factorisation", 14, 0.78, 0.17),
        "RETRIEVAL_PRACTICE" to InterventionEfficacy("RETRIEVAL_PRACTICE", "concept:4024:factorisation", 20, 0.84, 0.20),
        "VISUAL_EXPLANATION" to InterventionEfficacy("VISUAL_EXPLANATION", "concept:4024:factorisation", 11, 0.82, 0.15),
        "PREREQUISITE_REPAIR" to InterventionEfficacy("PREREQUISITE_REPAIR", "concept:4024:factorisation", 16, 0.89, 0.23),
        "TRANSFER_PRACTICE" to InterventionEfficacy("TRANSFER_PRACTICE", "concept:4024:factorisation", 8, 0.75, 0.18)
    )

    /**
     * Synthesizes an adaptive study plan tailored to the student's multi-factor profile,
     * target grade, age, and identified diagnostic gaps.
     */
    fun createPlan(
        studentId: String,
        courseVersionId: String,
        targetConceptId: String,
        targetConceptName: String,
        academicState: AcademicState?,
        profileCalibration: StudentProfileCalibration = StudentProfileCalibration(),
        identifiedGaps: List<String> = emptyList(),
        preferredIntervention: String? = null
    ): LearningPlan {
        val planId = "plan-" + UUID.randomUUID().toString().take(6)
        val selectedIntervention = preferredIntervention
            ?: if (identifiedGaps.isNotEmpty() && identifiedGaps.contains(targetConceptId)) {
                "WORKED_EXAMPLES"
            } else {
                selectBestIntervention(targetConceptId)
            }

        val prior = empiricalEfficacy[selectedIntervention]
        val baseGain = prior?.averageMasteryGain ?: 0.18
        val baseAcc = prior?.successRate ?: 0.80

        // Calibrate predictions by student baseline velocity and target grade
        val targetMultiplier = when (profileCalibration.targetGrade) {
            "A*" -> 1.2
            "A" -> 1.1
            "B" -> 0.95
            else -> 0.85
        }
        val expectedGain = (baseGain * (profileCalibration.baselineVelocity / 0.16) * targetMultiplier).coerceIn(0.10, 0.35)
        val expectedAcc = (baseAcc * (if (profileCalibration.studentAge <= 14) 0.9 else 1.0)).coerceIn(0.65, 0.95)

        val predictions = PlanPrediction(
            expectedMasteryGain = String.format("%.3f", expectedGain).toDouble(),
            expectedAccuracy = String.format("%.2f", expectedAcc).toDouble(),
            expectedDurationMinutes = profileCalibration.dailyTargetMinutes.coerceIn(20, 60),
            expectedCompletionProbability = 0.85
        )

        // Synthesize multi-stage study progression
        val stages = mutableListOf<PlanStage>()
        var stageIdx = 1

        if (identifiedGaps.isNotEmpty()) {
            identifiedGaps.take(2).forEach { gapConcept ->
                val gapName = gapConcept.substringAfterLast(":").replaceFirstChar { it.uppercase() }
                stages.add(
                    PlanStage(
                        stageIndex = stageIdx++,
                        stageTitle = "Prerequisite Repair: $gapName",
                        conceptId = gapConcept,
                        recommendedActivity = "PREREQUISITE_REPAIR",
                        targetMastery = 0.70
                    )
                )
            }
        }

        stages.add(
            PlanStage(
                stageIndex = stageIdx++,
                stageTitle = "Concept Discovery: $targetConceptName Explainer",
                conceptId = targetConceptId,
                recommendedActivity = "VIDEO_EXPLAINER",
                targetMastery = 0.65
            )
        )
        stages.add(
            PlanStage(
                stageIndex = stageIdx++,
                stageTitle = "Active Recall: $targetConceptName Flashcards & Quiz",
                conceptId = targetConceptId,
                recommendedActivity = "FLASHCARD_PRACTICE",
                targetMastery = 0.75
            )
        )
        stages.add(
            PlanStage(
                stageIndex = stageIdx++,
                stageTitle = "Deterministic Math Lab: Step Verification",
                conceptId = targetConceptId,
                recommendedActivity = "STEP_VERIFIER",
                targetMastery = 0.85
            )
        )
        stages.add(
            PlanStage(
                stageIndex = stageIdx++,
                stageTitle = "Synoptic Application: Podcast Brief & Transfer Task",
                conceptId = targetConceptId,
                recommendedActivity = "TRANSFER_TASK",
                targetMastery = 0.90
            )
        )

        return LearningPlan(
            planId = planId,
            studentId = studentId,
            courseVersionId = courseVersionId,
            targetConceptId = targetConceptId,
            targetConceptName = targetConceptName,
            objectiveIds = listOf("lo:4024:${targetConceptId.substringAfterLast(":")}:01"),
            interventionType = selectedIntervention,
            predictions = predictions,
            status = PlanStatus.ACTIVE,
            createdAtHlc = System.currentTimeMillis().toString(),
            planStages = stages,
            recalibrationCount = 0,
            lastRecalibrationReason = "Initial baseline plan synthesized from multi-factor diagnostic."
        )
    }

    private fun selectBestIntervention(conceptId: String): String {
        return empiricalEfficacy.values.maxByOrNull { it.averageMasteryGain }?.interventionType
            ?: "RETRIEVAL_PRACTICE"
    }

    /**
     * Continuous closed-loop calibration evaluator.
     * Compares observed performance against the baseline plan, detects discrepancies,
     * classifies the root cause (Content Deficiency vs Diagnostic Misestimation vs Pacing),
     * and recalibrates the plan and priors dynamically.
     */
    fun evaluatePlanExecution(
        plan: LearningPlan,
        observation: PlanObservation,
        baselineAcademicState: AcademicState? = null,
        prerequisiteAcademicState: AcademicState? = null,
        errorType: String? = null,
        triggerSource: String = "POST_STUDY_QUIZ"
    ): Pair<LearningPlan, RecalibrationRecord> {
        val deltaGain = observation.observedMasteryGain - plan.predictions.expectedMasteryGain
        val deltaAcc = observation.observedAccuracy - plan.predictions.expectedAccuracy
        val deltaDur = observation.observedDurationMinutes - plan.predictions.expectedDurationMinutes

        val deviation = PlanDeviation(
            deltaMasteryGain = String.format("%.3f", deltaGain).toDouble(),
            deltaAccuracy = String.format("%.2f", deltaAcc).toDouble(),
            deltaDurationMinutes = deltaDur
        )

        val isUnderperforming = (deltaGain < -0.06 || deltaAcc < -0.18)
        val isAccelerated = (deltaGain >= 0.08 && deltaAcc >= 0.12 && observation.observedAccuracy >= 0.85)

        // Diagnostic vs Content Deficiency Attribution:
        // Prerequisite check: is the prerequisite unverified or unconfident?
        val isPrereqUnverified = prerequisiteAcademicState == null ||
                prerequisiteAcademicState.evidenceCount < 2 ||
                prerequisiteAcademicState.confidence < 0.60 ||
                prerequisiteAcademicState.baseMastery < 0.60

        val hasAlgebraicPrereqError = errorType == "SIGN_ERROR" || errorType == "EXPANSION_ERROR"

        val classification: PlanClassification
        val attribution: RecalibrationAttribution
        val diagnosisNote: String
        val actionTaken: String
        var updatedIntervention = plan.interventionType

        when {
            !observation.completion || observation.observedDurationMinutes < 2 -> {
                classification = PlanClassification.EXECUTION_ISSUE
                attribution = RecalibrationAttribution.PACING_FATIGUE
                diagnosisNote = "Execution Issue: Session was interrupted or pacing was too rapid (< 2 min). Retaining current plan parameters."
                actionTaken = "Maintain current plan; recommend micro-study sessions."
            }

            isUnderperforming -> {
                if (isPrereqUnverified || hasAlgebraicPrereqError) {
                    classification = PlanClassification.ASSESSMENT_ERROR
                    attribution = RecalibrationAttribution.DIAGNOSTIC_STATE_MISESTIMATION
                    diagnosisNote = "Diagnostic Misestimation: Initial diagnostic baseline overestimated prerequisite mastery or missed foundational gap (${errorType ?: "prerequisite deficit"}). Evidence count on prereq was only ${prerequisiteAcademicState?.evidenceCount ?: 0}."
                    updatedIntervention = "PREREQUISITE_REPAIR"
                    actionTaken = "Swapped to PREREQUISITE_REPAIR. Inserted foundational review stage and scheduled micro-diagnostic."
                } else {
                    classification = PlanClassification.PLAN_ERROR
                    attribution = RecalibrationAttribution.CONTENT_DEFICIENCY
                    diagnosisNote = "Content Deficiency: Instructional strategy '${plan.interventionType}' failed to scaffold concept adequately (accuracy delta: ${String.format("%.2f", deltaAcc)}). Baseline prerequisite was verified, indicating pedagogical mismatch."
                    updatedIntervention = if (plan.interventionType == "RETRIEVAL_PRACTICE") "WORKED_EXAMPLES" else "VISUAL_EXPLANATION"
                    actionTaken = "Adapted pedagogical modality to $updatedIntervention and adjusted step-by-step scaffolding."
                }
            }

            isAccelerated -> {
                classification = PlanClassification.ON_TRACK
                attribution = RecalibrationAttribution.ACCELERATED_MASTERY
                diagnosisNote = "Accelerated Mastery: Observed velocity (${String.format("%.2f", observation.observedMasteryGain)}) significantly outpaced baseline projection. Mastery consolidated."
                updatedIntervention = "TRANSFER_PRACTICE"
                actionTaken = "Accelerated progression; unlocked synoptic Cambridge transfer tasks."
            }

            else -> {
                classification = PlanClassification.ON_TRACK
                attribution = RecalibrationAttribution.ON_TRACK
                diagnosisNote = "On Track: Post-study observations closely match diagnostic baseline projection (accuracy: ${(observation.observedAccuracy * 100).toInt()}%)."
                actionTaken = "Plan verified; continue along current study path."
            }
        }

        // Calibrate empirical priors (Meta-learning update)
        updateInterventionCalibration(plan.interventionType, plan.targetConceptId, observation)

        val updatedPlan = plan.copy(
            interventionType = updatedIntervention,
            observation = observation,
            deviation = deviation,
            classification = classification,
            diagnosisNote = diagnosisNote,
            status = if (isUnderperforming && attribution != RecalibrationAttribution.PACING_FATIGUE) PlanStatus.REVISED else PlanStatus.EVALUATED,
            recalibrationCount = plan.recalibrationCount + (if (isUnderperforming || isAccelerated) 1 else 0),
            lastRecalibrationReason = diagnosisNote
        )

        val recalibrationRecord = RecalibrationRecord(
            recalibrationId = "recal-" + UUID.randomUUID().toString().take(6),
            timestampMs = System.currentTimeMillis(),
            triggerSource = triggerSource,
            conceptId = plan.targetConceptId,
            previousIntervention = plan.interventionType,
            newIntervention = updatedIntervention,
            attribution = attribution,
            observedMasteryGain = observation.observedMasteryGain,
            expectedMasteryGain = plan.predictions.expectedMasteryGain,
            observedAccuracy = observation.observedAccuracy,
            expectedAccuracy = plan.predictions.expectedAccuracy,
            diagnosisNote = diagnosisNote,
            actionTaken = actionTaken
        )

        return Pair(updatedPlan, recalibrationRecord)
    }

    private fun updateInterventionCalibration(
        interventionType: String,
        conceptId: String,
        observation: PlanObservation
    ) {
        val current = empiricalEfficacy[interventionType] ?: InterventionEfficacy(
            interventionType = interventionType,
            conceptId = conceptId,
            attempts = 0,
            successRate = 0.70,
            averageMasteryGain = 0.15
        )

        val newAttempts = current.attempts + 1
        val newSuccessRate = ((current.successRate * current.attempts) + (if (observation.observedAccuracy >= 0.70) 1.0 else 0.0)) / newAttempts
        val newAvgGain = ((current.averageMasteryGain * current.attempts) + observation.observedMasteryGain) / newAttempts

        empiricalEfficacy[interventionType] = current.copy(
            attempts = newAttempts,
            successRate = String.format("%.2f", newSuccessRate).toDouble(),
            averageMasteryGain = String.format("%.3f", newAvgGain).toDouble()
        )
    }

    fun getInterventionEfficacyList(): List<InterventionEfficacy> {
        return empiricalEfficacy.values.toList()
    }
}
