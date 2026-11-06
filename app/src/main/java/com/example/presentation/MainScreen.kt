package com.example.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.components.NoorixTopBar
import com.example.presentation.components.HudGlyphStrip
import com.example.presentation.screens.*
import com.example.ui.theme.*

data class NoorixNavDestination(
    val tab: AppNavigationTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(viewModel: OLevelViewModel) {
    var isAppLoading by remember { mutableStateOf(true) }
    val indexStats by viewModel.curriculumIndexStats.collectAsState()
    val eventCount by viewModel.curriculumEventCount.collectAsState()

    if (isAppLoading) {
        NoorixLoadingScreen(
            indexedTopicCount = indexStats?.totalTopics?.takeIf { it > 0 } ?: 108,
            eventLedgerCount = eventCount,
            isIndexing = (indexStats == null),
            onLoadingComplete = { isAppLoading = false }
        )
        return
    }

    val currentTab by viewModel.currentTab.collectAsState()
    val onboarding by viewModel.onboardingState.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val newContentNotification by viewModel.newContentNotification.collectAsState()
    val activePkg by viewModel.activePackage.collectAsState()
    val hudGamification by viewModel.gamificationSnapshot.collectAsState()
    val hudAnalytics by viewModel.analyticsSnapshot.collectAsState()

    // Two-mode nav (docs/two-mode-experience.md §5): the learner's journey plus
    // its companions. Everything else (Math Lab, check-in, packages, audit,
    // parent) lives one level deeper inside these screens / Settings.
    val navDestinations = listOf(
        NoorixNavDestination(AppNavigationTab.DASHBOARD, "Home", Icons.Default.Home),
        NoorixNavDestination(AppNavigationTab.LEARNING_HUB, "Explore", Icons.Default.Explore),
        NoorixNavDestination(AppNavigationTab.PROGRESS, "Progress", Icons.Default.Insights),
        NoorixNavDestination(AppNavigationTab.PROGRAM, "Study Plan", Icons.Default.Route),
        NoorixNavDestination(AppNavigationTab.AI_AGENTS, "Tutors", Icons.AutoMirrored.Filled.Chat)
    )

    Scaffold(
        containerColor = NearBlackBg,
                topBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                Column {
                    NoorixTopBar(
                        onSystemHudClick = { viewModel.setTab(AppNavigationTab.PARENT) },
                        onProfileClick = { viewModel.setTab(AppNavigationTab.PARENT) }
                    )
                    // Ambient HUD glyph strip (§8): today bar + streak + subject dots.
                    HudGlyphStrip(
                        gamification = hudGamification,
                        analytics = hudAnalytics
                    )
                }
            }
        },
                bottomBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                NavigationBar(
                    containerColor = NearBlackSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(BorderStroke(1.dp, SubtleBorder))
                ) {
                    navDestinations.forEach { dest ->
                        val isSelected = currentTab == dest.tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.setTab(dest.tab) },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = dest.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealAccent,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = TealAccent,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = TealAccentSubtle
                            )
                        )
                    }
                }
            }
        }

    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NearBlackBg)
                .padding(innerPadding)
        ) {
            // Main Screen Routing
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            when (currentTab) {
                AppNavigationTab.ONBOARDING -> OnboardingScreen(viewModel)
                AppNavigationTab.DASHBOARD -> DashboardScreen(viewModel)
                AppNavigationTab.AI_AGENTS -> AiAgentsScreen(viewModel)
                AppNavigationTab.SYMBOLIC_TUTOR -> SymbolicTutorScreen(viewModel)
                AppNavigationTab.DIAGNOSTIC -> DiagnosticScreen(viewModel)
                AppNavigationTab.MEDIA_TRANSFER -> MediaTransferScreen(viewModel)
                AppNavigationTab.DECAY_SYNC -> DecaySyncScreen(viewModel)
                AppNavigationTab.PACKAGES -> PackagesScreen(viewModel)
                AppNavigationTab.LEARNING_HUB -> LearningHubScreen(viewModel)
                AppNavigationTab.PROGRESS -> ProgressScreen(viewModel)
                AppNavigationTab.PROGRAM -> ProgramScreen(viewModel)
                AppNavigationTab.AUDIT -> AuditScreen(viewModel, activity)
                AppNavigationTab.PARENT -> ParentDashboardScreen(viewModel)
            }

            // Dynamic Cloud Content Notification for Student's Class (User Preference Content Sync)
            AnimatedVisibility(
                visible = newContentNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                newContentNotification?.let { notifText ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NearBlackElevated,
                        border = BorderStroke(1.5.dp, TealAccent),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .testTag("new_content_class_notification_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = TealAccentSubtle,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = TealAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NEW CURRICULUM CONTENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealAccent,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = notifText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissNewContentNotification() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.dismissNewContentNotification() }
                                ) {
                                    Text("Not Now", color = TextSecondary, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.syncPendingContentForClass() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = NearBlackBg
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("sync_new_content_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync & Download", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // High-Tech Cyber Notification Banner -> Calm Notification Banner
            AnimatedVisibility(
                visible = statusBanner != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                statusBanner?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackElevated,
                        border = BorderStroke(1.dp, TealAccentSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.dismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = msg,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
