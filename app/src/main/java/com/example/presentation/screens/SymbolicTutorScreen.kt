package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.MathErrorType
import com.example.core.model.Step
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.MATH_SYMBOLS
import com.example.presentation.components.NoorixCameraCaptureDialog
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*

@Composable
fun SymbolicTutorScreen(viewModel: OLevelViewModel) {
    val verificationResult by viewModel.verificationResult.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()
    val isOcrScanning by viewModel.isOcrScanning.collectAsState()

    var problemInput by remember { mutableStateOf("x(x+2)") }
    var stepsList by remember { mutableStateOf(listOf("x^2 + 2x")) }
    var activeStepFocusIndex by remember { mutableIntStateOf(0) }
    var showCameraCapture by remember { mutableStateOf(false) }

    if (showCameraCapture) {
        NoorixCameraCaptureDialog(
            onDismiss = { showCameraCapture = false },
            onBitmapCaptured = { bitmap ->
                viewModel.processCapturedBitmap(bitmap)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Danish Math Lab Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_danish_math),
                            contentDescription = "Danish AI Math Tutor",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, TealAccent, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CAMBRIDGE MATH LAB • DANISH",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Step-by-Step Symbolic Verifier",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Write your algebraic working line-by-line. The deterministic engine verifies every algebraic transformation and highlights sign flips or bracket expansion traps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Preset Cambridge Problems
        item {
            Column {
                Text(
                    text = "QUICK PRACTICE PROBLEMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            problemInput = "x(x+2)"
                            stepsList = listOf("x^2 + 2x")
                            activeStepFocusIndex = 0
                        },
                        label = { Text("x(x+2)", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = NearBlackSurface, labelColor = TextPrimary),
                        border = BorderStroke(1.dp, SubtleBorder)
                    )
                    SuggestionChip(
                        onClick = {
                            problemInput = "2x^2 + 8x"
                            stepsList = listOf("2x(x+4)")
                            activeStepFocusIndex = 0
                        },
                        label = { Text("2x² + 8x", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = NearBlackSurface, labelColor = TextPrimary),
                        border = BorderStroke(1.dp, SubtleBorder)
                    )
                    SuggestionChip(
                        onClick = {
                            problemInput = "x(x+2)"
                            stepsList = listOf("x^2 + 3x") // Intentional sign trap
                            activeStepFocusIndex = 0
                        },
                        label = { Text("Sign Error Trap", fontSize = 11.sp, color = StepIssue) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = NearBlackSurface, labelColor = StepIssue),
                        border = BorderStroke(1.dp, StepIssue.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // 3. Problem & Multi-Step Working Editor
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("math_lab_workspace"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PROBLEM EXPRESSION / EQUATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = problemInput,
                        onValueChange = { problemInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("problem_input_field"),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NearBlackBg,
                            unfocusedContainerColor = NearBlackBg,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = SubtleBorder
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "WORKING STEPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    stepsList.forEachIndexed { index, stepText ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (activeStepFocusIndex == index) TealAccentSubtle else NearBlackBg,
                                border = BorderStroke(1.dp, if (activeStepFocusIndex == index) TealAccent else SubtleBorder),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (activeStepFocusIndex == index) TealAccent else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = stepText,
                                onValueChange = { newText ->
                                    val updated = stepsList.toMutableList()
                                    updated[index] = newText
                                    stepsList = updated
                                    activeStepFocusIndex = index
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("step_${index + 1}_input"),
                                placeholder = { Text("e.g. x^2 + 2x", color = TextMuted) },
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                ),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = NearBlackBg,
                                    unfocusedContainerColor = NearBlackBg,
                                    focusedBorderColor = TealAccent,
                                    unfocusedBorderColor = SubtleBorder
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            if (stepsList.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        val updated = stepsList.toMutableList()
                                        updated.removeAt(index)
                                        stepsList = updated
                                        if (activeStepFocusIndex >= stepsList.size) {
                                            activeStepFocusIndex = stepsList.size - 1
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                stepsList = stepsList + ""
                                activeStepFocusIndex = stepsList.size - 1
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Next Step", color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${stepsList.size} step(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // Virtual Math Symbol Keyboard
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MATH SYMBOLS PALETTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Shared math-keyboard symbols (pretty display, verifier-compatible insert).
                        MATH_SYMBOLS.forEach { mathSymbol ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier
                                    .clickable {
                                        val targetIdx = activeStepFocusIndex.coerceIn(0, stepsList.size - 1)
                                        val updated = stepsList.toMutableList()
                                        updated[targetIdx] = updated[targetIdx] + mathSymbol.insert
                                        stepsList = updated
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = mathSymbol.display,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TealAccent
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification CTA button
                    Button(
                        onClick = {
                            val steps = stepsList
                                .filter { it.isNotBlank() }
                                .mapIndexed { idx, expr -> Step(stepIndex = idx + 1, expression = expr) }
                            viewModel.verifyCustomMathSteps(problemInput, steps, "concept:4024:factorisation")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_math_steps_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify Math Working", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Deterministic Verification Feedback Card
        verificationResult?.let { res ->
            item {
                val isSuccess = res.isValid
                val borderColor = if (isSuccess) StepCorrect else StepIssue

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_result_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.5.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = borderColor.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = borderColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isSuccess) "VERIFIED AS MATHEMATICALLY VALID" else "STEP MISMATCH DETECTED",
                                    color = borderColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = if (isSuccess) "All steps preserve equivalence" else "Step ${res.firstInvalidStepIndex ?: 1}: ${UiCopy.formatMathError(res.errorType)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isSuccess) {
                                "Your working is mathematically equivalent to the problem expression across all test evaluations. Excellent Cambridge examination rigor!"
                            } else {
                                UiCopy.formatMathErrorExplanation(res.errorType)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))


                        Button(
                            onClick = {
                                viewModel.selectAgent("danish")
                                viewModel.setTab(AppNavigationTab.AI_AGENTS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NearBlackElevated, contentColor = TealAccent),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discuss this proof with Danish", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 5. Notebook Handwriting Camera Scanner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = TealAccentSubtle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PAPER NOTEBOOK OCR SCANNER",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Scan Handwritten Working",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Snap a photo of your handwritten paper working. On-device ML Kit v2 extracts the equations, and low confidence lines are flagged for review before submission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showCameraCapture = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_camerax_scanner_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open CameraX Live Scanner", fontWeight = FontWeight.Bold)
                    }

                    if (isOcrScanning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Processing handwriting with ML Kit OCR...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TealAccent
                            )
                        }
                    }

                    ocrResult?.let { ocr ->
                        val hasUnclear = ocr.regions.any { it.isUnclear }
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NearBlackBg,
                            border = BorderStroke(1.dp, if (hasUnclear) StepIssue.copy(alpha = 0.5f) else StepCorrect.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (hasUnclear) "⚠️ Note: Some handwritten steps were unclear" else "✅ Handwriting successfully extracted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (hasUnclear) StepIssue else StepCorrect
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ocr.rawTrace,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )

                                if (ocr.extractedSteps.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            problemInput = ocr.extractedSteps.firstOrNull()?.expression ?: problemInput
                                            stepsList = ocr.extractedSteps.drop(1).map { it.expression }.ifEmpty { listOf("x^2 + 2x") }
                                            activeStepFocusIndex = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NearBlackElevated, contentColor = TealAccent),
                                        border = BorderStroke(1.dp, TealAccentSubtle),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("import_ocr_steps_button")
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import ${ocr.extractedSteps.size} Extracted Steps to Workspace", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
