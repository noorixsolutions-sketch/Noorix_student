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
import com.example.ui.theme.*

data class NoorixAgent(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val tag: String,
    val imageRes: Int,
    val accentColor: Color,
    val targetTab: AppNavigationTab,
    val promptStarter: String
)

@Composable
fun AiAgentsScreen(viewModel: OLevelViewModel) {
    val selectedAgentId by viewModel.selectedAgentId.collectAsState()

    val agents = listOf(
        NoorixAgent(
            id = "danish",
            name = "DANISH",
            role = "Mathematics",
            description = "Specializes in algebraic step-by-step problem solving, concept clarity & Cambridge Math 4024 mastery.",
            tag = "Algebra / 4024",
            imageRes = R.drawable.img_danish_math,
            accentColor = NeonCyan,
            targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
            promptStarter = "Let's factorise quadratic expressions and check each line for sign mistakes."
        ),
        NoorixAgent(
            id = "zoya",
            name = "ZOYA",
            role = "Sciences",
            description = "Expert in Physics (5054), Chemistry & Biology with concept visualization and audio revision bites.",
            tag = "Physics / 5054",
            imageRes = R.drawable.img_zoya_science,
            accentColor = CyberGreen,
            targetTab = AppNavigationTab.MEDIA_TRANSFER,
            promptStarter = "Listen to our high-yield audio breakdown on kinematics and Newton's laws."
        ),
        NoorixAgent(
            id = "exam_coach",
            name = "EXAM COACH",
            role = "Exam Preparation",
            description = "Guides you with past paper strategies, adaptive diagnostic quizzes & Cambridge scoring tips.",
            tag = "Mock Tests / IRT",
            imageRes = R.drawable.img_exam_coach,
            accentColor = CyberGold,
            targetTab = AppNavigationTab.DIAGNOSTIC,
            promptStarter = "Take a 5-minute smart diagnostic check to calibrate your Cambridge readiness."
        ),
        NoorixAgent(
            id = "ayesha",
            name = "AYESHA",
            role = "Language",
            description = "Enhances your reading comprehension, formal writing & Cambridge English (1123) communication skills.",
            tag = "English / 1123",
            imageRes = R.drawable.img_student_avatar,
            accentColor = Color(0xFF64B5F6),
            targetTab = AppNavigationTab.MEDIA_TRANSFER,
            promptStarter = "Review persuasive essays and vocabulary precision for Paper 1."
        ),
        NoorixAgent(
            id = "bilal",
            name = "BILAL",
            role = "Aptitude & Logic",
            description = "Builds quantitative reasoning, pattern sequences and structural problem solving for competitive exams.",
            tag = "Reasoning",
            imageRes = R.drawable.img_danish_math,
            accentColor = Color(0xFFBA68C8),
            targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
            promptStarter = "Solve geometric sequence patterns with formal proofs."
        ),
        NoorixAgent(
            id = "career_guide",
            name = "CAREER GUIDE",
            role = "Academic Advisor",
            description = "Helps you discover the right subject combinations and university paths based on your academic strengths.",
            tag = "Career Paths",
            imageRes = R.drawable.img_student_avatar,
            accentColor = Color(0xFFFF8A65),
            targetTab = AppNavigationTab.DASHBOARD,
            promptStarter = "Map your O-Level subjects to Engineering, Medicine, or Computing degrees."
        )
    )

    val currentAgent = agents.find { it.id == selectedAgentId } ?: agents[0]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // 1. Header Banner
        item {
            NoorixCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeonCyan.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = NeonCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NOORIX AI LEARNING AGENTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Your Personal AI Tutors",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Specialized AI agents trained on Cambridge syllabuses. Select a mentor to dive into interactive problem solving, step-by-step verification, and smart diagnostics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberTextSecondary
                    )
                }
            }
        }

        // 2. Active Selected Agent Spotlight Card
        item {
            NoorixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_agent_card"),
                borderColor = currentAgent.accentColor,
                borderWidth = 1.5.dp,
                containerColor = CyberPanelElevated
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = currentAgent.imageRes),
                            contentDescription = currentAgent.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, currentAgent.accentColor, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentAgent.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = currentAgent.accentColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen)
                                )
                            }
                            Text(
                                text = currentAgent.role,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = currentAgent.accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = currentAgent.tag,
                                    color = currentAgent.accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentAgent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Agent Advice Bubble
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberDarkBg,
                        border = BorderStroke(1.dp, currentAgent.accentColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💬", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "\"${currentAgent.promptStarter}\"",
                                fontSize = 12.sp,
                                color = CyberTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.setTab(currentAgent.targetTab) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = currentAgent.accentColor, contentColor = Color.Black)
                    ) {
                        Text(
                            text = when (currentAgent.id) {
                                "danish" -> "Launch Math Lab with Danish"
                                "zoya" -> "Listen to Zoya's Audio Bites"
                                "exam_coach" -> "Start Exam Coach Quiz"
                                else -> "Study with ${currentAgent.name}"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 3. Grid of all AI Agents (Image 2 carousel)
        item {
            Text(
                text = "SELECT AN AI AGENT",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(agents.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { agent ->
                    val isSelected = agent.id == selectedAgentId
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) agent.accentColor else CyberPanelBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.selectAgent(agent.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) agent.accentColor.copy(alpha = 0.1f) else CyberPanel
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                Image(
                                    painter = painterResource(id = agent.imageRes),
                                    contentDescription = agent.name,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, agent.accentColor, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen)
                                        .align(Alignment.BottomEnd)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = agent.name,
                                fontWeight = FontWeight.ExtraBold,
                                color = agent.accentColor,
                                fontSize = 13.sp
                            )

                            Text(
                                text = agent.role,
                                fontSize = 10.sp,
                                color = CyberTextSecondary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = agent.accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = agent.tag,
                                    fontSize = 9.sp,
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
    }
}
