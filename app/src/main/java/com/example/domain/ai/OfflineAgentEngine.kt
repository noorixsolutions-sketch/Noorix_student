package com.example.domain.ai

import android.content.Context
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.KGNode
import com.example.core.model.LearningQuiz
import com.example.core.model.NodeType
import com.example.core.model.QuestionItem
import com.example.core.model.VerificationResult
import com.example.data.curriculum.CoursePackageProvider
import com.example.data.curriculum.LearningContentProvider
import com.example.domain.overlay.OverlayStatsProvider
import com.example.presentation.util.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic offline "brain" for every AI tutor.
 *
 * Zero network, zero API keys, zero config: it answers purely from the
 * app's bundled assets (course packages, learning content, knowledge graph)
 * plus scripted per-agent playbooks (exam technique, study skills, careers).
 *
 * Routing (see [NoorixAiAgentManager]): when the user has configured an
 * OpenRouter key + model, chat goes to the cloud; otherwise — or when the
 * cloud call fails — every agent falls back to this engine so the app is
 * always usable on a fresh install.
 *
 * Conversational state (active quiz / puzzle) is kept in memory per agent id
 * and lasts for the process lifetime. Content loading is lazy, cached, and
 * failure-tolerant: missing assets degrade to playbooks, never to a crash.
 */
class OfflineAgentEngine(private val context: Context) {

    // ------------------------------------------------------------------
    // Knowledge loading (lazy, cached, failure-tolerant)
    // ------------------------------------------------------------------

    private data class Knowledge(
        val concepts: List<KGNode>,
        val questions: List<QuestionItem>,
        val flashcards: List<Flashcard>,
        val quizzes: List<LearningQuiz>,
        val videos: List<ExplainerVideo>,
        val streakDays: Int,
        val accuracy: Int,
    )

    private val loadLock = Any()

    @Volatile
    private var knowledge: Knowledge? = null

    private suspend fun ensureLoaded(): Knowledge {
        knowledge?.let { return it }
        val fresh = loadKnowledge()
        synchronized(loadLock) {
            val existing = knowledge
            if (existing != null) return existing
            knowledge = fresh
            return fresh
        }
    }

    private suspend fun loadKnowledge(): Knowledge {
        val pkgProvider = CoursePackageProvider(context)
        val math = runCatching { pkgProvider.createMath4024Package() }.getOrNull()
        val physics = runCatching { pkgProvider.createPhysics5054Package() }.getOrNull()
        val content = runCatching { LearningContentProvider(context).load() }.getOrNull()
        val stats = OverlayStatsProvider.load(context)
        val concepts = ((math?.nodes.orEmpty()) + (physics?.nodes.orEmpty()))
            .filter { it.type == NodeType.CONCEPT || it.type == NodeType.TOPIC }
            .distinctBy { it.id }
            .sortedBy { it.name }
        return Knowledge(
            concepts = concepts,
            questions = ((math?.questions.orEmpty()) + (physics?.questions.orEmpty()))
                .sortedBy { it.id },
            flashcards = content?.flashcards.orEmpty(),
            quizzes = content?.quizzes.orEmpty(),
            videos = content?.videos.orEmpty(),
            streakDays = stats.gamification.streakDays,
            accuracy = stats.accuracyPercent,
        )
    }

    // ------------------------------------------------------------------
    // Conversational state (in-memory, per agent)
    // ------------------------------------------------------------------

    private data class QuizSession(
        val conceptId: String,
        val conceptName: String,
        val questionIds: List<String>,
        var index: Int = 0,
        var score: Int = 0,
        var asked: Int = 0,
    )

    private data class PuzzleState(
        var counter: Int = 0,
        var awaiting: String? = null,
        var attempts: Int = 0,
        var solved: Int = 0,
        var asked: Int = 0,
    )

    private val sessions = ConcurrentHashMap<String, QuizSession>()
    private val puzzleStates = ConcurrentHashMap<String, PuzzleState>()

    // ------------------------------------------------------------------
    // Entry point: cumulative-paragraph streaming, like the cloud agents
    // ------------------------------------------------------------------

