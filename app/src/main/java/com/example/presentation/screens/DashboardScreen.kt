package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.RecalibrationAttribution
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.NoorixSegmentedProgressBar
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*

@Composable
fun DashboardScreen(viewModel: OLevelViewModel) {
    val activePkg by viewModel.activePackage.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()
    val events by viewModel.eventsList.collectAsState()
    val userAccount by viewModel.userAccount.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()

    val courseTitle = activePkg?.manifest?.subjectName ?: "Cambridge O-Level"
    val latestConcept = studentModel.academicStates.values.maxByOrNull { it.lastAssessedPhysicalMs }
    val targetConceptName = latestConcept?.conceptName?.ifEmpty { "Algebraic Factorisation" } ?: "Algebraic Factorisation"
    val averageMastery = if (studentModel.academicStates.isNotEmpty()) {
        studentModel.academicStates.values.map { it.effectiveMastery }.average()
    } else 0.68
    val candidateName = userAccount?.displayName ?: studentModel.studentName

    var showRecalibrationHistory by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Candidate Hero & Course Progress Card (Noorix Cyberpunk Style)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_continue_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_noorix_circuit_emblem),
                                contentDescription = "Noorix Emblem",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, TealAccent, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "WELCOME BACK, ${candidateName.uppercase()}! 👋",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealAccent,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Continue your learning journey",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NearBlackSurface,
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "${studentModel.profileCalibration.studentGrade}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "\"The expert in anything was once a beginner.\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4-Pillar Quick Telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniStatBadge("Focus", targetConceptName.take(12), TealAccent, Modifier.weight(1f))
                        MiniStatBadge("Target", "${studentModel.profileCalibration.targetGrade}", CyberGold, Modifier.weight(1f))
                        MiniStatBadge("Readiness", "${(averageMastery * 100).toInt()}%", StepCorrect, Modifier.weight(1f))
                        MiniStatBadge("Streak", "12 Days", CyanAccent, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    NoorixSegmentedProgressBar(
                        progress = averageMastery.toFloat(),
                        totalSegments = 16,
                        activeColor = TealAccent,
                        inactiveColor = SubtleBorder
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setTab(AppNavigationTab.SYMBOLIC_TUTOR) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Math Lab", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setTab(AppNavigationTab.DIAGNOSTIC) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SubtleBorderActive),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Quiz", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 2. Continuous Closed-Loop Calibration Engine HUD Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StepCorrect))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONTINUOUS CALIBRATION ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = StepCorrect,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = if (showRecalibrationHistory) "Hide Logs" else "View History (${studentModel.recalibrationHistory.size})",
                            color = TealAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showRecalibrationHistory = !showRecalibrationHistory }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val latestRecal = studentModel.recalibrationHistory.lastOrNull()
                    val attributionText = when (latestRecal?.attribution) {
                        RecalibrationAttribution.CONTENT_DEFICIENCY -> "Strategy Adapted: Content Scaffolding (Plan Error)"
                        RecalibrationAttribution.DIAGNOSTIC_STATE_MISESTIMATION -> "Prerequisite Repair Scheduled (Assessment Error)"
                        RecalibrationAttribution.ACCELERATED_MASTERY -> "Accelerated: Progression Boosted"
                        RecalibrationAttribution.PACING_FATIGUE -> "Pacing Notice: Micro-learning Active"
                        else -> "Benchmark: Learning Tracking Diagnostic Baseline"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackElevated,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Closed-Loop Status:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("ACTIVE & SYNCHRONIZED", color = StepCorrect, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Baseline Velocity Prior:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("+${String.format("%.2f", studentModel.profileCalibration.baselineVelocity)} mastery/session", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Active Intervention:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("${activePlan?.interventionType ?: "WORKED_EXAMPLES"}", color = CyberGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = attributionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showRecalibrationHistory) {
                        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (studentModel.recalibrationHistory.isEmpty()) {
                                Text(
                                    text = "No divergence detected yet. Closed-loop calibration benchmark is running continuously.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            } else {
                                studentModel.recalibrationHistory.takeLast(3).reversed().forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = NearBlackBg,
                                        border = BorderStroke(1.dp, SubtleBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(item.triggerSource, color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                Text(item.attribution.name, color = if (item.attribution == RecalibrationAttribution.ON_TRACK) StepCorrect else CyberGold, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                            }
                                            Text(item.diagnosisNote, color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. AI Learning Agents (Full Noorix Roster)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI LEARNING AGENTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "View all agents →",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.setTab(AppNavigationTab.AI_AGENTS) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val agentList = listOf(
                AgentCardData("danish", "DANISH", "Mathematics", "Specializes in algebra & step proofs", R.drawable.img_danish_math, AppNavigationTab.SYMBOLIC_TUTOR, TealAccent, "Algebra"),
                AgentCardData("zoya", "ZOYA", "Sciences", "Expert in Physics & concept models", R.drawable.img_zoya_science, AppNavigationTab.MEDIA_TRANSFER, StepCorrect, "Physics"),
                AgentCardData("ayesha", "AYESHA", "Language", "Reading, summary & English essays", R.drawable.img_ayesha_language, AppNavigationTab.LEARNING_HUB, Color(0xFF38BDF8), "English"),
                AgentCardData("bilal", "BILAL", "Aptitude", "Logical thinking & sequences", R.drawable.img_bilal_aptitude, AppNavigationTab.LEARNING_HUB, Color(0xFFA78BFA), "Reasoning"),
                AgentCardData("exam_coach", "EXAM COACH", "Exam Prep", "Past papers & exam strategies", R.drawable.img_exam_coach, AppNavigationTab.DIAGNOSTIC, CyberGold, "Mock Tests"),
                AgentCardData("career_guide", "CAREER GUIDE", "Advisor", "A-Level & university pathways", R.drawable.img_career_guide, AppNavigationTab.DASHBOARD, Color(0xFFF472B6), "Career Paths")
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(agentList) { agent ->
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .clickable {
                                viewModel.selectAgent(agent.id)
                                viewModel.setTab(agent.targetTab)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = agent.imageResId),
                                contentDescription = agent.name,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, agent.accentColor, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = agent.role,
                                style = MaterialTheme.typography.labelSmall,
                                color = agent.accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = agent.specialty,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = agent.accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = agent.tag,
                                    color = agent.accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Study Hub Grid Navigation
        item {
            Text(
                text = "STUDY MODULES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            val hubItems = listOf(
                HubCardItem("Math Lab", "Step verifier & OCR scanner", Icons.Default.Calculate, TealAccent, AppNavigationTab.SYMBOLIC_TUTOR),
                HubCardItem("Adaptive Practice", "IRT Diagnostic check", Icons.Default.Psychology, CyberGold, AppNavigationTab.DIAGNOSTIC),
                HubCardItem("Knowledge Hub", "Explainers, flashcards, quiz", Icons.Default.School, StepCorrect, AppNavigationTab.LEARNING_HUB),
                HubCardItem("Revision Bites", "Audio briefings & transfer", Icons.Default.Headphones, CyanAccent, AppNavigationTab.MEDIA_TRANSFER),
                HubCardItem("Memory Retention", "Decay curve & time travel", Icons.Default.Timeline, Color(0xFFA78BFA), AppNavigationTab.DECAY_SYNC),
                HubCardItem("Curriculum Library", "Signed Cambridge packages", Icons.Default.LibraryBooks, Color(0xFF38BDF8), AppNavigationTab.PACKAGES)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (chunk in hubItems.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (item in chunk) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setTab(item.tab) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                                border = BorderStroke(1.dp, SubtleBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = item.accentColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                item.icon,
                                                contentDescription = null,
                                                tint = item.accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Learning Telemetry Overview
        item {
            Text(
                text = "LEARNING TELEMETRY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Events Logged",
                    value = "${events.size}",
                    subtitle = "Immutable store",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Concepts",
                    value = "${studentModel.academicStates.size}",
                    subtitle = "Calibrated",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Misconceptions",
                    value = "${studentModel.activeMisconceptions.size}",
                    subtitle = if (studentModel.activeMisconceptions.isEmpty()) "Clear" else "To resolve",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniStatBadge(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackBg,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 9.sp)
            Text(value, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = TealAccent, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, fontSize = 10.sp)
        }
    }
}

private data class HubCardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val tab: AppNavigationTab
)

private data class AgentCardData(
    val id: String,
    val name: String,
    val role: String,
    val specialty: String,
    val imageResId: Int,
    val targetTab: AppNavigationTab,
    val accentColor: Color,
    val tag: String
)
