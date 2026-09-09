package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.curriculum.LearningContentProvider
import com.example.core.model.safeEmbedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningContentProviderTest {
    @Test
    fun `learning content is linked to known graph concepts`() {
        val content = LearningContentProvider(
            ApplicationProvider.getApplicationContext<Context>()
        ).load()

        assertTrue(content.videos.isNotEmpty())
        assertTrue(content.flashcards.isNotEmpty())
        assertTrue(content.quizzes.isNotEmpty())
        assertNotNull(content.videos.first().safeEmbedUrl())
        assertEquals("concept:4024:factorisation", content.videos.first().conceptId)
        assertTrue(content.forConcept("concept:4024:factorisation").quizzes.isNotEmpty())
    }

    @Test
    fun `quiz answers are within their option range`() {
        val content = LearningContentProvider(
            ApplicationProvider.getApplicationContext<Context>()
        ).load()
        content.quizzes.forEach { quiz ->
            assertTrue(quiz.correctIndex in quiz.options.indices)
        }
    }
}
