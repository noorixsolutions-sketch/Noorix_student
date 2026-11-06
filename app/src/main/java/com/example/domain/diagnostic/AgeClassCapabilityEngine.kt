package com.example.domain.diagnostic

data class CapabilityQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val domain: String, // Numbers, Algebra, Geometry, Applied Reasoning
    val topicName: String
)

data class StudyPlanRoadmapItem(
    val stageName: String,
    val focusDescription: String,
    val weeklyHours: Int,
    val targetMilestone: String,
    val topics: List<String>
)

data class CapabilityEvaluation(
    val scorePercent: Double,
    val correctCount: Int,
    val totalCount: Int,
    val positionLevel: String, // Advanced, Proficient, Developing, Foundational
    val diagnosticSummary: String,
    val domainMasteries: Map<String, Double>,
    val recommendedFocusTopics: List<String>,
    val roadmap: List<StudyPlanRoadmapItem>
)

object AgeClassCapabilityEngine {

    fun getQuestionsFor(studentClass: String, age: Int): List<CapabilityQuestion> {
        val normalized = studentClass.lowercase()
        return when {
            normalized.contains("grade 6") || normalized.contains("grade 7") || age <= 13 -> getGrade6To7Questions()
            normalized.contains("grade 8") || normalized.contains("grade 9") || (age in 14..15) -> getGrade8To9Questions()
            else -> getGrade10To11Questions()
        }
    }

    private fun getGrade6To7Questions(): List<CapabilityQuestion> = listOf(
        CapabilityQuestion(
            id = "g67_q1",
            prompt = "What is the value of: (-8) + 15 - (-5)?",
            options = listOf("2", "12", "28", "-18"),
            correctIndex = 1,
            explanation = "(-8) + 15 = 7. Subtracting a negative is adding: 7 - (-5) = 7 + 5 = 12.",
            domain = "Numbers",
            topicName = "Negative Numbers & Integers"
        ),
        CapabilityQuestion(
            id = "g67_q2",
            prompt = "Solve for x:  3x + 7 = 22",
            options = listOf("x = 3", "x = 5", "x = 7", "x = 15"),
            correctIndex = 1,
            explanation = "Subtract 7 from both sides: 3x = 15. Divide by 3: x = 5.",
            domain = "Algebra",
            topicName = "Linear Equations"
        ),
        CapabilityQuestion(
            id = "g67_q3",
            prompt = "A recipe requires flour and sugar in the ratio 4 : 3. If you use 240g of flour, how much sugar is needed?",
            options = listOf("160g", "180g", "200g", "320g"),
            correctIndex = 1,
            explanation = "1 unit of ratio = 240 / 4 = 60g. Sugar required = 3 × 60g = 180g.",
            domain = "Applied Reasoning",
            topicName = "Ratio & Proportion"
        ),
        CapabilityQuestion(
            id = "g67_q4",
            prompt = "In a triangle, two angles are 65° and 45°. What is the third angle?",
            options = listOf("60°", "70°", "80°", "90°"),
            correctIndex = 1,
            explanation = "The sum of interior angles in any triangle is 180°. 180° - (65° + 45°) = 180° - 110° = 70°.",
            domain = "Geometry",
            topicName = "Angle Properties of Triangles"
        ),
        CapabilityQuestion(
            id = "g67_q5",
            prompt = "What is 35% of 160?",
            options = listOf("48", "56", "62", "70"),
            correctIndex = 1,
            explanation = "10% of 160 = 16. 30% = 48. 5% = 8. So 35% = 48 + 8 = 56 (or 0.35 × 160 = 56).",
            domain = "Numbers",
            topicName = "Percentages & Fractions"
        )
    )

