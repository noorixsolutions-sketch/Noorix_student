package com.example.domain.ai

import android.content.Context
import com.example.BuildConfig
import com.example.core.model.Step
import com.example.data.local.ChatMessageDao
import com.example.data.local.ChatMessageEntity
import com.example.domain.verifier.SymbolicMathVerifier
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages specialized Cambridge O-Level AI Tutoring Agents.
 *
 * Model routing (per message):
 *   1. If a local llama-server is connected (Audit → Local LLM), chat is
 *      served by the local model — fully offline-capable.
 *   2. If an OpenRouter API key and model are selected (Audit → OpenRouter),
 *      chat streams via OpenRouter.
 *   3. Otherwise, Firebase AI Logic (Gemini) is used when a real
 *      GEMINI_API_KEY is configured (BuildConfig, injected from .env).
 *   4. Otherwise the student gets an honest offline notice (plus the
 *      deterministic verifier trace when one was computed).
 *
 * Agents:
 * - DANISH: Cambridge Mathematics (4024) specialist. Deterministic
 *   SymbolicMathVerifier runs FIRST on step submissions, then the model
 *   explains the trace.
 * - ZOYA: Sciences (Physics 5054 / Chemistry 5070 / Biology 5090).
 * - EXAM COACH: Past-paper strategy, rubrics, IRT diagnostics.
 */
