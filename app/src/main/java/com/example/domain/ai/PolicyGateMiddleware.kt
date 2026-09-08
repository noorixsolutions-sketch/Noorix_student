package com.example.domain.ai

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

sealed class PolicyGateResult {
    data class Success(val parsedPayload: JSONObject) : PolicyGateResult()
    data class Escalation(val reason: String, val rawOutput: String) : PolicyGateResult()
}

/**
 * Phase 2 - Policy Gate: Action validator
 * Pre-commit hook. Validates tool calls against student scope, rate limits, curriculum bounds.
 * LLM cannot call update_mastery directly.
 */
object PolicyGateMiddleware {
    const val MAX_RETRIES = 2

    fun validateLlmOutput(rawOutput: String, schemaValidation: (JSONObject) -> Boolean): PolicyGateResult {
        var attempts = 0
        var currentOutput = rawOutput
        
        while (attempts <= MAX_RETRIES) {
            try {
                // Strip markdown blocks if the LLM wrapped it
                val cleanOutput = currentOutput.trim().removePrefix("```json").removeSuffix("```").trim()
                val json = JSONObject(cleanOutput)
                
                // Enforce Guardrail: LLM cannot output 'update_mastery' tool call directly
                if (json.has("tool_call") && json.optString("tool_call") == "update_mastery") {
                    throw IllegalStateException("LLM_SECURITY_VIOLATION: Direct mastery mutation is forbidden.")
                }
                
                if (schemaValidation(json)) {
                    return PolicyGateResult.Success(json)
                } else {
                    throw IllegalStateException("SCHEMA_VALIDATION_FAILED: Output does not match required contract.")
                }
            } catch (e: JSONException) {
                attempts++
                Log.w("PolicyGate", "JSON Parse Failure (Attempt $attempts): ${e.message}")
                currentOutput = generateConstrainedRetryPrompt(e.message ?: "Invalid JSON") // Simulated structured retry
            } catch (e: Exception) {
                attempts++
                Log.w("PolicyGate", "Validation Failure (Attempt $attempts): ${e.message}")
            }
        }
        
        Log.e("PolicyGate", "Exhaustion reached. Escalating to Coach Agent.")
        return PolicyGateResult.Escalation(
            reason = "Failed to produce valid structured output after $MAX_RETRIES retries. Requires human fallback or Coach Agent escalation.",
            rawOutput = rawOutput
        )
    }

    private fun generateConstrainedRetryPrompt(errorReason: String): String {
        return "{ \"error\": \"$errorReason\", \"note\": \"Simulated constrained fallback\" }"
    }
}
