package com.example.presentation.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.onboarding.AgeBand
import com.example.domain.onboarding.LearnerMode
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

/**
 * Home — "Your learning journey" (docs/two-mode-experience.md §5).
 *
 * One place that says: here's where we are, here's what we're working on,
 * **Continue** — plus the three companion destinations (Explore / Progress /
 * Program) and the learning crew. The home never interrogates
 * ("what do you want to study?") and never commands ("you must study X"):
 * the Continue button is always one, and it comes from the plan, not a menu.
 */
@Composable
fun DashboardScreen(viewModel: OLevelViewModel) {
    val onboarding by viewModel.onboardingState.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val userAccount by viewModel.userAccount.collectAsState()
    val gamification by viewModel.gamificationSnapshot.collectAsState()

    val mode = onboarding.mode ?: LearnerMode.EXPLORE
    val band = onboarding.band
    val profile = onboarding.profile
    val displayName = (userAccount?.displayName ?: onboarding.learnerName.trim()).ifBlank { null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Greeting — age-adaptive line, no "WELCOME BACK, CANDIDATE!"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (displayName != null) "Hi, $displayName" else "Hi there",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = profile.greeting,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (mode == LearnerMode.GUIDED) TealAccentSubtle else NearBlackSurface,
                    border = BorderStroke(1.dp, if (mode == LearnerMode.GUIDED) TealAccent.copy(alpha = 0.4f) else SubtleBorder)
                ) {
                    Text(
                        text = if (mode == LearnerMode.GUIDED) " Guided" else " Explore",
                        color = if (mode == LearnerMode.GUIDED) TealAccent else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. The journey card — where we are / what we're working on / Continue
        item {
            val plan = activePlan
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_journey_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = when (mode) {
                            LearnerMode.GUIDED -> "YOUR LEARNING JOURNEY"
                            LearnerMode.EXPLORE -> "EXPLORE MODE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            mode == LearnerMode.GUIDED && plan != null -> "Here's where we are."
                            mode == LearnerMode.GUIDED -> "Your journey starts here."
                            else -> "Learn anything you like."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            mode == LearnerMode.GUIDED && plan != null -> {
                                val stages = plan.planStages
                                val done = stages.count { it.isCompleted }
                                if (stages.isNotEmpty()) {
                                    "We're working on ${plan.targetConceptName}. $done of ${stages.size} steps done — one at a time."
                                } else {
                                    "We're working on ${plan.targetConceptName}."
                                }
                            }
                            mode == LearnerMode.GUIDED ->
                                "Take a quick check-in and I'll shape your first plan. No marks — I'm just finding the best place to start."
                            else ->
                                "No plan, no pressure — ask a question, watch an explainer, or practice a skill."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )

                    if (gamification.streakDays > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = NearBlackBg) {
                            Text(
                                text = "🌟 You're on a ${gamification.streakDays}-day learning streak",
                                color = CyberGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            when {
                                mode == LearnerMode.GUIDED && plan != null ->
                                    viewModel.setTab(AppNavigationTab.LEARNING_HUB)
                                mode == LearnerMode.GUIDED -> viewModel.beginGuidedOnboarding()
                                else -> viewModel.setTab(AppNavigationTab.LEARNING_HUB)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                mode == LearnerMode.GUIDED && plan != null -> "Continue"
                                mode == LearnerMode.GUIDED -> "Start my journey"
                                else -> "Explore"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Explore mode → invitation to be guided (one tap, skippable forever)
        if (mode == LearnerMode.EXPLORE) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.beginGuidedOnboarding() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.35f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = CyberGold.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Route, contentDescription = null, tint = CyberGold, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Want me to guide you?", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("I'll build a learning plan with you and keep track of your progress.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Text("Build my learning plan →", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. The three companion destinations
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DestinationTile(
                    icon = Icons.Default.Explore,
                    title = "Explore",
                    subtitle = "Learn anything",
                    accent = TealAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTab(AppNavigationTab.LEARNING_HUB) }
                )
                DestinationTile(
                    icon = Icons.Default.Insights,
                    title = "Progress",
                    subtitle = "How you're growing",
                    accent = StepCorrect,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTab(AppNavigationTab.PROGRESS) }
                )
                DestinationTile(
                    icon = Icons.Default.Route,
                    title = "Program",
                    subtitle = "The bigger journey",
                    accent = CyberGold,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTab(AppNavigationTab.PROGRAM) }
                )
            }
        }

        // 5. The learning crew (tutors stay one tap away; full roster in Tutors)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ASK A TUTOR ANYTHING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "All tutors →",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.setTab(AppNavigationTab.AI_AGENTS) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // The exam coach deep-links to the O-Level check-in only for
                // secondary learners; younger bands go to chat instead.
                val examCoachTab = if (band == AgeBand.SECONDARY) AppNavigationTab.DIAGNOSTIC else AppNavigationTab.AI_AGENTS
                val agentList = listOf(
                    AgentStripData("danish", "DANISH", "Math", R.drawable.img_danish_math, AppNavigationTab.SYMBOLIC_TUTOR, TealAccent),
                    AgentStripData("zoya", "ZOYA", "Physics", R.drawable.img_zoya_science, AppNavigationTab.MEDIA_TRANSFER, StepCorrect),
                    AgentStripData("ayesha", "AYESHA", "English", R.drawable.img_ayesha_language, AppNavigationTab.LEARNING_HUB, Color(0xFF38BDF8)),
                    AgentStripData("bilal", "BILAL", "Reasoning", R.drawable.img_bilal_aptitude, AppNavigationTab.LEARNING_HUB, Color(0xFFA78BFA)),
                    AgentStripData("exam_coach", "EXAM COACH", "Exam Prep", R.drawable.img_exam_coach, examCoachTab, CyberGold),
                    AgentStripData("career_guide", "CAREER GUIDE", "Pathways", R.drawable.img_career_guide, AppNavigationTab.PROGRAM, Color(0xFFF472B6))
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(agentList) { agent ->
                        Card(
                            modifier = Modifier
                                .width(104.dp)
                                .clickable {
                                    viewModel.selectAgent(agent.id)
                                    viewModel.setTab(agent.targetTab)
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = agent.imageResId),
                                    contentDescription = agent.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, agent.accentColor, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = agent.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = agent.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = agent.accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. O-Level learners: a quiet check-in entry (never a command)
        if (band == AgeBand.SECONDARY) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = TealAccentSubtle, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = TealAccent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check-in", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "A few questions to see how your learning is holding up. No marks — I'm just keeping my map of you up to date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.setTab(AppNavigationTab.DIAGNOSTIC) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SubtleBorderActive),
                            colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Text("Check in", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

private data class AgentStripData(
    val id: String,
    val name: String,
    val role: String,
    val imageResId: Int,
    val targetTab: AppNavigationTab,
    val accentColor: Color
)
