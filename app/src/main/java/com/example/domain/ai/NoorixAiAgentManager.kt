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
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages specialized Cambridge O-Level AI Tutoring Agents via Firebase AI Logic (Gemini).
 *
 * Each agent operates with an explicit, distinct pedagogical system instruction:
 * - DANISH: Cambridge Mathematics (4024) algebra and problem-solving specialist.
 *   Strict separation of concerns: deterministic math checks run through SymbolicMathVerifier
 *   before LLM natural language explanation.
 * - ZOYA: Cambridge Sciences (Physics 5054, Chemistry 5070, Biology 5090) conceptual tutor.
 * - EXAM COACH: Past-paper strategy, scoring rubrics, examiner reports, and IRT diagnostics.
 */
class NoorixAiAgentManager(
    private val context: Context,
    private val chatMessageDao: ChatMessageDao
) {
    private val mathVerifier = SymbolicMathVerifier()
    private val models = mutableMapOf<String, GenerativeModel>()

    init {
        ensureFirebase()
        initModels()
    }

    private fun ensureFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val apiKey = try {
                    BuildConfig.GEMINI_API_KEY.ifBlank { "AIzaSyNoorixLearningPlaceholderKey" }
                } catch (_: Exception) {
                    "AIzaSyNoorixLearningPlaceholderKey"
                }
                val options = FirebaseOptions.Builder()
                    .setApplicationId(context.packageName)
                    .setApiKey(apiKey)
                    .setProjectId("noorix-learning-solutions")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            android.util.Log.w("NoorixAiAgentManager", "FirebaseApp initialization fallback: ${e.message}")
        }
    }

    private fun initModels() {
        try {
            // DANISH: Math 4024
            models["danish"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Danish, the dedicated Cambridge O-Level Mathematics (Syllabus 4024) " +
                        "AI tutor for Noorix Learning Solutions. Your specialty is algebra, quadratic factorisation, " +
                        "geometric proofs, and step-by-step problem solving. " +
                        "PEDAGOGICAL INVARIANT: When a student submits steps, explain the mathematical reasoning, " +
                        "why algebraic rules apply, and address misconceptions gently. " +
                        "Tone: supportive, mathematically rigorous, cyber-tech tutor."
                    )
                }
            )

            // ZOYA: Sciences (5054 / 5070 / 5090)
            models["zoya"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Zoya, the dedicated Cambridge O-Level Sciences AI tutor for Noorix Learning Solutions, " +
                        "specializing in Physics (5054), Chemistry, and Biology. You explain kinematic graphs, " +
                        "Newton's laws, energy conservation, circuit electricity, and molecular dynamics. " +
                        "Use vivid conceptual analogies, clear unit conversions, and intuitive mental models. " +
                        "Tone: energetic, highly visual, encouraging STEM mentor."
                    )
                }
            )

            // EXAM COACH: Past paper strategy & IRT diagnostics
            models["exam_coach"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Exam Coach, the Cambridge O-Level Examination Strategy mentor for Noorix Learning Solutions. " +
                        "You provide tactical advice on past papers, mark schemes, method marks vs accuracy marks, " +
                        "time management per question, examiner report pitfalls, and exam room psychology. " +
                        "Tone: strategic, focused, confidence-building exam trainer."
                    )
                }
            )

            // AYESHA: Language & Communication
            models["ayesha"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Ayesha, the Cambridge O-Level English (1123) tutor for Noorix Learning Solutions. " +
                        "You guide students in reading comprehension analysis, summary writing, and formal persuasive essays."
                    )
                }
            )

            // BILAL: Aptitude & Logic
            models["bilal"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Bilal, the Logic and Aptitude mentor for Noorix Learning Solutions, training students " +
                        "in pattern sequences, mathematical puzzles, and deductive proofs."
                    )
                }
            )

            // CAREER GUIDE
            models["career_guide"] = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text(
                        "You are Career Guide, the Cambridge O-Level Academic Advisor for Noorix Learning Solutions. " +
                        "You map O-Level subject clusters to A-Level prerequisites and future university engineering/medical/CS degrees."
                    )
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("NoorixAiAgentManager", "Error initializing GenerativeModels: ${e.message}")
        }
    }

    fun getChatHistory(agentId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForAgentFlow(agentId)
    }

    suspend fun clearChat(agentId: String) {
        chatMessageDao.clearAgentChat(agentId)
    }

    /**
     * Sends a message to the specified agent with real streaming tokens.
     *
     * Invariant for DANISH: If the message contains algebraic step lines (e.g. multi-step working),
     * it executes SymbolicMathVerifier FIRST deterministically, then asks Gemini to explain the trace.
     */
    fun sendMessageStream(
        agentId: String,
        userMessageText: String,
        isExplicitStepVerification: Boolean = false
    ): Flow<String> = flow {
        val userMsgId = "msg-u-" + UUID.randomUUID().toString().take(6)
        val timestamp = System.currentTimeMillis()

        // 1. Check if Danish deterministic verifier should run
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

        // 2. Invoke real Firebase AI GenerativeModel stream
        val model = models[agentId] ?: models["danish"]
        val agentMsgId = "msg-a-" + UUID.randomUUID().toString().take(6)
        val responseBuilder = StringBuilder()

        if (model != null) {
            try {
                val responseFlow = model.generateContentStream(enrichedPrompt)
                responseFlow.collect { chunk ->
                    val text = chunk.text ?: ""
                    if (text.isNotEmpty()) {
                        responseBuilder.append(text)
                        emit(responseBuilder.toString())
                    }
                }
            } catch (e: Exception) {
                val fallbackNotice = if (responseBuilder.isEmpty()) {
                    "Note: Firebase AI encountered a connection notice (${e.localizedMessage ?: "Offline"}). " +
                    (if (isMathVerified) "\nDeterministic Verifier Result:\n$mathVerificationTrace" else "")
                } else {
                    "\n[Stream interrupted: ${e.localizedMessage}]"
                }
                responseBuilder.append(fallbackNotice)
                emit(responseBuilder.toString())
            }
        } else {
            val notice = "Agent $agentId initialized. Ready to solve Cambridge O-Level topics."
            responseBuilder.append(notice)
            emit(responseBuilder.toString())
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

    private fun containsAlgebraicSteps(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.size >= 2 && lines.any { it.contains("=") || it.contains("+") || it.contains("-") || it.contains("x") }
    }

    private data class ExtractedMath(val problem: String, val steps: List<Step>)

    private fun extractMathSteps(text: String): ExtractedMath {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ExtractedMath(text, emptyList())

        val problem = lines.first()
        val steps = lines.drop(1).mapIndexed { index, line ->
            Step(
                stepIndex = index + 1,
                expression = line,
                note = "Submitted student step"
            )
        }
        return ExtractedMath(problem, steps)
    }
}
