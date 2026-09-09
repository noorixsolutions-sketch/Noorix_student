package com.example.presentation.screens

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldAccent
import coil.compose.AsyncImage

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ============================================================
        // Google Account (Credential Manager OAuth)
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACCOUNT",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                                )
                            } ?: Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GoldAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = account.initials,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = account.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.signOutGoogle() },
                                enabled = !googleBusy
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign out")
                            }
                        }
                    } else {
                        Column {
                            Text(
                                text = "Sign in with Google to attach progress to your account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { activity?.let { viewModel.signInWithGoogle(it) } },
                                enabled = activity != null && !googleBusy,
                                modifier = Modifier.testTag("google_sign_in_button")
                            ) {
                                Text(if (googleBusy) "Signing in…" else "Sign in with Google")
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // AI Model Engine Settings
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI ENGINE CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ---------------- Local LLM (llama.cpp server) ----------------
                    Text(
                        text = "Local LLM — llama.cpp server",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Point to any running llama-server (this device over localhost, or another device on your LAN). " +
                            "Chat with Danish, Zoya and Exam Coach is routed to the local model while connected; Gemini is the fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = localHost,
                            onValueChange = { viewModel.setLocalLlamaHost(it) },
                            label = { Text("Host") },
                            placeholder = { Text("127.0.0.1") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = localPort,
                            onValueChange = { viewModel.setLocalLlamaPort(it.filter { c -> c.isDigit() }) },
                            label = { Text("Port") },
                            placeholder = { Text("8080") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!localConnected) {
                            Button(
                                onClick = { viewModel.connectLocalLlama() },
                                modifier = Modifier.testTag("connect_local_llama_button")
                            ) {
                                Icon(imageVector = Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect")
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.disconnectLocalLlama() }) {
                                Text("Disconnect")
                            }
                        }
                        Text(
                            text = if (localConnected) {
                                "CONNECTED — routing agents to local model"
                            } else {
                                "Offline — agents use Gemini when a key is configured"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (localConnected) Color(0xFF48BB78) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    localStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (localConnected) Color(0xFF48BB78) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (localConnected && localModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedLocalModelDropdown,
                            onExpandedChange = { expandedLocalModelDropdown = !expandedLocalModelDropdown }
                        ) {
                            OutlinedTextField(
                                value = localModels.find { it == selectedLocalModel } ?: "Select model…",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model on server") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocalModelDropdown) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedLocalModelDropdown,
                                onDismissRequest = { expandedLocalModelDropdown = false }
                            ) {
                                localModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model, fontFamily = FontFamily.Monospace) },
                                        onClick = {
                                            viewModel.selectLocalLlamaModel(model)
                                            expandedLocalModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // ---------------- OpenRouter (legacy cloud option) ----------------
                    Text(
                        text = "OpenRouter Cloud Models (optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = { viewModel.updateOpenRouterApiKey(it) },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = expandedModelDropdown,
                        onExpandedChange = { expandedModelDropdown = !expandedModelDropdown }
                    ) {
                        OutlinedTextField(
                            value = openRouterModels.find { it.id == selectedOpenRouterModel }?.name ?: "Select Model...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cloud Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModelDropdown,
                            onDismissRequest = { expandedModelDropdown = false }
                        ) {
                            openRouterModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
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

        // Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AUDIT TRAIL & DATA SOVEREIGNTY",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Evidence Primacy & Tamper-Evident Logging",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Every state mutation is backed by the immutable event log. The audit trail below is a hash chain — " +
                            "each entry's hash includes the previous entry's hash, so any later edit of a stored entry is detectable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Actions: Purge & Verification Proof
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RIGHT TO BE FORGOTTEN (DATA PURGE)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Purging removes all learning events for this student from the local store. " +
                            "A deterministic chain proof (hash of the purge action + current chain tail) is committed to the audit log as an attestation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showPurgeConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purge_data_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Data Purge")
                    }
                }
            }
        }

        // Immutable Event Log (Sovereign Evidence Store)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("immutable_event_store_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IMMUTABLE EVENT STORE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${events.size} Events Logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Events are appended monotonically at t=0 and replayed by the Projection Engine to derive student state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    events.takeLast(6).reversed().forEach { evt ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = evt.eventType.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                                Text(
                                    text = evt.eventId,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "HLC: ${evt.hlcTimestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = evt.payloadJson.take(80) + if (evt.payloadJson.length > 80) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Audit Trail Entries
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AUDIT LOG (HASH CHAIN)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (auditChainVerified) "CHAIN VERIFIED" else "CHAIN CHECK FAILED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (auditChainVerified) Color(0xFF48BB78) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Verification recomputes every entry's hash against the previous entry's hash. " +
                            "A failure usually means entries predate the chain (v3 schema) or the log was modified.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    auditLogs.take(8).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.action,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAccent
                                )
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = log.verificationHash.take(16) + "…",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            title = { Text("Confirm Data Purge") },
            text = {
                Text("This permanently removes all learning events for the current student from this device, " +
                    "and commits a chain proof of the purge to the audit log.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeStudentData()
                        showPurgeConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_purge_button")
                ) {
                    Text("Purge Data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
