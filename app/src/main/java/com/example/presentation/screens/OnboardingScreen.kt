package com.example.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.onboarding.OnboardingData
import com.example.domain.onboarding.OnboardingPhase
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

@Composable
fun OnboardingScreen(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            },
            label = "OnboardingPhase"
        ) { targetPhase ->
            when (targetPhase) {
                OnboardingPhase.WELCOME -> WelcomePhase { viewModel.startOnboardingPhase(OnboardingPhase.TEST_A_PSYCHOMETRIC) }
                OnboardingPhase.TEST_A_PSYCHOMETRIC -> TestAPhase(viewModel)
                OnboardingPhase.TEST_B_COGNITIVE -> TestBPhase(viewModel)
                OnboardingPhase.TEST_C_CURRICULUM -> TestCPhase(viewModel)
                OnboardingPhase.COMPLETE -> CompletePhase { viewModel.startOnboardingPhase(OnboardingPhase.COMPLETE) }
            }
        }
    }
}

@Composable
fun WelcomePhase(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Science, contentDescription = null, tint = TealAccent, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Noorix Learning Runtime", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to the personalized O-Level academic platform. Before we begin, we need to establish your baseline profile across three dimensions:\n\n1. Psychometric Signals\n2. Cognitive Reasoning\n3. Curriculum Mastery",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
        ) {
            Text("Begin Initialization", color = NearBlackBg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestAPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    val question = OnboardingData.testA[state.testAIndex]

    Column(
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Text("Test A: Psychometric Baseline", color = GoldAccent, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (state.testAIndex.toFloat() / OnboardingData.testA.size) },
            modifier = Modifier.fillMaxWidth(),
            color = TealAccent,
            trackColor = NearBlackSurface
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(question.text, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))

        listOf(
            1 to "Strongly Disagree",
            2 to "Disagree",
            3 to "Neutral",
            4 to "Agree",
            5 to "Strongly Agree"
        ).forEach { (score, label) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.submitTestAAnswer(score) },
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = borderStroke()
            ) {
                Text(label, color = TextPrimary, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestBPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    val question = OnboardingData.testB[state.testBIndex]

    Column(
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Text("Test B: Cognitive Reasoning Baseline", color = CyanAccent, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (state.testBIndex.toFloat() / OnboardingData.testB.size) },
            modifier = Modifier.fillMaxWidth(),
            color = CyanAccent,
            trackColor = NearBlackSurface
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(question.text, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))

        question.options.forEachIndexed { index, option ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.submitTestBAnswer(index) },
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = borderStroke()
            ) {
                Text(option, color = TextPrimary, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun TestCPhase(viewModel: OLevelViewModel) {
    val session by viewModel.diagnosticSession.collectAsState()
    val activePackage by viewModel.activePackage.collectAsState()
    // FIX (onboarding stuck): wait for the signed course package (question
    // bank) instead of running the diagnostic against an empty bank.
    val bankReady = activePackage != null

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Test C: Curriculum Diagnostic", color = TealAccent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        when {
            session.isCompleted ->
                CompletePhase { viewModel.startOnboardingPhase(OnboardingPhase.COMPLETE) }

            !bankReady ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = TealAccent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Preparing question bank…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

            else -> {
                // Re-use the existing diagnostic screen layout logic
                Box(modifier = Modifier.weight(1f)) {
                    DiagnosticScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CompletePhase(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(0.9f).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48BB78), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Calibration Complete", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your baseline signals have been recorded. The gap engine will now use these priors to sequence your academic interventions.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF48BB78))
        ) {
            Text("Enter Dashboard", color = NearBlackBg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
