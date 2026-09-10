package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.MediaArtifact
import com.example.core.model.TransferTask
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

@Composable
fun MediaTransferScreen(viewModel: OLevelViewModel) {
    val artifact by viewModel.currentMediaArtifact.collectAsState()
    val transferTask by viewModel.currentTransferTask.collectAsState()
    val feedback by viewModel.transferFeedback.collectAsState()
    val mediaRecord by viewModel.mediaRecord.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }
    var audioProgress by remember { mutableFloatStateOf(0.85f) }
    var selectedOption by remember { mutableIntStateOf(-1) }
    var showTranscript by remember { mutableStateOf(false) }

    // Auto-generate media if none is loaded yet
    LaunchedEffect(Unit) {
        if (artifact == null) {
            viewModel.generateMedia("concept:4024:factorisation")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Zoya Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_zoya_science),
                            contentDescription = "Zoya AI Sciences Tutor",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, StepCorrect, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "REVISION BITES • PRESENTED BY ZOYA",
                                style = MaterialTheme.typography.labelSmall,
                                color = StepCorrect,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Audio Walkthrough & Transfer Lab",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Listen to curriculum-grounded revision audio briefs, then prove conceptual mastery by solving a novel transfer problem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Audio Player Card
        artifact?.let { media ->
            item {
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
                                    text = "AUDIO BRIEF",
                                    color = StepCorrect,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = "${media.audioDurationSeconds / 60} mins",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = media.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Audio Wave Visualizer Simulation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barHeights = listOf(0.4f, 0.7f, 0.3f, 0.9f, 0.6f, 1.0f, 0.8f, 0.5f, 0.9f, 0.4f, 0.7f, 0.85f, 0.6f, 0.95f, 0.5f, 0.3f)
                            barHeights.forEachIndexed { idx, h ->
                                val active = (idx.toFloat() / barHeights.size) <= audioProgress
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(if (isPlaying) h else (h * 0.5f).coerceAtLeast(0.2f))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (active) StepCorrect else SubtleBorder)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Player controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StepCorrect,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable { isPlaying = !isPlaying }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = NearBlackBg,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isPlaying) "Playing audio..." else "Audio ready",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(audioProgress * 100).toInt()}% completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            TextButton(onClick = { showTranscript = !showTranscript }) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showTranscript) "Hide Script" else "Read Script", color = StepCorrect, fontSize = 12.sp)
                            }
                        }

                        // Transcript Reader
                        AnimatedVisibility(visible = showTranscript) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("AUDIO SCRIPT TRANSCRIPT:", style = MaterialTheme.typography.labelSmall, color = StepCorrect, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    media.scriptSegments.forEach { line ->
                                        Text(text = line, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Transfer Task Challenge
        transferTask?.let { task ->
            item {
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
                                    viewModel.submitTransferTask(task, selectedOption, audioProgress.toDouble())
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
        }
    }
}
