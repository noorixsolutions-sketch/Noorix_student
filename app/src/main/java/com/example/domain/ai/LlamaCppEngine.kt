package com.example.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REAL llama.cpp integration via a local llama-server.
 *
 * The app talks to a `llama-server` process (any device on the LAN, or the
 * phone itself over localhost) using its OpenAI-compatible HTTP API:
 *
 *   GET  /health                -> {"status":"ok"}
 *   GET  /v1/models             -> {"data":[{"id":"<model>"}, ...]}
 *   POST /v1/chat/completions   -> SSE stream, "data: {choices:[{delta:{content}}]}"
 *
 * This replaces the old stub (file pickers + fake Vulkan output). The audit
 * screen's "Local LLM" card now connects to host:port and lists live models.
 *
 * [parseSseDelta] is a pure function so the SSE handling is unit-testable
 * without a network.
 */
class LlamaCppEngine {

    data class Endpoint(val host: String, val port: Int) {
        val baseUrl: String get() = "http://$host:$port"
        override fun toString(): String = "$host:$port"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var endpoint: Endpoint? = null
    private var _models: List<String> = emptyList()
    var selectedModel: String? = null
        private set

    val connectedEndpoint: Endpoint? get() = endpoint
    val isConnected: Boolean get() = endpoint != null
    val models: List<String> get() = _models

    // ------------------------------------------------------------------
    // Connection management
    // ------------------------------------------------------------------

    /** Probes the server; on success stores endpoint + model list. */
    suspend fun connect(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        val normalizedHost = if (host.isBlank()) "127.0.0.1" else host.trim()
        val p = port.coerceIn(1, 65535)
        val base = Endpoint(normalizedHost, p).baseUrl
        try {
            val health = get("$base/health")
            if (health.code != 200) {
                return@withContext Result.failure(
                    RuntimeException("llama-server at $base answered health check with HTTP ${health.code}")
                )
            }
            // Try to list models (some builds expose it; if not, keep an empty list)
            val modelsResp = get("$base/v1/models")
            val modelIds = if (modelsResp.code == 200) {
                try {
                    val body = modelsResp.body?.string() ?: ""
                    val arr = JSONObject(body).optJSONArray("data") ?: org.json.JSONArray()
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotEmpty() } }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            endpoint = Endpoint(normalizedHost, p)
            _models = modelIds
            selectedModel = modelIds.firstOrNull()
            Result.success("Connected to llama-server at $base (${modelIds.size} model(s))")
        } catch (e: Exception) {
            endpoint = null
            _models = emptyList()
            Result.failure(
                RuntimeException("Cannot reach llama-server at $base (${e.localizedMessage ?: "connection refused"})")
            )
        }
    }

    /** Selects an available model for chat requests. */
    fun selectModel(modelId: String) {
        selectedModel = modelId
    }

    fun disconnect() {
        endpoint = null
        _models = emptyList()
        selectedModel = null
    }

    /** Re-fetches the live model list from a connected server. */
    suspend fun refreshModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val base = endpoint?.baseUrl ?: return@withContext Result.failure(RuntimeException("Not connected"))
        try {
            val resp = get("$base/v1/models")
            if (resp.code != 200) {
                return@withContext Result.failure(RuntimeException("Model list returned HTTP ${resp.code}"))
            }
            val body = resp.body?.string() ?: ""
            val arr = JSONObject(body).optJSONArray("data") ?: org.json.JSONArray()
            val ids = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotEmpty() } }
            _models = ids
            if (selectedModel !in ids) selectedModel = ids.firstOrNull()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(url: String): okhttp3.Response {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute()
    }

    // ------------------------------------------------------------------
    // Chat (SSE streaming)
    // ------------------------------------------------------------------

    /**
     * Streams a chat completion. Emits the ACCUMULATED assistant text after
     * each delta (matching the Gemini flow contract used by the UI).
     */
    fun streamChat(systemPrompt: String, userMessage: String): Flow<String> = flow {
        val base = endpoint?.baseUrl
            ?: throw IllegalStateException("llama-server is not connected")
        val modelId = selectedModel ?: _models.firstOrNull()
        if (modelId == null) {
            throw IllegalStateException("No model available on llama-server at $base")
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("stream", true)
            put("temperature", 0.7)
            put("max_tokens", 512)
            put(
                "messages",
                org.json.JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userMessage))
                }
            )
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$base/v1/chat/completions")
            .post(payload)
            .header("Accept", "text/event-stream")
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val errBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                throw RuntimeException("llama-server error HTTP ${response.code}: ${errBody.take(200)}")
            }
            val body: ResponseBody? = response.body ?: throw RuntimeException("Empty response body")
            val reader = body.byteStream().bufferedReader(Charsets.UTF_8)
            val builder = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                val data = parseSseData(line)
                if (data == "[DONE]") break
                if (data != null) {
                    val delta = parseSseDelta(data)
                    if (delta != null && delta.isNotEmpty()) {
                        builder.append(delta)
                        emit(builder.toString())
                    }
                }
                line = reader.readLine()
            }
            if (builder.isEmpty()) {
                emit("(llama-server returned an empty stream — check the model and server logs)")
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // SSE parsing (pure — unit tested)
    // ------------------------------------------------------------------

    /** Extracts the payload of an SSE "data:" line; null for comments/blank/other fields. */
    fun parseSseData(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(":")) return null // SSE comment
        if (!trimmed.startsWith("data:")) return null
        return trimmed.removePrefix("data:").trim()
    }

    /** Extracts choices[0].delta.content from a chat-completion SSE payload. */
    fun parseSseDelta(jsonLine: String): String? {
        return try {
            val obj = JSONObject(jsonLine)
            obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                ?.takeIf { it.isNotEmpty() }
                ?: obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}

