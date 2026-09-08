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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.NoorixCard
import com.example.presentation.components.NoorixSegmentedProgressBar
import com.example.ui.theme.*

@Composable
fun DashboardScreen(viewModel: OLevelViewModel) {
    val activePkg by viewModel.activePackage.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // ==========================================
        // 1. Hero Greeting Banner (Image 2)
        // "Welcome back, Ali! 👋"
        // "Continue your learning journey"
        // "The expert in anything was once a beginner."
        // Glowing circular Noorix emblem on right
        // ==========================================
        item {
            NoorixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_hero_card"),
                borderColor = NeonCyan.copy(alpha = 0.5f),
                borderWidth = 1.2.dp,
                containerColor = CyberPanelElevated
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Welcome back, Ali! 👋",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Continue your learning journey",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "“The expert in anything was once a beginner.”",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberTextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Holographic Glowing Circular Noorix Logo
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = NeonCyan.copy(alpha = 0.12f),
                                modifier = Modifier.size(72.dp)
                            ) {}
                            Image(
                                painter = painterResource(id = R.drawable.img_noorix_logo),
                                contentDescription = "Noorix Hologram",
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, NeonCyan, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats strip: Courses Enrolled, Hours Learned, Quizzes Solved, Avg. Score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMetricPill(
                            icon = Icons.Default.MenuBook,
                            label = "Courses Enrolled",
                            value = "08",
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricPill(
                            icon = Icons.Default.Schedule,
                            label = "Hours Learned",
                            value = "52.4",
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricPill(
                            icon = Icons.Default.Quiz,
                            label = "Quizzes Solved",
                            value = "236",
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricPill(
                            icon = Icons.Default.TrendingUp,
                            label = "Avg. Score",
                            value = "87%",
                            modifier = Modifier.weight(1f),
                            valueColor = CyberGreen
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. AI LEARNING AGENTS Section (Image 2)
        // ==========================================
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
                    color = NeonCyan,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "View all agents →",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.setTab(AppNavigationTab.AI_AGENTS) }
                )
            }
        }

        item {
            val agentList = listOf(
                AgentCardData("danish", "DANISH", "Mathematics", "Specializes in algebra problem solving & math clarity", "Algebra", R.drawable.img_danish_math, NeonCyan, AppNavigationTab.SYMBOLIC_TUTOR),
                AgentCardData("zoya", "ZOYA", "Sciences", "Expert in Physics & Chemistry concept visualization", "Physics", R.drawable.img_zoya_science, CyberGreen, AppNavigationTab.MEDIA_TRANSFER),
                AgentCardData("exam_coach", "EXAM COACH", "Exam Preparation", "Guides you with past paper strategies & mock tests", "Mock Tests", R.drawable.img_exam_coach, CyberGold, AppNavigationTab.DIAGNOSTIC),
                AgentCardData("ayesha", "AYESHA", "Language", "Enhances reading, writing & communication skills", "English", R.drawable.img_student_avatar, Color(0xFF64B5F6), AppNavigationTab.MEDIA_TRANSFER)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(agentList) { agent ->
                    Card(
                        modifier = Modifier
                            .width(140.dp)
                            .border(1.dp, agent.accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.selectAgent(agent.id)
                                viewModel.setTab(agent.targetTab)
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberPanel)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                Image(
                                    painter = painterResource(id = agent.imageRes),
                                    contentDescription = agent.name,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, agent.accentColor, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = agent.name,
                                fontWeight = FontWeight.ExtraBold,
                                color = agent.accentColor,
                                fontSize = 11.sp
                            )
                            Text(
                                text = agent.role,
                                fontSize = 9.sp,
                                color = CyberTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = agent.accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = agent.tag,
                                    fontSize = 8.sp,
                                    color = agent.accentColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. Your Learning Path (Image 2 Right Panel)
        // Overall Progress 68% + Segmented Bar + Milestone + Weekly Goal
        // ==========================================
        item {
            NoorixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("learning_path_card"),
                borderColor = NeonCyan.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your Learning Path",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            )
                        }
                        Text(
                            text = "68%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Segmented glowing progress bar
                    NoorixSegmentedProgressBar(
                        progress = 0.68f,
                        totalSegments = 20,
                        activeColor = NeonCyan
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current Course item
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberDarkBg,
                        border = BorderStroke(1.dp, CyberPanelBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Course", fontSize = 10.sp, color = CyberTextMuted)
                                Text(
                                    text = "Advanced Mathematics (4024)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CyberTextPrimary
                                )
                                Text(
                                    text = "Chapter 5: Quadratic Factorisation & Roots",
                                    fontSize = 11.sp,
                                    color = CyberTextSecondary
                                )
                            }
                            Button(
                                onClick = { viewModel.setTab(AppNavigationTab.SYMBOLIC_TUTOR) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                            ) {
                                Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Next Milestone
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Next Milestone", fontSize = 10.sp, color = CyberTextMuted)
                            Text(
                                text = "Solve 20 practice problems (Due in 2 days)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Weekly Goal: 4 of 6 Completed (M T W T F S S)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weekly Goal: 4 of 6 Completed",
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("M" to true, "T" to true, "W" to true, "T" to true, "F" to false, "S" to false, "S" to false).forEach { (day, done) ->
                                Surface(
                                    shape = CircleShape,
                                    color = if (done) CyberGreen.copy(alpha = 0.2f) else CyberPanelBorder,
                                    border = if (done) BorderStroke(1.dp, CyberGreen) else null,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = day,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (done) CyberGreen else CyberTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. Continue Learning (Image 2 Bottom Section)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONTINUE LEARNING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "View all courses →",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CourseProgressCard(
                    title = "Advanced Mathematics",
                    subtitle = "Chapter 5: Differential Calculus & Factorisation",
                    progress = 0.68f,
                    accentColor = NeonCyan,
                    onAction = { viewModel.setTab(AppNavigationTab.SYMBOLIC_TUTOR) }
                )
                CourseProgressCard(
                    title = "Physics Fundamentals",
                    subtitle = "Motion in a Straight Line & Dynamics",
                    progress = 0.52f,
                    accentColor = CyberGreen,
                    onAction = { viewModel.setTab(AppNavigationTab.MEDIA_TRANSFER) }
                )
                CourseProgressCard(
                    title = "English Communication",
                    subtitle = "Effective Writing & Reading Comprehension",
                    progress = 0.75f,
                    accentColor = Color(0xFF64B5F6),
                    onAction = { viewModel.setTab(AppNavigationTab.MEDIA_TRANSFER) }
                )
            }
        }

        // ==========================================
        // 5. Bottom Noorix Motivation Banner (Image 2)
        // "Build skills. Gain knowledge. Achieve your goals with Noorix."
        // ==========================================
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CyberPanelElevated,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NeonCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.School, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Build skills. Gain knowledge.",
                                fontSize = 10.sp,
                                color = CyberTextSecondary
                            )
                            Text(
                                text = "Achieve your goals with Noorix.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CyberTextPrimary
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.setTab(AppNavigationTab.DIAGNOSTIC) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Explore Pathways →", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class AgentCardData(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val tag: String,
    val imageRes: Int,
    val accentColor: Color,
    val targetTab: AppNavigationTab
)

@Composable
fun StatMetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CyberTextPrimary
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberDarkBg,
        border = BorderStroke(1.dp, CyberPanelBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 8.sp,
                color = CyberTextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CourseProgressCard(
    title: String,
    subtitle: String,
    progress: Float,
    accentColor: Color,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberPanelBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberPanel)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CyberTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = CyberTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor,
                        trackColor = CyberPanelElevated
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberPanelElevated, contentColor = CyberTextPrimary),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Text("Continue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
