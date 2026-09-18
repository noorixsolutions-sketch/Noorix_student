package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Shared math-keyboard concept — ADDITIVE.
 *
 * A cursor-aware symbol accessory that sits above the system keyboard. Used
 * by the tutor chat input and the Math Lab workspace so students can type
 * algebra without hunting for symbols.
 *
 * Each symbol has a pretty [display] label and a verifier-compatible
 * [insert] payload (the symbolic engine parses ASCII forms like `^2`).
 */
data class MathSymbol(val display: String, val insert: String)

/** Display labels are student-facing; inserts stay verifier-compatible. */
val MATH_SYMBOLS: List<MathSymbol> = listOf(
    MathSymbol("x", "x"),
    MathSymbol("y", "y"),
    MathSymbol("x²", "^2"),
    MathSymbol("x³", "^3"),
    MathSymbol("xⁿ", "^"),
    MathSymbol("√", "sqrt("),
    MathSymbol("π", "pi"),
    MathSymbol("+", "+"),
    MathSymbol("−", "-"),
    MathSymbol("×", "*"),
    MathSymbol("÷", "/"),
    MathSymbol("=", "="),
    MathSymbol("(", "("),
    MathSymbol(")", ")"),
    MathSymbol("≤", "<="),
    MathSymbol("≥", ">="),
    MathSymbol("≠", "!="),
    MathSymbol("±", "+/-"),
    MathSymbol(".", "."),
    MathSymbol(",", ", ")
)

/** Inserts [token] at the cursor (replacing any selection) — cursor-aware. */
fun insertToken(value: TextFieldValue, token: String): TextFieldValue {
    val selection = value.selection
    val text = value.text
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val newText = text.substring(0, minOf(start, end)) + token + text.substring(maxOf(start, end))
    val newCursor = minOf(start, end) + token.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}

/** Deletes one character before the cursor (or the current selection). */
fun backspaceToken(value: TextFieldValue): TextFieldValue {
    val selection = value.selection
    val text = value.text
    if (text.isEmpty()) return value
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    if (start != end) {
        val newText = text.substring(0, minOf(start, end)) + text.substring(maxOf(start, end))
        val cursor = minOf(start, end)
        return value.copy(text = newText, selection = TextRange(cursor))
    }
    if (start == 0) return value
    val newText = text.substring(0, start - 1) + text.substring(start)
    return value.copy(text = newText, selection = TextRange(start - 1))
}

/**
 * Collapsible math symbol strip. Rendered above the text input; [expanded]
 * is hoisted so each screen owns its toggle state.
 */
@Composable
fun MathKeyboardStrip(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Calculate,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MATH KEYBOARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (expanded) "Collapse math keyboard" else "Expand math keyboard",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MATH_SYMBOLS.forEach { symbol ->
                        MathKey(display = symbol.display, onClick = { onInsert(symbol.insert) })
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealAccentSubtle,
                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onBackspace() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            Icons.Default.Backspace,
                            contentDescription = "Delete",
                            tint = TealAccent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MathKey(display: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NearBlackBg,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Text(
                text = display,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}
