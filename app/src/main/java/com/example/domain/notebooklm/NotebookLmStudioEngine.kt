package com.example.domain.notebooklm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class K12Topic(
    val topicId: String,
    val title: String,
    val summary: String,
    val keyFormulas: List<String>,
    val sourceNotes: List<String>,
    val workedExample: String,
    val commonMisconception: String,
    val gradeName: String = "",
    val subjectName: String = "",
    val prerequisites: List<String> = emptyList(),
    val successors: List<String> = emptyList(),
    val syllabusCode: String = "",
    val learningObjectives: List<String> = emptyList()
)

data class K12Subject(
    val subjectId: String,
    val subjectName: String,
    val iconName: String,
    val topics: List<K12Topic>
)

data class K12Grade(
    val gradeId: String,
    val gradeName: String,
    val ageRange: String,
    val subjects: List<K12Subject>
)

data class K12Band(
    val bandId: String,
    val bandName: String,
    val grades: List<K12Grade>
)

data class NotebookSourceCard(
    val sourceId: String,
    val badgeLabel: String,
    val title: String,
    val content: String
)

data class PodcastDialogueTurn(
    val turnIndex: Int,
    val speaker: String, // "Alex" or "Zoya"
    val roleLabel: String,
    val text: String,
    val sourceCitation: String
)

data class NotebookLmPodcastOverview(
    val podcastId: String,
    val topicId: String,
    val title: String,
    val subtitle: String,
    val durationMinutes: Int,
    val turns: List<PodcastDialogueTurn>,
    val keyTakeaways: List<String>
)

data class VideoExplainerScene(
    val sceneNumber: Int,
    val title: String,
    val visualMode: String, // CONCEPT_MAP, FORMULA_BLUEPRINT, WORKED_STEP, TRAP_VS_TRUTH, SYNTHESIS_BADGE
    val visualHeadline: String,
    val visualFormulaOrKeyPoint: String,
    val bulletCallouts: List<String>,
    val narrationScript: String,
    val sourceCitation: String,
    val durationSeconds: Int = 15
)

data class NotebookLmVideoExplainer(
    val videoId: String,
    val topicId: String,
    val title: String,
    val subtitle: String,
    val totalDurationSeconds: Int,
    val scenes: List<VideoExplainerScene>
)

data class NotebookLmQuizItem(
    val id: String,
    val topicId: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val bloomLevel: String,
    val sourceCitation: String
)

data class StudentNotesDoc(
    val topicId: String,
    val title: String,
    val gradeSubjectHeader: String,
    val syllabusCode: String,
    val prerequisites: List<String>,
    val successors: List<String>,
    val corePrinciples: List<String>,
    val keyFormulas: List<String>,
    val workedExample: String,
    val commonMisconception: String,
    val reviewQuestions: List<NotebookLmQuizItem>,
    val printableMarkdown: String
)

data class NotebookLmTopicBundle(
    val topic: K12Topic,
    val sources: List<NotebookSourceCard>,
    val podcast: NotebookLmPodcastOverview,
    val videoExplainer: NotebookLmVideoExplainer,
    val quizItems: List<NotebookLmQuizItem>,
    val studentNotesDoc: StudentNotesDoc = NotebookLmStudioEngine.buildStudentNotesDoc(topic, quizItems)
)

object NotebookLmStudioEngine {

    private val bandFiles = listOf(
        "curriculum/k12_elementary_k_5.json",
        "curriculum/k12_middle_6_8.json",
        "curriculum/k12_high_9_10_olevel.json",
        "curriculum/k12_senior_11_12_alevel.json"
    )

    fun loadAllK12Bands(context: Context): List<K12Band> {
        return bandFiles.mapNotNull { path ->
            runCatching {
                val jsonStr = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseBandJson(jsonStr)
            }.getOrNull()
        }
    }

