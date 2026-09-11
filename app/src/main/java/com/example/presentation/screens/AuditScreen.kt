package com.example.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.bubble.AishaBubbleService
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(viewModel: OLevelViewModel, activity: Activity? = null) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    val events by viewModel.eventsList.collectAsState()

    val openRouterKey by viewModel.openRouterApiKey.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val selectedOpenRouterModel by viewModel.selectedOpenRouterModel.collectAsState()

    val localHost by viewModel.localLlamaHost.collectAsState()
    val localPort by viewModel.localLlamaPort.collectAsState()
    val localConnected by viewModel.localLlamaConnected.collectAsState()
    val localStatus by viewModel.localLlamaStatus.collectAsState()
    val localModels by viewModel.localLlamaModels.collectAsState()
    val selectedLocalModel by viewModel.selectedLocalLlamaModel.collectAsState()

    val userAccount by viewModel.userAccount.collectAsState()
    val googleBusy by viewModel.googleSignInBusy.collectAsState()

    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var expandedModelDropdown by remember { mutableStateOf(false) }
    var expandedLocalModelDropdown by remember { mutableStateOf(false) }
    val auditChainVerified = remember(auditLogs) { viewModel.verifyAuditChainNow() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // ============================================================
        // 1. Google Account Profile Card
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GOOGLE ACCOUNT & IDENTITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (userAccount != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val account = userAccount!!
                            account.photoUrl?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Profile photo",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, TealAccent, CircleShape)
                                )
                            } ?: Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(TealAccentSubtle)
                                    .border(1.dp, TealAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = account.initials,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (account.email.isNotEmpty()) {
                                    Text(
                                        text = account.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.signOutGoogle() },
                                enabled = !googleBusy,
                                border = BorderStroke(1.dp, SubtleBorderActive),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign out", color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Column {
                            Text(
                                text = "Sign in with Google using Credential Manager to synchronize your learning profile across devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { activity?.let { viewModel.signInWithGoogle(it) } },
                                enabled = activity != null && !googleBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("google_sign_in_button")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (googleBusy) "Signing in…" else "Sign in with Google", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // 2. AI Model Engine Settings
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI ENGINE ROUTING & CONFIGURATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // ---------------- Local llama-server ----------------
                    Text(
                        text = "1. Local LLM (llama.cpp server)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Direct HTTP SSE connection to llama-server (on-device or LAN). Fully offline capable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = localHost,
                            onValueChange = { viewModel.setLocalLlamaHost(it) },
                            label = { Text("Host", fontSize = 11.sp) },
                            placeholder = { Text("127.0.0.1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NearBlackBg,
                                unfocusedContainerColor = NearBlackBg,
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = SubtleBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = localPort,
                            onValueChange = { viewModel.setLocalLlamaPort(it.filter { c -> c.isDigit() }) },
                            label = { Text("Port", fontSize = 11.sp) },
                            placeholder = { Text("8080") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NearBlackBg,
                                unfocusedContainerColor = NearBlackBg,
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = SubtleBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!localConnected) {
                            Button(
                                onClick = { viewModel.connectLocalLlama() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
                            ) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.disconnectLocalLlama() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StepIssue.copy(alpha = 0.5f))
                            ) {
                                Text("Disconnect", color = StepIssue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    localStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (localConnected) StepCorrect else TextSecondary
                        )
                    }

                    // ---------------- OpenRouter Cloud Models ----------------
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "2. OpenRouter Cloud Models (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect to Claude, GPT-4o, or Llama 3 via OpenRouter API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = { viewModel.updateOpenRouterApiKey(it) },
                        label = { Text("OpenRouter API Key", fontSize = 11.sp) },
                        placeholder = { Text("sk-or-v1-...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NearBlackBg,
                            unfocusedContainerColor = NearBlackBg,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = SubtleBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (openRouterModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedModelDropdown,
                            onExpandedChange = { expandedModelDropdown = !expandedModelDropdown }
                        ) {
                            OutlinedTextField(
                                value = openRouterModels.find { it.id == selectedOpenRouterModel }?.name ?: "Select Model...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Active Model", fontSize = 11.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = NearBlackBg,
                                    unfocusedContainerColor = NearBlackBg,
                                    focusedBorderColor = TealAccent,
                                    unfocusedBorderColor = SubtleBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedModelDropdown,
                                onDismissRequest = { expandedModelDropdown = false }
                            ) {
                                openRouterModels.take(15).forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.name, fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.selectOpenRouterModel(model.id)
                                            expandedModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // 3. Tamper-Evident Audit Hash Chain
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                            text = "AUDIT HASH CHAIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (auditChainVerified) StepCorrect.copy(alpha = 0.15f) else StepIssue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (auditChainVerified) StepCorrect else StepIssue)
                        ) {
                            Text(
                                text = if (auditChainVerified) "CHAIN INTACT" else "CHAIN MISMATCH",
                                color = if (auditChainVerified) StepCorrect else StepIssue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Deterministic SHA-256 hash chain verifying that learning telemetry and events have not been tampered with.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    auditLogs.take(5).forEach { log ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.action, style = MaterialTheme.typography.labelMedium, color = TealAccent, fontWeight = FontWeight.Bold)
                                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontSize = 11.sp, maxLines = 1)
                                }
                                Text(
                                    text = log.verificationHash.take(12) + "…",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // 3b. Primary Surfaces: Bubble, Tutor Keyboard, Parent (ADDITIVE)
        // ============================================================
        item {
            val context = LocalContext.current
            var bubbleOn by remember { mutableStateOf(AishaBubbleService.isRunning) }
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    startBubbleService(context)
                    bubbleOn = true
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PRIMARY SURFACES (BETA)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // ---- 1. Aisha Bubble overlay ----
                    Text(
                        text = "1. Aisha Bubble ${if (bubbleOn) "(Active)" else "(Off)"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A floating 56dp bubble in every app. Tap for tutor chat, double-tap for the 8-layout fan, swipe up for the quick HUD.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!bubbleOn) {
                            Button(
                                onClick = {
                                    when {
                                        needsOverlayPermission(context) -> {
                                            activity?.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }
                                        needsNotifPermission(context) -> {
                                            notifPermissionLauncher.launch(
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                        else -> {
                                            startBubbleService(context)
                                            bubbleOn = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = NearBlackBg
                                ),
                                modifier = Modifier.testTag("enable_bubble_button")
                            ) {
                                Text("Enable Bubble", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    stopBubbleService(context)
                                    bubbleOn = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SubtleBorderActive)
                            ) {
                                Text("Hide Bubble", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (needsOverlayPermission(context) && !bubbleOn) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Requires the 'Display over other apps' permission — you'll be taken to system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // ---- 2. Tutor Keyboard IME ----
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "2. Tutor Keyboard",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A student-aware keyboard: streak glyph strip, 6-persona Tutor row, QWERTY + Math + Quiz modes. Enable it in system settings, then switch to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                activity?.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealAccent,
                                contentColor = NearBlackBg
                            )
                        ) {
                            Text("Enable in Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SubtleBorderActive)
                        ) {
                            Text("Switch keyboard", color = TextPrimary, fontSize = 12.sp)
                        }
                    }

                    // ---- 3. Parent Dashboard ----
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "3. Parent Dashboard",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PIN-gated weekly narrative, attention items and encouragement for parents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.setTab(AppNavigationTab.PARENT) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("open_parent_dashboard_button")
                    ) {
                        Icon(Icons.Default.FamilyRestroom, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Parent Dashboard", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ============================================================
        // 4. Sovereign Data Management (Right to be Forgotten)
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DATA SOVEREIGNTY & PURGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = StepIssue,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Permanently delete all locally recorded student events. A deterministic cryptographic proof of the purge action is committed to the immutable audit chain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showPurgeConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purge_data_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = StepIssue, contentColor = NearBlackBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purge Local Student Events", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = NearBlackElevated,
            title = { Text("Confirm Data Purge", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently purges all learning events for this candidate and commits a deterministic audit attestation. This action cannot be undone.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeStudentData()
                        showPurgeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StepIssue, contentColor = NearBlackBg),
                    modifier = Modifier.testTag("confirm_purge_button")
                ) {
                    Text("Execute Purge", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ------------------------------------------------------------------
// Primary-surface helpers (ADDITIVE): bubble service + permissions.
// ------------------------------------------------------------------

private fun needsOverlayPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)

private fun needsNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

private fun startBubbleService(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, AishaBubbleService::class.java).setAction(AishaBubbleService.ACTION_START)
    )
}

private fun stopBubbleService(context: Context) {
    context.stopService(Intent(context, AishaBubbleService::class.java))
}
