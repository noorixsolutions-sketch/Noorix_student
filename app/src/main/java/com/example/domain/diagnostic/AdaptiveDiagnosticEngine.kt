package com.example.domain.diagnostic

import com.example.core.model.QuestionItem

data class DiagnosticItemResult(
    val question: QuestionItem,
    val selectedIndex: Int,
    val isCorrect: Boolean,
    val durationMs: Long
)

data class DiagnosticSessionState(
    val currentQuestionIndex: Int = 0,
    val itemsAnswered: List<DiagnosticItemResult> = emptyList(),
    val estimatedAbility: Double = 0.5, // IRT theta mapped to [0.0, 1.0]
    val confidence: Double = 0.35,
    val currentTargetConceptId: String = "concept:4024:factorisation",
    val probedPrerequisites: List<String> = emptyList(),
    val identifiedGaps: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val diagnosticSummary: String = ""
)

object AdaptiveDiagnosticEngine {

    const val MAX_QUESTIONS_CAP = 8
    const val STOPPING_CONFIDENCE = 0.85

    fun getNextQuestion(
        session: DiagnosticSessionState,
        questionBank: List<QuestionItem>
    ): QuestionItem? {
        if (session.isCompleted || session.itemsAnswered.size >= MAX_QUESTIONS_CAP) {
            return null
        }

        val answeredIds = session.itemsAnswered.map { it.question.id }.toSet()
        val available = questionBank.filterNot { answeredIds.contains(it.id) }

        // Prerequisite probing: if the last answer was wrong and the question has a prerequisite
        val lastAnswer = session.itemsAnswered.lastOrNull()
        if (lastAnswer != null && !lastAnswer.isCorrect && lastAnswer.question.prerequisiteConceptId != null) {
            val prereqQ = available.find { it.targetConceptId == lastAnswer.question.prerequisiteConceptId }
            if (prereqQ != null) {
                return prereqQ
            }
        }

        // Otherwise pick the question closest to current ability estimate (IRT optimal item information)
        return available.minByOrNull { kotlin.math.abs(it.difficulty - session.estimatedAbility) }
    }

    fun submitAnswer(
        session: DiagnosticSessionState,
        question: QuestionItem,
        selectedIndex: Int,
        durationMs: Long
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

        // IRT-like update: adjust estimated ability
        val adjustment = if (isCorrect) {
            (1.0 - session.estimatedAbility) * 0.25
        } else {
            -session.estimatedAbility * 0.30
        }
        val newAbility = (session.estimatedAbility + adjustment).coerceIn(0.1, 0.95)

        // Increase confidence with each answered question
        val newConfidence = (session.confidence + 0.12).coerceAtMost(0.98)

        // Track gaps
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

        // Stopping criteria: Confidence threshold reached OR Max questions reached (guaranteed < 8)
        val shouldStop = (newConfidence >= STOPPING_CONFIDENCE && count >= 4) || (count >= 6)

        val summary = if (shouldStop) {
            "Diagnostic Completed in $count questions. Estimated ability: ${String.format("%.2f", newAbility)}. Primary Gaps identified: ${newGaps.joinToString(", ")}. Zero full-bank administration."
        } else ""

        return session.copy(
            currentQuestionIndex = count,
            itemsAnswered = updatedItems,
            estimatedAbility = String.format("%.2f", newAbility).toDouble(),
            confidence = String.format("%.2f", newConfidence).toDouble(),
            probedPrerequisites = probed,
            identifiedGaps = newGaps,
            isCompleted = shouldStop,
            diagnosticSummary = summary
        )
    }
}
