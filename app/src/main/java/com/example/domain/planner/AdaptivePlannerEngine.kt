package com.example.domain.planner

import com.example.core.model.AcademicState
import com.example.core.model.InterventionEfficacy
import com.example.core.model.LearningPlan
import com.example.core.model.PlanClassification
import com.example.core.model.PlanDeviation
import com.example.core.model.PlanObservation
import com.example.core.model.PlanPrediction
import com.example.core.model.PlanStatus
import java.util.UUID
import kotlin.math.abs

/**
 * Adaptive Planner Engine.
 * Formulates plans with explicit predictions, compares against observed student evidence,
 * classifies deviations into ASSESSMENT_ERROR or PLAN_ERROR, and calibrates priors.
 */
object AdaptivePlannerEngine {

    // Calibration table: intervention_type -> historical records
    private val empiricalEfficacy = mutableMapOf(
        "WORKED_EXAMPLES" to InterventionEfficacy("WORKED_EXAMPLES", "concept:4024:factorisation", 12, 0.75, 0.16),
        "RETRIEVAL_PRACTICE" to InterventionEfficacy("RETRIEVAL_PRACTICE", "concept:4024:factorisation", 18, 0.82, 0.19),
        "VISUAL_EXPLANATION" to InterventionEfficacy("VISUAL_EXPLANATION", "concept:4024:factorisation", 9, 0.80, 0.14),
        "PREREQUISITE_REPAIR" to InterventionEfficacy("PREREQUISITE_REPAIR", "concept:4024:factorisation", 14, 0.88, 0.22)
    )

    fun createPlan(
        studentId: String,
        courseVersionId: String,
        targetConceptId: String,
        targetConceptName: String,
        academicState: AcademicState?,
        preferredIntervention: String? = null
    ): LearningPlan {
        val planId = "plan-" + UUID.randomUUID().toString().take(6)
        val selectedIntervention = preferredIntervention ?: selectBestIntervention(targetConceptId)

        val prior = empiricalEfficacy[selectedIntervention]
        val expectedGain = prior?.averageMasteryGain ?: 0.18
        val expectedAcc = prior?.successRate ?: 0.80

        val predictions = PlanPrediction(
            expectedMasteryGain = expectedGain,
            expectedAccuracy = expectedAcc,
            expectedDurationMinutes = 25,
            expectedCompletionProbability = 0.85
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
            createdAtHlc = System.currentTimeMillis().toString()
        )
    }

    private fun selectBestIntervention(conceptId: String): String {
        return empiricalEfficacy.values.maxByOrNull { it.averageMasteryGain }?.interventionType
            ?: "RETRIEVAL_PRACTICE"
    }

    fun evaluatePlanExecution(
        plan: LearningPlan,
        observation: PlanObservation,
        baselineAcademicState: AcademicState?
    ): LearningPlan {
        val deltaGain = observation.observedMasteryGain - plan.predictions.expectedMasteryGain
        val deltaAcc = observation.observedAccuracy - plan.predictions.expectedAccuracy
        val deltaDur = observation.observedDurationMinutes - plan.predictions.expectedDurationMinutes

        val deviation = PlanDeviation(
            deltaMasteryGain = String.format("%.3f", deltaGain).toDouble(),
            deltaAccuracy = String.format("%.2f", deltaAcc).toDouble(),
            deltaDurationMinutes = deltaDur
        )

        // Frozen spec Decision Tree: Was baseline assessment reliable?
        // If evidence count < 3 or baseline confidence < 0.65, classification = ASSESSMENT_ERROR.
        // If baseline was reliable (confidence >= 0.65, count >= 3), but student underperformed, classification = PLAN_ERROR.
        val isUnderperforming = (deltaGain < -0.08 || deltaAcc < -0.20)
        val isBaselineUnreliable = baselineAcademicState == null ||
                baselineAcademicState.evidenceCount < 3 ||
                baselineAcademicState.confidence < 0.65

        val classification: PlanClassification
        val diagnosisNote: String

        if (!observation.completion || observation.observedDurationMinutes < 5) {
            classification = PlanClassification.EXECUTION_ISSUE
            diagnosisNote = "Execution Issue: Session was interrupted or incomplete. Retain current plan parameters."
        } else if (isUnderperforming) {
            if (isBaselineUnreliable) {
                classification = PlanClassification.ASSESSMENT_ERROR
                diagnosisNote = "Assessment Error: Baseline mastery estimate was too optimistic with low evidence count (${baselineAcademicState?.evidenceCount ?: 0}). Initiating prerequisite re-assessment."
            } else {
                classification = PlanClassification.PLAN_ERROR
                diagnosisNote = "Plan Error: Intervention '${plan.interventionType}' produced deviation ($deltaAcc accuracy). Strategy inappropriate for current learning phase. Re-planning with alternative pedagogical strategy."
            }
        } else {
            classification = PlanClassification.ON_TRACK
            diagnosisNote = "Plan Succeeded: Observations closely match or exceed prediction. Model accuracy confirmed."
        }

        // Calibrate empirical priors (Meta-learning update)
        updateInterventionCalibration(plan.interventionType, plan.targetConceptId, observation)

        return plan.copy(
            observation = observation,
            deviation = deviation,
            classification = classification,
            diagnosisNote = diagnosisNote,
            status = PlanStatus.EVALUATED
        )
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