class NoorixAiAgentManager(
    private val context: Context,
    private val chatMessageDao: ChatMessageDao
) {
    private val mathVerifier = SymbolicMathVerifier()
    private val models = mutableMapOf<String, GenerativeModel>()
    private val systemPrompts = mutableMapOf<String, String>()
    private var geminiAvailable = false

    private var localEngine: LlamaCppEngine? = null
    private var openRouterApiKey: String? = null
    private var openRouterModelId: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        ensureFirebase()
        initModels()
    }

    // ------------------------------------------------------------------
    // Engine configuration
    // ------------------------------------------------------------------

    /** Routes agent chat through the given local llama-server engine (or null for cloud). */
    fun setLocalEngine(engine: LlamaCppEngine?) {
        this.localEngine = engine
    }

    fun setOpenRouterConfig(apiKey: String?, modelId: String?) {
        this.openRouterApiKey = apiKey?.takeIf { it.isNotBlank() }
        this.openRouterModelId = modelId?.takeIf { it.isNotBlank() }
    }

    fun localEngine(): LlamaCppEngine? = localEngine

    val geminiReady: Boolean get() = geminiAvailable

    private fun ensureFirebase() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // No real key configured — Gemini path disabled, local engine may still work.
            geminiAvailable = false
            return
        }
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(context.packageName)
                    .setApiKey(apiKey)
                    .setProjectId("noorix-learning-solutions")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            geminiAvailable = true
        } catch (e: Exception) {
            android.util.Log.w("NoorixAiAgentManager", "FirebaseApp initialization failed: ${e.message}")
            geminiAvailable = false
        }
    }

    private fun initModels() {
        if (!geminiAvailable) return
        try {
            systemPrompts["danish"] =
                "You are Danish, the dedicated Cambridge O-Level Mathematics (Syllabus 4024) " +
                "AI tutor for Noorix Learning Solutions. Your specialty is algebra, quadratic factorisation, " +
                "geometric proofs, and step-by-step problem solving. " +
                "PEDAGOGICAL INVARIANT: When a student submits steps, explain the mathematical reasoning, " +
                "why algebraic rules apply, and address misconceptions gently. " +
                "Tone: supportive, mathematically rigorous, cyber-tech tutor."

            systemPrompts["zoya"] =
                "You are Zoya, the dedicated Cambridge O-Level Sciences AI tutor for Noorix Learning Solutions, " +
                "specializing in Physics (5054), Chemistry, and Biology. You explain kinematic graphs, " +
                "Newton's laws, energy conservation, circuit electricity, and molecular dynamics. " +
                "Use vivid conceptual analogies, clear unit conversions, and intuitive mental models. " +
                "Tone: energetic, highly visual, encouraging STEM mentor."

            systemPrompts["exam_coach"] =
                "You are Exam Coach, the Cambridge O-Level Examination Strategy mentor for Noorix Learning Solutions. " +
                "You provide tactical advice on past papers, mark schemes, method marks vs accuracy marks, " +
                "time management per question, examiner report pitfalls, and exam room psychology. " +
                "Tone: strategic, focused, confidence-building exam trainer."

            systemPrompts["ayesha"] =
                "You are Ayesha, the Cambridge O-Level English (1123) tutor for Noorix Learning Solutions. " +
                "You guide students in reading comprehension analysis, summary writing, and formal persuasive essays."

            systemPrompts["bilal"] =
                "You are Bilal, the Logic and Aptitude mentor for Noorix Learning Solutions, training students " +
                "in pattern sequences, mathematical puzzles, and deductive proofs."

            systemPrompts["career_guide"] =
                "You are Career Guide, the Cambridge O-Level Academic Advisor for Noorix Learning Solutions. " +
                "You map O-Level subject clusters to A-Level prerequisites and future university engineering/medical/CS degrees."

            for (agentId in systemPrompts.keys) {
                models[agentId] = Firebase.ai.generativeModel(
                    modelName = "gemini-2.5-flash",
                    systemInstruction = content {
                        text(systemPrompts.getValue(agentId))
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NoorixAiAgentManager", "Error initializing GenerativeModels: ${e.message}")
            geminiAvailable = false
        }
    }

    fun getChatHistory(agentId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForAgentFlow(agentId)
    }

    suspend fun clearChat(agentId: String) {
        chatMessageDao.clearAgentChat(agentId)
    }

    // ------------------------------------------------------------------
    // Messaging with real streaming (local llama-server preferred, Gemini fallback)
    // ------------------------------------------------------------------

    fun sendMessageStream(
        agentId: String,
        userMessageText: String,
        isExplicitStepVerification: Boolean = false
    ): Flow<String> = flow {
        val userMsgId = "msg-u-" + UUID.randomUUID().toString().take(6)
        val timestamp = System.currentTimeMillis()

        // 1. DANISH invariant: deterministic verifier runs FIRST on step submissions
        var mathVerificationTrace: String? = null
        var isMathVerified = false
        var enrichedPrompt = userMessageText

        if (agentId == "danish" && (isExplicitStepVerification || containsAlgebraicSteps(userMessageText))) {
            val parseResult = extractMathSteps(userMessageText)
            if (parseResult.steps.isNotEmpty()) {
                val verification = mathVerifier.verifySteps(
                    problemLaTeX = parseResult.problem,
                    studentSteps = parseResult.steps,
                    courseVersion = "2025-2027"
                )
                isMathVerified = true
                mathVerificationTrace = verification.symbolicTrace

                enrichedPrompt = buildString {
                    appendLine("The student submitted this step-by-step mathematical work:")
                    appendLine("Problem: ${parseResult.problem}")
                    parseResult.steps.forEach { step ->
                        appendLine("Step ${step.stepIndex}: ${step.expression}")
                    }
                    appendLine("\n--- DETERMINISTIC SYMBOLIC VERIFIER RESULT (Pre-computed rule engine) ---")
                    appendLine("Is Valid: ${verification.isValid}")
                    appendLine("First Invalid Step: ${verification.firstInvalidStepIndex ?: "None"}")
                    appendLine("Identified Error: ${verification.errorType?.name ?: "None"}")
                    appendLine("Verifier Trace:\n${verification.symbolicTrace}")
                    appendLine("\n--- INSTRUCTIONS FOR YOU ---")
                    appendLine("Explain this verification outcome in clear, natural pedagogical language to the student.")
                    appendLine("Do NOT re-evaluate the math yourself; use the deterministic trace above to explain WHY the step succeeded or failed, and how the student can correct their algebraic thinking.")
                }
            }
        }

        // Store user message in Room
        val userEntity = ChatMessageEntity(
            messageId = userMsgId,
            agentId = agentId,
            sender = "student",
            content = userMessageText,
            timestampMs = timestamp,
            isVerifiedMath = isMathVerified,
            mathVerificationTrace = mathVerificationTrace
        )
        chatMessageDao.insertMessage(userEntity)

        // 2. Stream: local llama-server first, then Gemini, then honest offline notice
        val local = localEngine
        val geminiModel = models[agentId] ?: if (agentId != "danish") models["danish"] else null
        val agentMsgId = "msg-a-" + UUID.randomUUID().toString().take(6)
        val responseBuilder = StringBuilder()

        when {
            local != null && local.isConnected -> {
                try {
                    local.streamChat(systemPrompts[agentId] ?: DEFAULT_PROMPT, enrichedPrompt).collect { partial ->
                        responseBuilder.setLength(0)
                        responseBuilder.append(partial)
                        emit(partial)
                    }
                } catch (e: Exception) {
                    val notice = if (responseBuilder.isEmpty()) {
                        "Local LLM stream failed (${e.localizedMessage ?: "connection error"}). " +
                            "Re-check host:port in Audit → Local LLM."
                    } else {
                        "\n[Local LLM stream interrupted: ${e.localizedMessage}]"
                    }
                    responseBuilder.append(if (responseBuilder.isEmpty()) notice else "\n$notice")
                    emit(responseBuilder.toString())
                }
            }

            openRouterApiKey != null && openRouterModelId != null -> {
                try {
                    streamOpenRouter(
                        systemPrompt = systemPrompts[agentId] ?: DEFAULT_PROMPT,
                        userMessage = enrichedPrompt,
                        apiKey = openRouterApiKey!!,
                        model = openRouterModelId!!
                    ).collect { partial ->
                        responseBuilder.setLength(0)
                        responseBuilder.append(partial)
                        emit(partial)
                    }
                } catch (e: Exception) {
                    val notice = if (responseBuilder.isEmpty()) {
                        "OpenRouter stream failed (${e.localizedMessage ?: "connection error"}). " +
                            "Check your API key and model selection in Settings."
                    } else {
                        "\n[OpenRouter stream interrupted: ${e.localizedMessage}]"
                    }
                    responseBuilder.append(if (responseBuilder.isEmpty()) notice else "\n$notice")
                    emit(responseBuilder.toString())
                }
            }

            geminiModel != null -> {
                try {
                    geminiModel.generateContentStream(enrichedPrompt).collect { chunk ->
                        val text = chunk.text ?: ""
                        if (text.isNotEmpty()) {
                            responseBuilder.append(text)
                            emit(responseBuilder.toString())
                        }
                    }
                } catch (e: Exception) {
                    val fallbackNotice = if (responseBuilder.isEmpty()) {
                        "Note: Gemini encountered a connection notice (${e.localizedMessage ?: "Offline"}). " +
                            (if (isMathVerified) "\nDeterministic Verifier Result:\n$mathVerificationTrace" else "")
                    } else {
                        "\n[Stream interrupted: ${e.localizedMessage}]"
                    }
                    responseBuilder.append(fallbackNotice)
                    emit(responseBuilder.toString())
                }
            }

            else -> {
                val notice = buildString {
                    append("No tutor model is reachable right now. Connect a local llama-server (Settings → Local LLM), ")
                    append("set an OpenRouter API key, or configure GEMINI_API_KEY to enable ${agentId}.")
                    if (isMathVerified && mathVerificationTrace != null) {
                        append("\n\nDeterministic Verifier Result:\n$mathVerificationTrace")
                    }
                }
                responseBuilder.append(notice)
                emit(responseBuilder.toString())
            }
        }

        // 3. Store completed agent response in Room
        val agentEntity = ChatMessageEntity(
            messageId = agentMsgId,
            agentId = agentId,
            sender = "agent",
            content = responseBuilder.toString(),
            timestampMs = System.currentTimeMillis(),
            isVerifiedMath = isMathVerified,
            mathVerificationTrace = mathVerificationTrace
        )
        chatMessageDao.insertMessage(agentEntity)
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val DEFAULT_PROMPT =
            "You are a Cambridge O-Level AI tutor for Noorix Learning Solutions. Be supportive and rigorous."
    }

    // ------------------------------------------------------------------
    // OpenRouter SSE Streaming
    // ------------------------------------------------------------------

    private fun streamOpenRouter(
        systemPrompt: String,
        userMessage: String,
        apiKey: String,
        model: String
    ): Flow<String> = flow {
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("temperature", 0.7)
            put("max_tokens", 1024)
            put(
                "messages",
                org.json.JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userMessage))
                }
            )
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(payload)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://noorix.solutions")
            .header("X-Title", "Noorix Learning Student")
            .header("Accept", "text/event-stream")
            .build()

        val response = httpClient.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val errBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                throw RuntimeException("OpenRouter HTTP ${response.code}: ${errBody.take(160)}")
            }
            val body = response.body ?: throw RuntimeException("Empty response body")
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
                emit("(OpenRouter returned empty stream)")
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseSseData(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null
        return trimmed.removePrefix("data:").trim()
    }

    private fun parseSseDelta(jsonLine: String): String? {
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

    // ------------------------------------------------------------------
    // Step detection (FIX: strict — a message only counts as "step work"
    // when at least 2 lines are pure algebraic expressions)
    // ------------------------------------------------------------------

    private val MATH_LINE_CHARS = Regex("^[0-9a-zA-Z()\\.^*\\+/=\\-\\s,²³\u00B2\u00B3]*$")
    private val LONG_WORD = Regex("[A-Za-z]{3,}")

    private fun isMathLine(line: String): Boolean {
        if (line.isEmpty() || !MATH_LINE_CHARS.matches(line)) return false
        val hasMathContent = line.any { it.isDigit() } || line.any { it in "=^*/()" } ||
            line.contains("x", true) || line.contains("a") || line.contains("b")
        if (!hasMathContent) return false
        // Reject prose that happens to be letter-only ("Isolvedit" style)
        if (LONG_WORD.containsMatchIn(line)) return false
        return true
    }

    fun containsAlgebraicSteps(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val mathLines = lines.count { isMathLine(it) }
        return mathLines >= 2
    }

    private data class ExtractedMath(val problem: String, val steps: List<Step>)

    private fun extractMathSteps(text: String): ExtractedMath {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ExtractedMath(text, emptyList())
        // Use the pure-math lines as the step work; fall back to the raw lines
        val mathLines = lines.filter { isMathLine(it) }
        val workLines = if (mathLines.size >= 2) mathLines else lines
        val problem = workLines.first()
        val steps = workLines.drop(1).mapIndexed { index, line ->
            Step(
                stepIndex = index + 1,
                expression = line,
                note = "Submitted student step"
            )
        }
        return ExtractedMath(problem, steps)
    }
}