    private fun getGrade8To9Questions(): List<CapabilityQuestion> = listOf(
        CapabilityQuestion(
            id = "g89_q1",
            prompt = "Expand and simplify the algebraic expression:  (2x + 3)(x - 4)",
            options = listOf("2x² - 12", "2x² - 5x - 12", "2x² + 5x - 12", "2x² - 8x - 12"),
            correctIndex = 1,
            explanation = "Use FOIL: (2x)(x) + (2x)(-4) + (3)(x) + (3)(-4) = 2x² - 8x + 3x - 12 = 2x² - 5x - 12.",
            domain = "Algebra",
            topicName = "Algebraic Expansion & Factorization"
        ),
        CapabilityQuestion(
            id = "g89_q2",
            prompt = "Solve the linear equation:  4(2x - 3) = 3(x + 6)",
            options = listOf("x = 4", "x = 6", "x = 8", "x = 10"),
            correctIndex = 1,
            explanation = "Expand: 8x - 12 = 3x + 18. Subtract 3x: 5x - 12 = 18. Add 12: 5x = 30. Divide by 5: x = 6.",
            domain = "Algebra",
            topicName = "Linear Equations with Brackets"
        ),
        CapabilityQuestion(
            id = "g89_q3",
            prompt = "A right-angled triangle has legs of length 6 cm and 8 cm. What is the length of the hypotenuse?",
            options = listOf("9 cm", "10 cm", "12 cm", "14 cm"),
            correctIndex = 1,
            explanation = "By Pythagoras theorem: c² = a² + b² = 6² + 8² = 36 + 64 = 100. Thus c = √100 = 10 cm.",
            domain = "Geometry",
            topicName = "Pythagoras Theorem"
        ),
        CapabilityQuestion(
            id = "g89_q4",
            prompt = "What is the gradient (slope) of the line passing through points A(2, 3) and B(6, 11)?",
            options = listOf("1", "2", "3", "4"),
            correctIndex = 1,
            explanation = "Gradient m = (y₂ - y₁) / (x₂ - x₁) = (11 - 3) / (6 - 2) = 8 / 4 = 2.",
            domain = "Geometry",
            topicName = "Coordinate Geometry & Gradients"
        ),
        CapabilityQuestion(
            id = "g89_q5",
            prompt = "A shopkeeper buys an item for \$80 and sells it for \$100. What is the percentage profit?",
            options = listOf("20%", "25%", "30%", "125%"),
            correctIndex = 1,
            explanation = "Profit = \$100 - \$80 = \$20. Percentage profit = (20 / 80) × 100% = 25%.",
            domain = "Applied Reasoning",
            topicName = "Financial Mathematics & Profit"
        )
    )

    private fun getGrade10To11Questions(): List<CapabilityQuestion> = listOf(
        CapabilityQuestion(
            id = "g1011_q1",
            prompt = "Find the roots of the quadratic equation:  x² - 7x + 12 = 0",
            options = listOf("x = -3, -4", "x = 3, 4", "x = 2, 6", "x = -2, -6"),
            correctIndex = 1,
            explanation = "Factor the quadratic: (x - 3)(x - 4) = 0. Therefore x = 3 or x = 4.",
            domain = "Algebra",
            topicName = "Quadratic Equations"
        ),
        CapabilityQuestion(
            id = "g1011_q2",
            prompt = "In a circle with center O, an angle subtended at the center by arc AB is 76°. What is the angle subtended by the same arc at the circumference?",
            options = listOf("24°", "38°", "76°", "152°"),
            correctIndex = 1,
            explanation = "Circle Theorem: The angle subtended by an arc at the center is twice the angle subtended at the circumference. 76° / 2 = 38°.",
            domain = "Geometry",
            topicName = "Circle Theorems"
        ),
        CapabilityQuestion(
            id = "g1011_q3",
            prompt = "Solve the simultaneous equations:\n2x + y = 11\nx - y = 1",
            options = listOf("x = 3, y = 5", "x = 4, y = 3", "x = 5, y = 1", "x = 2, y = 7"),
            correctIndex = 1,
            explanation = "Add equations: (2x + y) + (x - y) = 11 + 1 → 3x = 12 → x = 4. Substitute: 4 - y = 1 → y = 3.",
            domain = "Algebra",
            topicName = "Simultaneous Linear Equations"
        ),
        CapabilityQuestion(
            id = "g1011_q4",
            prompt = "A bag contains 5 red balls and 3 blue balls. If two balls are drawn one after another without replacement, what is the probability that both are red?",
            options = listOf("10/28 (5/14)", "25/64", "15/56", "1/2"),
            correctIndex = 0,
            explanation = "P(1st Red) = 5/8. P(2nd Red | 1st Red) = 4/7. P(both Red) = (5/8) × (4/7) = 20/56 = 5/14.",
            domain = "Applied Reasoning",
            topicName = "Probability & Dependent Events"
        ),
        CapabilityQuestion(
            id = "g1011_q5",
            prompt = "The gradient of line L₁ is 3/4. If line L₂ is perpendicular to L₁, what is the gradient of L₂?",
            options = listOf("4/3", "-4/3", "3/4", "-3/4"),
            correctIndex = 1,
            explanation = "For two perpendicular lines, m₁ × m₂ = -1. Therefore m₂ = -1 / (3/4) = -4/3.",
            domain = "Geometry",
            topicName = "Coordinate Geometry & Perpendicular Lines"
        )
    )

