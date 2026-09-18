package com.example.presentation.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.GraphStage
import com.example.domain.onboarding.CurriculumStageMapper
import com.example.domain.onboarding.LearnerMode
import com.example.presentation.OLevelViewModel
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*

/**
 * Program — "See the larger journey" (docs/two-mode-experience.md §5).
 *
 * The active plan (focus, how we'll start, stages), the learner's position on
 * the curriculum knowledge-graph journey map (the master-branch Pre-K → O-Level
 * material), and — for O-Level learners — the exam horizon. In Explore mode the
 * screen is the invitation to build the plan.
 */
@Composable
fun ProgramScreen(viewModel: OLevelViewModel) {
    val onboarding by viewModel.onboardingState.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val kgStages by viewModel.kgStages.collectAsState()

    val mode = onboarding.mode ?: LearnerMode.EXPLORE
    val learnerStageOrder = onboarding.kgStageOrder
    val learnerAge = onboarding.age
    val learnerStage: GraphStage? = kgStages.firstOrNull { it.order == learnerStageOrder }
    val teachableStages = kgStages.filter { it.order in 1..4 }
    val futureStages = kgStages.filter { it.order > 4 }
    val isSecondary = learnerAge != null && learnerAge >= 12

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Header
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
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Route, contentDescription = null, tint = CyberGold, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "YOUR PROGRAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = learnerStage?.name?.substringAfter(" ")
                                    ?: CurriculumStageMapper.stageNameFor(learnerStageOrder),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (onboarding.stage.isNotBlank()) "You're in ${onboarding.stage} — here's the road ahead." else "Here's the road ahead for your stage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Invitation (Explore mode, or guided but no plan yet)
        if (mode == LearnerMode.EXPLORE || activePlan == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.beginGuidedOnboarding() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (mode == LearnerMode.EXPLORE) "You're exploring for now." else "Your plan is waiting to be built.",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "A two-minute check-in and I'll point you the right way — then I'll keep track of the journey while you do the learning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.beginGuidedOnboarding() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Build my learning plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 3. The active plan
        activePlan?.let { plan ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "WHERE WE'RE HEADED",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = plan.targetConceptName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = UiCopy.formatReplan(plan.interventionType),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        if (plan.planStages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            plan.planStages.forEach { stageItem ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (stageItem.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (stageItem.isCompleted) StepCorrect else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stageItem.stageTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (stageItem.isCompleted) TextMuted else TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Exam horizon — O-Level learners only
        if (isSecondary && onboarding.targetGrade.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "YOUR EXAM HORIZON",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Aiming for", color = TextSecondary, fontSize = 13.sp)
                            Text(onboarding.targetGrade, color = CyberGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (onboarding.targetExamSession.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Exam session", color = TextSecondary, fontSize = 13.sp)
                                Text(onboarding.targetExamSession, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. The journey map — the learner's position on the Pre-K → O-Level KG
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "THE BIGGER PICTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    teachableStages.forEach { stage ->
                        val isCurrent = stage.order == learnerStageOrder
                        val isPast = stage.order < learnerStageOrder
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) TealAccentSubtle else NearBlackBg,
                            border = BorderStroke(1.dp, if (isCurrent) TealAccent else SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        isCurrent -> Icons.Default.Route
                                        isPast -> Icons.Default.CheckCircle
                                        else -> Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isCurrent -> TealAccent
                                        isPast -> StepCorrect
                                        else -> TextMuted
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stage.name.substringAfter(" "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) TextPrimary else TextSecondary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${stage.subjectCount} subjects",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // The learner's stage: its subjects from the master-branch library
                    learnerStage?.let { stage ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Subjects on your map right now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(stage.subjects.take(12)) { subject ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = NearBlackSurface,
                                            border = BorderStroke(1.dp, SubtleBorder)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                Text(
                                                    text = subject.name,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (!subject.syllabusCode.isNullOrEmpty()) {
                                                    Text(
                                                        text = subject.syllabusCode,
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (stage.subjects.size > 12) {
                                        item {
                                            Surface(shape = RoundedCornerShape(8.dp), color = NearBlackSurface) {
                                                Text(
                                                    text = "+${stage.subjects.size - 12} more",
                                                    color = TextMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (futureStages.isNotEmpty()) {
                        Text(
                            text = "Beyond O-Level — ${futureStages.joinToString(", ") { it.name.substringAfter(" ") }} — on the map, coming soon.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
