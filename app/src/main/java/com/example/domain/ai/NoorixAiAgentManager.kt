package com.example.domain.ai

import android.content.Context
import com.example.core.model.Step
import com.example.core.model.VerificationResult
import com.example.data.local.ChatMessageDao
import com.example.data.local.ChatMessageEntity
import com.example.domain.verifier.SymbolicMathVerifier
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
 *   1. When an OpenRouter API key + free model are configured (Settings →
 *      AI Engine), chat streams via OpenRouter with per-agent system prompts.
 *   2. Otherwise — and when the cloud stream fails before producing any
 *      content — the deterministic [OfflineAgentEngine] answers from bundled
 *      course packs and scripted playbooks. Zero config, always available.
 *
 * Agents: DANISH (Maths 4024), ZOYA (Sciences), AYESHA (English 1123),
 * BILAL (logic puzzles), EXAM COACH (exam strategy), CAREER GUIDE
 * (subjects & pathways). DANISH invariant: the deterministic
 * SymbolicMathVerifier runs FIRST on step submissions, then the active
 * engine explains the trace.
 */
class NoorixAiAgentManager(
    private val context: Context,
    private val chatMessageDao: ChatMessageDao
) {
    private val mathVerifier = SymbolicMathVerifier()
    private val offlineEngine = OfflineAgentEngine(context)
    private val systemPrompts = mutableMapOf<String, String>()

    private var openRouterApiKey: String? = null
    private var openRouterModelId: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        initPrompts()
    }

    // ------------------------------------------------------------------
    // Engine configuration
    // ------------------------------------------------------------------

    fun setOpenRouterConfig(apiKey: String?, modelId: String?) {
        this.openRouterApiKey = apiKey?.takeIf { it.isNotBlank() }
        this.openRouterModelId = modelId?.takeIf { it.isNotBlank() }
    }

    private fun initPrompts() {
        systemPrompts["danish"] =
            "You are Danish, the dedicated Cambridge O-Level & Lower Secondary Mathematics (Syllabus 4024 / Grades 6-11) " +
            "AI mentor for Noorix Learning Solutions. Your pedagogical mission spans foundational arithmetic (fractions, negative numbers, order of operations) " +
            "through algebraic expressions, linear equations, quadratic factorisation, simultaneous systems, trigonometry, and coordinate geometry.\n" +
            "PEDAGOGICAL INVARIANT: When tutoring younger students (Grades 6-8), use intuitive visual analogies (e.g. balance scales for equations, number lines for signed numbers). " +
            "When tutoring O-Level candidates (Grades 9-11), enforce strict Cambridge mathematical rigor, show method marks, and address sign distribution traps.\n" +
            "When a student submits steps, explain the deterministic verifier trace clearly, identify the exact breakdown point, and guide the student to self-correct.\n" +
            "Tone: supportive, mathematically rigorous, cyber-tech tutor."

        systemPrompts["zoya"] =
            "You are Zoya, the dedicated Cambridge O-Level & Lower Secondary Sciences AI mentor for Noorix Learning Solutions, " +
            "specializing in Physics (5054), Chemistry (5070), and Biology (5090) across Grades 6 to 11.\n" +
            "For Grades 6-8: Explain states of matter, basic forces, simple circuits, and cell structures using vivid real-world models.\n" +
            "For O-Level (Grades 9-11): Teach velocity-time graphs, Newton's laws (F=ma), work-energy conservation, thermal gas laws, Ohm's law (V=IR), chemical stoichiometry, and genetics with exact SI units and Cambridge examiner precision.\n" +
            "Tone: energetic, visual, encouraging STEM mentor."

        systemPrompts["ayesha"] =
            "You are Ayesha, the Cambridge O-Level English (Syllabus 1123 / Grades 6-11) language specialist for Noorix Learning Solutions.\n" +
            "For Grades 6-8: Focus on vocabulary expansion, parts of speech, active/passive voice, punctuation, and clear paragraphing.\n" +
            "For O-Level (Grades 9-11): Coach students on Cambridge Paper 1 Directed Writing (formal letters, speeches, persuasive articles) and Paper 2 Reading Comprehension & Summary Writing (extracting 10 discrete content points, writing within 150-180 words, using own words).\n" +
            "Tone: articulate, encouraging, linguistically precise."

        systemPrompts["bilal"] =
            "You are Bilal, the Logic, Aptitude, and Mathematical Reasoning mentor for Noorix Learning Solutions, training students across Grades 6 to O-Levels.\n" +
            "You specialize in number sequence puzzles, spatial rotations, Venn diagram logic, deductive problem solving, and quantitative speed drills.\n" +
            "Encourage students to articulate their thinking process (metacognition) and break complex problems into atomic logical steps.\n" +
            "Tone: sharp, engaging, enthusiastic logic coach."

        systemPrompts["exam_coach"] =
            "You are Exam Coach, the Cambridge O-Level Examination Strategy mentor for Noorix Learning Solutions.\n" +
            "You provide tactical guidance on Cambridge past papers, marking schemes (M marks for method, A marks for accuracy, B marks for independent accuracy), " +
            "command word breakdowns ('State' vs 'Explain' vs 'Calculate' vs 'Deduce'), time management (1 mark ≈ 1 minute), and avoiding examiner report pitfalls.\n" +
            "Tone: strategic, focused, confidence-building exam master."

        systemPrompts["career_guide"] =
            "You are Career Guide, the Cambridge Academic & Career Pathway Advisor for Noorix Learning Solutions.\n" +
            "You help students (Grades 6-11) explore subject combinations, understand O-Level subject selection, map prerequisites for A-Levels, " +
            "and align academic strengths with future career pathways in Engineering, Medicine, Computer Science & AI, Law, and Finance.\n" +
            "Tone: insightful, structured, inspirational academic advisor."

    }

    fun getChatHistory(agentId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForAgentFlow(agentId)
    }

    suspend fun clearChat(agentId: String) {
        chatMessageDao.clearAgentChat(agentId)
    }

    // ------------------------------------------------------------------
    // Messaging with real streaming (OpenRouter cloud, offline engine fallback)
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
        var danishVerification: VerificationResult? = null
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
                danishVerification = verification
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

        // 2. Stream: OpenRouter cloud first, offline engine otherwise (and on
        //    cloud failure before any content — the app always answers).
        val agentMsgId = "msg-a-" + UUID.randomUUID().toString().take(6)
        val responseBuilder = StringBuilder()

        if (openRouterApiKey != null && openRouterModelId != null) {
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
                if (responseBuilder.isEmpty()) {
                    offlineEngine.respond(agentId, userMessageText, danishVerification).collect { partial ->
                        responseBuilder.setLength(0)
                        responseBuilder.append(partial)
                        emit(partial)
                    }
                }
            } catch (e: Exception) {
                if (responseBuilder.isEmpty()) {
                    offlineEngine.respond(agentId, userMessageText, danishVerification).collect { partial ->
                        responseBuilder.setLength(0)
                        responseBuilder.append(partial)
                        emit(partial)
                    }
                } else {
                    val notice = "\n[OpenRouter stream interrupted: ${e.localizedMessage}]"
                    responseBuilder.append(notice)
                    emit(responseBuilder.toString())
                }
            }
        } else {
            offlineEngine.respond(agentId, userMessageText, danishVerification).collect { partial ->
                responseBuilder.setLength(0)
                responseBuilder.append(partial)
                emit(partial)
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
