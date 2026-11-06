package com.example.presentation.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.notebooklm.K12Band
import com.example.domain.notebooklm.K12Topic
import com.example.domain.notebooklm.NotebookLmPodcastOverview
import com.example.domain.notebooklm.NotebookLmQuizItem
import com.example.domain.notebooklm.NotebookLmVideoExplainer
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MediaTransferScreen(viewModel: OLevelViewModel) {
    BackHandler {
        viewModel.setTab(AppNavigationTab.DASHBOARD)
    }

    val context = LocalContext.current
    val k12Bands by viewModel.indexedK12Bands.collectAsState()
    val indexMeta by viewModel.curriculumIndexMeta.collectAsState()
    val curriculumSearchQuery by viewModel.curriculumSearchQuery.collectAsState()
    val browsedTopics by viewModel.browsedK12Topics.collectAsState()
    val selectedTopic by viewModel.selectedK12Topic.collectAsState()
    val bundle by viewModel.notebookLmBundle.collectAsState()
    val transferTask by viewModel.currentTransferTask.collectAsState()
    val feedback by viewModel.transferFeedback.collectAsState()

    // K-12 Selector State
    var selectedBandIndex by remember(k12Bands.size) { mutableIntStateOf(2.coerceAtMost((k12Bands.size - 1).coerceAtLeast(0))) }
    val currentBand: K12Band? = k12Bands.getOrNull(selectedBandIndex)

    var selectedGradeIndex by remember(selectedBandIndex) { mutableIntStateOf(0) }
    val currentGrade = currentBand?.grades?.getOrNull(selectedGradeIndex) ?: currentBand?.grades?.firstOrNull()

    var selectedSubjectIndex by remember(selectedBandIndex, selectedGradeIndex) { mutableIntStateOf(0) }
    val currentSubject = currentGrade?.subjects?.getOrNull(selectedSubjectIndex) ?: currentGrade?.subjects?.firstOrNull()

    // Studio Mode Tabs: 0 = Podcast Overview, 1 = Video Explainer, 2 = Topic Quiz, 3 = Transfer Lab
    var studioTab by remember { mutableIntStateOf(0) }
    var showSourcesDrawer by remember { mutableStateOf(false) }
    var customSourceInput by remember { mutableStateOf("") }

    // Android Text-To-Speech for Podcast & Video Explainer narration
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
                ttsReady = true
            }
        }
        ttsEngine = instance
        onDispose {
            runCatching {
                instance?.stop()
                instance?.shutdown()
            }
        }
    }

    // Auto-generate media if none is loaded yet
    LaunchedEffect(selectedTopic?.topicId) {
        val topicId = selectedTopic?.topicId ?: "concept:4024:factorisation"
        viewModel.generateMedia(topicId)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp)
    ) {
        // 1. NotebookLM Studio Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Image(
                                painter = painterResource(id = R.drawable.img_zoya_science),
                                contentDescription = "NotebookLM Studio",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, TealAccent, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "NOTEBOOKLM CURRICULUM STUDIO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "K–12 Podcast, Video & Quiz Lab",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TealAccentSubtle,
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { showSourcesDrawer = !showSourcesDrawer }
                                .testTag("toggle_notebooklm_sources_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = "Sources",
                                    tint = TealAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${bundle?.sources?.size ?: 4} Sources",
                                    color = TealAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select any Kindergarten–Grade 12 subject & topic below. NotebookLM grounds every 2-Host Audio Podcast, 5-Scene Video Explainer, and Topic Quiz directly in verified syllabus sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Complete K-12 Curriculum Browser (Room-Indexed via CurriculumManager)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. SELECT K–12 GRADE BAND & CLASS",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            val totalBands = indexMeta?.totalBands ?: k12Bands.size
                            val totalGrades = indexMeta?.totalGrades ?: k12Bands.sumOf { it.grades.size }
                            val totalSubjects = indexMeta?.totalSubjects ?: k12Bands.sumOf { b -> b.grades.sumOf { it.subjects.size } }
                            val totalTopics = indexMeta?.totalTopics ?: viewModel.allK12Topics.size
                            Text(
                                text = "Room Index v${indexMeta?.version ?: "2026.1"} • $totalBands Bands • $totalGrades Grades • $totalSubjects Subjects • $totalTopics Topics",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.reindexK12Curriculum() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("reindex_k12_curriculum_button")
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Re-index K-12 Curriculum in Room",
                                tint = TealAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fast Room Full-Text Search across all K-12 subjects, topics, and formulas
                    OutlinedTextField(
                        value = curriculumSearchQuery,
                        onValueChange = { viewModel.updateCurriculumSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("k12_curriculum_search_field"),
                        placeholder = {
                            Text(
                                "Search Room K–12 index (e.g., calculus, phonics, photosynthesis, 4024)...",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search Curriculum",
                                tint = TealAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (curriculumSearchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.updateCurriculumSearchQuery("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear curriculum search",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = NearBlackBg,
                            unfocusedContainerColor = NearBlackBg
                        )
                    )

                    if (curriculumSearchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ROOM SEARCH RESULTS (${browsedTopics.size} MATCHING TOPICS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        browsedTopics.take(10).forEach { topic ->
                            val isTopicSelected = selectedTopic?.topicId == topic.topicId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isTopicSelected) NearBlackElevated else NearBlackBg,
                                border = BorderStroke(1.dp, if (isTopicSelected) StepCorrect else SubtleBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { viewModel.selectK12Topic(topic) }
                                    .testTag("k12_search_result_${topic.topicId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isTopicSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isTopicSelected) StepCorrect else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${topic.gradeName} • ${topic.subjectName}",
                                            color = CyberGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = topic.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = topic.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Band chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        k12Bands.forEachIndexed { idx, band ->
                            val selected = idx == selectedBandIndex
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedBandIndex = idx
                                    selectedGradeIndex = 0
                                    selectedSubjectIndex = 0
                                    band.grades.firstOrNull()?.subjects?.firstOrNull()?.topics?.firstOrNull()?.let {
                                        viewModel.selectK12Topic(it)
                                    }
                                },
                                label = { Text(band.bandName.substringBefore(" ("), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealAccentSubtle,
                                    selectedLabelColor = TealAccent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Grade chips within Band
                    currentBand?.grades?.let { grades ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            grades.forEachIndexed { gIdx, grade ->
                                val isGradeSelected = gIdx == selectedGradeIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isGradeSelected) CyberGold.copy(alpha = 0.2f) else NearBlackBg,
                                    border = BorderStroke(1.dp, if (isGradeSelected) CyberGold else SubtleBorder),
                                    modifier = Modifier.clickable {
                                        selectedGradeIndex = gIdx
                                        selectedSubjectIndex = 0
                                        grade.subjects.firstOrNull()?.topics?.firstOrNull()?.let {
                                            viewModel.selectK12Topic(it)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = grade.gradeName,
                                        color = if (isGradeSelected) CyberGold else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isGradeSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "2. SELECT SUBJECT & TOPIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Subject chips
                    currentGrade?.subjects?.let { subjects ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subjects.forEachIndexed { sIdx, subj ->
                                val isSubjSelected = sIdx == selectedSubjectIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSubjSelected) TealAccentSubtle else NearBlackBg,
                                    border = BorderStroke(1.dp, if (isSubjSelected) TealAccent else SubtleBorder),
                                    modifier = Modifier.clickable {
                                        selectedSubjectIndex = sIdx
                                        subj.topics.firstOrNull()?.let { viewModel.selectK12Topic(it) }
                                    }
                                ) {
                                    Text(
                                        text = subj.subjectName,
                                        color = if (isSubjSelected) TealAccent else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSubjSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Topic cards within Subject
                    currentSubject?.topics?.forEach { topic ->
                        val isTopicSelected = selectedTopic?.topicId == topic.topicId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isTopicSelected) NearBlackElevated else NearBlackBg,
                            border = BorderStroke(1.dp, if (isTopicSelected) StepCorrect else SubtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { viewModel.selectK12Topic(topic) }
                                .testTag("k12_topic_${topic.topicId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isTopicSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isTopicSelected) StepCorrect else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topic.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = if (isTopicSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = topic.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Collapsible Grounded Sources Drawer (+ Add Custom Source Note)
        item {
            AnimatedVisibility(visible = showSourcesDrawer) {
                bundle?.let { b ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GROUNDED NOTEBOOK SOURCES (${b.sources.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                TextButton(onClick = { showSourcesDrawer = false }) {
                                    Text("Hide", color = TextSecondary, fontSize = 11.sp)
                                }
                            }

                            b.sources.forEach { src ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = NearBlackBg,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = src.badgeLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealAccent
                                        )
                                        Text(
                                            text = src.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = src.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Add custom source note to NotebookLM
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customSourceInput,
                                    onValueChange = { customSourceInput = it },
                                    placeholder = { Text("Add your own study note to ground Podcast & Video…", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (customSourceInput.isNotBlank()) {
                                            viewModel.addCustomSourceToNotebook(customSourceInput)
                                            customSourceInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Add Source", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Studio Format Selector Tabs (Podcast | Video Explainer | Topic Quiz | Student Notes & PDF | Transfer Lab)
        item {
            val tabItems = listOf(
                Triple(0, "Podcast", Icons.Default.Podcasts),
                Triple(1, "Video", Icons.Default.OndemandVideo),
                Triple(2, "Quiz", Icons.Default.Quiz),
                Triple(3, "Notes & PDF", Icons.Default.PictureAsPdf),
                Triple(4, "Transfer Lab", Icons.Default.Psychology)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabItems.forEach { (idx, label, icon) ->
                    val active = studioTab == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) TealAccent else NearBlackSurface,
                        border = BorderStroke(1.dp, if (active) TealAccent else SubtleBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                runCatching { ttsEngine?.stop() }
                                studioTab = idx
                            }
                            .testTag("studio_tab_$idx")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (active) NearBlackBg else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = if (active) NearBlackBg else TextPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 5. Render Active NotebookLM Studio Artifact
        bundle?.let { activeBundle ->
            when (studioTab) {
                0 -> item {
                    NotebookLmPodcastCard(
                        podcast = activeBundle.podcast,
                        ttsEngine = ttsEngine,
                        ttsReady = ttsReady,
                        onAskQuestion = { q -> viewModel.askNotebookLmPodcastHosts(q) }
                    )
                }

                1 -> item {
                    NotebookLmVideoExplainerCard(
                        video = activeBundle.videoExplainer,
                        ttsEngine = ttsEngine,
                        ttsReady = ttsReady
                    )
                }

                2 -> item {
                    NotebookLmTopicQuizCard(
                        topic = activeBundle.topic,
                        quizItems = activeBundle.quizItems,
                        onAnswerSubmitted = { item, selectedIdx ->
                            viewModel.recordNotebookLmQuizAnswer(activeBundle.topic, item, selectedIdx)
                        }
                    )
                }

                3 -> item {
                    NotebookLmStudentNotesPdfCard(
                        bundle = activeBundle,
                        onExportPdf = {
                            val file = com.example.domain.notebooklm.PdfNotesExporter.generatePdf(context, activeBundle)
                            com.example.domain.notebooklm.PdfNotesExporter.sharePdf(context, file, activeBundle.topic.title)
                        },
                        onNavigateToTopic = { topicId ->
                            viewModel.selectK12TopicById(topicId)
                        }
                    )
                }

                4 -> {
                    transferTask?.let { task ->
                        item {
                            TransferChallengeCard(
                                task = task,
                                feedback = feedback,
                                onSubmit = { optIdx ->
                                    viewModel.submitTransferTask(task, optIdx, 0.95)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookLmPodcastCard(
    podcast: NotebookLmPodcastOverview,
    ttsEngine: TextToSpeech?,
    ttsReady: Boolean,
    onAskQuestion: (String) -> Unit
) {
    var isPlaying by remember(podcast.podcastId) { mutableStateOf(false) }
    var currentTurnIdx by remember(podcast.podcastId) { mutableIntStateOf(0) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var listenerQuestion by remember { mutableStateOf("") }

    // Auto-advance turns and speak via TTS when playing
    LaunchedEffect(isPlaying, currentTurnIdx, speechRate, podcast.turns.size) {
        if (isPlaying && currentTurnIdx in podcast.turns.indices) {
            val turn = podcast.turns[currentTurnIdx]
            if (ttsReady && ttsEngine != null) {
                runCatching {
                    ttsEngine.setPitch(if (turn.speaker == "Alex") 0.95f else 1.12f)
                    ttsEngine.setSpeechRate(speechRate)
                    ttsEngine.speak(turn.text, TextToSpeech.QUEUE_FLUSH, null, "turn_${turn.turnIndex}")
                }
            }
            val estimatedMs = ((turn.text.length * 55L) / speechRate).toLong().coerceIn(3000L, 11000L)
            delay(estimatedMs)
            if (isPlaying) {
                if (currentTurnIdx < podcast.turns.lastIndex) {
                    currentTurnIdx++
                } else {
                    isPlaying = false
                }
            }
        } else if (!isPlaying) {
            runCatching { ttsEngine?.stop() }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StepCorrect.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "NOTEBOOKLM AUDIO OVERVIEW • 2 HOSTS",
                        color = StepCorrect,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${podcast.durationMinutes} min • ${podcast.turns.size} turns",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = podcast.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Wave Visualizer
            val progressFraction = if (podcast.turns.isEmpty()) 0f else (currentTurnIdx + 1).toFloat() / podcast.turns.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = listOf(0.4f, 0.75f, 0.35f, 0.9f, 0.6f, 1.0f, 0.8f, 0.5f, 0.95f, 0.45f, 0.7f, 0.85f, 0.6f, 0.95f, 0.5f, 0.35f)
                barHeights.forEachIndexed { idx, h ->
                    val active = (idx.toFloat() / barHeights.size) <= progressFraction
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(if (isPlaying) h else (h * 0.45f).coerceAtLeast(0.2f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) StepCorrect else SubtleBorder)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Player Controls (Prev, Play/Pause, Next, Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { if (currentTurnIdx > 0) currentTurnIdx-- },
                        enabled = currentTurnIdx > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Turn", tint = TextPrimary)
                    }

                    Surface(
                        shape = CircleShape,
                        color = StepCorrect,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { isPlaying = !isPlaying }
                            .testTag("podcast_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause Podcast",
                                tint = NearBlackBg,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (currentTurnIdx < podcast.turns.lastIndex) currentTurnIdx++ },
                        enabled = currentTurnIdx < podcast.turns.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Turn", tint = TextPrimary)
                    }
                }

                // Speed toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NearBlackBg,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.clickable {
                        speechRate = when (speechRate) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            else -> 1.0f
                        }
                    }
                ) {
                    Text(
                        text = "${speechRate}x Speed",
                        color = TealAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive 2-Host Dialogue Transcript
            Text(
                text = "2-HOST GROUNDED TRANSCRIPT (TAP ANY TURN TO JUMP):",
                style = MaterialTheme.typography.labelSmall,
                color = TealAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                podcast.turns.forEachIndexed { idx, turn ->
                    val isCurrent = idx == currentTurnIdx
                    val isAlex = turn.speaker == "Alex"
                    val speakerColor = if (isAlex) TealAccent else CyberGold

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) speakerColor.copy(alpha = 0.12f) else NearBlackBg,
                        border = BorderStroke(1.dp, if (isCurrent) speakerColor else SubtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentTurnIdx = idx
                                isPlaying = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = speakerColor.copy(alpha = 0.2f),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = turn.speaker.take(1),
                                                color = speakerColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${turn.speaker} (${turn.roleLabel})",
                                        color = speakerColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = turn.sourceCitation,
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive "Join In / Ask the Hosts" Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NearBlackBg,
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "INTERACTIVE CALL-IN • ASK HOSTS ALEX & ZOYA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = listenerQuestion,
                            onValueChange = { listenerQuestion = it },
                            placeholder = { Text("Ask a question about this topic…", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (listenerQuestion.isNotBlank()) {
                                    val prevSize = podcast.turns.size
                                    onAskQuestion(listenerQuestion)
                                    listenerQuestion = ""
                                    currentTurnIdx = prevSize
                                    isPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ask", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookLmVideoExplainerCard(
    video: NotebookLmVideoExplainer,
    ttsEngine: TextToSpeech?,
    ttsReady: Boolean
) {
    var currentSceneIdx by remember(video.videoId) { mutableIntStateOf(0) }
    var isPlayingVideo by remember(video.videoId) { mutableStateOf(false) }

    val currentScene = video.scenes.getOrNull(currentSceneIdx) ?: video.scenes.first()

    val infiniteTransition = rememberInfiniteTransition(label = "video_canvas_anim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePhase"
    )

    LaunchedEffect(isPlayingVideo, currentSceneIdx, video.videoId) {
        if (isPlayingVideo) {
            if (ttsReady && ttsEngine != null) {
                runCatching {
                    ttsEngine.setPitch(1.05f)
                    ttsEngine.setSpeechRate(1.02f)
                    ttsEngine.speak(currentScene.narrationScript, TextToSpeech.QUEUE_FLUSH, null, "scene_${currentScene.sceneNumber}")
                }
            }
            delay(currentScene.durationSeconds * 450L)
            if (isPlayingVideo) {
                if (currentSceneIdx < video.scenes.lastIndex) {
                    currentSceneIdx++
                } else {
                    isPlayingVideo = false
                }
            }
        } else {
            runCatching { ttsEngine?.stop() }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TealAccentSubtle
                ) {
                    Text(
                        text = "NOTEBOOKLM VIDEO EXPLAINER • SCENE ${currentScene.sceneNumber}/${video.scenes.size}",
                        color = TealAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = currentScene.sourceCitation,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Visual Storyboard Canvas Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D1B2A), Color(0xFF091018))
                        )
                    )
                    .border(1.dp, TealAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Subtle mathematical grid lines
                    val step = 48f
                    var x = 0f
                    while (x < w) {
                        drawLine(
                            color = Color(0xFF14B8A6).copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(
                            color = Color(0xFF14B8A6).copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }

                    // Dynamic geometric accent ring per scene
                    val ringColor = when (currentScene.visualMode) {
                        "TRAP_VS_TRUTH" -> Color(0xFFF59E0B)
                        "SYNTHESIS_BADGE" -> Color(0xFF10B981)
                        else -> Color(0xFF14B8A6)
                    }
                    drawCircle(
                        color = ringColor.copy(alpha = 0.14f),
                        radius = (h * 0.36f) * pulsePhase,
                        center = Offset(w * 0.85f, h * 0.3f)
                    )
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f),
                        radius = (h * 0.26f) * pulsePhase,
                        center = Offset(w * 0.85f, h * 0.3f),
                        style = Stroke(width = 3f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = currentScene.title.uppercase(),
                            color = TealAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentScene.visualHeadline,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackBg.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentScene.visualFormulaOrKeyPoint,
                            color = CyberGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        currentScene.bulletCallouts.take(2).forEach { bullet ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("▸ ", color = StepCorrect, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = bullet,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scene Scrubber Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                video.scenes.forEachIndexed { idx, scene ->
                    val selected = idx == currentSceneIdx
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) TealAccent else NearBlackBg,
                        border = BorderStroke(1.dp, if (selected) TealAccent else SubtleBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentSceneIdx = idx }
                    ) {
                        Text(
                            text = "S${scene.sceneNumber}",
                            color = if (selected) NearBlackBg else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Video Player Controls & Voiceover Caption
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { isPlayingVideo = !isPlayingVideo },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("video_explainer_play_button")
                ) {
                    Icon(
                        if (isPlayingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isPlayingVideo) "Pause Explainer" else "Play Video Explainer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Row {
                    IconButton(
                        onClick = { if (currentSceneIdx > 0) currentSceneIdx-- },
                        enabled = currentSceneIdx > 0
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous Scene", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = { if (currentSceneIdx < video.scenes.lastIndex) currentSceneIdx++ },
                        enabled = currentSceneIdx < video.scenes.lastIndex
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Scene", tint = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NearBlackBg,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "SYNCHRONIZED VOICEOVER CAPTION:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentScene.narrationScript,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookLmTopicQuizCard(
    topic: K12Topic,
    quizItems: List<NotebookLmQuizItem>,
    onAnswerSubmitted: (NotebookLmQuizItem, Int) -> Unit
) {
    var currentQIdx by remember(topic.topicId) { mutableIntStateOf(0) }
    var selectedAnswers by remember(topic.topicId) { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val currentQuiz = quizItems.getOrNull(currentQIdx) ?: return
    val chosenIdx = selectedAnswers[currentQuiz.id]
    val answeredCount = selectedAnswers.size
    val correctCount = quizItems.count { q -> selectedAnswers[q.id] == q.correctIndex }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyberGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "NOTEBOOKLM QUIZ • Q${currentQIdx + 1}/${quizItems.size} • ${currentQuiz.bloomLevel}",
                        color = CyberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "Score: $correctCount/$answeredCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = StepCorrect,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = currentQuiz.question,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grounded in ${currentQuiz.sourceCitation}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            currentQuiz.options.forEachIndexed { optIdx, optText ->
                val isSelected = chosenIdx == optIdx
                val isCorrectOption = optIdx == currentQuiz.correctIndex
                val borderColor = when {
                    chosenIdx == null -> SubtleBorder
                    isCorrectOption -> StepCorrect
                    isSelected -> StepIssue
                    else -> SubtleBorder
                }
                val bgColor = when {
                    chosenIdx == null -> NearBlackBg
                    isCorrectOption -> StepCorrect.copy(alpha = 0.12f)
                    isSelected -> StepIssue.copy(alpha = 0.12f)
                    else -> NearBlackBg
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = chosenIdx == null) {
                            selectedAnswers = selectedAnswers + (currentQuiz.id to optIdx)
                            onAnswerSubmitted(currentQuiz, optIdx)
                        }
                        .testTag("notebooklm_quiz_option_$optIdx")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NearBlackElevated,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ('A' + optIdx).toString(),
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = optText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (chosenIdx != null) {
                val isCorrect = chosenIdx == currentQuiz.correctIndex
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) StepCorrect.copy(alpha = 0.1f) else StepIssue.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isCorrect) StepCorrect else StepIssue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCorrect) "CORRECT • VERIFIED BY NOTEBOOKLM" else "REVIEW GROUNDED SOURCE",
                            color = if (isCorrect) StepCorrect else StepIssue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentQuiz.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (currentQIdx > 0) currentQIdx-- },
                    enabled = currentQIdx > 0
                ) {
                    Text("Previous", fontSize = 12.sp)
                }

                Button(
                    onClick = { if (currentQIdx < quizItems.lastIndex) currentQIdx++ },
                    enabled = currentQIdx < quizItems.lastIndex,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
                ) {
                    Text("Next Question", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TransferChallengeCard(
    task: com.example.core.model.TransferTask,
    feedback: String?,
    onSubmit: (Int) -> Unit
) {
    var selectedOption by remember(task.taskId) { mutableIntStateOf(-1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyberGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "TRANSFER CHALLENGE • ${task.bloomLevel}",
                        color = CyberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "Min. Score: ${(task.thresholdScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = task.scenarioContext,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            task.solutionOptions.forEachIndexed { optIdx, optText ->
                val isSelected = selectedOption == optIdx
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) TealAccentSubtle else NearBlackBg,
                    border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedOption = optIdx }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) TealAccent else NearBlackElevated,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ('A' + optIdx).toString(),
                                    color = if (isSelected) NearBlackBg else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = optText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (selectedOption != -1) {
                        onSubmit(selectedOption)
                    }
                },
                enabled = selectedOption != -1,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
            ) {
                Text("Submit Transfer Solution", fontWeight = FontWeight.Bold)
            }

            feedback?.let { msg ->
                val isSuccess = msg.contains("TRANSFER VALIDATED")
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSuccess) StepCorrect.copy(alpha = 0.1f) else StepIssue.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isSuccess) StepCorrect else StepIssue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) StepCorrect else StepIssue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookLmStudentNotesPdfCard(
    bundle: com.example.domain.notebooklm.NotebookLmTopicBundle,
    onExportPdf: () -> Unit,
    onNavigateToTopic: (String) -> Unit
) {
    val topic = bundle.topic
    val notes = bundle.studentNotesDoc
    val revealedAnswers = remember(topic.topicId) { mutableStateMapOf<Int, Boolean>() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notebooklm_student_notes_pdf_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header with Title & Quick Export Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TealAccentSubtle
                    ) {
                        Text(
                            text = "NOORIX STUDY PACK • STUDENT REVISION NOTES",
                            color = TealAccent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${topic.gradeName} • ${topic.subjectName} • ${notes.syllabusCode.ifBlank { "Standard Syllabus" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberGold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onExportPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_export_pdf_top")
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = "Export PDF",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Knowledge Graph Connections
            if (notes.prerequisites.isNotEmpty() || notes.successors.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NearBlackBg,
                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Knowledge Graph",
                                tint = TealAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KNOWLEDGE GRAPH TOPOLOGY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        if (notes.prerequisites.isNotEmpty()) {
                            Text(
                                text = "PREREQUISITE FOUNDATIONS (TAP TO OPEN):",
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                notes.prerequisites.forEach { prereq ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NearBlackElevated,
                                        border = BorderStroke(1.dp, SubtleBorder),
                                        modifier = Modifier.clickable { onNavigateToTopic(prereq) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.West,
                                                contentDescription = null,
                                                tint = CyberGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = prereq.substringAfterLast(":"),
                                                color = TextPrimary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (notes.successors.isNotEmpty()) {
                            Text(
                                text = "ADVANCED CONCEPTS UNLOCKED (TAP TO ADVANCE):",
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                notes.successors.forEach { succ ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NearBlackElevated,
                                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable { onNavigateToTopic(succ) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = succ.substringAfterLast(":"),
                                                color = TealAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.East,
                                                contentDescription = null,
                                                tint = TealAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Syllabus Core Principles
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1. SYLLABUS CORE PRINCIPLES & THEORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                notes.corePrinciples.forEach { principle ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "•",
                                color = TealAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = principle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 2. Essential Formulas & Rules
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2. ESSENTIAL FORMULAS & GOVERNING EQUATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NearBlackBg,
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (notes.keyFormulas.isEmpty()) {
                            Text(
                                text = "• Governing Principle: ${topic.summary}",
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                fontSize = 11.5.sp
                            )
                        } else {
                            notes.keyFormulas.forEach { formula ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Functions,
                                        contentDescription = null,
                                        tint = CyberGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formula,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Step-by-Step Guided Model Solution
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "3. GUIDED WORKED MODEL PROBLEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = StepCorrect,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NearBlackBg,
                    border = BorderStroke(1.dp, StepCorrect.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = notes.workedExample,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // 4. Examiner Trap Alert
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "4. EXAMINER TRAP ALERT • MISCONCEPTION SHIELD",
                    style = MaterialTheme.typography.labelSmall,
                    color = StepIssue,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StepIssue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, StepIssue.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Examiner Warning",
                            tint = StepIssue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = notes.commonMisconception,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 5. Diagnostic Self-Assessment Review
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "5. DIAGNOSTIC MASTERY SELF-ASSESSMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                notes.reviewQuestions.forEachIndexed { qIdx, item ->
                    val isRevealed = revealedAnswers[qIdx] == true
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "QUESTION ${qIdx + 1} • ${item.bloomLevel}",
                                    color = CyberGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { revealedAnswers[qIdx] = !isRevealed },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (isRevealed) "Hide Solution" else "Reveal Solution",
                                        color = TealAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = item.question,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            item.options.forEachIndexed { oIdx, opt ->
                                val isCorrect = oIdx == item.correctIndex
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + oIdx)}.",
                                        color = if (isRevealed && isCorrect) StepCorrect else TextMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = opt,
                                        color = if (isRevealed && isCorrect) StepCorrect else TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isRevealed && isCorrect) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            if (isRevealed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StepCorrect.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "✓ ${item.explanation}",
                                        color = StepCorrect,
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Bottom Action: Full PDF Export & Print
            Button(
                onClick = onExportPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_export_pdf_bottom"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GENERATE & EXPORT PRINTABLE PDF STUDY PACK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Standard A4 Document • Open in Drive, Acrobat, AirPrint, or Share",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        color = NearBlackBg.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