    /**
     * Streams the reply as cumulative snapshots (each emission is the full
     * text so far), mirroring the cloud streaming UX in the chat UI.
     */
    fun respond(agentId: String, message: String, verification: VerificationResult? = null): Flow<String> = flow {
        val k = ensureLoaded()
        val full = runCatching { compose(agentId.trim().lowercase(), message, verification, k) }
            .getOrElse { "Hmm, my offline brain hiccuped on that one. Try asking me to explain a concept or quiz you." }
        val parts = full.split("\n\n").filter { it.isNotBlank() }
        val acc = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (i > 0) acc.append("\n\n")
            acc.append(part)
            emit(acc.toString())
            if (i < parts.size - 1) delay(STREAM_DELAY_MS)
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // Personas
    // ------------------------------------------------------------------

    private data class Persona(val name: String, val role: String, val greeting: String)

    private val personas = mapOf(
        "danish" to Persona(
            "Danish", "Maths mentor",
            "I'll check your working step by step, explain any concept from your syllabus, or drill you with practice questions.",
        ),
        "zoya" to Persona(
            "Zoya", "Physics mentor",
            "I'll explain physics ideas in plain words, drill you with questions, and share practical-paper tips.",
        ),
        "ayesha" to Persona(
            "Ayesha", "English mentor",
            "I'll coach your directed writing, summary skills, and vocabulary — all matched to the O-Level paper format.",
        ),
        "bilal" to Persona(
            "Bilal", "Puzzle coach",
            "I'll sharpen your thinking with number puzzles and quick quizzes. Say **\"give me a puzzle\"** to start.",
        ),
        "exam_coach" to Persona(
            "Exam Coach", "exam strategist",
            "I'll coach command words, time plans, and marking-scheme habits that convert knowledge into marks.",
        ),
        "career_guide" to Persona(
            "Career Guide", "study & careers adviser",
            "I'll help you pick subjects, compare career paths, and build study habits that compound over time.",
        ),
    )

    private val defaultPersona = Persona("Tutor", "study tutor", "I'll explain concepts, quiz you, and share study tips.")

    private fun personaFor(agentId: String): Persona = personas[agentId] ?: defaultPersona

    // ------------------------------------------------------------------
    // Intent routing
    // ------------------------------------------------------------------

    private fun compose(agentId: String, raw: String, verification: VerificationResult?, k: Knowledge): String {
        val persona = personaFor(agentId)
        val msg = raw.trim()
        val lower = msg.lowercase()

        if (isStop(lower)) {
            val hadQuiz = sessions.remove(agentId) != null
            val hadPuzzle = puzzleStates.remove(agentId) != null
            return if (hadQuiz || hadPuzzle) {
                "Stopped — no pressure. Say **\"quiz me\"** or **\"give me a puzzle\"** whenever you want to restart."
            } else {
                "Nothing running right now. Say **\"quiz me on ${quizExample(k)}\"** or **\"give me a puzzle\"** to start."
            }
        }

        // Active quiz: grade the answer (or side-quest a mid-quiz question).
        sessions[agentId]?.let { session ->
            return gradeQuizAnswer(agentId, session, msg, lower, persona, k)
        }

        // Non-Danish agents receiving a verification payload: route to Danish's explainer voice.
        if (verification != null) return verificationReply(verification, k)

        // Active puzzle: check the answer.
        puzzleStates[agentId]?.let { state ->
            if (state.awaiting != null) return gradePuzzleAnswer(agentId, state, msg, k)
        }

        if (isQuizRequest(lower)) return startQuiz(agentId, lower, persona, k)
        if (isPuzzleRequest(lower)) return startPuzzle(agentId, lower, k)
        if (isNext(lower)) return nextStep(agentId, lower, k)
        if (isGreeting(lower)) return greeting(persona, k)
        if (isHelp(lower)) return help(persona, k)

        playbookReply(agentId, lower, k)?.let { return it }

        val concept = findConcept(lower, k, strong = true)
        if (concept != null) return conceptCard(concept, k)

        // Weak concept signal inside an explain-style question: still answer.
        if (isExplainRequest(lower)) {
            val weak = findConcept(lower, k, strong = false)
            if (weak != null) return conceptCard(weak, k)
        }

        suggestConcept(lower, k)?.let { suggestion ->
            return "I don't have notes on that exact topic offline, but this looks close:\n\n" +
                conceptCard(suggestion, k)
        }

        return fallback(persona, k)
    }

    // ------------------------------------------------------------------
    // Intent matchers
    // ------------------------------------------------------------------

    private fun isStop(lower: String): Boolean =
        lower == "stop" || lower == "quit" || lower == "cancel" || lower == "enough" ||
            lower.contains("stop the quiz") || lower.contains("end quiz") || lower.contains("quit quiz")

    private fun isQuizRequest(lower: String): Boolean =
        lower.contains("quiz") || lower.contains("test me") || lower.contains("practice") ||
            lower.contains("drill") || lower.contains("revise") || lower.contains("mcq") ||
            lower.contains("questions for") || lower.contains("question on") || lower.contains("examine me")

    private fun isPuzzleRequest(lower: String): Boolean =
        lower.contains("puzzle") || lower.contains("sequence") || lower.contains("riddle") ||
            lower.contains("brain teaser") || lower.contains("next number") || lower.contains("next in") ||
            lower.contains("pattern") || lower.contains("teaser")

    private fun isNext(lower: String): Boolean =
        lower == "next" || lower == "another" || lower == "more" || lower == "next one" ||
            lower == "next question" || lower == "next puzzle" || lower == "another one" ||
            lower == "give me another" || lower == "one more"

    private val greetingRegex = Regex("^(hi|hello|hey|salam|salaam|aoa|assalamualaikum|yo|sup|good morning|good afternoon|good evening)\\b[!. ]*$")

    private fun isGreeting(lower: String): Boolean =
        lower.length <= 30 && (greetingRegex.matches(lower) || greetingRegex.matches(lower.replace(Regex("\\s+(danish|zoya|ayesha|bilal|coach|tutor)\\s*$"), "")))

    private fun isHelp(lower: String): Boolean =
        lower.contains("what can you do") || lower == "help" || lower.contains("help me") ||
            lower.contains("commands") || lower.contains("options") || lower.contains("how does this work") ||
            lower.contains("how do you work")

    private fun isExplainRequest(lower: String): Boolean =
        lower.contains("explain") || lower.contains("what is") || lower.contains("what are") ||
            lower.contains("what is") || lower.contains("why") || lower.contains("how does") ||
            lower.contains("tell me about") || lower.contains("notes on") || lower.contains("define") ||
            lower.contains("meaning of") || lower.contains("teach me")

    // ------------------------------------------------------------------
    // Greeting / help / fallback
    // ------------------------------------------------------------------

    private fun greeting(persona: Persona, k: Knowledge): String {
        val streak = if (k.streakDays > 1) "\n\n🔥 You're on a **${k.streakDays}-day streak** — let's protect it." else ""
        return "Hey, I'm **${persona.name}**, your ${persona.role} — running fully offline right now, so everything I say comes straight from your course packs.$streak\n\n" +
            "${persona.greeting}\n\nTry: **\"quiz me on ${quizExample(k)}\"** or **\"explain ${quizExample(k)}\"**."
    }

    private fun help(persona: Persona, k: Knowledge): String {
        val example = quizExample(k)
        return "Here's what I can do offline, ${persona.name} style:\n\n" +
            capabilities(persona).joinToString("\n") { "- $it" } +
            "\n\nTry: **\"quiz me on $example\"**, **\"explain $example\"**" +
            (if (persona.name == "Bilal") ", or **\"give me a puzzle\"**" else "") + "."
    }

    private fun capabilities(persona: Persona): List<String> = when (persona.name) {
        "Danish" -> listOf(
            "Check your step-by-step working when you send it from the Symbol Lab",
            "Explain any concept from your Maths and Physics packs",
            "Quiz you with multiple-choice and structured questions, with marking points",
        )
        "Zoya" -> listOf(
            "Explain physics ideas in plain words with the key facts",
            "Quiz you on definitions, units, and understanding",
            "Share practical-paper technique (readings, graphs, conclusions)",
        )
        "Ayesha" -> listOf(
            "Coach directed writing with a proven paragraph framework",
            "Teach the 7-mark summary method the mark scheme rewards",
            "Drill vocabulary in small rotating sets",
        )
        "Bilal" -> listOf(
            "Serve endless number-sequence puzzles with hints",
            "Quiz you rapid-fire from your course packs",
            "Explain the pattern behind every answer",
        )
        "Exam Coach" -> listOf(
            "Decode command words (state, explain, calculate, evaluate)",
            "Build paper-wise time plans from the real syllabus weightings",
            "Flag the classic pitfalls that cost O-Level candidates marks",
        )
        else -> listOf(
            "Explain any concept from your course packs",
            "Quiz you with instant marking and feedback",
            "Share study techniques matched to your goal",
        )
    }

    private fun fallback(persona: Persona, k: Knowledge): String {
        val example = quizExample(k)
        return "I want to stay accurate, so offline I stick to what I know cold:\n\n" +
            capabilities(persona).joinToString("\n") { "- $it" } +
            "\n\nTry **\"explain $example\"** or **\"quiz me on $example\"**." +
            "\n\nFor open-ended chat, connect any free OpenRouter model in the **Settings tab → AI Engine** — I'll use it automatically once it's set up."
    }

    // ------------------------------------------------------------------
    // Concept search + explainer cards
    // ------------------------------------------------------------------

    private val stopwords = setOf(
        "the", "and", "for", "with", "from", "grade", "grades", "level", "levels",
        "olevel", "o-level", "gce", "chapter", "unit", "topic", "part", "of", "to",
        "in", "on", "a", "an", "syllabus", " GC ", "4024", "5054",
    )

    private fun conceptKeywords(node: KGNode): List<String> {
        val nameTokens = node.name.lowercase().split(Regex("[^a-z0-9]+"))
        val idTokens = node.id.substringAfterLast(":").lowercase().split(Regex("[^a-z0-9]+"))
        return (nameTokens + idTokens)
            .map { it.trim() }
            .filter { it.length > 2 && it !in stopwords && it.any(Char::isLetter) }
            .distinct()
    }

    /**
     * Strong match: two keyword hits, one long distinctive hit, or the full
     * name/id appearing verbatim. Weak match: a single keyword hit.
     */
    private fun findConcept(lower: String, k: Knowledge, strong: Boolean): KGNode? {
        if (k.concepts.isEmpty()) return null
        var best: KGNode? = null
        var bestHits = 0
        var bestLong = 0
        for (node in k.concepts) {
            if (lower.contains(node.name.lowercase()) || lower.contains(node.id.lowercase())) return node
            val hits = conceptKeywords(node).filter { lower.contains(it) }
            val long = hits.count { it.length >= 6 }
            if (hits.size > bestHits || (hits.size == bestHits && long > bestLong)) {
                best = node
                bestHits = hits.size
                bestLong = long
            }
        }
        if (best == null || bestHits == 0) return null
        return if (strong) {
            if (bestHits >= 2 || bestLong >= 1) best else null
        } else {
            best
        }
    }

    private fun suggestConcept(lower: String, k: Knowledge): KGNode? =
        findConcept(lower, k, strong = false)

    private fun shortName(node: KGNode): String =
        node.name.substringBefore(" (").trim().ifBlank { node.name }

    private fun conceptCard(node: KGNode, k: Knowledge): String {
        val short = shortName(node)
        val facts = node.properties.entries
            .filter { it.value.isNotBlank() && it.key.lowercase() != "definition" }
            .take(4)
        val definition = node.properties.entries
            .firstOrNull { it.key.lowercase() == "definition" && it.value.isNotBlank() }?.value
        val cards = k.flashcards.filter { it.conceptId == node.id }.take(3)
        val practice = k.questions.count { it.targetConceptId == node.id } +
            k.quizzes.count { it.conceptId == node.id }
        val videos = k.videos.filter { it.conceptId == node.id }.take(2)

        val sb = StringBuilder()
        sb.append("**$short**\n\n")
        if (!definition.isNullOrBlank()) sb.append("$definition\n\n")
        if (facts.isNotEmpty()) {
            sb.append("**Key facts**\n")
            facts.forEach { (key, value) -> sb.append("- **${key.trim().replaceFirstChar(Char::uppercase)}**: $value\n") }
            sb.append("\n")
        }
        if (cards.isNotEmpty()) {
            sb.append("**Remember**\n")
            cards.forEach { sb.append("- ${it.front} → **${it.back}**\n") }
            sb.append("\n")
        }
        if (practice > 0) {
            sb.append("**Practice**: $practice offline question${if (practice == 1) "" else "s"} ready — say **\"quiz me on $short\"**.\n\n")
        }
        if (videos.isNotEmpty()) {
            sb.append("**Watch** (needs internet):\n")
            videos.forEach {
                sb.append(if (it.durationLabel.isNotBlank()) "- ${it.title} (${it.durationLabel})\n" else "- ${it.title}\n")
            }
        }
        return sb.toString().trim()
    }

    // ------------------------------------------------------------------
    // Verification explanations (Danish's "why is my step wrong?")
    // ------------------------------------------------------------------

    private fun verificationReply(v: VerificationResult, k: Knowledge): String {
        val example = quizExample(k)
        return if (v.isValid) {
            "**${UiCopy.formatMathVerification(true, null, null)}** ✅\n\nWant to lock it in? Say **\"quiz me on $example\"**."
        } else {
            val step = (v.firstInvalidStepIndex ?: 1).coerceAtLeast(1)
            "**${UiCopy.formatMathVerification(false, step, v.errorType)}**\n\n" +
                "${UiCopy.formatMathErrorExplanation(v.errorType)}\n\n" +
                "Fix it by rewriting step $step from step ${(step - 1).coerceAtLeast(1)}, changing one thing at a time — then send your steps again and I'll re-check."
        }
    }

    // ------------------------------------------------------------------
    // Quiz sessions
    // ------------------------------------------------------------------

    private val quizIdPkg = "pkg:"
    private val quizIdQuiz = "quiz:"

    private fun isMcq(q: QuestionItem): Boolean =
        q.options.size >= 2 && q.correctIndex in q.options.indices

    private fun startQuiz(agentId: String, lower: String, persona: Persona, k: Knowledge): String {
        puzzleStates.remove(agentId)
        if (k.questions.isEmpty() && k.quizzes.isEmpty()) {
            return "My offline pack has no practice questions on this device, so I can't start a quiz. " +
                "I can still explain concepts — try **\"explain ${quizExample(k)}\"**."
        }
        val target = findConcept(lower, k, strong = true)
            ?: k.concepts.maxByOrNull { c ->
                k.questions.count { it.targetConceptId == c.id } + k.quizzes.count { it.conceptId == c.id }
            } ?: return "My offline pack has no practice questions on this device."
        val short = shortName(target)
        val pkgQs = k.questions.filter { it.targetConceptId == target.id }.take(3)
        val lqs = k.quizzes.filter { it.conceptId == target.id }.take(2)
        val gradeable = (pkgQs.filter { isMcq(it) }.map { quizIdPkg + it.id } +
            lqs.map { quizIdQuiz + it.id }).take(3)
        val reveal = pkgQs.filter { !isMcq(it) }.take(2).map { quizIdPkg + it.id }
        val queue = (gradeable + reveal).take(5)
        if (queue.isEmpty()) {
            return "I don't have offline questions for **$short** yet. Try **\"quiz me on ${quizExample(k)}\"** — or ask me to **explain $short** first."
        }
        val session = QuizSession(target.id, short, queue)
        sessions[agentId] = session
        val header = if (target.let { findConcept(lower, k, strong = true) } == null) {
            "Starting with **$short** (say **\"quiz me on …\"** to switch topic).\n\n"
        } else {
            "Quiz on **$short** — ${queue.size} question${if (queue.size == 1) "" else "s"}. ${persona.name} is marking. ✏️\n\n"
        }
        return header + renderQuestion(session, k)
    }

    private fun renderQuestion(s: QuizSession, k: Knowledge): String {
        val qid = s.questionIds[s.index]
        val number = s.asked + 1
        return when {
            qid.startsWith(quizIdPkg) -> {
                val q = k.questions.firstOrNull { it.id == qid.removePrefix(quizIdPkg) }
                    ?: return advanceOrFinish(s, null, k)
                if (isMcq(q)) {
                    val opts = q.options.mapIndexed { i, o -> "${'A' + i}) $o" }.joinToString("\n")
                    "**Question $number** — ${s.conceptName}\n\n${q.prompt}\n\n$opts\n\n(Reply with the letter, or **stop** to finish.)"
                } else {
                    s.asked++
                    "**Question $number** — ${s.conceptName}\n\n${q.prompt}\n\n(Type your answer — I'll show the marking points, then move on. **stop** finishes early.)"
                }
            }
            qid.startsWith(quizIdQuiz) -> {
                val q = k.quizzes.firstOrNull { it.id == qid.removePrefix(quizIdQuiz) }
                    ?: return advanceOrFinish(s, null, k)
                val opts = q.options.mapIndexed { i, o -> "${'A' + i}) $o" }.joinToString("\n")
                "**Question $number** — ${s.conceptName}\n\n${q.question}\n\n$opts\n\n(Reply with the letter, or **stop** to finish.)"
            }
            else -> advanceOrFinish(s, null, k)
        }
    }

    private val letterAnswer = Regex("\\b([a-dA-D])\\b")
    private val digitAnswer = Regex("^\\s*([1-4])\\s*$")
    private val optionWord = Regex("(?i)\\boption\\s+([a-d])\\b")

    private fun gradeQuizAnswer(agentId: String, s: QuizSession, msg: String, lower: String, persona: Persona, k: Knowledge): String {
        val qid = s.questionIds.getOrNull(s.index) ?: run {
            sessions.remove(agentId)
            return "That quiz already finished. Say **\"quiz me\"** to start a new one."
        }
        val isRevealType = qid.startsWith(quizIdPkg) &&
            k.questions.firstOrNull { it.id == qid.removePrefix(quizIdPkg) }?.let { !isMcq(it) } == true

        // Mid-quiz side question ("why …?") — answer it, keep the quiz paused.
        if (!isRevealType && looksLikeQuestion(msg, lower) && extractLetter(msg) == null) {
            val side = playbookReply(agentId, lower, k)
                ?: findConcept(lower, k, strong = false)?.let { conceptCard(it, k) }
                ?: "Good question — offline I answer best about syllabus concepts. Try **\"explain ${quizExample(k)}\"**."
            return "$side\n\n_(Quiz paused — reply to question ${s.asked + 1} to continue, or **stop** to end.)_"
        }

        if (isRevealType) {
            val q = k.questions.first { it.id == qid.removePrefix(quizIdPkg) }
            val points = q.rubrics.ifBlank {
                "My offline pack has no marking points for this one — compare your answer against this structure:\n" +
                    "- State the rule or formula you used\n- Show each substitution step\n- Box the final answer (with units, if any)"
            }
            s.index++
            val reveal = "**Marking points**\n\n$points\n\n"
            return reveal + advanceOrFinish(s, agentId, k)
        }

        // Multiple-choice grading.
        val (correctIdx, options, explanation) = when {
            qid.startsWith(quizIdPkg) -> {
                val q = k.questions.first { it.id == qid.removePrefix(quizIdPkg) }
                Triple(q.correctIndex, q.options, q.rubrics)
            }
            else -> {
                val q = k.quizzes.first { it.id == qid.removePrefix(quizIdQuiz) }
                Triple(q.correctIndex, q.options, q.explanation)
            }
        }
        val picked = extractLetter(msg) ?: matchOptionText(lower, options)
        if (picked == null) {
            return "Reply with a letter (**A**–**${'A' + options.size - 1}**), or say **stop** to finish the quiz."
        }
        s.asked++
        s.index++
        val correct = picked == correctIdx
        if (correct) s.score++
        val verdict = if (correct) {
            UiCopy.formatQuizFeedback(true)
        } else {
            val right = "${'A' + correctIdx}) ${options.getOrElse(correctIdx) { "" }}".trim()
            "${UiCopy.formatQuizFeedback(false)} The correct answer is **$right**."
        }
        val explainer = explanation.ifBlank {
            if (correct) "Well done — that matches the mark scheme." else "Re-read the question stem and try the next one fresh."
        }
        return "$verdict\n\n$explainer\n\n" + advanceOrFinish(s, agentId, k)
    }

    private fun extractLetter(msg: String): Int? {
        optionWord.find(msg)?.let { return it.groupValues[1].uppercase()[0] - 'A' }
        val trimmed = msg.trim()
        if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'D') return trimmed[0].uppercaseChar() - 'A'
        digitAnswer.matchEntire(trimmed)?.let { return it.groupValues[1].toInt() - 1 }
        if (trimmed.length <= 12) letterAnswer.find(trimmed)?.let { return it.groupValues[1].uppercase()[0] - 'A' }
        return null
    }

