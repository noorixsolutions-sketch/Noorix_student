package com.example.domain.onboarding

import com.example.core.model.StudentProfileCalibration
import com.example.domain.diagnostic.AdaptiveDiagnosticEngine
import com.example.domain.diagnostic.BuiltInCheckinBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the two-mode experience domain logic
 * (docs/two-mode-experience.md): age bands, stage mapping, the built-in
 * check-in banks, and the engine running a full baseline check on them.
 */
class TwoModeOnboardingTest {

    // ------------------------------------------------------------------
    // Age bands
    // ------------------------------------------------------------------

    @Test
    fun `age band boundaries`() {
        assertEquals(AgeBand.EARLY, AgeBand.fromAge(4))
        assertEquals(AgeBand.EARLY, AgeBand.fromAge(7))
        assertEquals(AgeBand.PRIMARY, AgeBand.fromAge(8))
        assertEquals(AgeBand.PRIMARY, AgeBand.fromAge(11))
        assertEquals(AgeBand.SECONDARY, AgeBand.fromAge(12))
        assertEquals(AgeBand.SECONDARY, AgeBand.fromAge(16))
    }

    @Test
    fun `out of range ages fall back to secondary band`() {
        assertEquals(AgeBand.SECONDARY, AgeBand.fromAge(3))
        assertEquals(AgeBand.SECONDARY, AgeBand.fromAge(17))
    }

    @Test
    fun `each band has a complete interaction profile`() {
        listOf(AgeBand.EARLY, AgeBand.PRIMARY, AgeBand.SECONDARY).forEach { band ->
            val profile = AgeBandProfile.of(band)
            assertEquals(band, profile.band)
            assertTrue(profile.ageOptions.all { AgeBand.fromAge(it) == band })
            assertTrue(profile.stageOptions.isNotEmpty())
            assertTrue(profile.checkIntro.isNotBlank())
            assertTrue(profile.checkSub.isNotBlank())
            assertTrue(profile.readyTitle.isNotBlank())
            assertTrue(profile.greeting.isNotBlank())
        }
    }

    @Test
    fun `only secondary band collects o-level fields`() {
        assertFalse(AgeBandProfile.ofEARLY.showTargetGrade)
        assertFalse(AgeBandProfile.ofEARLY.showExamSession)
        assertFalse(AgeBandProfile.ofEARLY.showDailyMinutes)
        assertFalse(AgeBandProfile.ofPRIMARY.showTargetGrade)
        assertTrue(AgeBandProfile.ofSECONDARY.showTargetGrade)
        assertTrue(AgeBandProfile.ofSECONDARY.showExamSession)
        assertTrue(AgeBandProfile.ofSECONDARY.showDailyMinutes)
    }

    // ------------------------------------------------------------------
    // Onboarding state
    // ------------------------------------------------------------------

    @Test
    fun `state without age defaults to secondary band`() {
        val state = OnboardingState()
        assertEquals(AgeBand.SECONDARY, state.band)
        assertFalse(state.hasProfile)
    }

    @Test
    fun `band follows the chosen age`() {
        assertEquals(AgeBand.EARLY, OnboardingState(age = 5).band)
        assertEquals(AgeBand.PRIMARY, OnboardingState(age = 9).band)
        assertEquals(AgeBand.SECONDARY, OnboardingState(age = 14).band)
    }

    @Test
    fun `hasProfile requires age and stage`() {
        assertFalse(OnboardingState(age = 10, stage = "").hasProfile)
        assertFalse(OnboardingState(age = null, stage = "Grade 4").hasProfile)
        assertTrue(OnboardingState(age = 10, stage = "Grade 4").hasProfile)
    }

    // ------------------------------------------------------------------
    // Curriculum stage mapping (Pre-K → O-Level KG)
    // ------------------------------------------------------------------

