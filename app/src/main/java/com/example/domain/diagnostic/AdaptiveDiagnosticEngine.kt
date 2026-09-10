package com.example.domain.diagnostic

import com.example.core.model.AcademicState
import com.example.core.model.LearningPlan
import com.example.core.model.QuestionItem
import com.example.core.model.StudentProfileCalibration
import com.example.domain.planner.AdaptivePlannerEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DiagnosticItemResult(
    val question: QuestionItem,
    val selectedIndex: Int,
    val isCorrect: Boolean,
    val durationMs: Long
)

data class DiagnosticSessionState(
    val currentQuestionIndex: Int = 0,
    val itemsAnswered: List<DiagnosticItemResult> = emptyList(),
    val estimatedAbility: Double = 0.50, // IRT theta mapped to [0.0, 1.0]
    val priorTheta: Double = 0.50,
    val confidence: Double = 0.35,
    val currentTargetConceptId: String = "concept:4024:factorisation",
    val candidateGrade: String = "O-Level Year 1 (Grade 10)",
    val candidateAge: Int = 15,
    val targetGrade: String = "A*",
    val probedPrerequisites: List<String> = emptyList(),
    val identifiedGaps: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val diagnosticSummary: String = "",
    val calibratedVelocity: Double = 0.16,
    val synthesizedPlan: LearningPlan? = null
)

object AdaptiveDiagnosticEngine {

    const val MAX_QUESTIONS_CAP = 8
    const val STOPPING_CONFIDENCE = 0.85

    /**
     * Computes the multi-factor prior theta based on student grade/class, age,
     * target grade aspiration, cognitive signals, and psychometric signals.
     */
    fun computeMultiFactorPrior(
        grade: String,
        age: Int,
        targetGrade: String,
        cognitiveSignals: Map<String, Float>?,
        psychometricSignals: Map<String, Float>?
    ): StudentProfileCalibration {
        // 1. Grade Baseline Prior
        val gradePrior = when {
            grade.contains("Grade 9") || grade.contains("Pre-O") -> 0.42
            grade.contains("Grade 10") || grade.contains("Year 1") -> 0.50
            grade.contains("Grade 11") || grade.contains("Year 2") -> 0.58
            else -> 0.52
        }

        // 2. Age Scaling Factor
        val agePrior = when {
            age <= 14 -> 0.44
            age == 15 -> 0.50
            age == 16 -> 0.56
            else -> 0.60
        }

        // 3. Cognitive Index
        val cogAvg = cognitiveSignals?.values?.map { it.toDouble() }?.average() ?: 0.65

        // 4. Psychometric Index
        val psyAvg = psychometricSignals?.values?.map { it.toDouble() }?.average() ?: 0.65

        // Combined Multi-Factor Prior Theta
        val priorTheta = (0.35 * gradePrior + 0.20 * agePrior + 0.25 * cogAvg + 0.20 * psyAvg)
            .coerceIn(0.20, 0.85)

        // Baseline Velocity calculation
        val targetMultiplier = when (targetGrade) {
            "A*" -> 1.25
            "A" -> 1.10
            "B" -> 0.95
            else -> 0.85
        }
        val baselineVelocity = (0.15 * targetMultiplier * (cogAvg / 0.65)).coerceIn(0.08, 0.30)

        val cogMap = cognitiveSignals?.mapValues { it.value.toDouble() } ?: mapOf(
            "workingMemory" to 0.65,
            "spatialReasoning" to 0.60,
            "logicalReasoning" to 0.70,
            "processingEfficiency" to 0.68
        )

        val psyMap = psychometricSignals?.mapValues { it.value.toDouble() } ?: mapOf(
            "persistence" to 0.70,
            "confidence" to 0.65,
            "frustrationResponse" to 0.60,
            "metacognition" to 0.75
        )

        return StudentProfileCalibration(
            studentGrade = grade,
            studentAge = age,
            targetGrade = targetGrade,
            priorTheta = String.format("%.2f", priorTheta).toDouble(),
            baselineVelocity = String.format("%.3f", baselineVelocity).toDouble(),
            cognitivePriors = cogMap,
            psychometricPriors = psyMap
        )
    }

    /**
     * Initializes a new adaptive diagnostic session calibrated with the student's profile.
     */
    fun initializeSession(
        profile: StudentProfileCalibration,
        initialConceptId: String = "concept:4024:factorisation"
    ): DiagnosticSessionState {
        return DiagnosticSessionState(
            currentQuestionIndex = 0,
            itemsAnswered = emptyList(),
            estimatedAbility = profile.priorTheta,
            priorTheta = profile.priorTheta,
            confidence = 0.35,
            currentTargetConceptId = initialConceptId,
            candidateGrade = profile.studentGrade,
            candidateAge = profile.studentAge,
            targetGrade = profile.targetGrade,
            calibratedVelocity = profile.baselineVelocity,
            probedPrerequisites = emptyList(),
            identifiedGaps = emptyList(),
            isCompleted = false,
            diagnosticSummary = ""
        )
    }

