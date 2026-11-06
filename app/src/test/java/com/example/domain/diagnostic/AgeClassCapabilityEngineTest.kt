package com.example.domain.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeClassCapabilityEngineTest {

    @Test
    fun `questions are tailored for Grade 6-7`() {
        val questions = AgeClassCapabilityEngine.getQuestionsFor("Grade 7 (Middle Secondary)", 12)
        assertEquals(5, questions.size)
        assertTrue(questions.any { it.domain == "Numbers" })
        assertTrue(questions.any { it.domain == "Algebra" })
        assertTrue(questions.any { it.domain == "Geometry" })
        assertTrue(questions.any { it.domain == "Applied Reasoning" })
    }

    @Test
    fun `questions are tailored for Grade 8-9`() {
        val questions = AgeClassCapabilityEngine.getQuestionsFor("Grade 9 (O-Level Year 1)", 14)
        assertEquals(5, questions.size)
        assertTrue(questions.any { it.topicName.contains("Expansion") })
        assertTrue(questions.any { it.topicName.contains("Pythagoras") })
    }

    @Test
    fun `questions are tailored for Grade 10-11`() {
        val questions = AgeClassCapabilityEngine.getQuestionsFor("Grade 11 (O-Level Final)", 16)
        assertEquals(5, questions.size)
        assertTrue(questions.any { it.topicName.contains("Quadratic") })
        assertTrue(questions.any { it.topicName.contains("Circle") })
    }

    @Test
    fun `evaluation computes correct score and level for perfect answers`() {
        val questions = AgeClassCapabilityEngine.getQuestionsFor("Grade 9", 14)
        val answers = questions.mapIndexed { index, q -> index to q.correctIndex }.toMap()

        val eval = AgeClassCapabilityEngine.evaluate(questions, answers, "Grade 9", 14)
        assertEquals(100.0, eval.scorePercent, 0.01)
        assertEquals(5, eval.correctCount)
        assertTrue(eval.positionLevel.contains("Advanced"))
        assertEquals(3, eval.roadmap.size)
    }

    @Test
    fun `evaluation computes developing level and flags weak topics`() {
        val questions = AgeClassCapabilityEngine.getQuestionsFor("Grade 9", 14)
        // Answer only 2 out of 5 correctly
        val answers = mapOf(
            0 to questions[0].correctIndex,
            1 to questions[1].correctIndex,
            2 to (questions[2].correctIndex + 1) % 4,
            3 to (questions[3].correctIndex + 1) % 4,
            4 to (questions[4].correctIndex + 1) % 4
        )

        val eval = AgeClassCapabilityEngine.evaluate(questions, answers, "Grade 9", 14)
        assertEquals(40.0, eval.scorePercent, 0.01)
        assertEquals(2, eval.correctCount)
        assertTrue(eval.positionLevel.contains("Developing"))
        assertEquals(3, eval.recommendedFocusTopics.size)
    }
}
