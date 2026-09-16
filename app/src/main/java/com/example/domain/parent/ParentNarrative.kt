package com.example.domain.parent

/**
 * Weekly parent narrative builder — pure, deterministic, ADDITIVE.
 *
 * Implements the core of §11 of the Goal Architecture: Aisha's weekly note
 * to the parent. This on-device v1 composes the note from real measured
 * stats with fixed, honest templates (no LLM) — every number in the note
 * is traceable to the event log. A cloud LLM pass can later paraphrase
 * these facts without changing the numbers.
 */

data class ParentWeekStats(
    val studentName: String,
    /** Estimated study minutes this week (trailing 7 days). */
    val weekMinutes: Int,
    /** Estimated study minutes the week before (for deltas; 0 when unknown). */
    val previousWeekMinutes: Int = 0,
    /** Learning events this week (sessions proxy). */
    val weekEvents: Int,
    /** Blended accuracy 0..100. */
    val accuracyPercent: Int,
    /** Weakest assessed concept name, if any. */
    val weakestConceptName: String? = null,
    /** Weakest concept mastery 0..1. */
    val weakestConceptMastery: Double = 0.0,
    /** Strongest assessed concept name, if any. */
    val strongestConceptName: String? = null,
    /** Current streak in days. */
    val streakDays: Int = 0,
    /** Active days this week (0..7). */
    val activeDaysThisWeek: Int = 0,
    /** Target exam session label, e.g. "May/June 2027". */
    val targetExamSession: String = ""
)

data class ParentNarrative(
    /** 3–5 sentence note, ready to display. */
    val note: String,
    /** Attention bullets ("Two things need attention"). */
    val attention: List<String>,
    /** Suggested next focus, one line. */
    val nextFocus: String
)

object ParentNarrativeBuilder {

    fun build(stats: ParentWeekStats): ParentNarrative {
        val firstName = stats.studentName.substringBefore(" ").ifBlank { "Your child" }
        val sentences = mutableListOf<String>()
        val attention = mutableListOf<String>()

        // Sentence 1 — time + delta.
        val timeLine = formatMinutes(stats.weekMinutes)
        if (stats.previousWeekMinutes > 0 && stats.previousWeekMinutes != stats.weekMinutes) {
            val delta = stats.weekMinutes - stats.previousWeekMinutes
            val direction = if (delta >= 0) "up from" else "down from"
            sentences.add(
                "$firstName studied $timeLine this week, $direction " +
                    "${formatMinutes(stats.previousWeekMinutes)} last week."
            )
        } else if (stats.weekMinutes > 0) {
            sentences.add("$firstName studied $timeLine this week.")
        } else {
            sentences.add("$firstName hasn't logged study time yet this week.")
        }

        // Sentence 2 — accuracy + weakest concept.
        if (stats.weekEvents > 0) {
            val weak = stats.weakestConceptName
            if (weak != null) {
                val pct = (stats.weakestConceptMastery * 100).toInt().coerceIn(0, 100)
                sentences.add(
                    "${weak.replaceFirstChar { it.uppercase() }} is the current focus area at $pct% " +
                        "mastery, with overall answer accuracy at ${stats.accuracyPercent}%."
                )
            } else {
                sentences.add("Overall answer accuracy sits at ${stats.accuracyPercent}%.")
            }
        }

        // Sentence 3 — streak / consistency.
        if (stats.streakDays >= 7) {
            sentences.add("The ${stats.streakDays}-day streak is solid — consistency is compounding.")
        } else if (stats.streakDays >= 2) {
            sentences.add("A ${stats.streakDays}-day streak is building — a gentle weekend nudge helps.")
        } else if (stats.activeDaysThisWeek in 1..2 && stats.weekMinutes > 0) {
            sentences.add("Study happened on ${stats.activeDaysThisWeek} day(s) — short daily sessions beat one long one.")
        }

        // Sentence 4 — strongest area (encouragement).
        if (stats.strongestConceptName != null && stats.weekEvents > 0) {
            sentences.add("${stats.strongestConceptName.replaceFirstChar { it.uppercase() }} is a clear strength to celebrate.")
        }

        // Sentence 5 — exam horizon.
        if (stats.targetExamSession.isNotBlank()) {
            sentences.add("Everything is on track for ${stats.targetExamSession}.")
        }

        // Attention bullets.
        if (stats.weakestConceptName != null && stats.weakestConceptMastery < 0.6 && stats.weekEvents > 0) {
            attention.add(
                "${stats.weakestConceptName.replaceFirstChar { it.uppercase() }} is below 60% — " +
                    "one focused week would move it most."
            )
        }
        val skippedDays = 7 - stats.activeDaysThisWeek
        if (stats.weekMinutes > 0 && skippedDays >= 4) {
            attention.add("Study skipped $skippedDays of 7 days — consistency matters more than duration.")
        }
        if (stats.weekEvents > 0 && stats.accuracyPercent in 1..49) {
            attention.add("Accuracy under 50% suggests guessing — reviewing mistakes with a tutor would help.")
        }
        if (stats.weekMinutes == 0) {
            attention.add("No study time logged — help ${firstName.lowercase()} start with a 10-minute session.")
        }

        val nextFocus = if (stats.weakestConceptName != null && stats.weekEvents > 0) {
            "Next focus: ${stats.weakestConceptName.replaceFirstChar { it.uppercase() }} — 20 minutes daily."
        } else {
            "Next focus: a 10-minute daily habit to start the streak."
        }

        return ParentNarrative(
            note = sentences.take(5).joinToString(" "),
            attention = attention.take(3),
            nextFocus = nextFocus
        )
    }

    private fun formatMinutes(totalMinutes: Int): String {
        if (totalMinutes < 60) return "$totalMinutes min"
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }
}