    private fun matchOptionText(lower: String, options: List<String>): Int? {
        options.forEachIndexed { i, opt ->
            val norm = opt.lowercase().trim()
            if (norm.length > 4 && (lower.contains(norm) || norm.contains(lower.trim()))) return i
        }
        return null
    }

    private fun looksLikeQuestion(msg: String, lower: String): Boolean =
        msg.trim().endsWith("?") || lower.startsWith("what") || lower.startsWith("why") ||
            lower.startsWith("how") || lower.startsWith("explain") || lower.startsWith("tell me")

    private fun advanceOrFinish(s: QuizSession, agentId: String?, k: Knowledge): String {
        if (s.index >= s.questionIds.size) {
            if (agentId != null) sessions.remove(agentId)
            val pct = if (s.asked == 0) 0 else (s.score * 100 / s.asked)
            val band = when {
                s.asked == 0 -> "No answers recorded — the warm-up doesn't count. 😄"
                pct == 100 -> "Perfect score — ${s.conceptName} is yours. 🏆"
                pct >= 70 -> "Strong work — a couple of marks from perfect."
                pct >= 40 -> "Good progress — re-read the ones you missed and go again."
                else -> "Tough round — that's exactly what practice is for. Review the concept card, then retry."
            }
            val other = k.concepts.firstOrNull { it.id != s.conceptId }?.let { shortName(it) }
            return "**Quiz complete: ${s.score}/${s.asked} ($pct%).** $band" +
                (if (other != null) "\n\nSay **\"quiz me on $other\"** to keep going." else "")
        }
        return renderQuestion(s, k)
    }

