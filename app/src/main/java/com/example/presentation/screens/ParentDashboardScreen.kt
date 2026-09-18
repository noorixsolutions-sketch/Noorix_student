package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.parent.ParentNarrativeBuilder
import com.example.domain.parent.ParentPrefs
import com.example.domain.parent.ParentWeekStats
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.StepIssue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Parent Dashboard — ADDITIVE (§11 Goal Architecture).
 *
 * PIN-gated trust surface: Aisha's weekly narrative note (composed on-device
 * from real measured stats), week stats, attention items, upcoming exams,
 * and parent actions. Reached from Settings — not in the student bottom nav.
 */
@Composable
fun ParentDashboardScreen(viewModel: OLevelViewModel) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.setTab(AppNavigationTab.DASHBOARD) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text(
                text = "PARENT DASHBOARD",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = if (unlocked) StepCorrect else CyberGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (!unlocked) {
            PinGate(onUnlock = { unlocked = true })
        } else {
            ParentContent(viewModel = viewModel, onLock = { unlocked = false })
        }
    }
}

@Composable
private fun PinGate(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    val hasPin = remember { ParentPrefs.hasPinSet(context) }
    var confirmPin by remember { mutableStateOf("") }
    var isSettingPin by remember { mutableStateOf(!hasPin) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = CyberGold.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = CyberGold,
                        modifier = Modifier.padding(14.dp).size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isSettingPin) "Set Parent PIN" else "Parents only",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isSettingPin) "Create a 4-digit PIN to secure the dashboard." else "Enter your 4-digit parent PIN to view the weekly report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isSettingPin) "New PIN" else "Parent PIN", color = TextMuted) },
                    placeholder = { Text("••••", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NearBlackBg,
                        unfocusedContainerColor = NearBlackBg,
                        focusedBorderColor = CyberGold,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                
                if (isSettingPin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter { c -> c.isDigit() }.take(6)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm PIN", color = TextMuted) },
                        placeholder = { Text("••••", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = error != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NearBlackBg,
                            unfocusedContainerColor = NearBlackBg,
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error!!, color = StepIssue, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (isSettingPin) {
                            if (pin.length < 4) {
                                error = "PIN must be at least 4 digits."
                            } else if (pin != confirmPin) {
                                error = "PINs do not match."
                            } else {
                                ParentPrefs.setPin(context, pin)
                                onUnlock()
                            }
                        } else {
                            if (ParentPrefs.checkPin(context, pin)) {
                                onUnlock()
                            } else {
                                error = "Incorrect PIN — try again."
                                pin = ""
                            }
                        }
                    },
                    enabled = pin.length >= 4 && (!isSettingPin || confirmPin.length >= 4),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isSettingPin) "Set PIN & Unlock" else "Unlock Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ParentContent(viewModel: OLevelViewModel, onLock: () -> Unit) {
    val context = LocalContext.current
    val analytics by viewModel.analyticsSnapshot.collectAsState()
    val gamification by viewModel.gamificationSnapshot.collectAsState()
    val studentModel by viewModel.studentModel.collectAsState()
    val userAccount by viewModel.userAccount.collectAsState()
    var showChangePin by remember { mutableStateOf(false) }

    val stats = remember(analytics, gamification, studentModel, userAccount) {
        val rows = analytics.conceptRows
        ParentWeekStats(
            studentName = userAccount?.displayName ?: studentModel.studentName,
            weekMinutes = analytics.minutesThisWeekEstimated,
            weekEvents = analytics.eventsThisWeek,
            accuracyPercent = analytics.accuracyPercent,
            weakestConceptName = rows.minByOrNull { it.mastery }?.conceptName,
            weakestConceptMastery = rows.minByOrNull { it.mastery }?.mastery ?: 0.0,
            strongestConceptName = rows.maxByOrNull { it.mastery }?.takeIf { rows.isNotEmpty() }?.conceptName,
            streakDays = gamification.streakDays,
            activeDaysThisWeek = analytics.activeDaysThisWeek,
            targetExamSession = studentModel.profileCalibration.targetExamSession
        )
    }
    val narrative = remember(stats) { ParentNarrativeBuilder.build(stats) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Aisha's note — the hero artifact.
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = CyberGold.copy(alpha = 0.15f)) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyberGold,
                                modifier = Modifier.padding(8.dp).size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AISHA'S NOTE TO YOU",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${stats.studentName.substringBefore(" ")}'s week",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"${narrative.note}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Composed on-device from measured activity • ${narrative.nextFocus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Week stats grid.
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentStatTile("Study time", formatParentMinutes(stats.weekMinutes), Modifier.weight(1f))
                ParentStatTile("Sessions", "${stats.weekEvents}", Modifier.weight(1f))
                ParentStatTile("Accuracy", "${stats.accuracyPercent}%", Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentStatTile("Active days", "${stats.activeDaysThisWeek}/7", Modifier.weight(1f))
                ParentStatTile("Streak", "${stats.streakDays} days", Modifier.weight(1f))
                ParentStatTile(
                    "Strongest",
                    stats.strongestConceptName?.take(14) ?: "—",
                    Modifier.weight(1f)
                )
            }
        }

        // Attention items.
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (narrative.attention.isEmpty()) "NOTHING NEEDS ATTENTION 🎉" else "THINGS THAT NEED ATTENTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (narrative.attention.isEmpty()) {
                        Text(
                            text = "All quiet — steady progress across the board.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        narrative.attention.forEach { bullet ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(text = "•  ", color = CyberGold, fontWeight = FontWeight.Bold)
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Upcoming.
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "UPCOMING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (stats.targetExamSession.isNotBlank()) {
                            "🎯 Target exam session: ${stats.targetExamSession}"
                        } else {
                            "No exam session set — add one during onboarding."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        // Actions.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        ParentPrefs.setMessageWaiting(context, true)
                        viewModel.sendParentEncouragement()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Encouragement 💛", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.setTab(AppNavigationTab.DASHBOARD) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NearBlackElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mastery Map", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.setTab(AppNavigationTab.AI_AGENTS) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NearBlackElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Talk to Aisha", fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showChangePin = true }) {
                        Text("Change PIN", color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { viewModel.setTab(AppNavigationTab.AUDIT) }) {
                        Text("System Settings", color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onLock) {
                        Text("Lock report", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showChangePin) {
        ChangePinDialog(
            onDismiss = { showChangePin = false },
            onSave = { newPin ->
                ParentPrefs.setPin(context, newPin)
                showChangePin = false
            }
        )
    }
}

@Composable
private fun ParentStatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, color = CyberGold, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm
    val valid = pin.length >= 4 && pin == confirm

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NearBlackElevated,
        title = { Text("Change parent PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("New PIN", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberGold,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Confirm PIN", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = mismatch,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberGold,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text("PINs do not match.", color = StepIssue, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(pin) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold, contentColor = NearBlackBg)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

private fun formatParentMinutes(totalMinutes: Int): String {
    if (totalMinutes < 60) return "${totalMinutes}m"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
}
