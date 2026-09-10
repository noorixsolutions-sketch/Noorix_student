package com.example.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
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
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "OnboardingPhaseTransition"
        ) { targetPhase ->
            when (targetPhase) {
                OnboardingPhase.WELCOME -> WelcomePhase {
                    viewModel.startOnboardingPhase(OnboardingPhase.TEST_A_PSYCHOMETRIC)
                }
                OnboardingPhase.TEST_A_PSYCHOMETRIC -> TestAPhase(viewModel)
                OnboardingPhase.TEST_B_COGNITIVE -> TestBPhase(viewModel)
                OnboardingPhase.TEST_C_CURRICULUM -> TestCPhase(viewModel)
                OnboardingPhase.COMPLETE -> CompletePhase {
                    viewModel.startOnboardingPhase(OnboardingPhase.COMPLETE)
                }
            }
        }
    }
}

@Composable
fun WelcomePhase(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = TealAccentSubtle,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.School, contentDescription = null, tint = TealAccent, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Noorix Learning Runtime",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Next-generation Cambridge O-Level study system with AI Mentors, deterministic Math Lab, and adaptive diagnostics.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NearBlackSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OnboardingBenefitItem("1. Psychometric Signals", "Calibrate persistence, metacognition & help-seeking", TealAccent)
                OnboardingBenefitItem("2. Cognitive Reasoning", "Map working memory & spatial logic priors", StepCorrect)
                OnboardingBenefitItem("3. Adaptive Diagnostic", "Detect prerequisite gaps without whole-bank tests", CyberGold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Begin Calibration Initialization", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun OnboardingBenefitItem(title: String, desc: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun TestAPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    val question = OnboardingData.testA[state.testAIndex]

    Column(
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("STEP 1 OF 3: PSYCHOMETRIC BASELINE", color = TealAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${state.testAIndex + 1} / ${OnboardingData.testA.size}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (state.testAIndex.toFloat() / OnboardingData.testA.size) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = TealAccent,
            trackColor = NearBlackSurface
        )

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        val likertOptions = listOf(
            1 to "1 • Strongly Disagree",
            2 to "2 • Disagree",
            3 to "3 • Neutral",
            4 to "4 • Agree",
            5 to "5 • Strongly Agree"
        )

        likertOptions.forEach { (score, label) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NearBlackSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.submitTestAAnswer(score) }
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun TestBPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    val question = OnboardingData.testB[state.testBIndex]

    Column(
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("STEP 2 OF 3: COGNITIVE REASONING", color = StepCorrect, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${state.testBIndex + 1} / ${OnboardingData.testB.size}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (state.testBIndex.toFloat() / OnboardingData.testB.size) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = StepCorrect,
            trackColor = NearBlackSurface
        )

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        question.options.forEachIndexed { index, option ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NearBlackSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.submitTestBAnswer(index) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NearBlackElevated,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(('A' + index).toString(), color = StepCorrect, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = option,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun TestCPhase(viewModel: OLevelViewModel) {
    val session by viewModel.diagnosticSession.collectAsState()
    val activePackage by viewModel.activePackage.collectAsState()
    val bankReady = activePackage != null

    Column(modifier = Modifier.fillMaxWidth()) {
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
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = StepCorrect.copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Calibration Complete! 🚀",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your baseline signals and readiness priors are successfully recorded in the immutable sovereign store. Your AI tutors and personalized plan are active.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = StepCorrect, contentColor = NearBlackBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Enter Candidate Dashboard", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
