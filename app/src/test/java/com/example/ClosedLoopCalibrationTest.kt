package com.example

import com.example.core.model.*
import com.example.domain.diagnostic.AdaptiveDiagnosticEngine
import com.example.domain.diagnostic.DiagnosticSessionState
import com.example.domain.planner.AdaptivePlannerEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Multi-Factor Profile Calibration, Adaptive Diagnostic Synthesis,
 * and Continuous Closed-Loop Recalibration (Content Deficiency vs Diagnostic Misestimation).
 */
class ClosedLoopCalibrationTest {

    @Test
    fun `multi factor profile calibration weights grade age and cognitive signals`() {
        val grade10Age15Profile = AdaptiveDiagnosticEngine.computeMultiFactorPrior(
            grade = "O-Level Year 1 (Grade 10)",
            age = 15,
            targetGrade = "A*",
            cognitiveSignals = mapOf("working_memory" to 0.8f, "logical_reasoning" to 0.8f),
            psychometricSignals = mapOf("persistence" to 0.8f, "metacognition" to 0.8f)
        )

        val grade9Age14Profile = AdaptiveDiagnosticEngine.computeMultiFactorPrior(
            grade = "Pre-O-Level (Grade 9)",
            age = 14,
            targetGrade = "B",
            cognitiveSignals = mapOf("working_memory" to 0.5f, "logical_reasoning" to 0.5f),
            psychometricSignals = mapOf("persistence" to 0.5f, "metacognition" to 0.5f)
        )

        assertTrue(
            "Grade 10 Age 15 student with high cognitive signals should have higher prior theta than Grade 9 Age 14 student",
            grade10Age15Profile.priorTheta > grade9Age14Profile.priorTheta
        )
        assertTrue(
            "Target A* aspiration with higher cognitive priors yields higher expected baseline velocity",
            grade10Age15Profile.baselineVelocity > grade9Age14Profile.baselineVelocity
        )
    }

    @Test
    fun `adaptive diagnostic synthesizes multi-stage plan upon completion`() {
        val profile = StudentProfileCalibration(
            studentGrade = "O-Level Year 1 (Grade 10)",
            studentAge = 15,
            targetGrade = "A*",
            priorTheta = 0.55,
            baselineVelocity = 0.18
        )

        var session = AdaptiveDiagnosticEngine.initializeSession(profile)
        assertEquals(0.55, session.estimatedAbility, 0.001)

        val questions = listOf(
            QuestionItem("q1", "v1", "concept:4024:factorisation", prompt = "Q1", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.5),
            QuestionItem("q2", "v1", "concept:4024:factorisation", prompt = "Q2", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.6),
            QuestionItem("q3", "v1", "concept:4024:factorisation", prompt = "Q3", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.7),
            QuestionItem("q4", "v1", "concept:4024:factorisation", prompt = "Q4", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.8),
            QuestionItem("q5", "v1", "concept:4024:factorisation", prompt = "Q5", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.85),
            QuestionItem("q6", "v1", "concept:4024:factorisation", prompt = "Q6", options = listOf("A", "B"), correctIndex = 0, difficulty = 0.9)
        )

        for (q in questions) {
            session = AdaptiveDiagnosticEngine.submitAnswer(session, q, selectedIndex = 0, durationMs = 5000L)
            if (session.isCompleted) break
        }

        assertTrue("Diagnostic session must terminate within maximum bounds", session.isCompleted)
        assertNotNull("Synthesized plan must be generated on completion", session.synthesizedPlan)
        assertTrue("Synthesized plan must contain structured stages", session.synthesizedPlan!!.planStages.isNotEmpty())
    }

    @Test
    fun `closed loop calibration classifies content deficiency when prerequisite is verified`() {
        val verifiedPrereqState = AcademicState(
            conceptId = "concept:4024:expansion",
            conceptName = "Expansion",
            baseMastery = 0.85,
            confidence = 0.90,
            evidenceCount = 5
        )

        val targetConceptState = AcademicState(
            conceptId = "concept:4024:factorisation",
            conceptName = "Factorisation",
            baseMastery = 0.60,
            confidence = 0.70,
            evidenceCount = 3
        )

        val plan = LearningPlan(
            planId = "plan-001",
            studentId = "student-101",
            courseVersionId = "v1",
            targetConceptId = "concept:4024:factorisation",
            targetConceptName = "Factorisation",
            objectiveIds = listOf("lo1"),
            interventionType = "RETRIEVAL_PRACTICE",
            predictions = PlanPrediction(expectedMasteryGain = 0.20, expectedAccuracy = 0.85)
        )

        // Underperforming observation: student scored 30% accuracy
        val obs = PlanObservation(
            observedMasteryGain = 0.05,
            observedAccuracy = 0.30,
            observedDurationMinutes = 15,
            completion = true
        )

        val (updatedPlan, recalRecord) = AdaptivePlannerEngine.evaluatePlanExecution(
            plan = plan,
            observation = obs,
            baselineAcademicState = targetConceptState,
            prerequisiteAcademicState = verifiedPrereqState,
            errorType = null
        )

        assertEquals(
            "When prerequisite is verified, strategy failure must be attributed to CONTENT_DEFICIENCY",
            RecalibrationAttribution.CONTENT_DEFICIENCY,
            recalRecord.attribution
        )
        assertNotEquals(
            "Intervention strategy must be adapted away from failed retrieval practice",
            "RETRIEVAL_PRACTICE",
            updatedPlan.interventionType
        )
    }

    @Test
    fun `closed loop calibration classifies diagnostic misestimation when prerequisite was unverified or had sign error`() {
        val unverifiedPrereqState = AcademicState(
            conceptId = "concept:4024:algebraic-signs",
            conceptName = "Signed Numbers",
            baseMastery = 0.40,
            confidence = 0.40,
            evidenceCount = 1
        )

        val targetConceptState = AcademicState(
            conceptId = "concept:4024:factorisation",
            conceptName = "Factorisation",
            baseMastery = 0.65,
            confidence = 0.70,
            evidenceCount = 3
        )

        val plan = LearningPlan(
            planId = "plan-002",
            studentId = "student-101",
            courseVersionId = "v1",
            targetConceptId = "concept:4024:factorisation",
            targetConceptName = "Factorisation",
            objectiveIds = listOf("lo1"),
            interventionType = "WORKED_EXAMPLES",
            predictions = PlanPrediction(expectedMasteryGain = 0.20, expectedAccuracy = 0.80)
        )

        val obs = PlanObservation(
            observedMasteryGain = 0.02,
            observedAccuracy = 0.35,
            observedDurationMinutes = 12,
            completion = true
        )

        val (updatedPlan, recalRecord) = AdaptivePlannerEngine.evaluatePlanExecution(
            plan = plan,
            observation = obs,
            baselineAcademicState = targetConceptState,
            prerequisiteAcademicState = unverifiedPrereqState,
            errorType = "SIGN_ERROR"
        )

        assertEquals(
            "When prerequisite has low evidence or fundamental sign error, must be attributed to DIAGNOSTIC_STATE_MISESTIMATION",
            RecalibrationAttribution.DIAGNOSTIC_STATE_MISESTIMATION,
            recalRecord.attribution
        )
        assertEquals(
            "Must swap to PREREQUISITE_REPAIR to fix foundational prerequisite",
            "PREREQUISITE_REPAIR",
            updatedPlan.interventionType
        )
    }
}
