package com.example.presentation.util

import com.example.core.crypto.VerificationStatus
import com.example.core.model.MathErrorType
import com.example.domain.sync.SyncBatchResult

/**
 * Single source of truth for student-facing plain language copy.
 * Strictly guarantees that no enum names, class names, hashes, or internal
 * algorithmic/diagnostic strings are ever rendered to the student.
 */
object UiCopy {

    /**
     * Translates algebraic verification errors into short, constructive student guidance.
     */
    fun formatMathError(errorType: MathErrorType?): String {
        return when (errorType) {
            MathErrorType.SIGN_ERROR -> "Check your signs (+ or -)"
            MathErrorType.EXPANSION_ERROR -> "Check bracket expansion"
            MathErrorType.FACTORISATION_ERROR -> "Check common factors"
            MathErrorType.ALGEBRAIC_MANIPULATION -> "This step doesn't follow from the previous one"
            MathErrorType.ARITHMETIC_ERROR -> "Check your calculation"
            MathErrorType.SYNTAX_ERROR -> "Check for missing symbols or brackets"
            null -> "Review this line"
        }
    }

    /**
     * Plain-language explanation for when the student taps "Why?".
     * Exactly one calm, constructive sentence.
     */
    fun formatMathErrorExplanation(errorType: MathErrorType?): String {
        return when (errorType) {
            MathErrorType.SIGN_ERROR ->
                "A negative sign may have been missed when expanding brackets or rearranging terms."
            MathErrorType.EXPANSION_ERROR ->
                "Remember to multiply every term inside the brackets by the factor outside."
            MathErrorType.FACTORISATION_ERROR ->
                "Verify that the extracted factor divides every term evenly without leaving a remainder."
            MathErrorType.ALGEBRAIC_MANIPULATION ->
                "The mathematical value of this line does not equal the line before it."
            MathErrorType.ARITHMETIC_ERROR ->
                "Double-check basic multiplication or addition between the numbers."
            MathErrorType.SYNTAX_ERROR ->
                "Ensure every open bracket has a matching closing bracket."
            null ->
                "Check the algebra in this step against your previous line."
        }
    }

    /**
     * Formats curriculum package status in plain language.
     */
    fun formatCurriculumStatus(status: VerificationStatus?): String {
        return when (status) {
            is VerificationStatus.Valid -> "Official Cambridge Syllabus"
            else -> "Course Content Ready"
        }
    }

    /**
     * Formats background sync status into a quiet, minimal status line.
     */
    fun formatSyncStatus(isSyncing: Boolean, result: SyncBatchResult?): String {
        return when {
            isSyncing -> "Saving your progress..."
            result != null -> "Synced just now"
            else -> "Saved locally"
        }
    }

    /**
     * Formats Cambridge O-Level course titles cleanly.
     */
    fun formatCourseTitle(courseId: String, rawName: String): String {
        return when {
            courseId.contains("4024") || rawName.contains("4024") -> "Mathematics (Cambridge 4024)"
            courseId.contains("5054") || rawName.contains("5054") -> "Physics (Cambridge 5054)"
            else -> rawName.ifBlank { "Cambridge O-Level" }
        }
    }

    /**
     * Formats mastery into natural learning stages.
     */
    fun formatMastery(mastery: Double): String {
        val pct = (mastery * 100).toInt().coerceIn(0, 100)
        return "$pct% Mastered"
    }

    /**
     * Formats diagnostic quiz feedback.
     */
    fun formatQuizFeedback(isCorrect: Boolean): String {
        return if (isCorrect) "Correct! Well done." else "Not quite. Review the steps above."
    }
}
