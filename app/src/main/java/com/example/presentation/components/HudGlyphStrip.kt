package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AnalyticsSnapshot
import com.example.core.model.GamificationSnapshot
import com.example.core.model.MasteryStatus
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.StepIssue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Cybernetic HUD glyph strip — ADDITIVE (§8 Goal Architecture).
 *
 * The ambient layer: Today Bar + Streak Flame + Subject Dots, rendered
 * beneath the top bar in main contexts. Glyph selection is rule-based v1
 * (the goal's TFLite Glyph Picker can replace [pickGlyphs] later without
 * touching call sites).
 */
@Composable
fun HudGlyphStrip(
    gamification: GamificationSnapshot,
    analytics: AnalyticsSnapshot,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NearBlackBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TodayBarGlyph(
                progress = gamification.dailyGoalProgress,
                label = "${gamification.todayMinutesEstimated}/${gamification.dailyTargetMinutes}m"
            )
            StreakGlyph(
                streakDays = gamification.streakDays,
                atRisk = gamification.streakDays > 0 && gamification.todayMinutesEstimated == 0
            )
            SubjectDotsGlyph(
                colors = analytics.conceptRows.take(6).map { masteryColor(it.status) }
            )
        }
    }
}

@Composable
private fun TodayBarGlyph(progress: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val filled = (progress.coerceIn(0f, 1f) * 5).toInt()
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < filled) TealAccent else SubtleBorder)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StreakGlyph(streakDays: Int, atRisk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = when {
                streakDays <= 0 -> TextMuted
                atRisk -> CyberGold
                else -> StepCorrect
            },
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$streakDays",
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (atRisk) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "at risk",
                color = CyberGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubjectDotsGlyph(colors: List<Color>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "Subjects",
            color = TextMuted,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )
        if (colors.isEmpty()) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(SubtleBorder)
                )
            }
        } else {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

private fun masteryColor(status: MasteryStatus): Color = when (status) {
    MasteryStatus.MASTERED -> StepCorrect
    MasteryStatus.DEVELOPING -> TealAccent
    MasteryStatus.EMERGING -> CyberGold
    MasteryStatus.REGRESSED -> StepIssue
    else -> TextMuted
}