    fun parseBandJson(jsonStr: String): K12Band {
        val root = JSONObject(jsonStr)
        val bandId = root.getString("bandId")
        val bandName = root.getString("bandName")
        val gradesArr = root.getJSONArray("grades")
        val grades = buildList {
            for (gIdx in 0 until gradesArr.length()) {
                val gObj = gradesArr.getJSONObject(gIdx)
                val gradeId = gObj.getString("gradeId")
                val gradeName = gObj.getString("gradeName")
                val ageRange = gObj.optString("ageRange", "")
                val subjArr = gObj.getJSONArray("subjects")
                val subjects = buildList {
                    for (sIdx in 0 until subjArr.length()) {
                        val sObj = subjArr.getJSONObject(sIdx)
                        val subjectId = sObj.getString("subjectId")
                        val subjectName = sObj.getString("subjectName")
                        val iconName = sObj.optString("iconName", "School")
                        val topicsArr = sObj.getJSONArray("topics")
                        val topics = buildList {
                            for (tIdx in 0 until topicsArr.length()) {
                                val tObj = topicsArr.getJSONObject(tIdx)
                                add(
                                    K12Topic(
                                        topicId = tObj.getString("topicId"),
                                        title = tObj.getString("title"),
                                        summary = tObj.getString("summary"),
                                        keyFormulas = tObj.optJSONArray("keyFormulas").toStringList(),
                                        sourceNotes = tObj.optJSONArray("sourceNotes").toStringList(),
                                        workedExample = tObj.optString("workedExample", ""),
                                        commonMisconception = tObj.optString("commonMisconception", ""),
                                        gradeName = gradeName,
                                        subjectName = subjectName,
                                        prerequisites = tObj.optJSONArray("prerequisites").toStringList(),
                                        successors = tObj.optJSONArray("successors").toStringList(),
                                        syllabusCode = tObj.optString("syllabusCode", ""),
                                        learningObjectives = tObj.optJSONArray("learningObjectives").toStringList()
                                    )
                                )
                            }
                        }
                        add(K12Subject(subjectId, subjectName, iconName, topics))
                    }
                }
                add(K12Grade(gradeId, gradeName, ageRange, subjects))
            }
        }
        return K12Band(bandId, bandName, grades)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { idx -> optString(idx, "") }.filter { it.isNotBlank() }
    }

    /**
     * Synthesizes a complete NotebookLM Topic Bundle (Sources, 2-Host Audio Podcast Overview,
     * 5-Scene Animated Video Explainer Storyboard, Grounded Quiz, and Printable Student Notes Doc) for any K–12 topic.
     */
    fun generateBundleForTopic(
        topic: K12Topic,
        customUserNotes: List<String> = emptyList()
    ): NotebookLmTopicBundle {
        val sources = buildSources(topic, customUserNotes)
        val podcast = buildPodcastOverview(topic, customUserNotes)
        val video = buildVideoExplainer(topic, customUserNotes)
        val quiz = buildTopicQuiz(topic)
        val notesDoc = buildStudentNotesDoc(topic, quiz)
        return NotebookLmTopicBundle(
            topic = topic,
            sources = sources,
            podcast = podcast,
            videoExplainer = video,
            quizItems = quiz,
            studentNotesDoc = notesDoc
        )
    }

