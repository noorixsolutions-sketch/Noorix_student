package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.onboarding.AgeBand
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.BadgesCard
import com.example.presentation.components.ConceptMasteryCard
import com.example.presentation.components.DailyGoalCard
import com.example.presentation.components.WeeklyActivityCard
import com.example.presentation.components.XpLevelCard
import com.example.ui.theme.*

/**
 * Progress — "Understand how you're developing"
 * (docs/two-mode-experience.md §5).
 *
 * The derived analytics (XP, daily goal, weekly activity, mastery, badges)
 * live here instead of on Home, so Home can stay the journey. Nothing on this
 * screen is a score report; it's the learner's own story of growth.
 * O-Level learners additionally get the check-in quiz entry (the one place a
 * "rigorous diagnostic" is explicitly optional).
 */
@Composable
fun ProgressScreen(viewModel: OLevelViewModel) {
    val onboarding by viewModel.onboardingState.collectAsState()
    val gamification by viewModel.gamificationSnapshot.collectAsState()
    val analytics by viewModel.analyticsSnapshot.collectAsState()

    val profile = onboarding.profile
    val band = onboarding.band

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
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
                            color = StepCorrect.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = profile.progressHeadline.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = StepCorrect,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = when (band) {
                                    AgeBand.EARLY -> "Every little bit counts."
                                    AgeBand.PRIMARY -> "Watch how far you've come."
                                    AgeBand.SECONDARY -> "The numbers behind your progress."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item { XpLevelCard(snapshot = gamification) }
        item { DailyGoalCard(snapshot = gamification, analytics = analytics) }
        item { WeeklyActivityCard(snapshot = analytics) }
        item {
            ConceptMasteryCard(
                rows = analytics.conceptRows,
                averageMastery = analytics.averageMastery
            )
        }
        item { BadgesCard(earned = gamification.earnedBadges) }

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
                            Text("Progress check", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "A few adaptive questions across your topics. No marks — I'm just keeping my map of you up to date.",
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
