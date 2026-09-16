package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AnalyticsSnapshot
import com.example.core.model.BadgeCatalog
import com.example.core.model.BadgeId
import com.example.core.model.ConceptMasteryRow
import com.example.core.model.GamificationSnapshot
import com.example.core.model.MasteryStatus
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.StepIssue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// ------------------------------------------------------------------
// XP + Level hero card
// ------------------------------------------------------------------

@Composable
fun XpLevelCard(snapshot: GamificationSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.35f))
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = TealAccent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LEVEL ${snapshot.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "${snapshot.totalXp} XP total",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StreakPill(streakDays = snapshot.streakDays)
            }

            Spacer(modifier = Modifier.height(12.dp))
            NoorixSegmentedProgressBar(
                progress = snapshot.levelProgress,
                totalSegments = 20,
                activeColor = TealAccent,
                inactiveColor = SubtleBorder
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${snapshot.xpIntoLevel}/${snapshot.xpForNextLevel} XP to Level ${snapshot.level + 1} • +${snapshot.todayXp} XP today",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun StreakPill(streakDays: Int, modifier: Modifier = Modifier) {
    val active = streakDays > 0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) CyberGold.copy(alpha = 0.15f) else NearBlackSurface,
        border = BorderStroke(1.dp, if (active) CyberGold.copy(alpha = 0.6f) else SubtleBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (active) CyberGold else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (active) "$streakDays day streak" else "No streak yet",
                color = if (active) CyberGold else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ------------------------------------------------------------------
// Daily goal ring + week stats
// ------------------------------------------------------------------

@Composable
fun DailyGoalCard(snapshot: GamificationSnapshot, analytics: AnalyticsSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DailyGoalRing(
                progress = snapshot.dailyGoalProgress,
                centerTop = "${snapshot.todayMinutesEstimated}m",
                centerBottom = "of ${snapshot.dailyTargetMinutes}m",
                goalMet = snapshot.dailyGoalMet
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAILY GOAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot.dailyGoalMet) StepCorrect else TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (snapshot.dailyGoalMet) "Goal smashed! 🎉" else "Keep going — every minute counts",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GoalMiniStat("${analytics.activeDaysThisWeek}/7", "active days")
                    GoalMiniStat("${analytics.accuracyPercent}%", "accuracy")
                    GoalMiniStat("${analytics.xpThisWeek}", "XP this week")
                }
            }
        }
    }
}

@Composable
private fun GoalMiniStat(value: String, label: String) {
    Column {
        Text(text = value, color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = label, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
fun DailyGoalRing(
    progress: Float,
    centerTop: String,
    centerBottom: String,
    goalMet: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = if (goalMet) StepCorrect else TealAccent
    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(88.dp)) {
            drawArc(
                color = SubtleBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerTop, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = centerBottom, color = TextMuted, fontSize = 9.sp)
        }
    }
}

// ------------------------------------------------------------------
// Badges row
// ------------------------------------------------------------------

@Composable
fun BadgesCard(earned: List<BadgeId>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    text = "ACHIEVEMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${earned.size}/${BadgeCatalog.all.size} unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGold,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeCatalog.all.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { def ->
                            BadgeChip(
                                title = def.title,
                                description = def.description,
                                earned = earned.contains(def.id),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(title: String, description: String, earned: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (earned) CyberGold.copy(alpha = 0.10f) else NearBlackBg,
        border = BorderStroke(1.dp, if (earned) CyberGold.copy(alpha = 0.55f) else SubtleBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (earned) CyberGold else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (earned) TextPrimary else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 2
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Weekly activity bar chart (Canvas — no new dependencies)
// ------------------------------------------------------------------

@Composable
fun WeeklyActivityCard(snapshot: AnalyticsSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    text = "WEEKLY ACTIVITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${snapshot.minutesThisWeekEstimated} min this week",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (snapshot.weeklyActivity.all { it.eventCount == 0 }) {
                Text(
                    text = "No study activity yet this week — your bars will grow here as you learn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                WeeklyBars(days = snapshot.weeklyActivity)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeekStatTile("Questions", "${snapshot.questionsCorrect}/${snapshot.questionsAnswered}", Modifier.weight(1f))
                WeekStatTile("Proofs ✓", "${snapshot.proofsVerified}", Modifier.weight(1f))
                WeekStatTile("Transfer ✓", "${snapshot.transferPassed}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeeklyBars(days: List<com.example.core.model.WeeklyActivityDay>) {
    val maxEvents = (days.maxOfOrNull { it.eventCount } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (day.eventCount > 0) {
                    Text(
                        text = "${day.xp}",
                        color = if (day.isToday) TealAccent else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val fraction = day.eventCount.toFloat() / maxEvents.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((18 + 72 * fraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (day.eventCount == 0) SubtleBorder.copy(alpha = 0.5f)
                            else if (day.isToday) TealAccent
                            else TealAccent.copy(alpha = 0.45f)
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = day.dayLabel,
                    color = if (day.isToday) TealAccent else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun WeekStatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackBg,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = TextMuted, fontSize = 9.sp)
            Text(text = value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ------------------------------------------------------------------
// Per-concept mastery breakdown
// ------------------------------------------------------------------

@Composable
fun ConceptMasteryCard(rows: List<ConceptMasteryRow>, averageMastery: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    text = "CONCEPT MASTERY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${(averageMastery * 100).toInt()}% avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Text(
                    text = "Finish a diagnostic or quiz to see per-concept mastery here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        ConceptMasteryRowView(row = row)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptMasteryRowView(row: ConceptMasteryRow) {
    val barColor = when (row.status) {
        MasteryStatus.MASTERED -> StepCorrect
        MasteryStatus.DEVELOPING -> TealAccent
        MasteryStatus.EMERGING -> CyberGold
        MasteryStatus.REGRESSED -> StepIssue
        else -> TextMuted
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.conceptName,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            MasteryStatusDot(status = row.status, color = barColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${(row.mastery * 100).toInt()}%",
                color = barColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NearBlackBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.mastery.toFloat().coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun MasteryStatusDot(status: MasteryStatus, color: Color) {
    val label = when (status) {
        MasteryStatus.MASTERED -> "Mastered"
        MasteryStatus.DEVELOPING -> "Developing"
        MasteryStatus.EMERGING -> "Emerging"
        MasteryStatus.REGRESSED -> "Needs review"
        MasteryStatus.UNCERTAIN -> "Uncertain"
        MasteryStatus.UNKNOWN -> "New"
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Small icon used by section headers across the new progress UI. */
@Composable
fun SectionHeaderIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
