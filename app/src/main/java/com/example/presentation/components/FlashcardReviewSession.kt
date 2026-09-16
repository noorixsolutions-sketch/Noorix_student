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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.Flashcard
import com.example.core.model.ReviewGrade
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.StepIssue
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Spaced-repetition Review Mode — ADDITIVE.
 *
 * Full-screen session: the student flips each card, self-grades recall
 * (Again / Hard / Good / Easy) and the SM-2 schedule updates through
 * [onGrade]. Nothing here changes the existing flashcard cards — this is a
 * separate focused-practice entry point.
 */
@Composable
fun FlashcardReviewSession(
    conceptName: String,
    queue: List<Flashcard>,
    onGrade: (Flashcard, ReviewGrade) -> Unit,
    onDismiss: () -> Unit
) {
    var index by remember(queue) { mutableIntStateOf(0) }
    var revealed by remember(queue) { mutableStateOf(false) }
    var showHint by remember(queue) { mutableStateOf(false) }
    var againCount by remember(queue) { mutableIntStateOf(0) }
    var recalledCount by remember(queue) { mutableIntStateOf(0) }
    var finished by remember(queue) { mutableStateOf(false) }

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REVIEW MODE • $conceptName".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (finished) "Session complete" else "Card ${minOf(index + 1, queue.size)} of ${queue.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close review", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!finished && queue.isNotEmpty()) {
                    NoorixSegmentedProgressBar(
                        progress = index.toFloat() / queue.size.toFloat(),
                        totalSegments = queue.size.coerceAtLeast(1).coerceAtMost(24),
                        activeColor = TealAccent,
                        inactiveColor = SubtleBorder
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    queue.isEmpty() -> {
                        EmptyReviewState(onDismiss)
                    }
                    finished -> {
                        ReviewSummaryCard(
                            total = queue.size,
                            recalled = recalledCount,
                            again = againCount,
                            onDone = onDismiss
                        )
                    }
                    else -> {
                        val card = queue[index.coerceIn(0, queue.lastIndex)]
                        // Reset per-card UI when the card changes.
                        ReviewCardBody(
                            card = card,
                            revealed = revealed,
                            showHint = showHint,
                            onToggleReveal = { revealed = !revealed },
                            onShowHint = { showHint = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (revealed) {
                            GradeButtons(
                                onGradeSelected = { grade ->
                                    onGrade(card, grade)
                                    if (grade == ReviewGrade.AGAIN) againCount += 1
                                    else recalledCount += 1
                                    if (index + 1 < queue.size) {
                                        index += 1
                                        revealed = false
                                        showHint = false
                                    } else {
                                        finished = true
                                    }
                                }
                            )
                        } else {
                            Button(
                                onClick = { revealed = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = NearBlackBg
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reveal Answer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCardBody(
    card: Flashcard,
    revealed: Boolean,
    showHint: Boolean,
    onToggleReveal: () -> Unit,
    onShowHint: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleReveal() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (revealed) NearBlackElevated else NearBlackSurface
        ),
        border = BorderStroke(1.dp, if (revealed) StepCorrect.copy(alpha = 0.5f) else TealAccent.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (revealed) StepCorrect.copy(alpha = 0.15f) else TealAccentSubtle
            ) {
                Text(
                    text = if (revealed) "ANSWER" else "RECALL PROMPT",
                    color = if (revealed) StepCorrect else TealAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (revealed) card.back else card.front,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp
            )
            if (!revealed && card.hint.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                if (showHint) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = CyberGold, modifier = Modifier.padding(end = 8.dp))
                            Text(card.hint, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text(
                        text = "Show hint",
                        color = CyberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onShowHint() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (revealed) "How well did you recall it?" else "Tap the card (or Reveal) when you have an answer in mind",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GradeButtons(onGradeSelected: (ReviewGrade) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradeButton(
                label = "Again",
                sublabel = "Forgot",
                color = StepIssue,
                onClick = { onGradeSelected(ReviewGrade.AGAIN) },
                modifier = Modifier.weight(1f)
            )
            GradeButton(
                label = "Hard",
                sublabel = "Struggled",
                color = CyberGold,
                onClick = { onGradeSelected(ReviewGrade.HARD) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradeButton(
                label = "Good",
                sublabel = "Recalled",
                color = TealAccent,
                onClick = { onGradeSelected(ReviewGrade.GOOD) },
                modifier = Modifier.weight(1f)
            )
            GradeButton(
                label = "Easy",
                sublabel = "Instant",
                color = StepCorrect,
                onClick = { onGradeSelected(ReviewGrade.EASY) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    sublabel: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = sublabel, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReviewSummaryCard(total: Int, recalled: Int, again: Int, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, StepCorrect.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🎉", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Review complete!",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You recalled $recalled of $total cards. $again card(s) will resurface tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStat("$recalled", "recalled", StepCorrect, Modifier.weight(1f))
                SummaryStat("$again", "to repeat", CyberGold, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackBg,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmptyReviewState(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "📭", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Nothing due right now",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "All caught up! Graded cards return on their spaced schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Back to Hub", fontWeight = FontWeight.Bold)
            }
        }
    }
}
