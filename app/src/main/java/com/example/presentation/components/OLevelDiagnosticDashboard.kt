package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.curriculum.LongTermMasteryAnalysis
import com.example.data.curriculum.toDomainTopic
import com.example.data.local.CurriculumInteractionEventEntity
import com.example.data.local.CurriculumTopicEntity
import com.example.data.local.OLevelSubjectMasteryAggregate
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*
import kotlin.math.max
import kotlin.math.min

/**
 * Diagnostic Dashboard View powered by Room SQL queries in [com.example.data.curriculum.CurriculumManager]
 * and immutable learning activity logs in [com.example.data.curriculum.EventLogger].
 *
 * Visualizes a student's progress across Cambridge O-Level subjects (`band:9-10`)
 * based on their topic mastery scores, highlights priority syllabus gaps, and displays
 * the tamper-evident learning activity trail for long-term mastery analysis.
 */
@Composable
fun OLevelDiagnosticDashboardView(
    viewModel: OLevelViewModel,
    modifier: Modifier = Modifier
) {
    val subjectMasteryList by viewModel.oLevelSubjectMastery.collectAsState()
    val priorityTopics by viewModel.oLevelPriorityTopics.collectAsState()
    val recentEvents by viewModel.curriculumInteractionEvents.collectAsState()
    val totalEventCount by viewModel.curriculumEventCount.collectAsState()
    val longTermAnalysis by viewModel.longTermMasteryAnalysis.collectAsState()

    var expandedSubjectName by remember { mutableStateOf<String?>(null) }

    val totalOLevelTopics = remember(subjectMasteryList) {
        subjectMasteryList.sumOf { it.totalTopics }
    }
    val totalMasteredTopics = remember(subjectMasteryList) {
        subjectMasteryList.sumOf { it.masteredTopics }
    }
    val totalDevelopingTopics = remember(subjectMasteryList) {
        subjectMasteryList.sumOf { it.developingTopics }
    }
    val totalEmergingTopics = remember(subjectMasteryList) {
        subjectMasteryList.sumOf { it.emergingTopics }
    }
    val overallOLevelMastery = remember(subjectMasteryList) {
        if (subjectMasteryList.isEmpty()) 0.0
        else {
            val totalWeighted = subjectMasteryList.sumOf { it.averageMastery * it.totalTopics }
            val count = subjectMasteryList.sumOf { it.totalTopics }.coerceAtLeast(1)
            totalWeighted / count
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("olevel_diagnostic_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. O-Level Mastery Overview & Readiness Gauge Card
        OLevelReadinessHeroCard(
            overallMastery = overallOLevelMastery,
            subjectCount = subjectMasteryList.size,
            totalTopics = totalOLevelTopics,
            masteredTopics = totalMasteredTopics,
            developingTopics = totalDevelopingTopics,
            emergingTopics = totalEmergingTopics,
            immutableEventCount = totalEventCount,
            isChainVerified = longTermAnalysis.isChainIntegrityVerified
        )

        // 2. O-Level Subject Progress Visualization Card (Room Query: getOLevelSubjectMasteryFlow)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("olevel_subject_mastery_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = "O-Level Subject Progress",
                                    tint = TealAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "O-LEVEL SUBJECT MASTERY DIAGNOSTIC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                            Text(
                                text = "Progress across Cambridge O-Level subjects (Room Index)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackSurface,
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Text(
                            text = "${subjectMasteryList.size} Subjects",
                            color = CyberGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (subjectMasteryList.isEmpty()) {
                    Text(
                        text = "Indexing O-Level subjects from CurriculumManager…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        subjectMasteryList.forEach { subjectAgg ->
                            val isExpanded = expandedSubjectName == subjectAgg.subjectName
                            val subjectTopics = remember(priorityTopics, subjectAgg.subjectName) {
                                priorityTopics.filter { it.subjectName == subjectAgg.subjectName }
                                    .sortedBy { it.sortOrder }
                            }
                            OLevelSubjectMasteryRowItem(
                                subject = subjectAgg,
                                topics = subjectTopics,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedSubjectName = if (isExpanded) null else subjectAgg.subjectName
                                },
                                onCalibrateTopicMastery = { topicId, score ->
                                    viewModel.recordTopicMasteryCalibration(
                                        topicId = topicId,
                                        masteryScore = score,
                                        note = "Diagnostic calibration on ${subjectAgg.subjectName}"
                                    )
                                },
                                onOpenTopicInStudio = { topicEntity ->
                                    viewModel.openNotebookLmStudioForTopic(topicEntity.toDomainTopic())
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Priority Syllabus Gaps Card (Room Query: getDiagnosticPriorityTopicsFlow)
        if (priorityTopics.isNotEmpty()) {
            PriorityDiagnosticGapsCard(
                priorityTopics = priorityTopics.take(4),
                onBoostTopicMastery = { topic ->
                    val boosted = min(0.95, max(0.35, topic.masteryScore + 0.22))
                    viewModel.recordTopicMasteryCalibration(
                        topicId = topic.topicId,
                        masteryScore = boosted,
                        note = "Targeted diagnostic gap practice for ${topic.title}"
                    )
                },
                onOpenStudio = { topic ->
                    viewModel.openNotebookLmStudioForTopic(topic.toDomainTopic())
                }
            )
        }

        // 4. Immutable Learning Activity Trail & Long-Term Mastery Analysis Card (EventLogger)
        EventLoggerActivityTrailCard(
            analysis = longTermAnalysis,
            recentEvents = recentEvents.take(8),
            totalEventCount = totalEventCount,
            formatTime = viewModel::formatTime
        )
    }
}

@Composable
private fun OLevelReadinessHeroCard(
    overallMastery: Double,
    subjectCount: Int,
    totalTopics: Int,
    masteredTopics: Int,
    developingTopics: Int,
    emergingTopics: Int,
    immutableEventCount: Int,
    isChainVerified: Boolean
) {
    val masteryPct = (overallMastery * 100.0).toInt().coerceIn(0, 100)
    val ringColor = when {
        masteryPct >= 80 -> StepCorrect
        masteryPct >= 55 -> TealAccent
        masteryPct > 0 -> CyberGold
        else -> TextSecondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("olevel_readiness_hero_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(84.dp)) {
                        drawArc(
                            color = SubtleBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * overallMastery.toFloat().coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$masteryPct%",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "O-LEVEL",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CAMBRIDGE O-LEVEL (GRADES 9–10)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            masteryPct >= 80 -> "Distinction Trajectory (A* / A)"
                            masteryPct >= 55 -> "Merit Trajectory — Strong Progress"
                            masteryPct > 0 -> "Building Core Syllabus Foundations"
                            else -> "Tap any subject below to calibrate mastery"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiagnosticMiniMetric("$subjectCount", "Subjects", TealAccent)
                        DiagnosticMiniMetric("$masteredTopics/$totalTopics", "Mastered", StepCorrect)
                        DiagnosticMiniMetric("${developingTopics + emergingTopics}", "In Progress", CyberGold)
                        DiagnosticMiniMetric("$immutableEventCount", "Logged Events", TealAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification & Trail Status Strip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NearBlackSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isChainVerified) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isChainVerified) StepCorrect else StepIssue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isChainVerified) {
                                "EventLogger Trail: Immutable SHA-256 Chain Verified ($immutableEventCount entries)"
                            } else {
                                "EventLogger Trail: Chain verification pending"
                            },
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "Room SQL Live",
                        color = TealAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMiniMetric(value: String, label: String, color: Color) {
    Column {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun OLevelSubjectMasteryRowItem(
    subject: OLevelSubjectMasteryAggregate,
    topics: List<CurriculumTopicEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCalibrateTopicMastery: (String, Double) -> Unit,
    onOpenTopicInStudio: (CurriculumTopicEntity) -> Unit
) {
    val barColor = when {
        subject.averageMastery >= 0.80 -> StepCorrect
        subject.averageMastery >= 0.55 -> TealAccent
        subject.averageMastery > 0.0 -> CyberGold
        else -> TextMuted
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NearBlackSurface,
        border = BorderStroke(1.dp, if (isExpanded) TealAccent.copy(alpha = 0.6f) else SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("olevel_subject_row_${subject.representativeSubjectId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = barColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = subjectIconFor(subject.iconName),
                                contentDescription = subject.subjectName,
                                tint = barColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject.subjectName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${subject.readinessLabel} • ${subject.assessedTopics}/${subject.totalTopics} topics assessed",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = barColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, barColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${subject.masteryPercent}%",
                            color = barColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse topics" else "Expand topics",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            NoorixSegmentedProgressBar(
                progress = subject.averageMastery.toFloat().coerceIn(0f, 1f),
                totalSegments = 16,
                activeColor = barColor,
                inactiveColor = SubtleBorder
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopicCountPill("Mastered: ${subject.masteredTopics}", StepCorrect)
                    TopicCountPill("Developing: ${subject.developingTopics}", TealAccent)
                    TopicCountPill("Emerging: ${subject.emergingTopics}", CyberGold)
                }
                Text(
                    text = "${subject.totalEvidenceCount} evidence logs",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = SubtleBorder)
                    Text(
                        text = "TOPIC MASTERY & INTERACTIVE CALIBRATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    topics.forEach { topic ->
                        val topicPct = (topic.masteryScore * 100.0).toInt().coerceIn(0, 100)
                        val topicColor = when {
                            topic.masteryScore >= 0.80 -> StepCorrect
                            topic.masteryScore >= 0.55 -> TealAccent
                            topic.masteryScore > 0.0 -> CyberGold
                            else -> TextMuted
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = topic.title,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${topic.gradeName} • Status: ${topic.masteryStatus} (${topic.evidenceCount} logs)",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$topicPct%",
                                        color = topicColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CalibrationChip(
                                        label = "Emerging (40%)",
                                        color = CyberGold,
                                        testTag = "calibrate_emerging_${topic.topicId}",
                                        onClick = { onCalibrateTopicMastery(topic.topicId, 0.40) }
                                    )
                                    CalibrationChip(
                                        label = "Developing (70%)",
                                        color = TealAccent,
                                        testTag = "calibrate_developing_${topic.topicId}",
                                        onClick = { onCalibrateTopicMastery(topic.topicId, 0.70) }
                                    )
                                    CalibrationChip(
                                        label = "Mastered (92%)",
                                        color = StepCorrect,
                                        testTag = "calibrate_mastered_${topic.topicId}",
                                        onClick = { onCalibrateTopicMastery(topic.topicId, 0.92) }
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = TealAccentSubtle,
                                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .clickable { onOpenTopicInStudio(topic) }
                                            .testTag("open_studio_${topic.topicId}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Podcasts,
                                                contentDescription = null,
                                                tint = TealAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Studio",
                                                color = TealAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
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
    }
}

@Composable
private fun TopicCountPill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CalibrationChip(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PriorityDiagnosticGapsCard(
    priorityTopics: List<CurriculumTopicEntity>,
    onBoostTopicMastery: (CurriculumTopicEntity) -> Unit,
    onOpenStudio: (CurriculumTopicEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("olevel_priority_gaps_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = CyberGold.copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = "Priority Syllabus Gaps",
                            tint = CyberGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "PRIORITY O-LEVEL FOCUS TOPICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Lowest-mastery syllabus topics queried from Room for targeted practice",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                priorityTopics.forEach { topic ->
                    val pct = (topic.masteryScore * 100.0).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topic.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${topic.subjectName} • Mastery: $pct%",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StepCorrect.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, StepCorrect.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clickable { onBoostTopicMastery(topic) }
                                        .testTag("practice_gap_${topic.topicId}")
                                ) {
                                    Text(
                                        text = "+Practice",
                                        color = StepCorrect,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TealAccentSubtle,
                                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable { onOpenStudio(topic) }
                                ) {
                                    Text(
                                        text = "Studio",
                                        color = TealAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun EventLoggerActivityTrailCard(
    analysis: LongTermMasteryAnalysis,
    recentEvents: List<CurriculumInteractionEventEntity>,
    totalEventCount: Int,
    formatTime: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_logger_trail_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = StepCorrect.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HistoryEdu,
                                contentDescription = "EventLogger Immutable Trail",
                                tint = StepCorrect,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EVENTLOGGER IMMUTABLE ACTIVITY TRAIL",
                            style = MaterialTheme.typography.labelSmall,
                            color = StepCorrect,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Long-term mastery analysis from append-only Room records",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (analysis.isChainIntegrityVerified) StepCorrect.copy(alpha = 0.15f) else StepIssue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (analysis.isChainIntegrityVerified) StepCorrect else StepIssue)
                ) {
                    Text(
                        text = if (analysis.isChainIntegrityVerified) "CHAIN OK" else "CHECK",
                        color = if (analysis.isChainIntegrityVerified) StepCorrect else StepIssue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Long-term Mastery Analysis Summary Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NearBlackSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val avgGainPct = (analysis.averageMasteryGainPerInteraction * 100.0).toInt()
                    val avgQuizPct = (analysis.overallAverageQuizAccuracy * 100.0).toInt()
                    val studyMins = max(0, analysis.totalStudyDurationSeconds / 60)

                    DiagnosticMiniMetric(
                        value = "$totalEventCount",
                        label = "Immutable Logs",
                        color = TealAccent
                    )
                    DiagnosticMiniMetric(
                        value = if (avgGainPct >= 0) "+$avgGainPct%" else "$avgGainPct%",
                        label = "Avg Mastery Δ",
                        color = StepCorrect
                    )
                    DiagnosticMiniMetric(
                        value = "$avgQuizPct%",
                        label = "Quiz Accuracy",
                        color = CyberGold
                    )
                    DiagnosticMiniMetric(
                        value = "${studyMins}m",
                        label = "Logged Study",
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentEvents.isEmpty()) {
                Text(
                    text = "No curriculum interactions recorded yet. Calibrate a topic above, explore a topic in NotebookLM Studio, or answer a diagnostic check-in question to append an immutable entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentEvents.forEach { evt ->
                        val beforePct = (evt.masteryBefore * 100.0).toInt()
                        val afterPct = (evt.masteryAfter * 100.0).toInt()
                        val deltaPct = afterPct - beforePct
                        val deltaSign = if (deltaPct >= 0) "+$deltaPct%" else "$deltaPct%"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "#${evt.sequenceNumber} • ${evt.interactionType}",
                                            color = TealAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = formatTime(evt.timestampMs),
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        text = "${evt.topicTitle} (${evt.subjectName})",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "SHA-256: ${evt.chainHash.take(16)}…",
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$beforePct% → $afterPct%",
                                        color = StepCorrect,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = deltaSign,
                                        color = if (deltaPct >= 0) StepCorrect else CyberGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
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

private fun subjectIconFor(iconName: String): ImageVector = when (iconName.lowercase()) {
    "calculate", "math", "mathematics" -> Icons.Default.Calculate
    "science", "physics" -> Icons.Default.Science
    "biotech", "chemistry" -> Icons.Default.Biotech
    "spa", "biology" -> Icons.Default.Eco
    "computer", "cs" -> Icons.Default.Computer
    else -> Icons.Default.School
}
