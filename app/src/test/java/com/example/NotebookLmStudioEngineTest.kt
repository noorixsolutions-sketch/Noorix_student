package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.notebooklm.NotebookLmStudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotebookLmStudioEngineTest {

    @Test
    fun `loads complete K-12 curriculum JSON files across all grade bands and subjects`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bands = NotebookLmStudioEngine.loadAllK12Bands(context)

        assertEquals(4, bands.size)
        val allGrades = bands.flatMap { it.grades }
        // Kindergarten + Grades 1..12 = 13 grades
        assertEquals(13, allGrades.size)

        val allSubjects = allGrades.flatMap { it.subjects }
        assertTrue(allSubjects.size >= 25)

        val allTopics = allSubjects.flatMap { it.topics }
        assertTrue(allTopics.size >= 35)
    }

    @Test
    fun `generates grounded NotebookLM quiz, 2-host podcast, and 5-scene video explainer per topic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bands = NotebookLmStudioEngine.loadAllK12Bands(context)
        val allTopics = bands.flatMap { b -> b.grades.flatMap { g -> g.subjects.flatMap { s -> s.topics } } }

        allTopics.forEach { topic ->
            val bundle = NotebookLmStudioEngine.generateBundleForTopic(
                topic = topic,
                customUserNotes = listOf("Student note: Always check boundary conditions.")
            )
            assertTrue(bundle.sources.size >= 5)
            assertTrue(bundle.podcast.turns.size >= 10)
            assertEquals("Alex", bundle.podcast.turns[0].speaker)
            assertEquals("Zoya", bundle.podcast.turns[1].speaker)
            assertEquals(5, bundle.videoExplainer.scenes.size)
            assertEquals(4, bundle.quizItems.size)
            bundle.quizItems.forEach { q ->
                assertTrue(q.correctIndex in q.options.indices)
                assertTrue(q.explanation.isNotBlank())
            }
        }
    }

    @Test
    fun `generates structured student notes doc with knowledge graph links and pdf export`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bands = NotebookLmStudioEngine.loadAllK12Bands(context)
        val allTopics = bands.flatMap { b -> b.grades.flatMap { g -> g.subjects.flatMap { s -> s.topics } } }

        assertTrue(allTopics.isNotEmpty())

        allTopics.forEach { topic ->
            val bundle = NotebookLmStudioEngine.generateBundleForTopic(topic)
            val notes = bundle.studentNotesDoc
            assertEquals(topic.topicId, notes.topicId)
            assertEquals(topic.title, notes.title)
            assertTrue("Student notes must have core principles", notes.corePrinciples.isNotEmpty())
            assertTrue("Student notes must have worked example", notes.workedExample.isNotBlank())
            assertTrue("Student notes must have examiner misconception alert", notes.commonMisconception.isNotBlank())
            assertTrue("Student notes must include review questions", notes.reviewQuestions.isNotEmpty())
            assertTrue("Student notes markdown must contain title", notes.printableMarkdown.contains(topic.title))
        }

        // Test real PDF generation on sample topics across different grades
        val sampleTopics = listOf(
            allTopics.first(), // Elementary
            allTopics[allTopics.size / 2], // Middle / O-Level
            allTopics.last() // A-Level
        )

        sampleTopics.forEach { sampleTopic ->
            val bundle = NotebookLmStudioEngine.generateBundleForTopic(sampleTopic)
            val pdfFile = com.example.domain.notebooklm.PdfNotesExporter.generatePdf(context, bundle)
            assertTrue("Generated PDF file must exist", pdfFile.exists())
            assertTrue("Generated PDF file size must be non-empty (>1KB)", pdfFile.length() > 1024L)
        }
    }
}
