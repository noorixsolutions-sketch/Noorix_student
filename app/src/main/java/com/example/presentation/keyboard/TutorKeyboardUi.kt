package com.example.presentation.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.GamificationSnapshot
import com.example.presentation.components.MATH_SYMBOLS
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Tutor Keyboard UI — ADDITIVE (§3.5 Goal Architecture, v1).
 *
 * Three stacked layers per the goal: glyph strip (always) + Tutor row
 * (always) + active mode (swaps). V1 ships 3 of the 8 goal modes —
 * QWERTY (+symbols layer), Math, Quiz — behind [KeyboardMode], so the
 * remaining modes (Handwriting, Agent Picker, Media, Reference, Glyph
 * Display) slot in without touching the service.
 */

enum class KeyboardMode {
    QWERTY,
    SYMBOLS,
    MATH,
    QUIZ
}

data class KeyboardPersona(
    val id: String,
    val letter: String,
    val name: String,
    val color: Color
)

val KEYBOARD_PERSONAS: List<KeyboardPersona> = listOf(
    KeyboardPersona("danish", "D", "Danish", TealAccent),
    KeyboardPersona("zoya", "Z", "Zoya", StepCorrect),
    KeyboardPersona("ayesha", "A", "Ayesha", Color(0xFF38BDF8)),
    KeyboardPersona("bilal", "B", "Bilal", Color(0xFFA78BFA)),
    KeyboardPersona("exam_coach", "E", "Coach", CyberGold),
    KeyboardPersona("career_guide", "C", "Guide", Color(0xFFF472B6))
)

@Composable
fun TutorKeyboardRoot(
    stats: GamificationSnapshot,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onPersona: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(KeyboardMode.QWERTY) }
    var caps by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NearBlackBg)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        KeyboardGlyphStrip(stats = stats)
        Spacer(modifier = Modifier.height(4.dp))
        TutorRow(personas = KEYBOARD_PERSONAS, onPersona = onPersona)
        Spacer(modifier = Modifier.height(4.dp))
        when (mode) {
            KeyboardMode.QWERTY -> QwertyLayer(
                caps = caps,
                onToggleCaps = { caps = !caps },
                onCommit = {
                    onCommit(it)
                    if (caps) caps = false // single-shot shift
                },
                onBackspace = onBackspace,
                onEnter = onEnter,
                onMode = { mode = it }
            )
            KeyboardMode.SYMBOLS -> SymbolsLayer(
                onCommit = onCommit,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onMode = { mode = it }
            )
            KeyboardMode.MATH -> MathLayer(
                onCommit = onCommit,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onMode = { mode = it }
            )
            KeyboardMode.QUIZ -> QuizLayer(
                onCommit = onCommit,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onMode = { mode = it }
            )
        }
    }
}

// ------------------------------------------------------------------
// Layer 1 — glyph strip (28dp, always): streak · today XP · level.
// ------------------------------------------------------------------

@Composable
private fun KeyboardGlyphStrip(stats: GamificationSnapshot) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🔥 ${stats.streakDays}   ·   +${stats.todayXp} XP today   ·   Lv ${stats.level}",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Aisha",
                color = TealAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ------------------------------------------------------------------
// Layer 2 — Tutor row (52dp, always): the 6 brand personas.
// ------------------------------------------------------------------

@Composable
private fun TutorRow(personas: List<KeyboardPersona>, onPersona: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        personas.forEach { persona ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = persona.color.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, persona.color.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPersona(persona.id) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = persona.letter,
                        color = persona.color,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = persona.name,
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Layer 3 — active modes.
// ------------------------------------------------------------------

@Composable
private fun QwertyLayer(
    caps: Boolean,
    onToggleCaps: () -> Unit,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onMode: (KeyboardMode) -> Unit
) {
    fun key(label: String): String = if (caps) label.uppercase() else label

    KeyboardRow(keys = "qwertyuiop".map { key(it.toString()) }, onCommit = onCommit)
    Spacer(modifier = Modifier.height(4.dp))
    KeyboardRow(keys = "asdfghjkl".map { key(it.toString()) }, onCommit = onCommit)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyButton(
            label = null,
            icon = Icons.Default.KeyboardCapslock,
            onClick = onToggleCaps,
            modifier = Modifier.weight(1.2f),
            highlighted = caps
        )
        "zxcvbnm".forEach { char ->
            KeyButton(label = key(char.toString()), onClick = { onCommit(key(char.toString())) }, modifier = Modifier.weight(1f))
        }
        KeyButton(label = null, icon = Icons.Default.Backspace, onClick = onBackspace, modifier = Modifier.weight(1.2f))
    }
    Spacer(modifier = Modifier.height(4.dp))
    BottomRow(onMode = onMode, onCommit = onCommit, onBackspace = onBackspace, onEnter = onEnter, showSymbolsKey = true)
}

@Composable
private fun SymbolsLayer(
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onMode: (KeyboardMode) -> Unit
) {
    KeyboardRow(keys = "1234567890".map { it.toString() }, onCommit = onCommit)
    Spacer(modifier = Modifier.height(4.dp))
    KeyboardRow(keys = listOf("-", "/", ":", ";", "(", ")", "&", "@", "\""), onCommit = onCommit)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyButton(label = "ABC", onClick = { onMode(KeyboardMode.QWERTY) }, modifier = Modifier.weight(1.5f), small = true)
        listOf(".", ",", "?", "!", "'").forEach { symbol ->
            KeyButton(label = symbol, onClick = { onCommit(symbol) }, modifier = Modifier.weight(1f))
        }
        KeyButton(label = null, icon = Icons.Default.Backspace, onClick = onBackspace, modifier = Modifier.weight(1.5f))
    }
    Spacer(modifier = Modifier.height(4.dp))
    BottomRow(onMode = onMode, onCommit = onCommit, onBackspace = onBackspace, onEnter = onEnter, showSymbolsKey = true)
}