    fun evaluate(
        questions: List<CapabilityQuestion>,
        userAnswers: Map<Int, Int>, // question index -> chosen option index
        studentClass: String,
        age: Int
    ): CapabilityEvaluation {
        var correct = 0
        val domainCorrect = mutableMapOf<String, Int>()
        val domainTotal = mutableMapOf<String, Int>()
        val weakTopics = mutableListOf<String>()

        questions.forEachIndexed { index, q ->
            val chosen = userAnswers[index]
            val isCorrect = chosen != null && chosen == q.correctIndex
            if (isCorrect) {
                correct++
                domainCorrect[q.domain] = (domainCorrect[q.domain] ?: 0) + 1
            } else {
                weakTopics.add(q.topicName)
            }
            domainTotal[q.domain] = (domainTotal[q.domain] ?: 0) + 1
        }

        val total = questions.size.coerceAtLeast(1)
        val scorePercent = (correct.toDouble() / total.toDouble()) * 100.0

        val (positionLevel, summary) = when {
            scorePercent >= 80.0 -> Pair(
                "Advanced / Distinction Ready",
                "Demonstrates excellent mastery of $studentClass concepts. Ready for higher-order reasoning, speed calibration, and exam-standard problem solving."
            )
            scorePercent >= 60.0 -> Pair(
                "Proficient / Solid Baseline",
                "Has a solid grasp of key principles for $studentClass. Strengthening weak areas in ${weakTopics.take(2).joinToString(", ").ifEmpty { "applied reasoning" }} will quickly push you to top marks."
            )
            scorePercent >= 40.0 -> Pair(
                "Developing / Core Needs Attention",
                "Grasps basic terms, but requires targeted step-by-step reinforcement in algebraic procedures and geometric theorems before tackling complex past papers."
            )
            else -> Pair(
                "Foundational / Requires Bridge",
                "Significant knowledge gaps in core prerequisites. Your customized study plan will start with foundational concept builders to establish confidence."
            )
        }

        val domainScores = mutableMapOf<String, Double>()
        domainTotal.forEach { (dom, count) ->
            val corr = domainCorrect[dom] ?: 0
            domainScores[dom] = corr.toDouble() / count.toDouble()
        }

        val roadmap = listOf(
            StudyPlanRoadmapItem(
                stageName = "Stage 1: Prerequisite Reinforcement",
                focusDescription = if (weakTopics.isNotEmpty()) "Focus on ${weakTopics.take(2).joinToString(" & ")}" else "Foundational review & speed drills",
                weeklyHours = if (scorePercent < 60.0) 6 else 4,
                targetMilestone = "Master foundational prerequisite competencies with 85%+ accuracy.",
                topics = if (weakTopics.isNotEmpty()) weakTopics.take(3) else listOf("Algebraic Techniques", "Number Operations")
            ),
            StudyPlanRoadmapItem(
                stageName = "Stage 2: Core Syllabus Deep-Dive",
                focusDescription = "Systematic coverage of Cambridge $studentClass core units with interactive worked examples.",
                weeklyHours = 5,
                targetMilestone = "Complete all topical check-ins across Cambridge 4024 / 5054 syllabus.",
                topics = listOf("Equations & Functions", "Coordinate Geometry", "Mensuration & Vectors", "Applied Statistics")
            ),
            StudyPlanRoadmapItem(
                stageName = "Stage 3: Past Paper Synthesis & Timed Trials",
                focusDescription = "Full-length exam questions with rubric grading and symbolic mistake checks.",
                weeklyHours = 6,
                targetMilestone = "Achieve target distinction grade under timed examination conditions.",
                topics = listOf("Past Paper 1 Drills", "Past Paper 2 Structured Multi-step", "Examiner Trap Avoidance")
            )
        )

        return CapabilityEvaluation(
            scorePercent = scorePercent,
            correctCount = correct,
            totalCount = total,
            positionLevel = positionLevel,
            diagnosticSummary = summary,
            domainMasteries = domainScores,
            recommendedFocusTopics = weakTopics.ifEmpty { listOf("Advanced Past Paper Modeling") },
            roadmap = roadmap
        )
    }
}
