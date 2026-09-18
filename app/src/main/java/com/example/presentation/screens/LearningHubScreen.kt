@file:OptIn(ExperimentalLayoutApi::class)

package com.example.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.example.core.model.BookmarkKind
import com.example.core.model.ExplainerVideo
import com.example.core.model.Flashcard
import com.example.core.model.LearningQuiz
import com.example.core.model.safeEmbedUrl
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.FlashcardReviewSession
import com.example.presentation.components.NotesEditorDialog
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

    // --- ADDITIVE study-tools state (search, bookmarks, review, notes) ---
    var searchQuery by remember { mutableStateOf("") }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarks.collectAsState()
    val flashProgress by viewModel.flashcardProgress.collectAsState()
    val conceptNotes by viewModel.notesForConcept(selectedConceptId).collectAsState(initial = emptyList())

    fun isMarked(kind: BookmarkKind, refId: String): Boolean =
        bookmarks.any { it.kind == kind && it.refId == refId }

    val visibleVideos = remember(filtered.videos, searchQuery, showBookmarksOnly, bookmarks) {
        filtered.videos.filter { video ->
            (!showBookmarksOnly || isMarked(BookmarkKind.VIDEO, video.id)) &&
                (searchQuery.isBlank() ||
                    video.title.contains(searchQuery, ignoreCase = true) ||
                    video.description.contains(searchQuery, ignoreCase = true))
        }
    }
    val visibleFlashcards = remember(filtered.flashcards, searchQuery, showBookmarksOnly, bookmarks) {
        filtered.flashcards.filter { card ->
            (!showBookmarksOnly || isMarked(BookmarkKind.FLASHCARD, card.id)) &&
                (searchQuery.isBlank() ||
                    card.front.contains(searchQuery, ignoreCase = true) ||
                    card.back.contains(searchQuery, ignoreCase = true) ||
                    card.hint.contains(searchQuery, ignoreCase = true))
        }
    }
    val visibleQuizzes = remember(filtered.quizzes, searchQuery, showBookmarksOnly, bookmarks) {
        filtered.quizzes.filter { quiz ->
            (!showBookmarksOnly || isMarked(BookmarkKind.QUIZ, quiz.id)) &&
                (searchQuery.isBlank() ||
                    quiz.question.contains(searchQuery, ignoreCase = true) ||
                    quiz.explanation.contains(searchQuery, ignoreCase = true))
        }
    }
    val reviewQueue = remember(filtered.flashcards, flashProgress) {
        viewModel.reviewQueue(filtered.flashcards)
    }
    val dueCount = remember(filtered.flashcards, flashProgress) {
        viewModel.dueFlashcards(filtered.flashcards).size
    }
    val selectedConceptName = selectedConcept?.name
        ?: selectedConceptId.substringAfterLast(":").replace("-", " ")
            .replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Explore Header (docs/two-mode-experience.md §5)
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
                                Icon(Icons.Default.Explore, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "EXPLORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Learn anything — no program needed",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ask a tutor anything, browse the curriculum by stage, or dive into the concept hub: explainers, flashcards and quizzes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 1a. Ask a tutor (Explore's conversational entry — "explain photosynthesis")
        item {
            AskTutorCard(viewModel)
        }

        // 1b. Browse the curriculum knowledge graph by stage (Pre-K → O-Level
        // material from the master-branch Cambridge_Curriculum_Library)
        item {
            KgBrowseCard(viewModel)
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

        // 2b. Search + Study Tools (ADDITIVE)
        item {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_search_field"),
                    placeholder = { Text("Search videos, flashcards, quizzes…", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NearBlackSurface,
                        unfocusedContainerColor = NearBlackSurface,
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubToolChip(
                        label = if (dueCount > 0) "Review ($dueCount due)" else "Review Mode",
                        icon = Icons.Default.Autorenew,
                        active = false,
                        onClick = { selectedTab = 1; showReview = true },
                        modifier = Modifier.weight(1f)
                    )
                    HubToolChip(
                        label = if (conceptNotes.isEmpty()) "Notes" else "Notes (${conceptNotes.size})",
                        icon = Icons.Default.StickyNote2,
                        active = false,
                        onClick = { showNotes = true },
                        modifier = Modifier.weight(1f)
                    )
                    HubToolChip(
                        label = "Saved",
                        icon = if (showBookmarksOnly) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        active = showBookmarksOnly,
                        onClick = { showBookmarksOnly = !showBookmarksOnly },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showBookmarksOnly || searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${visibleVideos.size + visibleFlashcards.size + visibleQuizzes.size} result(s)" +
                            (if (showBookmarksOnly) " in Saved" else "") +
                            (if (searchQuery.isNotBlank()) " for \"$searchQuery\"" else ""),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
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
                    "Explainers (${visibleVideos.size})" to Icons.Default.PlayCircleOutline,
                    "Flashcards (${visibleFlashcards.size})" to Icons.Default.FlipToBack,
                    "Concept Quiz (${visibleQuizzes.size})" to Icons.Default.Quiz
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
                if (visibleVideos.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.VideoLibrary,
                            title = if (showBookmarksOnly || searchQuery.isNotBlank()) "No Matching Videos" else "No Video Explainers Available",
                            subtitle = if (showBookmarksOnly || searchQuery.isNotBlank()) "Try clearing search or Saved filter." else "Video explainers for ${selectedConcept?.name ?: "this concept"} will appear here once linked."
                        )
                    }
                } else {
                    items(visibleVideos, key = { it.id }) { video ->
                        HubVideoCard(
                            video = video,
                            isBookmarked = isMarked(BookmarkKind.VIDEO, video.id),
                            onToggleBookmark = {
                                viewModel.toggleBookmark(BookmarkKind.VIDEO, video.id, selectedConceptId, video.title)
                            }
                        )
                    }
                }
            }

            1 -> {
                if (visibleFlashcards.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.Style,
                            title = if (showBookmarksOnly || searchQuery.isNotBlank()) "No Matching Flashcards" else "No Flashcards Linked",
                            subtitle = if (showBookmarksOnly || searchQuery.isNotBlank()) "Try clearing search or Saved filter." else "Active recall cards for ${selectedConcept?.name ?: "this concept"} are being prepared."
                        )
                    }
                } else {
                    items(visibleFlashcards, key = { it.id }) { card ->
                        HubFlashcardCard(
                            card = card,
                            isBookmarked = isMarked(BookmarkKind.FLASHCARD, card.id),
                            onToggleBookmark = {
                                viewModel.toggleBookmark(BookmarkKind.FLASHCARD, card.id, selectedConceptId, card.front.take(60))
                            }
                        )
                    }
                }
            }

            else -> {
                if (visibleQuizzes.isEmpty()) {
                    item {
                        EmptyHubState(
                            icon = Icons.Default.Psychology,
                            title = if (showBookmarksOnly || searchQuery.isNotBlank()) "No Matching Quizzes" else "No Practice Quiz Found",
                            subtitle = if (showBookmarksOnly || searchQuery.isNotBlank()) "Try clearing search or Saved filter." else "Practice quizzes for ${selectedConcept?.name ?: "this concept"} will appear here."
                        )
                    }
                } else {
                    items(visibleQuizzes, key = { it.id }) { quiz ->
                        HubQuizCard(
                            quiz = quiz,
                            onAnswerSubmitted = { isCorrect ->
                                viewModel.submitLearningHubQuiz(selectedConceptId, quiz.id, isCorrect)
                            },
                            isBookmarked = isMarked(BookmarkKind.QUIZ, quiz.id),
                            onToggleBookmark = {
                                viewModel.toggleBookmark(BookmarkKind.QUIZ, quiz.id, selectedConceptId, quiz.question.take(60))
                            }
                        )
                    }
                }
            }
        }
    }

    // --- ADDITIVE dialogs: Review Mode + Notes ---
    if (showReview) {
        FlashcardReviewSession(
            conceptName = selectedConceptName,
            queue = reviewQueue,
            onGrade = { card, grade ->
                viewModel.gradeFlashcard(card.id, card.conceptId, grade)
            },
            onDismiss = { showReview = false }
        )
    }
    if (showNotes) {
        NotesEditorDialog(
            conceptName = selectedConceptName,
            notes = conceptNotes,
            onSave = { noteId, title, body ->
                viewModel.saveStudyNote(
                    noteId = noteId,
                    conceptId = selectedConceptId,
                    conceptName = selectedConceptName,
                    title = title,
                    body = body
                )
            },
            onDelete = { noteId -> viewModel.deleteStudyNote(noteId) },
            onDismiss = { showNotes = false }
        )
    }
}

