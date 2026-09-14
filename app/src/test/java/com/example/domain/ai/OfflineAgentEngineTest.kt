package com.example.domain.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.MathErrorType
import com.example.core.model.VerificationResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OfflineAgentEngine must answer deterministically with zero network:
 * greetings, concept cards, quiz sessions, puzzles, and verification
 * explanations all come from bundled assets + scripted playbooks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineAgentEngineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun ask(agentId: String, message: String, verification: VerificationResult? = null): String =
        runBlocking {
            OfflineAgentEngine(context).respond(agentId, message, verification).toList().last()
        }

    @Test
    fun `greeting names the agent and declares offline mode`() {
        val reply = ask("danish", "hello")
        assertTrue(reply.contains("Danish"))
        assertTrue(reply.contains("offline", ignoreCase = true))
    }

    @Test
    fun `unknown agent id still gets a safe reply`() {
        val reply = ask("mystery_agent", "hi")
        assertTrue(reply.contains("offline", ignoreCase = true))
    }

    @Test
    fun `concept question returns an explainer card`() {
        val reply = ask("danish", "explain quadratic factorisation")
        assertTrue(reply.contains("Factoris", ignoreCase = true))
        assertTrue(reply.contains("quiz me on", ignoreCase = true))
    }

    @Test
    fun `quiz session advances and finishes with a summary`() {
        val engine = OfflineAgentEngine(context)
        val first = runBlocking { engine.respond("danish", "quiz me on factorisation").toList().last() }
        assertTrue(first.contains("Question 1"))

        // Answer (right or wrong) must advance the session, never stall it.
        val second = runBlocking { engine.respond("danish", "A").toList().last() }
        assertTrue(second.contains("Question 2") || second.contains("complete", ignoreCase = true))

        val done = runBlocking { engine.respond("danish", "stop").toList().last() }
        assertTrue(done.contains("Stopped", ignoreCase = true) || done.contains("complete", ignoreCase = true))
    }

    @Test
    fun `quiz without topic picks a concept instead of stalling`() {
        val reply = ask("zoya", "quiz me")
        assertTrue(reply.contains("Question 1"))
    }

    @Test
    fun `puzzle flow serves hint then accepts the deterministic answer`() {
        val engine = OfflineAgentEngine(context)
        // First puzzle is deterministic: a=2, d=3 -> 2, 5, 8, 11, ? (answer 14).
        val first = runBlocking { engine.respond("bilal", "give me a puzzle").toList().last() }
        assertTrue(first.contains("2, 5, 8, 11"))

        val noNumber = runBlocking { engine.respond("bilal", "banana").toList().last() }
        assertTrue(noNumber.contains("number", ignoreCase = true))

        val wrong = runBlocking { engine.respond("bilal", "999").toList().last() }
        assertTrue(wrong.contains("hint", ignoreCase = true))

        val right = runBlocking { engine.respond("bilal", "14").toList().last() }
        assertTrue(right.contains("Correct", ignoreCase = true))
    }

    @Test
    fun `invalid verification explains the failing step`() {
        val v = VerificationResult(
            isValid = false,
            firstInvalidStepIndex = 2,
            errorType = MathErrorType.SIGN_ERROR,
            symbolicTrace = "x=2",
        )
        val reply = ask("danish", "check my steps", v)
        assertTrue(reply.contains("Step 2"))
        assertTrue(reply.contains("sign", ignoreCase = true))
    }

    @Test
    fun `valid verification confirms success`() {
        val v = VerificationResult(
            isValid = true,
            firstInvalidStepIndex = null,
            errorType = null,
            symbolicTrace = "ok",
        )
        val reply = ask("danish", "check my steps", v)
        assertTrue(reply.contains("check out", ignoreCase = true))
    }

    @Test
    fun `exam coach answers command-word questions from its playbook`() {
        val reply = ask("exam_coach", "what do command words mean?")
        assertTrue(reply.contains("Command words", ignoreCase = true))
    }

    @Test
    fun `out-of-scope question falls back honestly without crashing`() {
        val reply = ask("danish", "who won the football world cup in 1966?")
        assertTrue(reply.contains("offline", ignoreCase = true))
        assertTrue(reply.contains("OpenRouter"))
    }
}
