package com.example.data.curriculum

import android.content.Context
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.KnowledgeLearningContent
import com.example.core.model.LearningQuiz
import com.example.core.model.isSafeYouTubeId
import org.json.JSONArray
import org.json.JSONObject

class LearningContentProvider(private val context: Context) {
    fun load(): KnowledgeLearningContent {
        val json = context.assets.open("curriculum/learning_content.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        return KnowledgeLearningContent(
            videos = root.optJSONArray("videos").toExplainers(),
            flashcards = root.optJSONArray("flashcards").toFlashcards(),
            quizzes = root.optJSONArray("quizzes").toQuizzes()
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
