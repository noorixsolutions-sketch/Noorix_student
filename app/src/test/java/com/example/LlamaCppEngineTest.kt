package com.example

import com.example.domain.ai.LlamaCppEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the llama.cpp client's SSE parsing and connection
 * bookkeeping. Robolectric is used so org.json is a real implementation (the
 * plain-unit-test android.jar stubs throw). These never open a network
 * socket: only the parsers and local state transitions are exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LlamaCppEngineTest {

    @Test
    fun `endpoint formats base url and display string`() {
        val endpoint = LlamaCppEngine.Endpoint("10.0.0.5", 5050)
        assertEquals("http://10.0.0.5:5050", endpoint.baseUrl)
        assertEquals("10.0.0.5:5050", endpoint.toString())
    }

    @Test
    fun `new engine is disconnected with no models`() {
        val engine = LlamaCppEngine()
        assertFalse(engine.isConnected)
        assertNull(engine.connectedEndpoint)
        assertTrue(engine.models.isEmpty())
        assertNull(engine.selectedModel)
    }

    @Test
    fun `selectModel updates selection`() {
        val engine = LlamaCppEngine()
        engine.selectModel("llama-3-8b.Q4_K_M.gguf")
        assertEquals("llama-3-8b.Q4_K_M.gguf", engine.selectedModel)
    }

    @Test
    fun `parseSseData extracts the data payload`() {
        val engine = LlamaCppEngine()
        assertEquals("""{"id":"1"}""", engine.parseSseData("data: {\"id\":\"1\"}"))
        assertEquals("""{"id":"1"}""", engine.parseSseData("data:{\"id\":\"1\"}"))
    }

    @Test
    fun `parseSseData ignores comments, events, and blank lines`() {
        val engine = LlamaCppEngine()
        assertNull(engine.parseSseData("   "))
        assertNull(engine.parseSseData(": keep-alive comment"))
        assertNull(engine.parseSseData("event: message"))
    }

    @Test
    fun `parseSseDelta reads streaming delta content`() {
        val engine = LlamaCppEngine()
        val payload = """{"choices":[{"delta":{"content":"Hel"}}]}"""
        assertEquals("Hel", engine.parseSseDelta(payload))
    }

    @Test
    fun `parseSseDelta falls back to message content`() {
        val engine = LlamaCppEngine()
        val payload = """{"choices":[{"message":{"content":"world"}}]}"""
        assertEquals("world", engine.parseSseDelta(payload))
    }

    @Test
    fun `parseSseDelta returns null for empty or malformed payloads`() {
        val engine = LlamaCppEngine()
        assertNull(engine.parseSseDelta("""{"choices":[{"delta":{}}]}"""))
        assertNull(engine.parseSseDelta("{}"))
        assertNull(engine.parseSseDelta("this is not json"))
    }
}