    fun buildStudentNotesDoc(
        topic: K12Topic,
        quizItems: List<NotebookLmQuizItem>
    ): StudentNotesDoc {
        val gradeSubj = "${topic.gradeName} • ${topic.subjectName}"
        val md = buildString {
            appendLine("# ${topic.title}")
            appendLine("### $gradeSubj • ${topic.syllabusCode.ifBlank { "Curriculum Standard" }}")
            appendLine()
            appendLine("## 1. Knowledge Graph Connections")
            if (topic.prerequisites.isNotEmpty()) {
                appendLine("- **Prerequisites:** ${topic.prerequisites.joinToString(", ")}")
            }
            if (topic.successors.isNotEmpty()) {
                appendLine("- **Unlocks Next:** ${topic.successors.joinToString(", ")}")
            }
            appendLine()
            appendLine("## 2. Core Principles & Syllabus Notes")
            topic.sourceNotes.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## 3. Essential Formulas & Laws")
            if (topic.keyFormulas.isNotEmpty()) {
                topic.keyFormulas.forEach { appendLine("- `$it`") }
            } else {
                appendLine("- *Governing Concept:* ${topic.summary}")
            }
            appendLine()
            appendLine("## 4. Guided Worked Example")
            appendLine(topic.workedExample)
            appendLine()
            appendLine("## 5. Examiner Trap Alert & Misconceptions")
            appendLine("> **Warning:** ${topic.commonMisconception}")
            appendLine()
            appendLine("## 6. Diagnostic Self-Check Questions")
            quizItems.forEachIndexed { idx, q ->
                appendLine("### Q${idx + 1} (${q.bloomLevel}): ${q.question}")
                q.options.forEachIndexed { oIdx, opt ->
                    val marker = if (oIdx == q.correctIndex) "✓" else " "
                    appendLine("  [$marker] ${('A' + oIdx)}. $opt")
                }
                appendLine("  *Explanation:* ${q.explanation}")
                appendLine()
            }
        }

        return StudentNotesDoc(
            topicId = topic.topicId,
            title = topic.title,
            gradeSubjectHeader = gradeSubj,
            syllabusCode = topic.syllabusCode,
            prerequisites = topic.prerequisites,
            successors = topic.successors,
            corePrinciples = topic.sourceNotes,
            keyFormulas = topic.keyFormulas,
            workedExample = topic.workedExample,
            commonMisconception = topic.commonMisconception,
            reviewQuestions = quizItems,
            printableMarkdown = md
        )
    }

    private fun buildSources(topic: K12Topic, customNotes: List<String>): List<NotebookSourceCard> {
        val list = mutableListOf(
            NotebookSourceCard(
                sourceId = "src-1",
                badgeLabel = "SOURCE 1 • SYLLABUS NOTES",
                title = "${topic.title} — Core Principles",
                content = topic.sourceNotes.joinToString("\n• ", prefix = "• ")
            ),
            NotebookSourceCard(
                sourceId = "src-2",
                badgeLabel = "SOURCE 2 • KEY FORMULAS & RULES",
                title = "Essential Equations & Definitions",
                content = topic.keyFormulas.joinToString("\n• ", prefix = "• ")
            ),
            NotebookSourceCard(
                sourceId = "src-3",
                badgeLabel = "SOURCE 3 • WORKED EXAMPLE",
                title = "Step-by-Step Guided Walkthrough",
                content = topic.workedExample
            ),
            NotebookSourceCard(
                sourceId = "src-4",
                badgeLabel = "SOURCE 4 • EXAMINER PITFALL REPORT",
                title = "High-Frequency Misconception to Avoid",
                content = topic.commonMisconception
            )
        )
        customNotes.forEachIndexed { idx, note ->
            list.add(
                NotebookSourceCard(
                    sourceId = "src-custom-${idx + 1}",
                    badgeLabel = "SOURCE ${5 + idx} • STUDENT NOTEBOOK SOURCE",
                    title = "Custom Grounded Note #${idx + 1}",
                    content = note
                )
            )
        }
        return list
    }