@Composable
private fun MathLayer(
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onMode: (KeyboardMode) -> Unit
) {
    // Shared verifier-compatible symbols (pretty display, ASCII insert).
    MATH_SYMBOLS.chunked(5).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { symbol ->
                KeyButton(
                    label = symbol.display,
                    onClick = { onCommit(symbol.insert) },
                    modifier = Modifier.weight(1f),
                    mono = true
                )
            }
            repeat(5 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    BottomRow(onMode = onMode, onCommit = onCommit, onBackspace = onBackspace, onEnter = onEnter, showSymbolsKey = false)
}

@Composable
private fun QuizLayer(
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onMode: (KeyboardMode) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("A", "B", "C", "D").forEach { answer ->
            KeyButton(
                label = answer,
                onClick = { onCommit(answer) },
                modifier = Modifier.weight(1f).height(56.dp),
                highlighted = true
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyButton(label = "True", onClick = { onCommit("True") }, modifier = Modifier.weight(1f), small = true)
        KeyButton(label = "False", onClick = { onCommit("False") }, modifier = Modifier.weight(1f), small = true)
        KeyButton(label = null, icon = Icons.Default.Backspace, onClick = onBackspace, modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(4.dp))
    BottomRow(onMode = onMode, onCommit = onCommit, onBackspace = onBackspace, onEnter = onEnter, showSymbolsKey = false)
}

@Composable
private fun BottomRow(
    onMode: (KeyboardMode) -> Unit,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    showSymbolsKey: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showSymbolsKey) {
            KeyButton(label = "?123", onClick = { onMode(KeyboardMode.SYMBOLS) }, modifier = Modifier.weight(1.3f), small = true)
        } else {
            KeyButton(label = "ABC", onClick = { onMode(KeyboardMode.QWERTY) }, modifier = Modifier.weight(1.3f), small = true)
        }
        KeyButton(label = "∑", onClick = { onMode(KeyboardMode.MATH) }, modifier = Modifier.weight(1f))
        KeyButton(label = "A•", onClick = { onMode(KeyboardMode.QUIZ) }, modifier = Modifier.weight(1f), small = true)
        KeyButton(label = "space", onClick = { onCommit(" ") }, modifier = Modifier.weight(3f), small = true)
        KeyButton(label = ".", onClick = { onCommit(".") }, modifier = Modifier.weight(0.9f))
        KeyButton(label = null, icon = Icons.Default.SubdirectoryArrowLeft, onClick = onEnter, modifier = Modifier.weight(1.2f), highlighted = true)
    }
}

@Composable
private fun KeyboardRow(keys: List<String>, onCommit: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { label ->
            KeyButton(label = label, onClick = { onCommit(label) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyButton(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    small: Boolean = false,
    mono: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = when {
            highlighted -> TealAccent.copy(alpha = 0.22f)
            label == "space" -> NearBlackElevated
            else -> NearBlackSurface
        },
        border = BorderStroke(1.dp, if (highlighted) TealAccent.copy(alpha = 0.6f) else SubtleBorder),
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(
                    icon,
                    contentDescription = label,
                    tint = if (highlighted) TealAccent else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                else -> Text(
                    text = label ?: "",
                    color = if (highlighted) TealAccent else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (small) 12.sp else 16.sp,
                    fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
                )
            }
        }
    }
}
