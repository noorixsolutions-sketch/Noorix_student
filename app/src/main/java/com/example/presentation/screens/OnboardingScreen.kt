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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
                    viewModel.startOnboardingPhase(OnboardingPhase.PROFILE_SETUP)
                }
                OnboardingPhase.PROFILE_SETUP -> ProfileSetupPhase(viewModel)
                OnboardingPhase.TEST_A_PSYCHOMETRIC -> TestAPhase(viewModel)
                OnboardingPhase.TEST_B_COGNITIVE -> TestBPhase(viewModel)
                OnboardingPhase.TEST_C_CURRICULUM -> TestCPhase(viewModel)
                OnboardingPhase.COMPLETE -> CompletePhase(viewModel) {
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
            .verticalScroll(rememberScrollState())
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
            text = "Next-generation Cambridge O-Level study system with AI Mentors, deterministic Math Lab, and continuous closed-loop adaptive calibration.",
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
                OnboardingBenefitItem("1. Profile Calibration", "Calibrated baseline by class/grade, age & target", TealAccent)
                OnboardingBenefitItem("2. Psychometric & Cognitive", "Map working memory, persistence & reasoning priors", StepCorrect)
                OnboardingBenefitItem("3. Closed-Loop Diagnostic", "Synthesize adaptive plan & continuously recalibrate", CyberGold)
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
fun ProfileSetupPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    var name by remember { mutableStateOf(state.candidateName) }
    var selectedGrade by remember { mutableStateOf(state.candidateGrade) }
    var selectedAge by remember { mutableIntStateOf(state.candidateAge) }
    var selectedTargetGrade by remember { mutableStateOf(state.targetGrade) }
    var selectedSession by remember { mutableStateOf(state.targetExamSession) }
    var selectedMinutes by remember { mutableIntStateOf(state.dailyTargetMinutes) }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CALIBRATION STEP 1: CANDIDATE PROFILE", color = TealAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Step 1 of 4", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { 0.25f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = TealAccent,
            trackColor = NearBlackSurface
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Tell us about your academic stage",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your class, age, and target exam session calibrate your initial Bayesian prior ability and expected learning velocity.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Name Field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Candidate Name / Nickname") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = SubtleBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Class / Grade Selection
        Text("Academic Class / Grade", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingData.gradeOptions.forEach { gradeOption ->
                val isSelected = selectedGrade == gradeOption
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) TealAccentSubtle else NearBlackSurface,
                    border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                    modifier = Modifier.fillMaxWidth().clickable { selectedGrade = gradeOption }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedGrade = gradeOption },
                            colors = RadioButtonDefaults.colors(selectedColor = TealAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(gradeOption, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Age & Target Grade Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Student Age", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnboardingData.ageOptions.take(4).forEach { age ->
                        val isSelected = selectedAge == age
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TealAccent else NearBlackSurface,
                            border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                            modifier = Modifier.weight(1f).clickable { selectedAge = age }
                        ) {
                            Text(
                                text = "$age",
                                color = if (isSelected) NearBlackBg else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Target Grade", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnboardingData.targetGradeOptions.forEach { tGrade ->
                        val isSelected = selectedTargetGrade == tGrade
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyberGold else NearBlackSurface,
                            border = BorderStroke(1.dp, if (isSelected) CyberGold else SubtleBorder),
                            modifier = Modifier.weight(1f).clickable { selectedTargetGrade = tGrade }
                        ) {
                            Text(
                                text = tGrade,
                                color = if (isSelected) NearBlackBg else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Exam Session
        Text("Target Exam Session", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingData.examSessionOptions.take(2).forEach { sessionOption ->
                val isSelected = selectedSession == sessionOption
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) StepCorrect.copy(alpha = 0.15f) else NearBlackSurface,
                    border = BorderStroke(1.dp, if (isSelected) StepCorrect else SubtleBorder),
                    modifier = Modifier.weight(1f).clickable { selectedSession = sessionOption }
                ) {
                    Text(
                        text = sessionOption,
                        color = if (isSelected) StepCorrect else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.updateOnboardingProfile(
                    name = name.ifBlank { "Cambridge Candidate" },
                    grade = selectedGrade,
                    age = selectedAge,
                    targetGrade = selectedTargetGrade,
                    targetExamSession = selectedSession,
                    dailyMinutes = selectedMinutes
                )
                viewModel.startOnboardingPhase(OnboardingPhase.TEST_A_PSYCHOMETRIC)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Proceed to Psychometric Baseline", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
            Text("STEP 2 OF 4: PSYCHOMETRIC BASELINE", color = TealAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${state.testAIndex + 1} / ${OnboardingData.testA.size}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { 0.25f + 0.25f * ((state.testAIndex + 1).toFloat() / OnboardingData.testA.size) },
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
            Text("STEP 3 OF 4: COGNITIVE REASONING", color = StepCorrect, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${state.testBIndex + 1} / ${OnboardingData.testB.size}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { 0.50f + 0.25f * ((state.testBIndex + 1).toFloat() / OnboardingData.testB.size) },
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
                CompletePhase(viewModel) { viewModel.startOnboardingPhase(OnboardingPhase.COMPLETE) }

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
fun CompletePhase(viewModel: OLevelViewModel, onFinish: () -> Unit) {
    val studentModel by viewModel.studentModel.collectAsState()
    val plan by viewModel.activePlan.collectAsState()
    val session by viewModel.diagnosticSession.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = StepCorrect.copy(alpha = 0.15f),
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(38.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Calibration Initialized! 🚀",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your multi-factor profile baseline has been benchmarked and synthesized into an adaptive study plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Multi-Factor Profile Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MULTI-FACTOR BASELINE CALIBRATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Candidate Stage:", color = TextSecondary, fontSize = 12.sp)
                    Text("${studentModel.profileCalibration.studentGrade} (Age ${studentModel.profileCalibration.studentAge})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Grade & Session:", color = TextSecondary, fontSize = 12.sp)
                    Text("${studentModel.profileCalibration.targetGrade} • ${studentModel.profileCalibration.targetExamSession}", color = CyberGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Calibrated Ability (θ):", color = TextSecondary, fontSize = 12.sp)
                    Text("${(session.estimatedAbility * 100).toInt()}% IRT θ (Prior: ${(session.priorTheta * 100).toInt()}%)", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Baseline Velocity Prior:", color = TextSecondary, fontSize = 12.sp)
                    Text("+${String.format("%.2f", studentModel.profileCalibration.baselineVelocity)} mastery/session", color = StepCorrect, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Identified Syllabus Gaps:", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        if (session.identifiedGaps.isEmpty()) "None (Direct Mastery Track)" else "${session.identifiedGaps.size} concept(s) isolated",
                        color = if (session.identifiedGaps.isEmpty()) StepCorrect else CyberGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synthesized Adaptive Plan Preview
        if (plan != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SYNTHESIZED ADAPTIVE PLAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = StepCorrect,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Surface(shape = RoundedCornerShape(4.dp), color = StepCorrect.copy(alpha = 0.15f)) {
                            Text(plan?.interventionType ?: "WORKED_EXAMPLES", color = StepCorrect, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Text(
                        text = "Target: ${plan?.targetConceptName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    plan?.planStages?.take(3)?.forEach { stage ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowRight, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stage.stageTitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