    private fun buildPodcastOverview(
        topic: K12Topic,
        customNotes: List<String>
    ): NotebookLmPodcastOverview {
        val primaryNote = topic.sourceNotes.firstOrNull() ?: topic.summary
        val secondaryNote = topic.sourceNotes.getOrNull(1) ?: topic.summary
        val primaryFormula = topic.keyFormulas.firstOrNull() ?: topic.title

        val baseTurns = mutableListOf(
            PodcastDialogueTurn(
                turnIndex = 1,
                speaker = "Alex",
                roleLabel = "NotebookLM Co-Host",
                text = "Welcome back to Noorix NotebookLM Deep Dive! Today we've loaded the ${topic.gradeName} ${topic.subjectName} sources for '${topic.title}'. Zoya, why is this topic such a cornerstone?",
                sourceCitation = "[Source 1: Syllabus Overview]"
            ),
            PodcastDialogueTurn(
                turnIndex = 2,
                speaker = "Zoya",
                roleLabel = "Curriculum Specialist",
                text = "Great question, Alex! At its heart, this topic is about ${topic.summary.replaceFirstChar { it.lowercase() }} And our first source note makes a crucial point: $primaryNote",
                sourceCitation = "[Source 1: Core Notes]"
            ),
            PodcastDialogueTurn(
                turnIndex = 3,
                speaker = "Alex",
                roleLabel = "NotebookLM Co-Host",
                text = "That clicks right away. Now, when students open their notebook to solve a problem, what's the anchor rule or formula they should keep front and center?",
                sourceCitation = "[Source 2: Key Formulas]"
            ),
            PodcastDialogueTurn(
                turnIndex = 4,
                speaker = "Zoya",
                roleLabel = "Curriculum Specialist",
                text = "The key relationship to lock in is: $primaryFormula. Also remember: $secondaryNote",
                sourceCitation = "[Source 2: Key Formulas]"
            ),
            PodcastDialogueTurn(
                turnIndex = 5,
                speaker = "Alex",
                roleLabel = "NotebookLM Co-Host",
                text = "Let's make it concrete! Walk me through the worked example from Source 3 step by step.",
                sourceCitation = "[Source 3: Worked Example]"
            ),
            PodcastDialogueTurn(
                turnIndex = 6,
                speaker = "Zoya",
                roleLabel = "Curriculum Specialist",
                text = "Here's how it unfolds in practice: ${topic.workedExample}",
                sourceCitation = "[Source 3: Worked Example]"
            ),
            PodcastDialogueTurn(
                turnIndex = 7,
                speaker = "Alex",
                roleLabel = "NotebookLM Co-Host",
                text = "Wait — before we wrap up, Source 4 flags a classic trap that catches students on tests. What should everyone watch out for?",
                sourceCitation = "[Source 4: Examiner Pitfall]"
            ),
            PodcastDialogueTurn(
                turnIndex = 8,
                speaker = "Zoya",
                roleLabel = "Curriculum Specialist",
                text = "Spot on! The #1 misconception to avoid is: ${topic.commonMisconception} Keep that check in mind, and you'll ace '${topic.title}' every time!",
                sourceCitation = "[Source 4: Examiner Pitfall]"
            )
        )

        if (customNotes.isNotEmpty()) {
            baseTurns.add(
                PodcastDialogueTurn(
                    turnIndex = baseTurns.size + 1,
                    speaker = "Alex",
                    roleLabel = "NotebookLM Co-Host",
                    text = "And look, the learner also added their own custom source note to the notebook: '${customNotes.last()}'. How does that tie in?",
                    sourceCitation = "[Source 5: Student Note]"
                )
            )
            baseTurns.add(
                PodcastDialogueTurn(
                    turnIndex = baseTurns.size + 1,
                    speaker = "Zoya",
                    roleLabel = "Curriculum Specialist",
                    text = "It connects directly to '${topic.title}' by reinforcing how ${primaryFormula} applies to real problems!",
                    sourceCitation = "[Source 5: Student Note]"
                )
            )
        }

        return NotebookLmPodcastOverview(
            podcastId = "podcast:${topic.topicId}",
            topicId = topic.topicId,
            title = "Deep Dive Audio Overview: ${topic.title}",
            subtitle = "${topic.gradeName} • ${topic.subjectName} • 2-Host Grounded Discussion",
            durationMinutes = 5 + customNotes.size,
            turns = baseTurns,
            keyTakeaways = buildList {
                add(primaryNote)
                if (topic.keyFormulas.isNotEmpty()) add("Core Rule: ${topic.keyFormulas.joinToString(" | ")}")
                add("Avoid Pitfall: ${topic.commonMisconception}")
            }
        )
    }

