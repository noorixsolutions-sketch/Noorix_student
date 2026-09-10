package com.example.presentation.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.LearningQuiz
import com.example.core.model.safeEmbedUrl
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

@Composable
fun LearningHubScreen(viewModel: OLevelViewModel) {
    val activePkg by viewModel.activePackage.collectAsState()
    val content = viewModel.learningContent

    val concepts = remember(activePkg) {
        activePkg?.nodes
            ?.filter { it.type.name == "CONCEPT" }
            ?.distinctBy { it.id }
            .orEmpty()
    }

    var selectedConceptId by remember(concepts) {
        mutableStateOf(concepts.firstOrNull()?.id ?: "concept:4024:factorisation")
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedConcept = concepts.firstOrNull { it.id == selectedConceptId }
    val filtered = content.forConcept(selectedConceptId)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = TealAccentSubtle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.School, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "KNOWLEDGE LEARNING HUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Cambridge Concept Mastery",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Explore syllabus-linked video explainers, practice active recall with interactive flashcards, and verify your comprehension with diagnostic quizzes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Concept Selector Carousel
        item {
            Column {
                Text(
                    text = "SELECT CONCEPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (concepts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Loading syllabus concepts...",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        concepts.forEach { concept ->
                            val isSelected = concept.id == selectedConceptId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) TealAccent else NearBlackSurface,
                                border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                                modifier = Modifier.clickable { selectedConceptId = concept.id }
                            ) {
                                Text(
                                    text = concept.name,
                                    color = if (isSelected) NearBlackBg else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Tab Selector (Explainers, Flashcards, Quiz)
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = NearBlackSurface,
                contentColor = TealAccent,
                divider = { HorizontalDivider(color = SubtleBorder) }
            ) {
                listOf(
                    "Explainers (${filtered.videos.size})" to Icons.Default.PlayCircleOutline,
                    "Flashcards (${filtered.flashcards.size})" to Icons.Default.FlipToBack,
                    "Concept Quiz (${filtered.quizzes.size})" to Icons.Default.Quiz
                ).forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        selectedContentColor = TealAccent,
                        unselectedContentColor = TextSecondary
                    )
                }
            }
        }

        // 4. Tab Content
        when (selectedTab) {
            0 -> {
                if (filtered.videos.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.VideoLibrary,
                            title = "No Video Explainers Available",
                            subtitle = "Video explainers for ${selectedConcept?.name ?: "this concept"} will appear here once linked."
                        )
                    }
                } else {
                    items(filtered.videos, key = { it.id }) { video ->
                        HubVideoCard(video)
                    }
                }
            }

            1 -> {
                if (filtered.flashcards.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.Style,
                            title = "No Flashcards Linked",
                            subtitle = "Active recall cards for ${selectedConcept?.name ?: "this concept"} are being prepared."
                        )
                    }
                } else {
                    items(filtered.flashcards, key = { it.id }) { card ->
                        HubFlashcardCard(card)
                    }
                }
            }

            else -> {
                if (filtered.quizzes.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.Psychology,
                            title = "No Practice Quiz Found",
                            subtitle = "Practice quizzes for ${selectedConcept?.name ?: "this concept"} will appear here."
                        )
                    }
                } else {
                    items(filtered.quizzes, key = { it.id }) { quiz ->
                        HubQuizCard(
                            quiz = quiz,
                            onAnswerSubmitted = { isCorrect ->
                                viewModel.submitLearningHubQuiz(selectedConceptId, quiz.id, isCorrect)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHubState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HubVideoCard(video: ExplainerVideo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (video.durationLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TealAccentSubtle
                    ) {
                        Text(
                            text = video.durationLabel,
                            color = TealAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (video.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            loadDataWithBaseURL(
                                "https://www.youtube.com",
                                "<html><body style='margin:0;background:#090D12;'><iframe width='100%' height='100%' src='${video.safeEmbedUrl() ?: "about:blank"}' title='YouTube video' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture' allowfullscreen></iframe></body></html>",
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
}

@Composable
private fun HubFlashcardCard(card: Flashcard) {
    var isRevealed by remember(card.id) { mutableStateOf(false) }
    var showHint by remember(card.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isRevealed = !isRevealed },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRevealed) NearBlackElevated else NearBlackSurface
        ),
        border = BorderStroke(1.dp, if (isRevealed) TealAccent.copy(alpha = 0.5f) else SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isRevealed) StepCorrect.copy(alpha = 0.15f) else TealAccentSubtle
                ) {
                    Text(
                        text = if (isRevealed) "ANSWER (REVEALED)" else "PROMPT (QUESTION)",
                        color = if (isRevealed) StepCorrect else TealAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = if (isRevealed) "Tap to hide" else "Tap to flip",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isRevealed) card.back else card.front,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp
            )

            if (!isRevealed && card.hint.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AnimatedVisibility(visible = showHint) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(card.hint, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!showHint) {
                    TextButton(
                        onClick = { showHint = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = CyberGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show Hint", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { isRevealed = !isRevealed },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SubtleBorderActive)
                ) {
                    Icon(
                        if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TealAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRevealed) "Hide" else "Reveal Answer", fontSize = 11.sp, color = TextPrimary)
                }

                if (isRevealed) {
                    Text(
                        text = "Mastered! ✨",
                        color = StepCorrect,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HubQuizCard(
    quiz: LearningQuiz,
    onAnswerSubmitted: ((Boolean) -> Unit)? = null
) {
    var selectedIndex by remember(quiz.id) { mutableIntStateOf(-1) }
    val isAnswered = selectedIndex >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "CONCEPT QUIZ",
                style = MaterialTheme.typography.labelSmall,
                color = TealAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = quiz.question,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(14.dp))

            quiz.options.forEachIndexed { index, option ->
                val isSelected = selectedIndex == index
                val isCorrect = index == quiz.correctIndex
                val optionBg = when {
                    isAnswered && isCorrect -> StepCorrect.copy(alpha = 0.15f)
                    isAnswered && isSelected && !isCorrect -> StepIssue.copy(alpha = 0.15f)
                    isSelected -> TealAccentSubtle
                    else -> NearBlackBg
                }
                val optionBorder = when {
                    isAnswered && isCorrect -> StepCorrect
                    isAnswered && isSelected && !isCorrect -> StepIssue
                    isSelected -> TealAccent
                    else -> SubtleBorder
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = optionBg,
                    border = BorderStroke(1.dp, optionBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = !isAnswered) {
                            selectedIndex = index
                            onAnswerSubmitted?.invoke(index == quiz.correctIndex)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) TealAccent else SubtleBorder,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ('A' + index).toString(),
                                    color = if (isSelected) NearBlackBg else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (isAnswered) {
                val isCorrect = selectedIndex == quiz.correctIndex
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) StepCorrect.copy(alpha = 0.1f) else StepIssue.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isCorrect) StepCorrect else StepIssue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isCorrect) StepCorrect else StepIssue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCorrect) "Correct Answer! 🎉" else "Not quite right",
                                color = if (isCorrect) StepCorrect else StepIssue,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = quiz.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { selectedIndex = -1 },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Try Again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
