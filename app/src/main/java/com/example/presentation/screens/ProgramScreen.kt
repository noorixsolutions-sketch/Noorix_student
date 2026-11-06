package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.diagnostic.CapabilityQuestion
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

/**
 * Study Plan — Complete Dedicated Route.
 *
 * Includes:
 * 1. Study Plan Onboarding with an Age- and Class-calibrated Capability Test to accurately gauge student starting position.
 * 2. Interactive in-place Capability Test (never the app starting screen!).
 * 3. Complete personalized Study Plan Roadmap with domain proficiencies, target milestones, and daily schedule.
 */
@Composable
fun ProgramScreen(viewModel: OLevelViewModel) {
    val activeStudent by viewModel.activeRegisteredStudent.collectAsState()
    val capabilityQuestions by viewModel.capabilityQuestions.collectAsState()
    val capabilityEvaluation by viewModel.capabilityEvaluation.collectAsState()

    var isTestingMode by remember { mutableStateOf(false) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, Int>() }
    var selectedOptionIndex by remember { mutableIntStateOf(-1) }
    var hasCheckedAnswer by remember { mutableStateOf(false) }

    val hasDiagnostic = (activeStudent?.diagnosticTaken == true) || (capabilityEvaluation != null)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Top Header
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
                            color = CyberGold.copy(alpha = 0.12f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = "Study Plan",
                                    tint = CyberGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "STUDY PLAN & SYLLABUS ROADMAP",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = activeStudent?.name?.let { "$it's Learning Plan" } ?: "Your Study Plan",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackSurface,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${activeStudent?.studentClass ?: "Grade 9"} • Age ${activeStudent?.age ?: 14}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Reg: ${activeStudent?.id ?: "Assigned"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // CASE 1: Capability Testing Mode (In Progress)
        // =====================================================================
        if (isTestingMode && capabilityQuestions.isNotEmpty()) {
            item {
                val q = capabilityQuestions.getOrNull(currentQuestionIndex)
                if (q != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Progress & Domain Tag
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
                                        text = "QUESTION ${currentQuestionIndex + 1} OF ${capabilityQuestions.size}",
                                        color = CyberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TealAccentSubtle
                                ) {
                                    Text(
                                        text = "${q.domain} • ${q.topicName}",
                                        color = TealAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (currentQuestionIndex + 1).toFloat() / capabilityQuestions.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = CyberGold,
                                trackColor = NearBlackSurface
                            )

                            // Question Prompt
                            Text(
                                text = q.prompt,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            )

                            Divider(color = SubtleBorder)

                            // Options
                            q.options.forEachIndexed { optIdx, optText ->
                                val isSelected = selectedOptionIndex == optIdx
                                val isCorrectOption = optIdx == q.correctIndex
                                val containerColor = when {
                                    hasCheckedAnswer && isCorrectOption -> TealAccentSubtle
                                    hasCheckedAnswer && isSelected && !isCorrectOption -> Color.Red.copy(alpha = 0.15f)
                                    isSelected -> CyberGold.copy(alpha = 0.12f)
                                    else -> NearBlackSurface
                                }
                                val borderColor = when {
                                    hasCheckedAnswer && isCorrectOption -> TealAccent
                                    hasCheckedAnswer && isSelected && !isCorrectOption -> Color.Red.copy(alpha = 0.5f)
                                    isSelected -> CyberGold
                                    else -> SubtleBorder
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = containerColor,
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !hasCheckedAnswer) {
                                            selectedOptionIndex = optIdx
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) CyberGold else NearBlackBg,
                                            border = BorderStroke(1.dp, if (isSelected) CyberGold else SubtleBorder),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = ('A' + optIdx).toString(),
                                                    color = if (isSelected) NearBlackBg else TextSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = optText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Explanation if checked
                            if (hasCheckedAnswer) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = NearBlackSurface,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (selectedOptionIndex == q.correctIndex) "✓ Correct!" else "Explanation:",
                                            color = if (selectedOptionIndex == q.correctIndex) TealAccent else CyberGold,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = q.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action Button
                            if (!hasCheckedAnswer) {
                                Button(
                                    onClick = {
                                        if (selectedOptionIndex >= 0) {
                                            userAnswers[currentQuestionIndex] = selectedOptionIndex
                                            hasCheckedAnswer = true
                                        }
                                    },
                                    enabled = selectedOptionIndex >= 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg)
                                ) {
                                    Text("Check Answer", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                val isLast = currentQuestionIndex >= capabilityQuestions.size - 1
                                Button(
                                    onClick = {
                                        if (isLast) {
                                            viewModel.submitCapabilityTest(userAnswers)
                                            isTestingMode = false
                                        } else {
                                            currentQuestionIndex++
                                            selectedOptionIndex = -1
                                            hasCheckedAnswer = false
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg)
                                ) {
                                    Text(
                                        text = if (isLast) "Finish & Unlock Personalized Plan" else "Next Question",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // CASE 2: Study Plan Onboarding (Before taking Capability Test)
        // =====================================================================
        else if (!hasDiagnostic) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyberGold.copy(alpha = 0.12f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = CyberGold,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Calibrate Your Study Plan",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To build a realistic, high-impact study roadmap tailored to your exact position, take a 5-question capability check aligned with ${activeStudent?.studentClass ?: "your class"}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // What is evaluated
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NearBlackSurface, RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, SubtleBorder), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "WHAT WILL BE ASSESSED:",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold
                            )
                            AssessmentFeatureRow("Numbers & Integers", "Fundamental number sense and operations")
                            AssessmentFeatureRow("Algebraic Systems", "Expansion, linear equations & manipulation")
                            AssessmentFeatureRow("Geometry & Space", "Triangles, Pythagoras & angle relations")
                            AssessmentFeatureRow("Applied Problem Solving", "Word problems, ratios & reasoning")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.startCapabilityTestForActiveStudent()
                                isTestingMode = true
                                currentQuestionIndex = 0
                                userAnswers.clear()
                                selectedOptionIndex = -1
                                hasCheckedAnswer = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_capability_test_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg)
                        ) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Capability Test",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // CASE 3: Complete Study Plan (Diagnostic Taken)
        // =====================================================================
        else {
            val eval = capabilityEvaluation
            val scorePercent = eval?.scorePercent ?: activeStudent?.diagnosticScorePercent ?: 75.0
            val positionLevel = eval?.positionLevel ?: activeStudent?.diagnosticLevel?.ifBlank { "Proficient / Solid Baseline" } ?: "Proficient / Solid Baseline"
            val feedback = eval?.diagnosticSummary ?: activeStudent?.diagnosticFeedback?.ifBlank {
                "Demonstrates solid working grasp of key curriculum principles for ${activeStudent?.studentClass}. Regular practice on multi-step problems will lock in top grades."
            } ?: ""

            // 1. Current Position Gauge Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CURRENT POSITION GAUGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TealAccentSubtle
                            ) {
                                Text(
                                    text = "Score: ${scorePercent.toInt()}%",
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = positionLevel,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Domain Mastery Progress Bars
                        Text(
                            text = "COMPETENCY BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val domains = eval?.domainMasteries ?: mapOf(
                            "Numbers" to 0.9,
                            "Algebra" to (scorePercent / 100.0).coerceIn(0.4, 0.95),
                            "Geometry" to ((scorePercent - 10.0) / 100.0).coerceIn(0.3, 0.9),
                            "Applied Reasoning" to (scorePercent / 100.0).coerceIn(0.5, 0.85)
                        )

                        domains.forEach { (dom, mastery) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = dom, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    Text(text = "${(mastery * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = CyberGold, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { mastery.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (mastery >= 0.7) TealAccent else CyberGold,
                                    trackColor = NearBlackSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. Personalized Syllabus Roadmap Stages
            item {
                Text(
                    text = "SYLLABUS ROADMAP STAGES",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            val roadmapStages = eval?.roadmap ?: listOf(
                com.example.domain.diagnostic.StudyPlanRoadmapItem(
                    stageName = "Stage 1: Prerequisite Reinforcement",
                    focusDescription = "Consolidate foundational algebra, fraction laws & negative numbers.",
                    weeklyHours = 5,
                    targetMilestone = "85%+ accuracy on core arithmetic & algebraic manipulation.",
                    topics = listOf("Linear Equations", "Signed Integers", "Fractions & Percentages")
                ),
                com.example.domain.diagnostic.StudyPlanRoadmapItem(
                    stageName = "Stage 2: Core Syllabus Mastery",
                    focusDescription = "Structured progression through ${activeStudent?.studentClass ?: "Grade 9"} syllabus units.",
                    weeklyHours = 6,
                    targetMilestone = "Complete topical check-ins across Cambridge 4024 / 5054 topics.",
                    topics = listOf("Algebraic Expansion", "Pythagoras Theorem", "Coordinate Geometry", "Ratios & Rates")
                ),
                com.example.domain.diagnostic.StudyPlanRoadmapItem(
                    stageName = "Stage 3: Past Paper Synthesis & Timed Trials",
                    focusDescription = "Examiner problem solving, trap avoidance & timed full-length practice.",
                    weeklyHours = 6,
                    targetMilestone = "Distinction grade timing on past papers.",
                    topics = listOf("Paper 1 Speed Drills", "Paper 2 Structured Multi-step", "Examiner Trap Avoidance")
                )
            )

            items(roadmapStages) { stage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stage.stageName,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyberGold.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${stage.weeklyHours} hrs/week",
                                    color = CyberGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stage.focusDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Milestone: ${stage.targetMilestone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(stage.topics) { topic ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NearBlackSurface,
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Daily Targets & Schedule
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DAILY TARGETS & EXAM HORIZON",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TargetPill(
                                modifier = Modifier.weight(1f),
                                label = "DAILY TIME",
                                value = "45 mins",
                                sub = "Recommended pace"
                            )
                            TargetPill(
                                modifier = Modifier.weight(1f),
                                label = "PRACTICE GOAL",
                                value = "15 Questions",
                                sub = "Per week"
                            )
                            TargetPill(
                                modifier = Modifier.weight(1f),
                                label = "TARGET SESSION",
                                value = "May/June 2027",
                                sub = "Cambridge O-Level"
                            )
                        }
                    }
                }
            }

            // 4. Retake Capability Test Action
            item {
                OutlinedButton(
                    onClick = {
                        viewModel.startCapabilityTestForActiveStudent()
                        isTestingMode = true
                        currentQuestionIndex = 0
                        userAnswers.clear()
                        selectedOptionIndex = -1
                        hasCheckedAnswer = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retake Capability Test & Re-calibrate Gauge",
                        color = CyberGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentFeatureRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = TealAccent,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TargetPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NearBlackSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = CyberGold, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}
