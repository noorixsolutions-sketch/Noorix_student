package com.example.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------
// Upgraded tutor chat bubble: avatar, name + timestamp header,
// markdown-lite body, copy action, verifier provenance.
// ------------------------------------------------------------------

@Composable
fun NoorixChatBubble(
    message: ChatMessageEntity,
    agentName: String,
    agentAccentColor: Color,
    agentImageRes: Int? = null,
    onCopy: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "student"
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var justCopied by remember(message.messageId) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Agent avatar (left) — user messages stay clean on the right.
        if (!isUser) {
            if (agentImageRes != null) {
                Image(
                    painter = painterResource(id = agentImageRes),
                    contentDescription = agentName,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.2.dp, agentAccentColor, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = agentAccentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, agentAccentColor),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = agentName.firstOrNull()?.uppercase() ?: "N",
                            color = agentAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) TealAccent.copy(alpha = 0.13f) else NearBlackSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) TealAccent.copy(alpha = 0.45f) else agentAccentColor.copy(alpha = 0.35f)
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header: sender + timestamp + copy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUser) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isUser) "YOU" else agentName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) TealAccent else agentAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatChatTime(message.timestampMs),
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isVerifiedMath && !isUser) {
                            VerifiedMathBadge()
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(message.content))
                                    onCopy?.invoke(message.content)
                                    justCopied = true
                                    scope.launch {
                                        delay(1400)
                                        justCopied = false
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (justCopied) "Copied" else "Copy message",
                                tint = if (justCopied) StepCorrect else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = renderMarkdownLite(message.content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 21.sp
                )

                if (!message.mathVerificationTrace.isNullOrEmpty() && !isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VerifierTraceCard(trace = message.mathVerificationTrace)
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = TealAccent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VerifiedMathBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = StepCorrect.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, StepCorrect.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "VERIFIED",
                color = StepCorrect,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VerifierTraceCard(trace: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackBg,
        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "DETERMINISTIC VERIFIER TRACE",
                style = MaterialTheme.typography.labelSmall,
                color = TealAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trace.lines().take(6).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ------------------------------------------------------------------
// Streaming bubble with animated "typing" cursor
// ------------------------------------------------------------------

@Composable
fun NoorixStreamingBubble(
    text: String,
    agentName: String,
    agentAccentColor: Color,
    agentImageRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stream-cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor-alpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (agentImageRes != null) {
            Image(
                painter = painterResource(id = agentImageRes),
                contentDescription = agentName,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, agentAccentColor, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = NearBlackSurface,
            border = BorderStroke(1.dp, agentAccentColor.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(agentAccentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${agentName.uppercase()} IS WRITING",
                        style = MaterialTheme.typography.labelSmall,
                        color = agentAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append(renderMarkdownLite(text.ifEmpty { "…" }))
                        withStyle(SpanStyle(color = agentAccentColor.copy(alpha = cursorAlpha))) {
                            append("▍")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Quick-reply suggestion chips
// ------------------------------------------------------------------

@Composable
fun SuggestionChipsRow(
    suggestions: List<String>,
    accentColor: Color,
    onSuggestionClick: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (enabled) NearBlackElevated else NearBlackBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = if (enabled) 0.45f else 0.2f)),
                modifier = Modifier.clickable(enabled = enabled) { onSuggestionClick(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Markdown-lite renderer: headings, bullets, **bold**, `code`.
// ------------------------------------------------------------------

private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val CODE_REGEX = Regex("`([^`]+?)`")

fun renderMarkdownLite(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { index, originalLine ->
        val line = originalLine.trimEnd()
        when {
            line.startsWith("### ") -> {
                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                    appendInline(line.removePrefix("### "))
                }
            }
            line.startsWith("## ") -> {
                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    appendInline(line.removePrefix("## "))
                }
            }
            line.startsWith("# ") -> {
                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                    appendInline(line.removePrefix("# "))
                }
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("• ") -> {
                val bullet = line.trimStart().removePrefix("- ").removePrefix("• ")
                withStyle(SpanStyle(color = TealAccent, fontWeight = FontWeight.Bold)) {
                    append("•  ")
                }
                withStyle(SpanStyle(color = TextPrimary)) {
                    appendInline(bullet)
                }
            }
            numberedBullet(line) != null -> {
                val (number, rest) = numberedBullet(line)!!
                withStyle(SpanStyle(color = TealAccent, fontWeight = FontWeight.Bold)) {
                    append("$number. ")
                }
                withStyle(SpanStyle(color = TextPrimary)) {
                    appendInline(rest)
                }
            }
            else -> {
                withStyle(SpanStyle(color = TextPrimary)) {
                    appendInline(line)
                }
            }
        }
        if (index < lines.lastIndex) append("\n")
    }
}

private val NUMBERED_REGEX = Regex("^(\\d+)[.)]\\s+(.*)$")

private fun numberedBullet(line: String): Pair<String, String>? {
    val match = NUMBERED_REGEX.matchEntire(line.trimStart()) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

/** Inline **bold** and `code` spans within a single line. */
private fun AnnotatedString.Builder.appendInline(line: String) {
    if (line.isEmpty()) return
    // Split by code spans first so markdown inside code stays literal.
    var cursor = 0
    val codeMatches = CODE_REGEX.findAll(line).toList()
    if (codeMatches.isEmpty()) {
        appendBold(line)
        return
    }
    for (match in codeMatches) {
        if (match.range.first > cursor) {
            appendBold(line.substring(cursor, match.range.first))
        }
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = NearBlackBg,
                color = TealAccent,
                fontSize = 13.sp
            )
        ) {
            append(" ${match.groupValues[1]} ")
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) {
        appendBold(line.substring(cursor))
    }
}

private fun AnnotatedString.Builder.appendBold(segment: String) {
    var cursor = 0
    val matches = BOLD_REGEX.findAll(segment).toList()
    if (matches.isEmpty()) {
        append(segment)
        return
    }
    for (match in matches) {
        if (match.range.first > cursor) {
            append(segment.substring(cursor, match.range.first))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    if (cursor < segment.length) {
        append(segment.substring(cursor))
    }
}

private fun formatChatTime(timestampMs: Long): String {
    return try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    } catch (_: Exception) {
        ""
    }
}
