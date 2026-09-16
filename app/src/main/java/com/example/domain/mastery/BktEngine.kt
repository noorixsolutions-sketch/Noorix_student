package com.example.domain.mastery

import com.example.core.model.EventType
import com.example.core.model.LearningEvent

/**
 * Bayesian Knowledge Tracing (BKT) — pure, deterministic, ADDITIVE.
 *
 * Implements §7 of the Goal Architecture at the concept level: per
 * (student, concept) probability P(L) that the student has learned the
 * concept, updated O(1) per attempt. Runs alongside the existing Bayesian
 * projection + decay model (which remains the source of truth for
 * effective mastery); P(L) is surfaced where the goal calls for it —
 * parent narrative, mastery map, agent context.
 *
 * Standard BKT update:
 *   P(L|correct)   = P(L)·(1−P(S)) / [P(L)·(1−P(S)) + (1−P(L))·P(G)]
 *   P(L|incorrect) = P(L)·P(S)     / [P(L)·P(S)     + (1−P(L))·(1−P(G))]
 *   P(L)' = P(L|evidence) + (1 − P(L|evidence))·P(T)
 */
object BktEngine {

    data class BktParams(
        /** Prior P(L) before any attempt. */
        val pL0: Double = 0.25,
        /** P(T): learned→unlearned transition per opportunity. */
        val pT: Double = 0.12,
        /** P(S): slip — wrong despite knowing. */
        val pS: Double = 0.08,
        /** P(G): guess — right despite not knowing. */
        val pG: Double = 0.20
    )

    val DEFAULT_PARAMS = BktParams()

    /** Single-attempt BKT update. Input and output are in [0, 1]. */
    fun update(priorPL: Double, correct: Boolean, params: BktParams = DEFAULT_PARAMS): Double {
        val pL = priorPL.coerceIn(0.0, 1.0)
        val posterior = if (correct) {
            val numerator = pL * (1.0 - params.pS)
            val denominator = numerator + (1.0 - pL) * params.pG
            if (denominator <= 0.0) pL else numerator / denominator
        } else {
            val numerator = pL * params.pS
            val denominator = numerator + (1.0 - pL) * (1.0 - params.pG)
            if (denominator <= 0.0) pL else numerator / denominator
        }
        return (posterior + (1.0 - posterior) * params.pT).coerceIn(0.0, 1.0)
    }

    /** Folds a chronological correctness sequence into a final P(L). */
    fun tracePL(attempts: List<Boolean>, params: BktParams = DEFAULT_PARAMS): Double {
        var pL = params.pL0
        for (correct in attempts) {
            pL = update(pL, correct, params)
        }
        return pL
    }

    // ------------------------------------------------------------------
    // Event-log projection (regex-based, pure-JVM testable)
    // ------------------------------------------------------------------

    private val SUCCESS_PATTERNS = listOf(
        Regex("\"isCorrect\"\\s*:\\s*true"),
        Regex("\"passed\"\\s*:\\s*true"),
        Regex("\"isValid\"\\s*:\\s*true")
    )
    private val CONCEPT_PATTERN = Regex("\"conceptId\"\\s*:\\s*\"([^\"]+)\"")

    private fun isSuccess(payloadJson: String): Boolean =
        SUCCESS_PATTERNS.any { it.containsMatchIn(payloadJson) }

    private fun conceptOf(payloadJson: String): String? =
        CONCEPT_PATTERN.find(payloadJson)?.groupValues?.getOrNull(1)

    private fun isAttempt(event: LearningEvent): Boolean = when (event.eventType) {
        EventType.DIAGNOSTIC_ITEM_SUBMITTED,
        EventType.TRANSFER_TASK_EVALUATED,
        EventType.SYMBOLIC_VERIFICATION_COMPLETED -> true
        else -> false
    }

    /**
     * Chronological correctness sequence for one concept, oldest first.
     * Empty when the concept has no graded attempts yet.
     */
    fun attemptsForConcept(events: List<LearningEvent>, conceptId: String): List<Boolean> =
        events
            .filter { isAttempt(it) && conceptOf(it.payloadJson) == conceptId }
            .sortedWith(compareBy({ it.physicalMs }, { it.hlcTimestamp }))
            .map { isSuccess(it.payloadJson) }

    /**
     * P(L) for one concept, or null when there are no graded attempts.
     * Callers should fall back to the projection model's effective mastery.
     */
    fun plForConcept(
        events: List<LearningEvent>,
        conceptId: String,
        params: BktParams = DEFAULT_PARAMS
    ): Double? {
        val attempts = attemptsForConcept(events, conceptId)
        if (attempts.isEmpty()) return null
        return tracePL(attempts, params)
    }

    /**
     * Wrong answers among the [n] most recent graded attempts (any concept).
     * Feeds the bubble's confusion signal (§3): repeated struggle tilts Aisha.
     */
    fun recentWrongCount(events: List<LearningEvent>, n: Int = 5): Int =
        events
            .filter { isAttempt(it) }
            .sortedWith(compareBy({ it.physicalMs }, { it.hlcTimestamp }))
            .takeLast(n.coerceAtLeast(1))
            .count { !isSuccess(it.payloadJson) }

    /** P(L) for every concept with at least one graded attempt. */
    fun plForAllConcepts(
        events: List<LearningEvent>,
        params: BktParams = DEFAULT_PARAMS
    ): Map<String, Double> {
        val byConcept = events
            .filter { isAttempt(it) }
            .mapNotNull { event ->
                val concept = conceptOf(event.payloadJson) ?: return@mapNotNull null
                concept to event
            }
            .groupBy({ it.first }, { it.second })
        return byConcept.mapValues { (_, conceptEvents) ->
            val attempts = conceptEvents
                .sortedWith(compareBy({ it.physicalMs }, { it.hlcTimestamp }))
                .map { isSuccess(it.payloadJson) }
            tracePL(attempts, params)
        }
    }
}
