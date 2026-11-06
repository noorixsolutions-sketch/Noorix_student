package com.example.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/**
 * Validated question item parsed from a cloud-delivered quiz artifact.
 */
data class CloudQuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val bloomLevel: String = "UNDERSTANDING",
    val hasAnswerKey: Boolean = true
)

/**
 * Validated quiz payload delivered from Google Drive or local cache.
 * Follows Section 10: Separates student-visible questions from protected answer keys,
 * gracefully handles malformed items, and enforces client evaluation transparency.
 */
data class CloudQuizPayload(
    val quizId: String,
    val topicId: String,
    val title: String,
    val questions: List<CloudQuizQuestion>,
    val passThreshold: Double = 0.70
) {
    companion object {
        /**
         * Validates and parses incoming quiz JSON from an artifact download or bundle.
         * Gracefully filters out malformed questions without crashing.
         */
        fun parse(jsonString: String, fallbackTopicId: String = ""): CloudQuizPayload {
            val root = JSONObject(jsonString)
            val quizId = root.optString("quizId", "quiz-${System.currentTimeMillis()}")
            val topicId = root.optString("topicId", fallbackTopicId)
            val title = root.optString("title", "Topic Practice Quiz")
            val threshold = root.optDouble("passThreshold", 0.70)

            val questionsArray = root.optJSONArray("questions") ?: JSONArray()
            val parsedQuestions = mutableListOf<CloudQuizQuestion>()

            for (i in 0 until questionsArray.length()) {
                val qObj = questionsArray.optJSONObject(i) ?: continue
                val qText = qObj.optString("question", "").trim()
                if (qText.isBlank()) continue

                val optsArray = qObj.optJSONArray("options") ?: JSONArray()
                val options = mutableListOf<String>()
                for (k in 0 until optsArray.length()) {
                    val opt = optsArray.optString(k, "").trim()
                    if (opt.isNotBlank()) options.add(opt)
                }

                if (options.size < 2) continue // Need at least two choices for multiple choice

                val correctIdx = qObj.optInt("correctIndex", -1)
                val hasKey = correctIdx in options.indices
                val safeIdx = if (hasKey) correctIdx else 0

                val qId = qObj.optString("id", "q-$i")
                val explanation = qObj.optString("explanation", "")
                val bloom = qObj.optString("bloomLevel", "APPLICATION")

                parsedQuestions.add(
                    CloudQuizQuestion(
                        id = qId,
                        question = qText,
                        options = options,
                        correctIndex = safeIdx,
                        explanation = explanation,
                        bloomLevel = bloom,
                        hasAnswerKey = hasKey
                    )
                )
            }

            return CloudQuizPayload(
                quizId = quizId,
                topicId = topicId,
                title = title,
                questions = parsedQuestions,
                passThreshold = threshold
            )
        }
    }
}