@Composable
private fun HubToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) TealAccent.copy(alpha = 0.18f) else NearBlackSurface,
        border = BorderStroke(1.dp, if (active) TealAccent else SubtleBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) TealAccent else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) TealAccent else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
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
private fun HubVideoCard(
    video: ExplainerVideo,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
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
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark video",
                        tint = if (isBookmarked) TealAccent else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
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
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString()
                                    return if (url != null && url.startsWith("https://www.youtube.com/")) {
                                        false
                                    } else {
                                        true
                                    }
                                }
                            }
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
private fun HubFlashcardCard(
    card: Flashcard,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRevealed) "Tap to hide" else "Tap to flip",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark flashcard",
                            tint = if (isBookmarked) TealAccent else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
    onAnswerSubmitted: ((Boolean) -> Unit)? = null,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONCEPT QUIZ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark quiz",
                        tint = if (isBookmarked) TealAccent else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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

// ---------------------------------------------------------------------------
// Explore additions (docs/two-mode-experience.md §5)
// ---------------------------------------------------------------------------

@Composable
private fun AskTutorCard(viewModel: OLevelViewModel) {
    var question by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf("danish") }

    val tutors = listOf(
        "danish" to "Danish · Math",
        "zoya" to "Zoya · Physics",
        "ayesha" to "Ayesha · English",
        "bilal" to "Bilal · Reasoning",
        "exam_coach" to "Exam Coach",
        "career_guide" to "Career Guide"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "ASK A TUTOR ANYTHING",
                style = MaterialTheme.typography.labelSmall,
                color = TealAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tutors.forEach { (id, label) ->
                    val isSelected = agentId == id
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) TealAccentSubtle else NearBlackBg,
                        border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                        modifier = Modifier.clickable { agentId = id }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TealAccent else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. Explain photosynthesis…", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NearBlackBg,
                        unfocusedContainerColor = NearBlackBg,
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = TealAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        val q = question.trim()
                        if (q.isNotBlank()) {
                            viewModel.askTutor(agentId, q)
                            question = ""
                        }
                    },
                    enabled = question.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
                ) {
                    Text("Ask", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun KgBrowseCard(viewModel: OLevelViewModel) {
    val kgStages by viewModel.kgStages.collectAsState()
    val onboarding by viewModel.onboardingState.collectAsState()
    var stageOrder by remember(onboarding.kgStageOrder) {
        mutableIntStateOf(onboarding.kgStageOrder)
    }
    val stage = kgStages.firstOrNull { it.order == stageOrder }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "BROWSE THE CURRICULUM",
                style = MaterialTheme.typography.labelSmall,
                color = CyberGold,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "From Early Years to O-Level — the subjects on the map for each stage.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (kgStages.isEmpty()) {
                Text(
                    text = "Curriculum map is still loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kgStages.forEach { s ->
                        val isSelected = s.order == stageOrder
                        val isCurrent = s.order == onboarding.kgStageOrder
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) CyberGold.copy(alpha = 0.15f) else NearBlackBg,
                            border = BorderStroke(1.dp, if (isSelected) CyberGold else SubtleBorder),
                            modifier = Modifier.clickable { stageOrder = s.order }
                        ) {
                            Text(
                                text = "${s.order} · ${s.name.substringAfter(" ")}",
                                color = when {
                                    isSelected -> CyberGold
                                    isCurrent -> TealAccent
                                    else -> TextSecondary
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                stage?.let { selectedStage ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedStage.subjects.take(6).forEach { subject ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = subject.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!subject.syllabusCode.isNullOrEmpty()) {
                                        Text(
                                            text = subject.syllabusCode,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedStage.subjects.size > 6) {
                            Text(
                                text = "+${selectedStage.subjects.size - 6} more subjects — see Program for the full map",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
