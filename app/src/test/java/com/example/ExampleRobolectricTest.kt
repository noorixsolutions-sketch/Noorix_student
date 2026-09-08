package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.PackageVerifier
import com.example.core.crypto.VerificationStatus
import com.example.core.model.AcademicState
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.core.model.MasteryStatus
import com.example.core.model.PlanClassification
import com.example.core.model.PlanObservation
import com.example.core.model.Step
import com.example.data.curriculum.CoursePackageProvider
import com.example.domain.planner.AdaptivePlannerEngine
import com.example.domain.projection.ProjectionEngine
import com.example.domain.verifier.SymbolicMathVerifier
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app_name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Noorix Learning", appName)
    }

    @Test
    fun `symbolic verifier correctly identifies sign error in x(x+2) = x2 + 3x`() = runBlocking {
        val verifier = SymbolicMathVerifier()

        // Valid expansion test
        val validSteps = listOf(Step(1, "x^2 + 2x"))
        val validResult = verifier.verifySteps("x(x+2)", validSteps, "2025-2027")
        assertTrue("Expected valid expansion to pass", validResult.isValid)
        assertNull(validResult.firstInvalidStepIndex)

        // Invalid sign/coefficient error test
        val invalidSteps = listOf(Step(1, "x^2 + 3x"))
        val invalidResult = verifier.verifySteps("x(x+2)", invalidSteps, "2025-2027")
        assertFalse("Expected x(x+2) = x^2+3x to be rejected", invalidResult.isValid)
        assertEquals(1, invalidResult.firstInvalidStepIndex)
        assertNotNull(invalidResult.errorType)
    }

    @Test
    fun `package verifier accepts valid signed package and rejects tampered package`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mathPkg = CoursePackageProvider(context).createMath4024Package()
        val validStatus = PackageVerifier.verifyPackage(mathPkg)
        assertTrue("Expected valid package status", validStatus is VerificationStatus.Valid)

        // Tamper test
        val tamperedPkg = mathPkg.copy(
            manifest = mathPkg.manifest.copy(
                contentHash = "tampered_fake_hash_00000000000000000000"
            )
        )
        val tamperedStatus = PackageVerifier.verifyPackage(tamperedPkg)
        assertTrue("Expected tampered package to be rejected", tamperedStatus is VerificationStatus.HashMismatch)
    }

    @Test
    fun `planner classifies deviation into ASSESSMENT_ERROR vs PLAN_ERROR`() {
        val plan = AdaptivePlannerEngine.createPlan(
            studentId = "student-1",
            courseVersionId = "2025-2027",
            targetConceptId = "concept:4024:factorisation",
            targetConceptName = "Factorisation",
            academicState = null
        )

        // Low evidence baseline (<3 count) -> Underperformance yields ASSESSMENT_ERROR
        val unreliableBaseline = AcademicState(
            conceptId = "concept:4024:factorisation",
            evidenceCount = 1,
            confidence = 0.40
        )
        val observation = PlanObservation(
            observedMasteryGain = 0.02,
            observedAccuracy = 0.35,
            observedDurationMinutes = 28,
            completion = true
        )
        val evaluatedAssessmentError = AdaptivePlannerEngine.evaluatePlanExecution(plan, observation, unreliableBaseline)
        assertEquals(PlanClassification.ASSESSMENT_ERROR, evaluatedAssessmentError.classification)

        // Reliable baseline (confidence >= 0.65, count >= 3) -> Underperformance yields PLAN_ERROR
        val reliableBaseline = AcademicState(
            conceptId = "concept:4024:factorisation",
            evidenceCount = 8,
            confidence = 0.85
        )
        val evaluatedPlanError = AdaptivePlannerEngine.evaluatePlanExecution(plan, observation, reliableBaseline)
        assertEquals(PlanClassification.PLAN_ERROR, evaluatedPlanError.classification)
    }

    @Test
    fun `temporal decay recalculates mastery and triggers REGRESSED status`() {
        val t0 = 1700000000000L
        val events = listOf(
            LearningEvent(
                eventId = "evt-1",
                eventType = EventType.MASTERY_UPDATED,
                studentId = "student-1",
                courseVersionId = "2025-2027",
                hlcTimestamp = "hlc-1",
                physicalMs = t0,
                payloadJson = JSONObject().apply {
                    put("conceptId", "concept:4024:factorisation")
                    put("conceptName", "Factorisation")
                    put("score", 1.0)
                    put("lambda", 0.05)
                    put("evidenceStrength", 0.95)
                }.toString()
            ),
            LearningEvent(
                eventId = "evt-2",
                eventType = EventType.MASTERY_UPDATED,
                studentId = "student-1",
                courseVersionId = "2025-2027",
                hlcTimestamp = "hlc-2",
                physicalMs = t0,
                payloadJson = JSONObject().apply {
                    put("conceptId", "concept:4024:factorisation")
                    put("conceptName", "Factorisation")
                    put("score", 1.0)
                    put("lambda", 0.05)
                    put("evidenceStrength", 0.95)
                }.toString()
            ),
            LearningEvent(
                eventId = "evt-3",
                eventType = EventType.MASTERY_UPDATED,
                studentId = "student-1",
                courseVersionId = "2025-2027",
                hlcTimestamp = "hlc-3",
                physicalMs = t0,
                payloadJson = JSONObject().apply {
                    put("conceptId", "concept:4024:factorisation")
                    put("conceptName", "Factorisation")
                    put("score", 1.0)
                    put("lambda", 0.05)
                    put("evidenceStrength", 0.95)
                }.toString()
            )
        )

        // Day 0: Effective mastery is ~0.90+, MASTERED
        val stateT0 = ProjectionEngine.projectStudentState(events, t0)
        val factT0 = stateT0.academicStates["concept:4024:factorisation"]
        assertNotNull(factT0)
        assertEquals(MasteryStatus.MASTERED, factT0!!.status)

        // Day 30: 30 days elapsed -> exp(-0.05 * 30) = exp(-1.5) ≈ 0.223. Effective < 0.60 ∧ Base > 0.70 -> REGRESSED
        val t30 = t0 + (30L * 86_400_000L)
        val stateT30 = ProjectionEngine.projectStudentState(events, t30)
        val factT30 = stateT30.academicStates["concept:4024:factorisation"]
        assertNotNull(factT30)
        assertTrue("Effective mastery should decay significantly", factT30!!.effectiveMastery < 0.60)
        assertEquals(MasteryStatus.REGRESSED, factT30.status)
    }
}