    private fun quizExample(k: Knowledge): String =
        k.concepts.firstOrNull()?.let { shortName(it) } ?: "factorisation"

    // ------------------------------------------------------------------
    // Number puzzles (Bilal's game, open to every agent)
    // ------------------------------------------------------------------

    private data class Puzzle(val prompt: String, val answer: String, val hint: String)

    private fun makePuzzle(counter: Int): Puzzle = when (counter % 6) {
        0 -> {
            val a = 2 + (counter % 5)
            val d = 3 + (counter % 4)
            Puzzle(
                "What comes next? **$a, ${a + d}, ${a + 2 * d}, ${a + 3 * d}, ?**",
                (a + 4 * d).toString(),
                "Add $d each time.",
            )
        }
        1 -> {
            val a = 2 + (counter % 3)
            Puzzle(
                "What comes next? **$a, ${2 * a}, ${4 * a}, ${8 * a}, ?**",
                (16 * a).toString(),
                "Multiply by 2 each time.",
            )
        }
        2 -> {
            val n = 3 + (counter % 4)
            Puzzle(
                "What comes next? **${n * n}, ${(n + 1) * (n + 1)}, ${(n + 2) * (n + 2)}, ${(n + 3) * (n + 3)}, ?**",
                ((n + 4) * (n + 4)).toString(),
                "These are square numbers: ${(n)}², ${(n + 1)}², …",
            )
        }
        3 -> {
            val a = 1 + (counter % 3)
            val b = a + 1
            Puzzle(
                "What comes next? **$a, $b, ${a + b}, ${a + 2 * b}, ${2 * a + 3 * b}, ?**",
                (3 * a + 5 * b).toString(),
                "Add the previous two terms.",
            )
        }
        4 -> {
            val primes = listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29)
            val start = counter % 3
            val shown = primes.subList(start, start + 4)
            Puzzle(
                "What comes next? **${shown.joinToString(", ")}, ?**",
                primes[start + 4].toString(),
                "Prime numbers in order.",
            )
        }
        else -> {
            val a = 5 + (counter % 5)
            Puzzle(
                "What comes next? **$a, ${a + 4}, ${a + 3}, ${a + 7}, ${a + 6}, ?**",
                (a + 10).toString(),
                "The pattern alternates +4, −1.",
            )
        }
    }

    private fun startPuzzle(agentId: String, lower: String, k: Knowledge): String {
        sessions.remove(agentId)
        val state = puzzleStates.getOrPut(agentId) { PuzzleState() }
        if (state.awaiting == null && (lower.contains("next") || lower.contains("another") || lower.contains("more"))) {
            state.counter++
        }
        val puzzle = makePuzzle(state.counter)
        state.awaiting = puzzle.answer
        state.attempts = 0
        state.asked++
        return "🧩 ${puzzle.prompt}\n\n(Reply with the number — I'll give you a hint if you get stuck.)"
    }

    private fun nextStep(agentId: String, lower: String, k: Knowledge): String {
        val puzzle = puzzleStates[agentId]
        if (puzzle != null && (puzzle.awaiting != null || lower.contains("puzzle"))) {
            if (puzzle.awaiting != null) {
                return "One puzzle at a time! Answer the current one first — or say **stop** to skip it."
            }
            puzzle.counter++
            return startPuzzle(agentId, "", k)
        }
        if (sessions[agentId] != null) {
            return "You're mid-quiz — answer the current question or say **stop** to end it, then I'll serve the next round."
        }
        return startPuzzle(agentId, "", k)
    }

    private val numberInText = Regex("-?\\d+")

    private fun gradePuzzleAnswer(agentId: String, state: PuzzleState, msg: String, k: Knowledge): String {
        val expected = state.awaiting ?: return startPuzzle(agentId, "", k)
        val found = numberInText.find(msg)?.value
        if (found == null) {
            return "I need a number for this one — take a guess! (Or say **stop** to skip it.)"
        }
        if (found == expected || found.toIntOrNull() == expected.toIntOrNull()) {
            state.awaiting = null
            state.counter++
            state.solved++
            val puzzle = makePuzzle(state.counter)
            state.awaiting = puzzle.answer
            state.attempts = 0
            state.asked++
            return "✅ **Correct!** ${describePattern(state.counter - 1)}\n\nSolved ${state.solved}/${state.asked - 1} so far. Next one:\n\n🧩 ${puzzle.prompt}"
        }
        state.attempts++
        return if (state.attempts == 1) {
            val puzzle = makePuzzle(state.counter)
            "Not quite — here's a hint: **${puzzle.hint}** Try again! (Or say **stop** to skip.)"
        } else {
            val pattern = describePattern(state.counter)
            state.awaiting = null
            state.counter++
            "The answer was **$expected**. $pattern\n\nSay **next** and I'll serve another one."
        }
    }

    private fun describePattern(counter: Int): String = when (counter % 6) {
        0 -> "It's an arithmetic sequence — same step every time."
        1 -> "It's a geometric sequence — multiply by the same number each time."
        2 -> "Those were consecutive square numbers."
        3 -> "Each term is the sum of the previous two — Fibonacci style."
        4 -> "Those were consecutive prime numbers."
        else -> "The pattern alternated +4, −1."
    }

    // ------------------------------------------------------------------
    // Per-agent playbooks (scripted expertise, always available offline)
    // ------------------------------------------------------------------

    private fun playbookReply(agentId: String, lower: String, k: Knowledge): String? = when (agentId) {
        "exam_coach" -> examPlaybook(lower)
        "ayesha" -> ayeshaPlaybook(lower, agentId)
        "career_guide" -> careerPlaybook(lower)
        "zoya" -> zoyaPlaybook(lower)
        "danish" -> danishPlaybook(lower)
        "bilal" -> bilalPlaybook(lower)
        else -> genericPlaybook(lower)
    }

    private fun examPlaybook(lower: String): String? {
        if (lower.contains("command") || lower.contains("state ") || lower.contains("describe") || lower.contains("evaluate")) {
            return "**Command words decoded** 🎯\n\n" +
                "- **State / Give / Name** (1 mark): one crisp fact. No explanation needed.\n" +
                "- **Describe** (2 marks): say WHAT happens, step by step. No reasons.\n" +
                "- **Explain** (2–3 marks): WHAT happens + WHY, using scientific vocabulary.\n" +
                "- **Calculate**: show the formula, substitution, and final answer with units — method marks live here.\n" +
                "- **Evaluate / Discuss**: both sides + a justified conclusion.\n\n" +
                "Match your effort to the marks: roughly **1 mark ≈ 1 minute ≈ 1 point**."
        }
        if (lower.contains("time") || lower.contains("plan") || lower.contains("manage") || lower.contains("slow") || lower.contains("finish")) {
            return "**Paper time plan** ⏱️\n\n" +
                "- Spend ~**1 minute per mark**. A 10-mark question gets 10 minutes, then you move on.\n" +
                "- Do a first pass: answer everything you can, **star** anything sticky.\n" +
                "- Second pass: return to starred questions with fresh eyes.\n" +
                "- Last 10 minutes: units, significant figures, and the questions you skipped — never leave blanks.\n\n" +
                "If a question eats double its time budget, it's stealing marks from easier ones. Move."
        }
        if (lower.contains("mark") || lower.contains("scheme") || lower.contains("score") || lower.contains("grade") || lower.contains("pass")) {
            return "**How marking schemes think** 📝\n\n" +
                "- Schemes award **points**, not essays. Each bullet in the scheme is usually one mark.\n" +
                "- In **calculate** questions, an error carried forward still earns method marks — always show working.\n" +
                "- Contradicting yourself cancels the mark: if unsure, commit to one answer.\n" +
                "- Key terms matter: *directly proportional*, *resultant force*, *factorise fully* — learn the exact phrases.\n\n" +
                "Say **\"quiz me\"** and I'll drill you the way the paper will."
        }
        if (lower.contains("practical") || lower.contains("paper 3") || lower.contains("paper3") || lower.contains("alternative to practical")) {
            return "**Practical paper habits** 🔬\n\n" +
                "- Read every value to the **precision of the instrument** (ruler → mm, stopwatch → 0.01s).\n" +
                "- Repeat readings and **average** — never average an anomalous result; circle it instead.\n" +
                "- Graph checklist: labelled axes with units, sensible scale, best-fit **line or curve** (never dot-to-dot), large triangle for gradients.\n" +
                "- Conclusion = **pattern + data**: \"As X increased, Y decreased\" plus one quoted pair.\n\n" +
                "Half the practical marks are technique, not knowledge — rehearse the routine."
        }
        if (lower.contains("tip") || lower.contains("advice") || lower.contains("strategy") || lower.contains("prepare") || lower.contains("revise") || lower.contains("study")) {
            return "**High-leverage exam tips** 📈\n\n" +
                "- **Past papers under timed conditions** beat re-reading notes, every time.\n" +
                "- Keep an **error log**: every lost mark, one line on WHY. Review it weekly.\n" +
                "- Learn **definitions verbatim** — they're free marks if word-perfect.\n" +
                "- Sleep beats cramming: memory consolidates overnight.\n\n" +
                "Want a drill? Say **\"quiz me\"** and pick any topic."
        }
        if (lower.contains("pitfall") || lower.contains("mistake") || lower.contains("common error") || lower.contains("lose marks")) {
            return "**Classic mark-losers** ⚠️\n\n" +
                "- Missing or wrong **units** on final answers.\n" +
                "- Rounding too early — keep full precision until the last step.\n" +
                "- Describing when asked to **explain** (no reasons = half marks).\n" +
                "- Unlabelled graphs and dot-to-dot \"best fit\" lines.\n" +
                "- Leaving blanks — a sensible guess can still earn method marks."
        }
        return null
    }

    private val vocabSets = listOf(
        listOf(" meticulous " to "showing great care for detail", " pragmatic " to "practical rather than idealistic", " vivid " to "bright, clear, and detailed", " reluctant " to "unwilling; hesitant", " essential " to "absolutely necessary", " concise " to "brief but complete"),
        listOf(" bewildered " to "confused; puzzled", " frugal " to "careful with money; thrifty", " ample " to "plenty; more than enough", " diligent " to "hardworking and careful", " obscure " to "unclear; little-known", " tranquil " to "calm and peaceful"),
        listOf(" inevitable " to "certain to happen", " versatile " to "able to do many things well", " candid " to "honest and direct", " hasty " to "rushed; too quick", " subtle " to "slight; hard to notice", " robust " to "strong and healthy"),
    )

    private fun ayeshaPlaybook(lower: String, agentId: String): String? {
        if (lower.contains("summary") || lower.contains("summar")) {
            return "**The 7-mark summary method** ✍️\n\n" +
                "- Read the question FIRST so you know which points count.\n" +
                "- Lift **7 distinct content points** — one mark each. Number them as you draft.\n" +
                "- Write in **your own words**, in continuous prose (no bullets), within the word limit.\n" +
                "- Own-words + organisation + correctness carry the remaining style marks — copy-pasting caps your score.\n\n" +
                "Drill: summarise any paragraph into exactly 3 sentences, then check you kept every key fact."
        }
        if (lower.contains("essay") || lower.contains("directed") || lower.contains("letter") || lower.contains("report") || lower.contains("article") || lower.contains("speech") || lower.contains("writing")) {
            return "**Directed writing framework** 📄\n\n" +
                "- **Paragraph 1**: address the task directly — purpose + audience + format (letter, report, speech…).\n" +
                "- **Middle paragraphs**: one bullet from the task per paragraph, each developed with a detail or example.\n" +
                "- **Final paragraph**: round off with a recommendation, warning, or call to action.\n" +
                "- Register matters: a formal letter never sounds like a text message.\n\n" +
                "Plan 5 minutes, write 25. Examiners reward **task fulfilment + varied sentences + accurate spelling**."
        }
        if (lower.contains("vocab") || lower.contains("words") || lower.contains("synonym") || lower.contains("meaning")) {
            val idx = (puzzleStates.getOrPut(agentId) { PuzzleState() }.counter % vocabSets.size)
                .also { puzzleStates[agentId]!!.counter++ }
            val set = vocabSets[idx]
            return "**Vocabulary set ${idx + 1}** 📚\n\n" +
                set.joinToString("\n") { (w, m) -> "- **${w.trim()}** — $m" } +
                "\n\nUse each word in one sentence today — that's what moves words into long-term memory. Say **\"more words\"** for the next set."
        }
        if (lower.contains("grammar") || lower.contains("tense") || lower.contains("punctuation") || lower.contains("sentence")) {
            return "**Grammar that earns marks** ✅\n\n" +
                "- **Tense consistency**: pick past or present and stay there unless the meaning shifts.\n" +
                "- **Sentence variety**: mix short punchy sentences with longer complex ones.\n" +
                "- **Punctuation**: commas for clauses, apostrophes only for possession/contraction — never plurals.\n" +
                "- Read your work backwards, sentence by sentence: errors jump out when the flow can't hide them."
        }
        if (lower.contains("comprehension") || lower.contains("passage") || lower.contains("inference") || lower.contains("infer")) {
            return "**Comprehension technique** 🔍\n\n" +
                "- Answer in **your own words** unless told to quote — lifting the passage caps your marks.\n" +
                "- For inference questions: **evidence + what it suggests**. Quote briefly, then explain the implication.\n" +
                "- Match the mark count: 2 marks = 2 distinct points.\n" +
                "- Vocabulary-in-context: substitute your guess back into the sentence — if it fits, it scores."
        }
        return null
    }

    private fun careerPlaybook(lower: String): String? {
        if (lower.contains("subject") || lower.contains("choose") || lower.contains("o-level") || lower.contains("olevel") || lower.contains("combination") || lower.contains("pick")) {
            return "**Choosing O-Level subjects** 🧭\n\n" +
                "- Keep **Maths + English + Science** — they keep every door open.\n" +
                "- Pick at least one subject you genuinely enjoy; motivation is a grade multiplier.\n" +
                "- Check prerequisites: medicine wants Biology + Chemistry, engineering wants Physics + Add Maths.\n" +
                "- Breadth beats stacking similar subjects — pair sciences with a humanity or commerce subject.\n\n" +
                "Tell me a career you're curious about and I'll map the subjects to it."
        }
        if (lower.contains("doctor") || lower.contains("medic") || lower.contains("engineer") || lower.contains("computer") || lower.contains("business") || lower.contains("law") || lower.contains("career") || lower.contains("job") || lower.contains("future") || lower.contains("become") || lower.contains("a-level") || lower.contains("alevel")) {
            return "**Career clusters to explore** 🌟\n\n" +
                "- **Medicine & Life Sciences**: Biology, Chemistry, Physics/Maths → A-Level sciences.\n" +
                "- **Engineering & Tech**: Physics, Add Maths, Computer Science → build things early (code, circuits, CAD).\n" +
                "- **Business & Finance**: Maths, Commerce/Accounts, Economics → mental maths + communication.\n" +
                "- **Law & Humanities**: English, History/Islamiat, strong writing → debate, essay practice.\n\n" +
                "Rule of thumb: **subjects open doors, skills walk through them.** Build one demonstrable skill alongside grades."
        }
        if (lower.contains("study") || lower.contains("habit") || lower.contains("routine") || lower.contains("focus") || lower.contains("concentrat") || lower.contains("motivat") || lower.contains("procrastinat")) {
            return "**Study habits that compound** 📚\n\n" +
                "- **25-minute focused blocks** with 5-minute breaks beat 3-hour slogs.\n" +
                "- **Active recall**: close the book and write what you remember — then check.\n" +
                "- **Spaced repetition**: revisit each topic after 1 day, 1 week, 1 month.\n" +
                "- Same time, same place daily — routine removes the need for motivation."
        }
        if (lower.contains("grade") || lower.contains("marks") || lower.contains("improve") || lower.contains("result") || lower.contains("score")) {
            return "**Raising your grades** 📈\n\n" +
                "- Diagnose before treating: is it **knowledge gaps**, **technique**, or **time management**?\n" +
                "- Knowledge gaps → concept cards + quizzes (ask any tutor to **quiz you**).\n" +
                "- Technique → past papers marked against the scheme, with an error log.\n" +
                "- One subject at a time, one grade boundary at a time — small wins compound."
        }
        return null
    }

    private fun zoyaPlaybook(lower: String): String? {
        if (lower.contains("practical") || lower.contains("experiment") || lower.contains("graph") || lower.contains("reading")) {
            return "**Physics practical technique** 🔬\n\n" +
                "- Zero-check every instrument before you start.\n" +
                "- Parallax kills: read scales with your eye **perpendicular** to the needle.\n" +
                "- Plot the **independent variable on the x-axis**, choose scales that fill the grid, and draw a smooth best-fit line.\n" +
                "- Gradient = large triangle (at least half the line) with substituted values shown."
        }
        if (lower.contains("unit") || lower.contains("si ") || lower.contains("convert") || lower.contains("prefix")) {
            return "**Units survival kit** 📏\n\n" +
                "- Base SI: **kg, m, s, A, K, mol**. Everything else derives from these.\n" +
                "- Learn the derivations: N = kg·m/s², J = N·m, W = J/s, Pa = N/m², C = A·s.\n" +
                "- Prefix ladder: milli (10⁻³), centi (10⁻²), kilo (10³), mega (10⁶) — convert BEFORE substituting.\n" +
                "- A final answer without units loses the mark. Always."
        }
        if (lower.contains("formula") || lower.contains("equation") || lower.contains("derive") || lower.contains("memoris") || lower.contains("remember")) {
            return "**Owning physics formulas** 🧠\n\n" +
                "- Learn each formula as a **triangle or a sentence**, not just letters: \"speed equals distance over time\".\n" +
                "- Derive, don't just memorise: if you can get there from definitions, you can rebuild it mid-exam.\n" +
                "- Every time you use one, **write it down first** — the method mark is often in the formula line."
        }
        return null
    }

    private fun danishPlaybook(lower: String): String? {
        if (lower.contains("working") || lower.contains("method") || lower.contains("steps") || lower.contains("show")) {
            return "**Working that earns method marks** ➗\n\n" +
                "- One step per line, lined up with equals signs — markers follow your logic line by line.\n" +
                "- Never do two operations in one step; that's where sign errors breed.\n" +
                "- A wrong answer with clear working still banks method marks; a right answer with none banks nothing extra.\n\n" +
                "Send your steps from the Symbol Lab and I'll check each one."
        }
        if (lower.contains("check") || lower.contains("verify") || lower.contains("correct") || lower.contains("answer")) {
            return "**Self-checking routine** ✔️\n\n" +
                "- **Substitute back**: put your answer into the original equation — both sides must agree.\n" +
                "- **Estimate first**: 19 × 21 ≈ 400. If your answer is 40, you've slipped a decimal.\n" +
                "- **Units & sense**: a person's height can't be 175 m. Sanity-check every final value."
        }
        return null
    }

    private fun bilalPlaybook(lower: String): String? {
        if (lower.contains("hint") || lower.contains("stuck") || lower.contains("don't know") || lower.contains("dont know") || lower.contains("help")) {
            return "**Puzzle-cracking toolkit** 🧩\n\n" +
                "- Write the **differences** between consecutive terms — constant difference means arithmetic.\n" +
                "- Try **ratios** next — constant ratio means geometric.\n" +
                "- Think squares, cubes, primes, and Fibonacci when differences fail.\n" +
                "- Say **\"give me a puzzle\"** and practise the routine."
        }
        if (lower.contains("logic") || lower.contains("think") || lower.contains("smart") || lower.contains("iq")) {
            return "**Training mathematical thinking** 🧠\n\n" +
                "- Do one puzzle a day — pattern recognition is a trained muscle.\n" +
                "- After solving, **name the pattern** out loud. Naming it files it for reuse.\n" +
                "- Stuck for 5 minutes? Take the hint — learning the pattern beats staring at it.\n\n" +
                "Say **\"give me a puzzle\"** to start today's rep."
        }
        return null
    }

    private fun genericPlaybook(lower: String): String? {
        if (lower.contains("tip") || lower.contains("study") || lower.contains("learn") || lower.contains("memoris") || lower.contains("remember")) {
            return "**Study smarter** 📚\n\n" +
                "- **Recall, don't re-read**: close the book and reconstruct the idea.\n" +
                "- **Space it**: revisit after 1 day, 1 week, 1 month.\n" +
                "- **Interleave**: mix topics in one session instead of blocking one chapter for hours.\n\n" +
                "Ask me to **explain** or **quiz** any topic to practise the recall habit right now."
        }
        return null
    }

    companion object {
        private const val STREAM_DELAY_MS = 90L
    }
}