    fun generateFollowUpTurns(
        topic: K12Topic,
        studentQuestion: String,
        startingTurnIndex: Int
    ): List<PodcastDialogueTurn> {
        val q = studentQuestion.trim()
        val relevantFormula = topic.keyFormulas.firstOrNull() ?: topic.title
        return listOf(
            PodcastDialogueTurn(
                turnIndex = startingTurnIndex,
                speaker = "Alex",
                roleLabel = "Listener Call-In",
                text = "Listener Question: \"$q\" — Zoya, let's check our grounded sources for ${topic.title}!",
                sourceCitation = "[Interactive Q&A]"
            ),
            PodcastDialogueTurn(
                turnIndex = startingTurnIndex + 1,
                speaker = "Zoya",
                roleLabel = "Curriculum Specialist",
                text = "Awesome question! Grounding in our ${topic.subjectName} notes: ${topic.sourceNotes.firstOrNull() ?: topic.summary} Using '$relevantFormula', we apply it just like in our example (${topic.workedExample}) while avoiding the trap of ${topic.commonMisconception.replaceFirstChar { it.lowercase() }}",
                sourceCitation = "[Sources 1–4 Synthesis]"
            )
        )
    }

    private fun buildVideoExplainer(
        topic: K12Topic,
        customNotes: List<String>
    ): NotebookLmVideoExplainer {
        val f1 = topic.keyFormulas.firstOrNull() ?: topic.title
        val f2 = topic.keyFormulas.getOrNull(1) ?: f1
        val n1 = topic.sourceNotes.firstOrNull() ?: topic.summary
        val n2 = topic.sourceNotes.getOrNull(1) ?: topic.summary

        val scenes = listOf(
            VideoExplainerScene(
                sceneNumber = 1,
                title = "Scene 1: Conceptual Hook & Intuition",
                visualMode = "CONCEPT_MAP",
                visualHeadline = topic.title,
                visualFormulaOrKeyPoint = "${topic.gradeName} • ${topic.subjectName}",
                bulletCallouts = listOf(
                    topic.summary,
                    n1
                ),
                narrationScript = "Scene 1: Let's visualize ${topic.title}. ${topic.summary} Specifically, $n1",
                sourceCitation = "[Source 1: Core Notes]",
                durationSeconds = 14
            ),
            VideoExplainerScene(
                sceneNumber = 2,
                title = "Scene 2: Core Formula & Visual Architecture",
                visualMode = "FORMULA_BLUEPRINT",
                visualHeadline = "Core Rule & Structure",
                visualFormulaOrKeyPoint = f1,
                bulletCallouts = topic.keyFormulas.ifEmpty { listOf(n1, n2) },
                narrationScript = "Scene 2: Look at the governing rule on screen: $f1. Notice how $n2",
                sourceCitation = "[Source 2: Key Formulas]",
                durationSeconds = 16
            ),
            VideoExplainerScene(
                sceneNumber = 3,
                title = "Scene 3: Step-by-Step Worked Walkthrough",
                visualMode = "WORKED_STEP",
                visualHeadline = "Guided Problem Solving",
                visualFormulaOrKeyPoint = f2,
                bulletCallouts = topic.workedExample.split(". ").map { it.trim() }.filter { it.isNotBlank() },
                narrationScript = "Scene 3: Let's trace the worked example step by step. ${topic.workedExample}",
                sourceCitation = "[Source 3: Worked Example]",
                durationSeconds = 18
            ),
            VideoExplainerScene(
                sceneNumber = 4,
                title = "Scene 4: Trap Alert vs. Verified Method",
                visualMode = "TRAP_VS_TRUTH",
                visualHeadline = "Misconception Shield",
                visualFormulaOrKeyPoint = "AVOID: ${topic.commonMisconception.take(64)}",
                bulletCallouts = listOf(
                    "Common Trap: ${topic.commonMisconception}",
                    "Verified Rule: $n1"
                ),
                narrationScript = "Scene 4: Watch out for this frequent mistake! ${topic.commonMisconception} Always verify using our primary principle.",
                sourceCitation = "[Source 4: Examiner Pitfall]",
                durationSeconds = 15
            ),
            VideoExplainerScene(
                sceneNumber = 5,
                title = "Scene 5: Mastery Synthesis & Takeaway",
                visualMode = "SYNTHESIS_BADGE",
                visualHeadline = "Topic Mastery Unlocked",
                visualFormulaOrKeyPoint = f1,
                bulletCallouts = buildList {
                    add("Mastered Concept: ${topic.title}")
                    add("Key Rule: $f1")
                    if (customNotes.isNotEmpty()) add("Custom Note Integrated: ${customNotes.last()}")
                },
                narrationScript = "Scene 5: You've completed the visual explainer for ${topic.title}! Head over to the NotebookLM Quiz tab to test your mastery.",
                sourceCitation = "[Sources 1–4 Complete]",
                durationSeconds = 12
            )
        )

        return NotebookLmVideoExplainer(
            videoId = "vid-explainer:${topic.topicId}",
            topicId = topic.topicId,
            title = "Visual Storyboard Explainer: ${topic.title}",
            subtitle = "5-Scene Animated Concept Breakdown with Voiceover",
            totalDurationSeconds = scenes.sumOf { it.durationSeconds },
            scenes = scenes
        )
    }

