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
import com.example.presentation.screens.*
import com.example.ui.theme.*

data class NoorixNavDestination(
    val tab: AppNavigationTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(viewModel: OLevelViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val onboarding by viewModel.onboardingState.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val activePkg by viewModel.activePackage.collectAsState()

    // Reduced nav: Home, Tutors, Courses, Practice, Media, Settings (Audit)
    val navDestinations = listOf(
        NoorixNavDestination(AppNavigationTab.DASHBOARD, "Home", Icons.Default.Home),
        NoorixNavDestination(AppNavigationTab.AI_AGENTS, "Tutors", Icons.Default.Chat),
        NoorixNavDestination(AppNavigationTab.PACKAGES, "Courses", Icons.Default.LibraryBooks),
        NoorixNavDestination(AppNavigationTab.DIAGNOSTIC, "Practice", Icons.Default.Psychology),
        NoorixNavDestination(AppNavigationTab.SYMBOLIC_TUTOR, "Math Lab", Icons.Default.Calculate)
    )

    Scaffold(
        containerColor = NearBlackBg,
                topBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                NoorixTopBar(
                    onSystemHudClick = { viewModel.setTab(AppNavigationTab.AUDIT) }, 
                    onProfileClick = { viewModel.setTab(AppNavigationTab.AUDIT) }
                )
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
                AppNavigationTab.AUDIT -> AuditScreen(viewModel, activity)
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
