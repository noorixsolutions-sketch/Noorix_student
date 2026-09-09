package com.example.core.model

data class ExplainerVideo(
    val id: String,
    val conceptId: String,
    val title: String,
    val youtubeVideoId: String,
    val description: String,
    val durationLabel: String = ""
) {
    val embedUrl: String get() = "https://www.youtube.com/embed/$youtubeVideoId"
}

data class Flashcard(
    val id: String,
    val conceptId: String,
    val front: String,
    val back: String,
    val hint: String = ""
)

data class LearningQuiz(
    val id: String,
    val conceptId: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class KnowledgeLearningContent(
    val videos: List<ExplainerVideo> = emptyList(),
    val flashcards: List<Flashcard> = emptyList(),
    val quizzes: List<LearningQuiz> = emptyList()
) {
    fun forConcept(conceptId: String): KnowledgeLearningContent = copy(
        videos = videos.filter { it.conceptId == conceptId },
        flashcards = flashcards.filter { it.conceptId == conceptId },
        quizzes = quizzes.filter { it.conceptId == conceptId }
    )
}

fun ExplainerVideo.isSafeYouTubeId(): Boolean = youtubeVideoId.matches(Regex("^[A-Za-z0-9_-]{6,20}$"))
fun ExplainerVideo.safeEmbedUrl(): String? = if (isSafeYouTubeId()) embedUrl else null