    fun getNextQuestion(
        session: DiagnosticSessionState,
        questionBank: List<QuestionItem>
    ): QuestionItem? {
        if (session.isCompleted || session.itemsAnswered.size >= MAX_QUESTIONS_CAP) {
            return null
        }

        val answeredIds = session.itemsAnswered.map { it.question.id }.toSet()
        val available = questionBank.filterNot { answeredIds.contains(it.id) }
        if (available.isEmpty()) return null

        // Prerequisite probing: if the last answer was wrong and the question has a prerequisite
        val lastAnswer = session.itemsAnswered.lastOrNull()
        if (lastAnswer != null && !lastAnswer.isCorrect && lastAnswer.question.prerequisiteConceptId != null) {
            val prereqQ = available.find { it.targetConceptId == lastAnswer.question.prerequisiteConceptId }
            if (prereqQ != null) {
                return prereqQ
            }
        }

        // Otherwise pick the question closest to current ability estimate (IRT optimal item information)
        return available.minByOrNull { abs(it.difficulty - session.estimatedAbility) }
    }

    fun submitAnswer(
        session: DiagnosticSessionState,
        question: QuestionItem,
        selectedIndex: Int,
        durationMs: Long,
        studentId: String = "student-101",
        courseVersionId: String = "course-version:mathematics-4024:2025-2027"
    ): DiagnosticSessionState {
        val isCorrect = selectedIndex == question.correctIndex
        val result = DiagnosticItemResult(
            question = question,
            selectedIndex = selectedIndex,
            isCorrect = isCorrect,
            durationMs = durationMs
        )

        val updatedItems = session.itemsAnswered + result
        val count = updatedItems.size

        // IRT-like update: adjust estimated ability with difficulty sensitivity
        val adjustment = if (isCorrect) {
            (1.0 - session.estimatedAbility) * (0.22 + 0.05 * (1.0 - question.difficulty))
        } else {
            -session.estimatedAbility * (0.25 + 0.05 * question.difficulty)
        }
        val newAbility = (session.estimatedAbility + adjustment).coerceIn(0.10, 0.95)

        // Increase confidence with each answered question
        val newConfidence = (session.confidence + 0.12).coerceAtMost(0.98)

        // Track gaps and probed prerequisites
        val newGaps = session.identifiedGaps.toMutableList()
        val probed = session.probedPrerequisites.toMutableList()
        if (!isCorrect) {
            if (!newGaps.contains(question.targetConceptId)) {
                newGaps.add(question.targetConceptId)
            }
            if (question.prerequisiteConceptId != null && !probed.contains(question.prerequisiteConceptId)) {
                probed.add(question.prerequisiteConceptId)
            }
        }

        // Stopping criteria: Confidence threshold reached OR Max questions reached (guaranteed <= 6)
        val shouldStop = (newConfidence >= STOPPING_CONFIDENCE && count >= 4) || (count >= 6)

        var synthesizedPlan: LearningPlan? = null
        val summary = if (shouldStop) {
            val planConcept = if (newGaps.isNotEmpty()) newGaps.first() else question.targetConceptId
            val planConceptName = planConcept.substringAfterLast(":").replaceFirstChar { it.uppercase() }

            val profileCalib = StudentProfileCalibration(
                studentGrade = session.candidateGrade,
                studentAge = session.candidateAge,
                targetGrade = session.targetGrade,
                priorTheta = session.priorTheta,
                baselineVelocity = session.calibratedVelocity
            )

            synthesizedPlan = AdaptivePlannerEngine.createPlan(
                studentId = studentId,
                courseVersionId = courseVersionId,
                targetConceptId = planConcept,
                targetConceptName = planConceptName,
                academicState = AcademicState(
                    conceptId = planConcept,
                    conceptName = planConceptName,
                    baseMastery = newAbility,
                    confidence = newConfidence
                ),
                profileCalibration = profileCalib,
                identifiedGaps = newGaps
            )

            "Multi-Factor Calibrated Diagnostic Completed in $count questions. Profile: ${session.candidateGrade} (Age ${session.candidateAge}). Estimated Ability: ${String.format("%.2f", newAbility)} (Prior: ${String.format("%.2f", session.priorTheta)}). Identified Gaps: ${if (newGaps.isEmpty()) "None (Direct Mastery)" else newGaps.joinToString(", ")}. Synthesized Adaptive Plan active."
        } else ""

        return session.copy(
            currentQuestionIndex = count,
            itemsAnswered = updatedItems,
            estimatedAbility = String.format("%.2f", newAbility).toDouble(),
            confidence = String.format("%.2f", newConfidence).toDouble(),
            probedPrerequisites = probed,
            identifiedGaps = newGaps,
            isCompleted = shouldStop,
            diagnosticSummary = summary,
            synthesizedPlan = synthesizedPlan
        )
    }
}
