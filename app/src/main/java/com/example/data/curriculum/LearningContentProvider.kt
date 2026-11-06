package com.example.data.curriculum

import android.content.Context
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.KnowledgeLearningContent
import com.example.core.model.LearningQuiz
import com.example.core.model.isSafeYouTubeId
import com.example.domain.notebooklm.NotebookLmStudioEngine
import org.json.JSONArray
import org.json.JSONObject

class LearningContentProvider(private val context: Context) {
    fun load(): KnowledgeLearningContent {
        val json = context.assets.open("curriculum/learning_content.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        val baseVideos = root.optJSONArray("videos").toExplainers()
        val baseFlashcards = root.optJSONArray("flashcards").toFlashcards()
        val baseQuizzes = root.optJSONArray("quizzes").toQuizzes()

        val k12Bands = runCatching { NotebookLmStudioEngine.loadAllK12Bands(context) }.getOrDefault(emptyList())
        val k12Topics = k12Bands.flatMap { band -> band.grades.flatMap { g -> g.subjects.flatMap { s -> s.topics } } }

        val extraFlashcards = k12Topics.flatMap { topic ->
            buildList {
                topic.keyFormulas.forEachIndexed { idx, formula ->
                    add(
                        Flashcard(
                            id = "k12-card:${topic.topicId}:$idx",
                            conceptId = topic.topicId,
                            front = "${topic.title} — Key Rule #${idx + 1}",
                            back = formula,
                            hint = topic.summary
                        )
                    )
                }
                if (topic.commonMisconception.isNotBlank()) {
                    add(
                        Flashcard(
                            id = "k12-card:${topic.topicId}:trap",
                            conceptId = topic.topicId,
                            front = "What is the #1 common misconception in ${topic.title}?",
                            back = topic.commonMisconception,
                            hint = "Check Source 4: Examiner Pitfall Report"
                        )
                    )
                }
            }
        }

        val extraQuizzes = k12Topics.flatMap { topic ->
            NotebookLmStudioEngine.generateBundleForTopic(topic).quizItems.map { q ->
                LearningQuiz(
                    id = q.id,
                    conceptId = q.topicId,
                    question = q.question,
                    options = q.options,
                    correctIndex = q.correctIndex,
                    explanation = "${q.explanation} ${q.sourceCitation}"
                )
            }
        }

        return KnowledgeLearningContent(
            videos = baseVideos,
            flashcards = (baseFlashcards + extraFlashcards).distinctBy { it.id },
            quizzes = (baseQuizzes + extraQuizzes).distinctBy { it.id }
        )
    }

    private fun JSONArray?.toExplainers(): List<ExplainerVideo> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = getJSONObject(i)
                val video = ExplainerVideo(
                    id = item.getString("id"),
                    conceptId = item.getString("conceptId"),
                    title = item.getString("title"),
                    youtubeVideoId = item.getString("youtubeVideoId"),
                    description = item.optString("description"),
                    durationLabel = item.optString("durationLabel")
                )
                if (video.isSafeYouTubeId()) add(video)
            }
        }
    }

    private fun JSONArray?.toFlashcards(): List<Flashcard> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = getJSONObject(i)
                add(Flashcard(item.getString("id"), item.getString("conceptId"), item.getString("front"), item.getString("back"), item.optString("hint")))
            }
        }
    }

    private fun JSONArray?.toQuizzes(): List<LearningQuiz> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = getJSONObject(i)
                val optionsJson = item.getJSONArray("options")
                val options = List(optionsJson.length()) { index -> optionsJson.getString(index) }
                val correctIndex = item.getInt("correctIndex")
                require(correctIndex in options.indices) { "Invalid quiz answer for ${item.getString("id")}" }
                add(LearningQuiz(item.getString("id"), item.getString("conceptId"), item.getString("question"), options, correctIndex, item.getString("explanation")))
            }
        }
    }
}
