package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.MathErrorType
import com.example.core.model.Step
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldAccent

@Composable
fun SymbolicTutorScreen(viewModel: OLevelViewModel) {
    val verificationResult by viewModel.verificationResult.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()

    var problemInput by remember { mutableStateOf("x(x+2)") }
    var step1Input by remember { mutableStateOf("x^2 + 2x") }
    var step2Input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // 1. Student Friendly Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_danish_math),
                            contentDescription = "Danish AI Tutor",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, CyanAccent, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CAMBRIDGE MATH LAB • MENTORED BY DANISH",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Step-by-Step Problem Solver",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Write your algebraic steps line-by-line. Your AI tutor verifies every step mathematically and immediately highlights sign flips or expansion errors so you never lose marks on Cambridge exams.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Preset Cambridge Problems Selector
        item {
            Column {
                Text(
                    text = "Quick Cambridge Practice Problems:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            problemInput = "x(x+2)"
                            step1Input = "x^2 + 2x"
                        },
                        label = { Text("x(x+2)", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            problemInput = "2x^2 + 8x"
                            step1Input = "2x(x+4)"
                        },
                        label = { Text("2x² + 8x", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            problemInput = "x(x+2)"
                            step1Input = "x^2 + 3x" // test sign error
                        },
                        label = { Text("Test Sign Error", fontSize = 12.sp, color = Color(0xFFFF9999)) }
                    )
                }
            }
        }

        // 3. Problem & Step Workspace
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("math_lab_workspace"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PROBLEM EXPRESSION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = problemInput,
                        onValueChange = { problemInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("problem_input_field"),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "STEP 1 (YOUR WORKING):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = step1Input,
                        onValueChange = { step1Input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("step1_input_field"),
                        placeholder = { Text("e.g. x^2 + 2x") },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Virtual Math Helper Toolbar
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Math Symbols:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("^2", "+", "-", "(", ")", "*", "/", "x").forEach { symbol ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    step1Input += symbol
                                }
                            ) {
                                Text(
                                    text = symbol,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val steps = mutableListOf(Step(1, step1Input))
                            if (step2Input.isNotEmpty()) {
                                steps.add(Step(2, step2Input))
                            }
                            viewModel.verifyCustomMathSteps(problemInput, steps, "concept:4024:factorisation")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_math_steps_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify My Algebraic Steps", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Live Tutor Feedback Card
        verificationResult?.let { res ->
            item {
                val isSuccess = res.isValid
                val cardBg = if (isSuccess) Color(0xFF0D2818) else Color(0xFF331414)
                val borderColor = if (isSuccess) Color(0xFF48BB78) else Color(0xFFFF5252)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("math_feedback_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = borderColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSuccess) "Step Verified Correct! 🎉" else "Algebraic Issue Spotted ⚠️",
                                fontWeight = FontWeight.ExtraBold,
                                color = borderColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isSuccess) {
                            Text(
                                text = "Your working is mathematically equivalent to the problem expression. Proven valid across all test points. Excellent Cambridge exam technique!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0)
                            )
                        } else {
                            val tipText = when (res.errorType) {
                                MathErrorType.SIGN_ERROR -> "Tutor Tip: You have a sign or coefficient mismatch in Step ${res.firstInvalidStepIndex}. Double-check your multiplication when expanding terms."
                                MathErrorType.EXPANSION_ERROR -> "Tutor Tip: Parentheses expansion error in Step ${res.firstInvalidStepIndex}. Remember that each term inside the bracket must be multiplied."
                                MathErrorType.FACTORISATION_ERROR -> "Tutor Tip: Common factor not fully factored out in Step ${res.firstInvalidStepIndex}."
                                else -> "Tutor Tip: Step ${res.firstInvalidStepIndex} does not equal the previous line. Recheck your arithmetic."
                            }
                            Text(
                                text = tipText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFE0E0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33000000)
                        ) {
                            Text(
                                text = res.symbolicTrace.lines().take(4).joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFFA0AEC0),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Notebook Handwriting Scanner Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan Notebook Handwritten Working",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Snap a photo of your paper working. The AI OCR scans your handwriting and automatically flags any messy or unclear steps before submitting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Simulate clean student notebook page
                                val lines = listOf(
                                    "x(x+2)" to 0.95,
                                    "x^2 + 2x" to 0.92
                                )
                                viewModel.runNotebookOcr(lines)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Scan Clean Working", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                // Simulate blurry or unreadable student handwriting
                                val lines = listOf(
                                    "x(x+2)" to 0.90,
                                    "x?2 + ????" to 0.42 // Below 0.70 floor
                                )
                                viewModel.runNotebookOcr(lines)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Test Blurry Handwriting", fontSize = 11.sp, color = Color(0xFFFF9999))
                        }
                    }

                    ocrResult?.let { ocr ->
                        val hasUnclear = ocr.regions.any { it.isUnclear }
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Scan Status: ${if (hasUnclear) "⚠️ Re-take photo (handwriting unclear)" else "✅ Handwriting clean & verified"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (hasUnclear) Color(0xFFFF8080) else Color(0xFF68D391)
                                )
                                Text(
                                    text = ocr.rawTrace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
