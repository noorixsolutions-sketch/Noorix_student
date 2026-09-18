package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.NoorixSegmentedProgressBar
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*

@Composable
fun DiagnosticScreen(viewModel: OLevelViewModel) {
    val session by viewModel.diagnosticSession.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()

    // Bank = active package for secondary learners, the built-in check-in bank
    // for younger bands (docs/two-mode-experience.md §3).
    val activePkg by viewModel.activePackage.collectAsState()
    val bankReady = remember(activePkg) { viewModel.diagnosticBankReady() }
    val nextQuestion = remember(session, bankReady) {
        if (bankReady) viewModel.getNextDiagnosticQuestion() else null
    }
    var selectedOption by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Header Card with Multi-Factor Calibration & Exam Coach
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
                            painter = painterResource(id = R.drawable.img_exam_coach),
                            contentDescription = "Exam Coach",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, CyberGold, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CHECK-IN • EXAM COACH",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "A few questions to see where you're strong",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Profile line — stage and target, never ability scores
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackSurface,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stage: ${studentModel.profileCalibration.studentGrade} (Age ${studentModel.profileCalibration.studentAge})",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (studentModel.profileCalibration.targetGrade.isNotBlank()) "Target ${studentModel.profileCalibration.targetGrade}" else "No marks here",
                                color = TealAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Questions adjust as you answer, so I can find the best place to work with you. There are no marks here — I'm figuring out where to start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress tracker — plain language, no confidence/ability percentages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Where we are", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = "Finding your starting point…",
                                fontWeight = FontWeight.Bold,
                                color = if (session.itemsAnswered.size >= 5) StepCorrect else TealAccent,
                                fontSize = 13.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NearBlackSurface,
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "Question ${session.itemsAnswered.size} / 6",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TealAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    NoorixSegmentedProgressBar(
                        progress = (session.itemsAnswered.size / 6f).coerceIn(0f, 1f),
                        totalSegments = 6,
                        activeColor = TealAccent,
                        inactiveColor = SubtleBorder
                    )
                }
            }
        }

        // 2. Active Question, Loading or Completed State
        if (!bankReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TealAccent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "I'm preparing your questions…",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "One moment — I want them to fit you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        } else if (session.isCompleted || nextQuestion == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostic_completed_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.5.dp, StepCorrect)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StepCorrect.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Celebration, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(30.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nice work! 🎉",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = StepCorrect
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Here's where we'll start. I'll keep adjusting as you learn.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Results breakdown
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Questions answered:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("${session.itemsAnswered.size} items", fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Where we'll build:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        if (session.identifiedGaps.isEmpty()) "Straight into new material" else "${session.identifiedGaps.size} small idea${if (session.identifiedGaps.size == 1) "" else "s"}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (session.identifiedGaps.isEmpty()) StepCorrect else CyberGold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("How we'll start:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        UiCopy.formatReplan(activePlan?.interventionType),
                                        fontWeight = FontWeight.Bold,
                                        color = StepCorrect,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        // Synthesized Plan Stage Overview
                        if (activePlan?.planStages?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NearBlackElevated,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "THE NEXT FEW STEPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StepCorrect,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    activePlan?.planStages?.take(3)?.forEach { stage ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = TealAccent, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stage.stageTitle, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.startDiagnostic()
                                    selectedOption = -1
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retake check-in", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.setTab(AppNavigationTab.PROGRAM) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SubtleBorderActive),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("See my plan", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostic_question_card"),
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
                                    text = nextQuestion.targetConceptId.substringAfterLast(":").uppercase(),
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = "No marks here",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = nextQuestion.prompt,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        nextQuestion.options.forEachIndexed { optIndex, optionText ->
                            val isSelected = selectedOption == optIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) TealAccentSubtle else NearBlackBg,
                                border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedOption = optIndex }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) TealAccent else NearBlackElevated,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = ('A' + optIndex).toString(),
                                                color = if (isSelected) NearBlackBg else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = optionText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (selectedOption != -1) {
                                    viewModel.submitDiagnosticItem(nextQuestion, selectedOption)
                                    selectedOption = -1
                                }
                            },
                            enabled = selectedOption != -1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_diagnostic_answer_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
                        ) {
                            Text("Submit Answer", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
