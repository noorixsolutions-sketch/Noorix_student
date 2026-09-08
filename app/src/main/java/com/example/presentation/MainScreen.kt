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
import com.example.presentation.components.CyberHudSecurityDialog
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
    val statusBanner by viewModel.statusBanner.collectAsState()
    val activePkg by viewModel.activePackage.collectAsState()

    var showCyberHud by remember { mutableStateOf(false) }

    val navDestinations = listOf(
        NoorixNavDestination(AppNavigationTab.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NoorixNavDestination(AppNavigationTab.AI_AGENTS, "AI Agents", Icons.Default.SmartToy),
        NoorixNavDestination(AppNavigationTab.SYMBOLIC_TUTOR, "Math Lab", Icons.Default.Calculate),
        NoorixNavDestination(AppNavigationTab.DIAGNOSTIC, "Quiz", Icons.Default.Psychology),
        NoorixNavDestination(AppNavigationTab.MEDIA_TRANSFER, "Podcasts", Icons.Default.Podcasts),
        NoorixNavDestination(AppNavigationTab.DECAY_SYNC, "Memory", Icons.Default.Timeline)
    )

    Scaffold(
        containerColor = CyberDarkBg,
        topBar = {
            NoorixTopBar(
                onSystemHudClick = { showCyberHud = true },
                onProfileClick = { showCyberHud = true }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CyberPanel,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .border(BorderStroke(1.dp, CyberPanelBorder))
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
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = dest.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            unselectedIconColor = CyberTextSecondary,
                            selectedTextColor = NeonCyan,
                            unselectedTextColor = CyberTextSecondary,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberDarkBg)
                .padding(innerPadding)
        ) {
            // Main Screen Routing
            when (currentTab) {
                AppNavigationTab.DASHBOARD -> DashboardScreen(viewModel)
                AppNavigationTab.AI_AGENTS -> AiAgentsScreen(viewModel)
                AppNavigationTab.SYMBOLIC_TUTOR -> SymbolicTutorScreen(viewModel)
                AppNavigationTab.DIAGNOSTIC -> DiagnosticScreen(viewModel)
                AppNavigationTab.MEDIA_TRANSFER -> MediaTransferScreen(viewModel)
                AppNavigationTab.DECAY_SYNC -> DecaySyncScreen(viewModel)
                AppNavigationTab.PACKAGES -> PackagesScreen(viewModel)
                AppNavigationTab.AUDIT -> AuditScreen(viewModel)
            }

            // High-Tech Cyber Notification Banner
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
                        shape = RoundedCornerShape(12.dp),
                        color = CyberPanelElevated,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.dismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                color = CyberTextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = CyberTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Cyber HUD System Check & Security Modal (Matching Image 3)
    if (showCyberHud) {
        CyberHudSecurityDialog(
            viewModel = viewModel,
            onDismiss = { showCyberHud = false }
        )
    }
}