    private fun buildTopicQuiz(topic: K12Topic): List<NotebookLmQuizItem> {
        val note0 = topic.sourceNotes.firstOrNull() ?: topic.summary
        val formula0 = topic.keyFormulas.firstOrNull() ?: topic.summary
        val formula1 = topic.keyFormulas.getOrNull(1) ?: "The core relation in ${topic.title}"

        return listOf(
            NotebookLmQuizItem(
                id = "quiz:${topic.topicId}:1",
                topicId = topic.topicId,
                question = "According to the syllabus notes for '${topic.title}', which statement is accurate?",
                options = listOf(
                    note0,
                    "The opposite of the rule applies whenever values are negative or reversed.",
                    "Wait until all terms are combined without checking units or structure.",
                    "Ignore initial conditions and estimate using only the final digit."
                ),
                correctIndex = 0,
                explanation = "Grounded in Source 1 (Syllabus Notes): $note0",
                bloomLevel = "UNDERSTANDING",
                sourceCitation = "[Source 1: Syllabus Notes]"
            ),
            NotebookLmQuizItem(
                id = "quiz:${topic.topicId}:2",
                topicId = topic.topicId,
                question = "Which key formula, law, or structural rule governs '${topic.title}'?",
                options = listOf(
                    "Random trial without invariants",
                    formula0,
                    "Dividing all constants by zero before simplifying",
                    "Ignoring units and exponents during transformation"
                ),
                correctIndex = 1,
                explanation = "Grounded in Source 2 (Key Formulas & Rules): $formula0 ($formula1).",
                bloomLevel = "APPLICATION",
                sourceCitation = "[Source 2: Key Formulas]"
            ),
            NotebookLmQuizItem(
                id = "quiz:${topic.topicId}:3",
                topicId = topic.topicId,
                question = "In the guided worked example for '${topic.title}', how is the solution verified?",
                options = listOf(
                    "By guessing the middle value without calculation",
                    "By skipping intermediate steps",
                    topic.workedExample,
                    "By reversing the question prompt"
                ),
                correctIndex = 2,
                explanation = "Grounded in Source 3 (Worked Example): ${topic.workedExample}",
                bloomLevel = "ANALYSIS",
                sourceCitation = "[Source 3: Worked Example]"
            ),
            NotebookLmQuizItem(
                id = "quiz:${topic.topicId}:4",
                topicId = topic.topicId,
                question = "Which common pitfall or misconception must you actively avoid when working on '${topic.title}'?",
                options = listOf(
                    "Writing out each step clearly",
                    "Checking units and signs carefully",
                    "Verifying the final answer against the original problem",
                    topic.commonMisconception
                ),
                correctIndex = 3,
                explanation = "Grounded in Source 4 (Examiner Pitfall Alert): ${topic.commonMisconception}",
                bloomLevel = "EVALUATION",
                sourceCitation = "[Source 4: Examiner Pitfall]"
            )
        )
    }
}
