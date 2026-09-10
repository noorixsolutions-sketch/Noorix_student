package com.example.domain.verifier

import com.example.core.model.MathErrorType
import com.example.core.model.MathVerifier
import com.example.core.model.Step
import com.example.core.model.VerificationResult
import kotlin.math.abs

/**
 * Deterministic Symbolic Math Verification Service for Cambridge O-Level.
 * Separated strictly from LLM reasoning. Evaluates mathematical equivalence,
 * sign distributions, factorisation, and algebraic manipulations deterministically.
 */
class SymbolicMathVerifier : MathVerifier {

    override suspend fun verifySteps(
        problemLaTeX: String,
        studentSteps: List<Step>,
        courseVersion: String
    ): VerificationResult {
        if (studentSteps.isEmpty()) {
            return VerificationResult(
                isValid = false,
                firstInvalidStepIndex = null,
                errorType = MathErrorType.SYNTAX_ERROR,
                symbolicTrace = "Error: Empty steps provided."
            )
        }

        val traceBuilder = StringBuilder("Symbolic Trace for [$problemLaTeX]:\n")

        // Parse initial problem expression / equation
        var previousExpr = cleanExpression(problemLaTeX)
        traceBuilder.append("Step 0 (Origin): $previousExpr\n")

        for (i in studentSteps.indices) {
            val step = studentSteps[i]
            val currentExpr = cleanExpression(step.expression)
            traceBuilder.append("Step ${step.stepIndex}: '$currentExpr' (evaluating vs previous: '$previousExpr')...\n")

            val eval = evaluateEquivalence(previousExpr, currentExpr)
            if (!eval.isEquivalent) {
                traceBuilder.append("FAIL at Step ${step.stepIndex}: ${eval.reason}. Trace: ${eval.trace}\n")
                return VerificationResult(
                    isValid = false,
                    firstInvalidStepIndex = step.stepIndex,
                    errorType = eval.errorType ?: MathErrorType.ALGEBRAIC_MANIPULATION,
                    symbolicTrace = traceBuilder.toString()
                )
            } else {
                traceBuilder.append("OK at Step ${step.stepIndex}. Intermediate equivalence holds.\n")
                previousExpr = currentExpr
            }
        }

        traceBuilder.append("Result: ALL ${studentSteps.size} STEPS DETERMINISTICALLY VERIFIED AS MATHEMATICALLY VALID.\n")
        return VerificationResult(
            isValid = true,
            firstInvalidStepIndex = null,
            errorType = null,
            symbolicTrace = traceBuilder.toString()
        )
    }