    @Test
    fun `age maps onto the curriculum knowledge graph stage`() {
        assertEquals(1, CurriculumStageMapper.stageOrderFor(AgeBand.EARLY, "KG 1"))
        assertEquals(2, CurriculumStageMapper.stageOrderFor(AgeBand.PRIMARY, "Grade 4"))
        assertEquals(3, CurriculumStageMapper.stageOrderFor(AgeBand.SECONDARY, "Pre-O-Level (Grade 9)"))
        assertEquals(4, CurriculumStageMapper.stageOrderFor(AgeBand.SECONDARY, "O-Level Year 1 (Grade 10)"))
        assertEquals(4, CurriculumStageMapper.stageOrderFor(AgeBand.SECONDARY, "O-Level Year 2 (Grade 11)"))
        assertEquals(4, CurriculumStageMapper.stageOrderFor(AgeBand.SECONDARY, "Private Candidate"))
    }

    // ------------------------------------------------------------------
    // Built-in check-in banks (younger bands)
    // ------------------------------------------------------------------

    @Test
    fun `built in banks are valid question banks for the adaptive engine`() {
        listOf(AgeBand.EARLY, AgeBand.PRIMARY).forEach { band ->
            val bank = BuiltInCheckinBank.forBand(band)
            assertEquals(6, bank.size)
            assertTrue(bank.map { it.id }.toSet().size == bank.size)
            bank.forEach { q ->
                assertTrue(q.prompt.isNotBlank())
                assertTrue(q.options.size in 2..6)
                assertTrue(q.correctIndex in q.options.indices)
                assertTrue(q.difficulty in 0.0..1.0)
                assertTrue(q.targetConceptId.startsWith("concept:"))
            }
        }
        // Secondary learners run from the signed course package instead.
        assertTrue(BuiltInCheckinBank.forBand(AgeBand.SECONDARY).isEmpty())
    }

    @Test
    fun `early learner baseline runs through the shared adaptive engine`() {
        val bank = BuiltInCheckinBank.forBand(AgeBand.EARLY)
        val profile = StudentProfileCalibration(
            studentGrade = "KG 1",
            studentAge = 5,
            targetGrade = "",
            priorTheta = 0.50,
            baselineVelocity = 0.16
        )
        var session = AdaptiveDiagnosticEngine.initializeSession(profile)
        var asked = 0
        while (!session.isCompleted && asked < AdaptiveDiagnosticEngine.MAX_QUESTIONS_CAP) {
            val question = AdaptiveDiagnosticEngine.getNextQuestion(session, bank)
            assertNotNull("bank must supply the next question", question)
            session = AdaptiveDiagnosticEngine.submitAnswer(
                session = session,
                question = question!!,
                selectedIndex = question.correctIndex, // learner answers well
                durationMs = 15_000L
            )
            asked++
        }
        assertTrue("check-in must complete within the cap", session.isCompleted)
        assertTrue("completed in at most 6 questions, was $asked", asked <= 6)
        assertNotNull("a first plan must be synthesized", session.synthesizedPlan)
        assertEquals(asked, session.itemsAnswered.size)
    }

    @Test
    fun `struggling early learner still completes and produces a gap plan`() {
        val bank = BuiltInCheckinBank.forBand(AgeBand.EARLY)
        var session = AdaptiveDiagnosticEngine.initializeSession(
            StudentProfileCalibration(studentGrade = "KG 2", studentAge = 6, priorTheta = 0.50)
        )
        var asked = 0
        while (!session.isCompleted && asked < AdaptiveDiagnosticEngine.MAX_QUESTIONS_CAP) {
            val question = AdaptiveDiagnosticEngine.getNextQuestion(session, bank)
            assertNotNull(question)
            // Answer the first wrong option — the engine must stay stable.
            session = AdaptiveDiagnosticEngine.submitAnswer(
                session = session,
                question = question!!,
                selectedIndex = if (question.correctIndex == 0) 1 else 0,
                durationMs = 15_000L
            )
            asked++
        }
        assertTrue(session.isCompleted)
        assertNotNull(session.synthesizedPlan)
        assertTrue("wrong answers must surface gaps", session.identifiedGaps.isNotEmpty())
    }
}
