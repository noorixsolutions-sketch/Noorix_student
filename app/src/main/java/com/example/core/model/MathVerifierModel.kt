package com.example.core.model

data class Step(
    val stepIndex: Int,
    val expression: String,
    val note: String = ""
)

enum class MathErrorType {
    SIGN_ERROR,
    ALGEBRAIC_MANIPULATION,
    FACTORISATION_ERROR,
    EXPANSION_ERROR,
    ARITHMETIC_ERROR,
    SYNTAX_ERROR
}

data class VerificationResult(
    val isValid: Boolean,
    val firstInvalidStepIndex: Int?,
    val errorType: MathErrorType?,
    val symbolicTrace: String
)

interface MathVerifier {
    suspend fun verifySteps(
        problemLaTeX: String,
        studentSteps: List<Step>,
        courseVersion: String
    ): VerificationResult
}