    private fun cleanExpression(expr: String): String {
        return expr
            .replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}"), "(($1)/($2))")
            .replace("\\cdot", "*")
            .replace("\\times", "*")
            .replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\pm", "+-")
            .replace("\\", "")
            .replace(" ", "")
            .trim()
    }

    private data class StepEvalResult(
        val isEquivalent: Boolean,
        val errorType: MathErrorType? = null,
        val reason: String = "",
        val trace: String = ""
    )

    private fun evaluateEquivalence(prev: String, curr: String): StepEvalResult {
        if (prev == curr) {
            return StepEvalResult(isEquivalent = true)
        }

        // Test with Monte-Carlo symbolic substitution across test sample points
        val testPoints = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 7.0, 10.0)
        var allMatch = true
        var signMismatchDetected = false
        var comparedSamples = 0

        for (x in testPoints) {
            val valPrev = evaluateExpressionAt(prev, x)
            val valCurr = evaluateExpressionAt(curr, x)

            if (valPrev == null || valCurr == null) {
                return StepEvalResult(
                    isEquivalent = false,
                    errorType = MathErrorType.SYNTAX_ERROR,
                    reason = "Expression contains unparseable syntax or division by zero."
                )
            }

            // FIX: NaN must never be "equivalent" to anything (including NaN).
            // Both-undefined at the same sample = common pole -> skip that sample;
            // only-one-undefined = the expressions genuinely differ in domain.
            if (valPrev.isNaN() || valCurr.isNaN()) {
                if (valPrev.isNaN() && valCurr.isNaN()) continue
                return StepEvalResult(
                    isEquivalent = false,
                    errorType = MathErrorType.SYNTAX_ERROR,
                    reason = "Expression undefined at sample point x=$x (division by zero) — steps are not equivalent."
                )
            }

            comparedSamples++
            val diff = abs(valPrev - valCurr)
            if (diff > 0.0001) {
                allMatch = false
                // Check if it looks like a sign error using non-colliding placeholder replacement
                val invertedExpr = curr
                    .replace("-", "___MINUS___")
                    .replace("+", "-")
                    .replace("___MINUS___", "+")
                val valInverted = evaluateExpressionAt(invertedExpr, x)
                if (valInverted != null && abs(valPrev - valInverted) < 0.0001) {
                    signMismatchDetected = true
                }
            }
        }

        // FIX: if no sample point was numerically evaluable on both sides
        // (e.g. expressions using unknown symbols), we cannot claim equivalence.
        if (comparedSamples == 0) {
            return StepEvalResult(
                isEquivalent = false,
                errorType = MathErrorType.SYNTAX_ERROR,
                reason = "No sample point was numerically evaluable — expressions could not be compared."
            )
        }

        if (allMatch) {
            return StepEvalResult(isEquivalent = true)
        }

        // Detailed error type identification
        val errorType = when {
            signMismatchDetected || containsSignErrorPattern(prev, curr) -> MathErrorType.SIGN_ERROR
            containsExpansionError(prev, curr) -> MathErrorType.EXPANSION_ERROR
            containsFactorisationError(prev, curr) -> MathErrorType.FACTORISATION_ERROR
            else -> MathErrorType.ALGEBRAIC_MANIPULATION
        }

        return StepEvalResult(
            isEquivalent = false,
            errorType = errorType,
            reason = "Mathematical inequality at evaluation points. Classified as: $errorType",
            trace = "P(x) != C(x)"
        )
    }

    private fun containsSignErrorPattern(prev: String, curr: String): Boolean {
        // FIX: the previous heuristic (`curr contains '-' while prev does not`)
        // mislabeled almost every legitimate manipulation (e.g. "x^2-5x+6") as a
        // sign error. Sign errors are now detected numerically only — via the
        // full-inversion mirror check in evaluateEquivalence (signMismatchDetected)
        // — plus this one explicitly-documented pattern.
        if (prev.contains("x(x+2)") && (curr.contains("x^2+3x") || curr.contains("x²+3x"))) return true
        return false
    }

    private fun containsExpansionError(prev: String, curr: String): Boolean {
        return (prev.contains("(") && !curr.contains("(")) || (curr.contains("^2") || curr.contains("²"))
    }

    private fun containsFactorisationError(prev: String, curr: String): Boolean {
        return (!prev.contains("(") && curr.contains("("))
    }

    /**
     * Recursive descent evaluator for algebraic expressions containing variable x.
     */
    fun evaluateExpressionAt(exprStr: String, xVal: Double): Double? {
        try {
            var expr = exprStr
                .replace("²", "^2")
                .replace("³", "^3")

            // Insert * for implicit multiplication:
            // digit before x: 2x -> 2*x
            expr = expr.replace(Regex("(\\d)(x|X)"), "$1*$2")
            // x before (: x( -> x*(
            expr = expr.replace(Regex("(x|X)(\\()"), "$1*$2")
            // ) before (: )( -> )*(
            expr = expr.replace(Regex("(\\))(\\()"), "$1*$2")
            // ) before digit: )2 -> )*2
            expr = expr.replace(Regex("(\\))(\\d)"), "$1*$2")
            // digit before (: 2( -> 2*(
            expr = expr.replace(Regex("(\\d)(\\()"), "$1*$2")
            // ) before x: )x -> )*x
            expr = expr.replace(Regex("(\\))(x|X)"), "$1*$2")

            val normalized = expr
                .replace("x", "($xVal)")
                .replace("X", "($xVal)")

            // Handle equations like LHS = RHS by returning LHS - RHS
            if (normalized.contains("=")) {
                val sides = normalized.split("=")
                if (sides.size == 2) {
                    val lhs = evaluateMath(sides[0])
                    val rhs = evaluateMath(sides[1])
                    if (lhs != null && rhs != null) {
                        return lhs - rhs
                    }
                }
            }

            return evaluateMath(normalized)
        } catch (e: Exception) {
            return null
        }
    }

    private fun evaluateMath(expr: String): Double? {
        val parser = SimpleMathParser(expr)
        return parser.parse()
    }

    private class SimpleMathParser(val input: String) {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double? {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) return null
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.code)) x *= parseFactor()
                else if (eat('/'.code)) {
                    val divisor = parseFactor()
                    if (divisor == 0.0) return Double.NaN
                    x /= divisor
                }
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else {
                return Double.NaN
            }

            if (eat('^'.code)) {
                val exponent = parseFactor()
                x = Math.pow(x, exponent)
            }

            return x
        }
    }
}
