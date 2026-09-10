package com.example.presentation.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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

    val courseTitle = activePkg?.manifest?.subjectName ?: "Cambridge O-Level"
    val latestConcept = studentModel.academicStates.values.maxByOrNull { it.lastAssessedPhysicalMs }
    val targetConceptName = latestConcept?.conceptName?.ifEmpty { "Algebraic Factorisation" } ?: "Algebraic Factorisation"
    val averageMastery = if (studentModel.academicStates.isNotEmpty()) {
        studentModel.academicStates.values.map { it.effectiveMastery }.average()
    } else 0.68
    val candidateName = userAccount?.displayName ?: studentModel.studentName

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Candidate Hero & Course Progress Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_continue_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = TealAccentSubtle,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = TealAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CAMBRIDGE STUDY PATH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealAccent,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = candidateName,
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
                                text = UiCopy.formatCourseTitle("", courseTitle),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Current Focus: $targetConceptName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Calibrated Readiness: ${UiCopy.formatMastery(averageMastery)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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

        // 2. Study Hub Grid Navigation
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
                HubCardItem(
                    title = "Math Lab",
                    subtitle = "Step verifier & OCR scanner",
                    icon = Icons.Default.Calculate,
                    accentColor = TealAccent,
                    tab = AppNavigationTab.SYMBOLIC_TUTOR
                ),
                HubCardItem(
                    title = "Adaptive Practice",
                    subtitle = "IRT Diagnostic check",
                    icon = Icons.Default.Psychology,
                    accentColor = CyberGold,
                    tab = AppNavigationTab.DIAGNOSTIC
                ),
                HubCardItem(
                    title = "Knowledge Hub",
                    subtitle = "Explainers, flashcards, quiz",
                    icon = Icons.Default.School,
                    accentColor = StepCorrect,
                    tab = AppNavigationTab.LEARNING_HUB
                ),
                HubCardItem(
                    title = "Revision Bites",
                    subtitle = "Audio briefings & transfer",
                    icon = Icons.Default.Headphones,
                    accentColor = CyanAccent,
                    tab = AppNavigationTab.MEDIA_TRANSFER
                ),
                HubCardItem(
                    title = "Memory Retention",
                    subtitle = "Decay curve & time travel",
                    icon = Icons.Default.Timeline,
                    accentColor = Color(0xFFA78BFA),
                    tab = AppNavigationTab.DECAY_SYNC
                ),
                HubCardItem(
                    title = "Curriculum Library",
                    subtitle = "Signed Cambridge packages",
                    icon = Icons.Default.LibraryBooks,
                    accentColor = Color(0xFF38BDF8),
                    tab = AppNavigationTab.PACKAGES
                )
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

        // 3. AI Tutors Squad
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI TUTORS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.setTab(AppNavigationTab.AI_AGENTS) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val agentList = listOf(
                AgentCardData(
                    id = "danish",
                    name = "Danish",
                    role = "Mathematics (4024)",
                    specialty = "Algebra & Step Verifier",
                    imageResId = R.drawable.img_danish_math,
                    targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
                    accentColor = TealAccent
                ),
                AgentCardData(
                    id = "zoya",
                    name = "Zoya",
                    role = "Sciences (5054)",
                    specialty = "Physics & Conceptual Models",
                    imageResId = R.drawable.img_zoya_science,
                    targetTab = AppNavigationTab.MEDIA_TRANSFER,
                    accentColor = StepCorrect
                ),
                AgentCardData(
                    id = "exam_coach",
                    name = "Exam Coach",
                    role = "Past Paper Strategy",
                    specialty = "Mark Schemes & Timing",
                    imageResId = R.drawable.img_exam_coach,
                    targetTab = AppNavigationTab.DIAGNOSTIC,
                    accentColor = CyberGold
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                agentList.forEach { agent ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectAgent(agent.id)
                                viewModel.setTab(agent.targetTab)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = agent.imageResId),
                                contentDescription = agent.name,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, agent.accentColor, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = agent.accentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = agent.role,
                                            color = agent.accentColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = agent.specialty,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.selectAgent(agent.id)
                                    viewModel.setTab(AppNavigationTab.AI_AGENTS)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Chat",
                                    tint = agent.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Learning Analytics Overview
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
    val accentColor: Color
)
