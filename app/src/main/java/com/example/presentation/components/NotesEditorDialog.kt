package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.StudyNote
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepIssue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-concept study notes — ADDITIVE.
 *
 * A full-screen dialog listing the student's notes for one concept plus a
 * small editor. Persists through [onSave]/[onDelete] into `study_tools.db`.
 */
@Composable
fun NotesEditorDialog(
    conceptName: String,
    notes: List<StudyNote>,
    onSave: (noteId: String?, title: String, body: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<StudyNote?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NearBlackBg)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (editing != null || isCreating) {
                            IconButton(
                                onClick = { editing = null; isCreating = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to notes", tint = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column {
                            Text(
                                text = "MY NOTES • $conceptName".uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = when {
                                    editing != null -> "Edit note"
                                    isCreating -> "New note"
                                    else -> "${notes.size} note(s)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close notes", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    editing != null || isCreating -> {
                        val current = editing
                        NoteEditorForm(
                            initialTitle = current?.title.orEmpty(),
                            initialBody = current?.body.orEmpty(),
                            onSave = { title, body ->
                                onSave(current?.noteId, title, body)
                                editing = null
                                isCreating = false
                            },
                            onCancel = { editing = null; isCreating = false }
                        )
                    }
                    notes.isEmpty() -> {
                        EmptyNotesState(onCreate = { isCreating = true })
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notes, key = { it.noteId }) { note ->
                                NoteRowCard(
                                    note = note,
                                    onOpen = { editing = note },
                                    onDelete = { onDelete(note.noteId) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { isCreating = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealAccent,
                                contentColor = NearBlackBg
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRowCard(note: StudyNote, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(note.noteId) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = if (confirmDelete) "Confirm delete" else "Delete note",
                        tint = if (confirmDelete) StepIssue else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (confirmDelete) {
                Text(
                    text = "Tap again to confirm delete",
                    color = StepIssue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = note.body.lines().take(3).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Updated ${formatNoteTime(note.updatedAtMs)}",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NoteEditorForm(
    initialTitle: String,
    initialBody: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NearBlackSurface,
                unfocusedContainerColor = NearBlackSurface,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = SubtleBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            label = { Text("Write your understanding in your own words…", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NearBlackSurface,
                unfocusedContainerColor = NearBlackSurface,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = SubtleBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${body.length} characters • explaining in your own words boosts retention",
            color = TextMuted,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NearBlackElevated,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(title.trim(), body.trim()) },
                enabled = body.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = NearBlackBg
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Note", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyNotesState(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = TealAccent.copy(alpha = 0.12f)) {
                Icon(
                    Icons.Default.StickyNote2,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No notes yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Capture key formulas, traps and examiner tips for this concept.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Write First Note", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatNoteTime(timestampMs: Long): String {
    return try {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    } catch (_: Exception) {
        ""
    }
}
