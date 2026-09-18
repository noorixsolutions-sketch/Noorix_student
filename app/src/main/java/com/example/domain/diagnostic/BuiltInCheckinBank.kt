package com.example.domain.diagnostic

import com.example.core.model.QuestionItem
import com.example.domain.onboarding.AgeBand

/**
 * Built-in baseline check-ins for the younger bands (docs/two-mode-experience.md §3).
 *
 * Signed question packages currently cover O-Level subjects (Cambridge 4024
 * Mathematics, 5054 Physics). For Little Learners (4–7) and Explorers (8–11) the
 * baseline is a short, age-appropriate number game / quick check instead of a
 * syllabus diagnostic. These items run through the *same* adaptive diagnostic
 * session, the *same* IRT ability update, the *same* student model and the same
 * plan synthesis — the engine is common; only the items differ.
 *
 * Items are deterministic, offline and mark-free by design: they calibrate a
 * starting hypothesis, which the continuous recalibration loop then corrects
 * through ordinary learning.
 */
object BuiltInCheckinBank {

    private const val COURSE_VERSION = "course-version:builtin-checkin"

    fun forBand(band: AgeBand): List<QuestionItem> = when (band) {
        AgeBand.EARLY -> early()
        AgeBand.PRIMARY -> primary()
        AgeBand.SECONDARY -> emptyList() // secondary learners use the active package bank
    }

    private fun q(
        id: String,
        concept: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        difficulty: Double
    ) = QuestionItem(
        id = id,
        courseVersionId = COURSE_VERSION,
        targetConceptId = concept,
        questionType = "MULTIPLE_CHOICE",
        prompt = prompt,
        options = options,
        correctIndex = correctIndex,
        difficulty = difficulty
    )

    private fun early(): List<QuestionItem> = listOf(
        q(
            id = "checkin:early:counting",
            concept = "concept:early:counting",
            prompt = "How many stars can you see?  ⭐ ⭐ ⭐ ",
            options = listOf("Three", "Four", "Five", "Six"),
            correctIndex = 1,
            difficulty = 0.40
        ),
        q(
            id = "checkin:early:shapes",
            concept = "concept:early:shapes",
            prompt = "Which shape has four corners?",
            options = listOf("Circle", "Triangle", "Square", "Star"),
            correctIndex = 2,
            difficulty = 0.42
        ),
        q(
            id = "checkin:early:number-bonds",
            concept = "concept:early:number-bonds",
            prompt = "2 + 3 = ?",
            options = listOf("4", "5", "6", "8"),
            correctIndex = 1,
            difficulty = 0.48
        ),
        q(
            id = "checkin:early:patterns",
            concept = "concept:early:patterns",
            prompt = "What comes next?  🍎  🍌  🍎  🍌  …",
            options = listOf("Apple", "Banana", "Cherry", "Pear"),
            correctIndex = 1,
            difficulty = 0.52
        ),
        q(
            id = "checkin:early:addition-10",
            concept = "concept:early:addition-10",
            prompt = "4 + 2 = ?",
            options = listOf("5", "6", "7", "8"),
            correctIndex = 1,
            difficulty = 0.55
        ),
        q(
            id = "checkin:early:subtraction-10",
            concept = "concept:early:subtraction-10",
            prompt = "You have 5 sweets. You share 2. How many are left?",
            options = listOf("2", "3", "4", "6"),
            correctIndex = 1,
            difficulty = 0.58
        )
    )

    private fun primary(): List<QuestionItem> = listOf(
        q(
            id = "checkin:primary:times-tables",
            concept = "concept:primary:times-tables",
            prompt = "6 × 7 = ?",
            options = listOf("36", "42", "48", "54"),
            correctIndex = 1,
            difficulty = 0.45
        ),
        q(
            id = "checkin:primary:fractions",
            concept = "concept:primary:fractions",
            prompt = "What is half of 20?",
            options = listOf("8", "10", "12", "15"),
            correctIndex = 1,
            difficulty = 0.50
        ),
        q(
            id = "checkin:primary:geometry",
            concept = "concept:primary:geometry",
            prompt = "What is the sum of the three angles inside a triangle?",
            options = listOf("90°", "180°", "270°", "360°"),
            correctIndex = 1,
            difficulty = 0.55
        ),
        q(
            id = "checkin:primary:measurement",
            concept = "concept:primary:measurement",
            prompt = "How many centimetres are in 2.5 metres?",
            options = listOf("25 cm", "50 cm", "250 cm", "2500 cm"),
            correctIndex = 2,
            difficulty = 0.52
        ),
        q(
            id = "checkin:primary:word-problems",
            concept = "concept:primary:word-problems",
            prompt = "Aya has 3 packs of pencils. Each pack has 6 pencils. How many pencils does she have in total?",
            options = listOf("9", "12", "18", "24"),
            correctIndex = 2,
            difficulty = 0.60
        ),
        q(
            id = "checkin:primary:data",
            concept = "concept:primary:data",
            prompt = "What is the average of 4, 6 and 8?",
            options = listOf("5", "6", "7", "8"),
            correctIndex = 1,
            difficulty = 0.62
        )
    )
}
