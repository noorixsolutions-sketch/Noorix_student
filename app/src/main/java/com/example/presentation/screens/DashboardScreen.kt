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
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*

@Composable
fun DashboardScreen(viewModel: OLevelViewModel) {
    val activePkg by viewModel.activePackage.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Continue where you left off
        item {
            val courseTitle = activePkg?.manifest?.subjectName ?: "O-Level Studies"
            val targetConcept = studentModel.academicStates.values.maxByOrNull { it.lastAssessedPhysicalMs }?.conceptName ?: "Start your learning journey"
            val mastery = studentModel.academicStates.values.maxByOrNull { it.lastAssessedPhysicalMs }?.confidence ?: 0.0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONTINUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = UiCopy.formatCourseTitle("", courseTitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = targetConcept,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = UiCopy.formatMastery(mastery),
                            style = MaterialTheme.typography.labelMedium,
                            color = TealAccent
                        )
                        
                        Button(
                            onClick = { viewModel.setTab(AppNavigationTab.PACKAGES) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Resume", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tutors
        item {
            Text(
                text = "TUTORS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            val agentList = listOf(
                AgentCardData("danish", "Danish", "Math Tutor", R.drawable.img_danish_math, AppNavigationTab.SYMBOLIC_TUTOR),
                AgentCardData("zoya", "Zoya", "Sciences Tutor", R.drawable.img_zoya_science, AppNavigationTab.AI_AGENTS),
                AgentCardData("exam_coach", "Exam Coach", "Past Paper Strategy", R.drawable.img_exam_coach, AppNavigationTab.AI_AGENTS)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = agent.imageResId),
                                contentDescription = agent.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = agent.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = agent.role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Chat",
                                tint = TealAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AgentCardData(
    val id: String,
    val name: String,
    val role: String,
    val imageResId: Int,
    val targetTab: AppNavigationTab
)
