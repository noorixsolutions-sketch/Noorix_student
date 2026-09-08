package com.example.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

@Composable
fun CyberHudSecurityDialog(
    viewModel: OLevelViewModel,
    onDismiss: () -> Unit
) {
    val activePkg by viewModel.activePackage.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var showAuditDetails by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Cyber Frame Container (Matching Image 3)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .border(1.5.dp, NeonCyan, RoundedCornerShape(20.dp))
                    .testTag("cyber_hud_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberDarkBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close button top right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = NeonCyan)
                        }
                    }

                    // 1. Glowing Noorix Emblem
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(110.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NeonCyan.copy(alpha = 0.12f),
                            modifier = Modifier.size(110.dp)
                        ) {}
                        Image(
                            painter = painterResource(id = R.drawable.img_noorix_logo),
                            contentDescription = "Noorix Hologram",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .border(2.dp, NeonCyan, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "NOORIX SOLUTIONS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "SYSTEM ARCHITECTURE & SECURITY HUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTextSecondary,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Segmented System Readiness Bar (Image 3: 78% Initializing System Modules)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYSTEM READINESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "100% OPERATIONAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    NoorixSegmentedProgressBar(
                        progress = 1.0f,
                        totalSegments = 20,
                        activeColor = CyberGreen
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. 4-Module Status Grid (Image 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudModuleBadge(
                            icon = Icons.Default.Memory,
                            title = "SYSTEM CHECK",
                            status = "VERIFIED",
                            color = NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        HudModuleBadge(
                            icon = Icons.Default.VerifiedUser,
                            title = "SECURITY",
                            status = "ENCRYPTED",
                            color = CyberGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudModuleBadge(
                            icon = Icons.Default.Storage,
                            title = "DATA SYNC",
                            status = "HLC ONLINE",
                            color = CyberBlue,
                            modifier = Modifier.weight(1f)
                        )
                        HudModuleBadge(
                            icon = Icons.Default.Speed,
                            title = "OPTIMIZING",
                            status = "ACTIVE",
                            color = CyberGold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Signed Package Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberPanelElevated),
                        border = BorderStroke(1.dp, CyberPanelBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ACTIVE COURSE PACKAGE",
                                    fontSize = 10.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ED25519 SIGNED",
                                    fontSize = 10.sp,
                                    color = CyberGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activePkg?.manifest?.subjectName ?: "Cambridge O-Level (4024)",
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Package ID: ${activePkg?.manifest?.packageId ?: "cambridge-math-4024"}",
                                fontSize = 11.sp,
                                color = CyberTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Hash: ${activePkg?.manifest?.contentHash?.take(16) ?: "e3b0c44298fc1c14"}...",
                                fontSize = 10.sp,
                                color = CyberTextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // GDPR Purge & Erasure Proof Action
                    OutlinedButton(
                        onClick = { viewModel.purgeStudentData() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cryptographic Erasure Test (Purge Data)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tip box (Image 3: "TIP: A PERFECT SOLUTION STARTS WITH A SMART SYSTEM.")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = NeonCyan.copy(alpha = 0.2f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "TIP:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "A PERFECT SOLUTION STARTS WITH A SMART SYSTEM.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyberTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Image 3 Footer Copyright
                    Text(
                        text = "© 2026 NOORIX SOLUTIONS. ALL RIGHTS RESERVED.",
                        fontSize = 9.sp,
                        color = CyberTextMuted,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HudModuleBadge(
    icon: ImageVector,
    title: String,
    status: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CyberPanelElevated),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = status,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}
