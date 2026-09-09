package com.example.presentation.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.KnowledgeLearningContent
import com.example.core.model.LearningQuiz
import com.example.core.model.safeEmbedUrl
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.TealAccent

@Composable
fun LearningHubScreen(viewModel: OLevelViewModel) {
    val content = viewModel.learningContent
    val concepts = viewModel.activePackage.value?.nodes
        ?.filter { it.type.name == "CONCEPT" }
        ?.distinctBy { it.id }
        .orEmpty()
    var selectedConceptId by remember(concepts) { mutableStateOf(concepts.firstOrNull()?.id) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedConcept = concepts.firstOrNull { it.id == selectedConceptId }
    val filtered = content.forConcept(selectedConceptId.orEmpty())

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NearBlackBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Knowledge Learning Hub", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Choose a concept from the signed knowledge graph, then learn, recall, and check your understanding.", color = Color.LightGray)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                concepts.forEach { concept ->
                    FilterChip(
                        selected = concept.id == selectedConceptId,
                        onClick = { selectedConceptId = concept.id },
                        label = { Text(concept.name) }
                    )
                }
            }
        }
        item {
            Surface(color = NearBlackSurface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(14.dp)) {
                    Text(selectedConcept?.name ?: "No concept selected", style = MaterialTheme.typography.titleLarge, color = TealAccent)
                    Text(selectedConcept?.id ?: "Install a course package to load graph concepts", color = Color.LightGray)
                }
            }
        }
        item {
            TabRow(selectedTabIndex = selectedTab, containerColor = NearBlackSurface) {
                listOf("Explainers", "Flashcards", "Quiz").forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
        }
        when (selectedTab) {
            0 -> {
                if (filtered.videos.isEmpty()) item { EmptyContent("No explainer is mapped to this concept yet.") }
                items(filtered.videos, key = { it.id }) { video -> VideoCard(video) }
            }
            1 -> {
                if (filtered.flashcards.isEmpty()) item { EmptyContent("No flashcards are mapped to this concept yet.") }
                items(filtered.flashcards, key = { it.id }) { card -> FlashcardCard(card) }
            }
            else -> {
                if (filtered.quizzes.isEmpty()) item { EmptyContent("No quiz is mapped to this concept yet.") }
                items(filtered.quizzes, key = { it.id }) { quiz -> QuizCard(quiz) }
            }
        }
    }
}

@Composable
private fun EmptyContent(message: String) {
    Text(message, color = Color.LightGray, modifier = Modifier.padding(vertical = 24.dp))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoCard(video: ExplainerVideo) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleMedium)
            Text(video.description, color = Color.Gray)
            if (video.durationLabel.isNotBlank()) Text(video.durationLabel, color = TealAccent)
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(210.dp),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            "<html><body style='margin:0;background:#101418'><iframe width='100%' height='100%' src='${video.safeEmbedUrl() ?: "about:blank"}' title='YouTube explainer' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture' allowfullscreen></iframe></body></html>",
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { it.loadUrl(video.safeEmbedUrl() ?: "about:blank") }
            )
        }
    }
}

@Composable
private fun FlashcardCard(card: Flashcard) {
    var revealed by remember(card.id) { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(if (revealed) "Answer" else "Prompt", color = TealAccent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(if (revealed) card.back else card.front, style = MaterialTheme.typography.titleMedium)
            if (!revealed && card.hint.isNotBlank()) Text("Hint: ${card.hint}", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { revealed = !revealed }) { Text(if (revealed) "Hide answer" else "Reveal answer") }
        }
    }
}

@Composable
private fun QuizCard(quiz: LearningQuiz) {
    var selected by remember(quiz.id) { mutableIntStateOf(-1) }
    val answered = selected >= 0
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(quiz.question, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            quiz.options.forEachIndexed { index, option ->
                OutlinedButton(onClick = { if (!answered) selected = index }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(option)
                }
            }
            if (answered) {
                val correct = selected == quiz.correctIndex
                Text(if (correct) "Correct" else "Review this one", color = if (correct) Color(0xFF68D391) else Color(0xFFFF8A80), modifier = Modifier.padding(top = 8.dp))
                Text(quiz.explanation, color = Color.LightGray)
                LinearProgressIndicator(progress = { if (correct) 1f else 0.35f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button(onClick = { selected = -1 }, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
            }
        }
    }
}
