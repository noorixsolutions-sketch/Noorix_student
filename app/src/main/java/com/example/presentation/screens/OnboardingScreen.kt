@file:OptIn(ExperimentalLayoutApi::class)

package com.example.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.QuestionItem
import com.example.domain.onboarding.AgeBand
import com.example.domain.onboarding.AgeBandProfile
import com.example.domain.onboarding.OnboardingData
import com.example.domain.onboarding.OnboardingPhase
import com.example.presentation.OLevelViewModel
import com.example.presentation.util.UiCopy
import com.example.ui.theme.*
import kotlin.math.min

/**
 * Two-mode onboarding (docs/two-mode-experience.md §4).
 *
 * Conversational welcome → one of two paths:
 *  - Explore something  → zero friction, straight into Explore mode.
 *  - Build my learning plan → tiny age-adaptive profile → embedded first
 *    check (a number game / mark-free check-in, never an entrance exam) →
 *    friendly "you're all set" preview.
 */
@Composable
fun OnboardingScreen(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 20.dp)
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = {
                fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
            },
            label = "OnboardingPhaseTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetPhase ->
            when (targetPhase) {
                OnboardingPhase.WELCOME -> WelcomePhase(
                    onExplore = { viewModel.chooseExploreMode() },
                    onGuided = { viewModel.beginGuidedOnboarding() }
                )
                OnboardingPhase.GUIDED_PROFILE -> GuidedProfilePhase(viewModel)
                OnboardingPhase.GUIDED_FIRST_CHECK -> GuidedFirstCheckPhase(viewModel)
                OnboardingPhase.GUIDED_READY -> GuidedReadyPhase(viewModel) {
                    viewModel.finishOnboarding()
                }
                OnboardingPhase.COMPLETE -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 0 — Conversational welcome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomePhase(onExplore: () -> Unit, onGuided: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = CircleShape,
            color = TealAccentSubtle,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.School, contentDescription = null, tint = TealAccent, modifier = Modifier.size(38.dp))
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Hi, I'm here to help you learn.",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Ask me about anything you're studying.\nOr, if you'd like, we can make a learning plan together and I'll keep track of what you're learning and help you progress.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Path 1 — Explore
        OnboardingPathCard(
            icon = { Icon(Icons.Default.Explore, contentDescription = null, tint = TealAccent, modifier = Modifier.size(26.dp)) },
            title = "Explore something",
            subtitle = "Learn anything, anytime. No sign-up, no pressure.",
            accent = TealAccent,
            onClick = onExplore
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Path 2 — Guided program
        OnboardingPathCard(
            icon = { Icon(Icons.Default.Route, contentDescription = null, tint = CyberGold, modifier = Modifier.size(26.dp)) },
            title = "Build my learning plan",
            subtitle = "I'll guide you step by step and help you progress.",
            accent = CyberGold,
            onClick = onGuided
        )

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "You can change this anytime — there's no wrong choice.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPathCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NearBlackSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1b — Tiny, age-adaptive profile (guided path only)
// ---------------------------------------------------------------------------

@Composable
private fun GuidedProfilePhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    var name by remember(state.learnerName) { mutableStateOf(state.learnerName) }
    var age by remember(state.age) { mutableIntStateOf(state.age ?: 0) }
    var stage by remember(state.stage) { mutableStateOf(state.stage) }
    var targetGrade by remember(state.targetGrade) { mutableStateOf(state.targetGrade.ifBlank { "A*" }) }
    var examSession by remember(state.targetExamSession) { mutableStateOf(state.targetExamSession.ifBlank { OnboardingData.examSessionOptions.first() }) }
    var minutes by remember(state.dailyTargetMinutes) { mutableIntStateOf(state.dailyTargetMinutes) }

    // The interaction model adapts to the age chosen — the form literally grows
    // or shrinks, and the tone changes with the band.
    val band = age.takeIf { it > 0 }?.let { AgeBand.fromAge(it) }
    val profile = band?.let { AgeBandProfile.of(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        TextButton(onClick = { viewModel.startOnboardingPhase(OnboardingPhase.WELCOME) }) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back", color = TextSecondary)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (profile != null) "${profile.emoji} ${profile.profileTitle}" else "👋 Let's set up your learning journey",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (profile != null) profile.profileSub else "A couple of questions so I know how to help you.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Name (optional)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name (or a nickname you like)") },
            placeholder = { Text("e.g. Ali") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = SubtleBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = TealAccent,
                unfocusedLabelColor = TextSecondary
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Age chips
        Text("How old are you?", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val defaultOptions = listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            defaultOptions.forEach { optionAge ->
                val isSelected = age == optionAge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) TealAccent else NearBlackSurface,
                    border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { age = optionAge }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$optionAge",
                            color = if (isSelected) NearBlackBg else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Stage — revealed only once the age is known (the form adapts to the band)
        Text("What's your stage?", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (profile == null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NearBlackSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pick your age first — then I'll ask about your stage.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profile.stageOptions.forEach { stageOption ->
                    val isSelected = stage == stageOption
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) TealAccentSubtle else NearBlackSurface,
                        border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stage = stageOption }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(if (isSelected) 8.dp else 0.dp))
                            Text(
                                text = stageOption,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // O-Level-only fields — younger bands never see target grade / exam / minutes
        if (profile != null && profile.showTargetGrade) {
            Spacer(modifier = Modifier.height(18.dp))
            Text("The grade you're aiming for", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingData.targetGradeOptions.forEach { tGrade ->
                    val isSelected = targetGrade == tGrade
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) CyberGold else NearBlackSurface,
                        border = BorderStroke(1.dp, if (isSelected) CyberGold else SubtleBorder),
                        modifier = Modifier.weight(1f).clickable { targetGrade = tGrade }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(tGrade, color = if (isSelected) NearBlackBg else TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (profile != null && profile.showExamSession) {
            Spacer(modifier = Modifier.height(18.dp))
            Text("Your next exam", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnboardingData.examSessionOptions.chunked(2).forEach { rowSessions ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSessions.forEach { sessionOption ->
                            val isSelected = examSession == sessionOption
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) StepCorrect.copy(alpha = 0.15f) else NearBlackSurface,
                                border = BorderStroke(1.dp, if (isSelected) StepCorrect else SubtleBorder),
                                modifier = Modifier.weight(1f).clickable { examSession = sessionOption }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sessionOption,
                                        color = if (isSelected) StepCorrect else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (profile != null && profile.showDailyMinutes) {
            Spacer(modifier = Modifier.height(18.dp))
            Text("How many minutes a day can you give? (changeable later)", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingData.dailyMinutesOptions.forEach { m ->
                    val isSelected = minutes == m
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) TealAccentSubtle else NearBlackSurface,
                        border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                        modifier = Modifier.weight(1f).clickable { minutes = m }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "$m min",
                                color = if (isSelected) TealAccent else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val selectedAge = age
                viewModel.updateGuidedProfile(
                    name = name.trim(),
                    age = selectedAge,
                    stage = stage,
                    targetGrade = if (profile?.showTargetGrade == true) targetGrade else "",
                    targetExamSession = if (profile?.showExamSession == true) examSession else "",
                    dailyMinutes = minutes
                )
                viewModel.startOnboardingPhase(OnboardingPhase.GUIDED_FIRST_CHECK)
            },
            enabled = age != 0 && stage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = profile?.profileCta ?: "Continue",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1c — The embedded first check (assessment as learning, not an exam)
// ---------------------------------------------------------------------------

@Composable
private fun GuidedFirstCheckPhase(viewModel: OLevelViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    val profile = state.profile
    val session by viewModel.diagnosticSession.collectAsState()
    val activePkg by viewModel.activePackage.collectAsState()
    // Recompute when the course package (secondary bank) finishes loading.
    val bankReady = remember(activePkg) { viewModel.diagnosticBankReady() }

    // Move on automatically once the check-in is complete.
    LaunchedEffect(session.isCompleted) {
        if (session.isCompleted) viewModel.startOnboardingPhase(OnboardingPhase.GUIDED_READY)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp)
    ) {
        // Age-framed intro — a number game for a five-year-old, a mark-free
        // check-in for a fifteen-year-old. Same engine underneath.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(profile.checkEmoji, fontSize = 34.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = profile.checkIntro,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = profile.checkSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !bankReady -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TealAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("I'm preparing your questions…", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("One moment — I want them to fit you.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.finishOnboarding() }) {
                    Text("Skip for now — we can do this later", color = TextMuted)
                }
            }

            else -> {
                val nextQuestion = remember(session, bankReady) {
                    if (bankReady) viewModel.getNextDiagnosticQuestion() else null
                }

                // Bank exhausted before the confidence stop: treat as complete.
                LaunchedEffect(nextQuestion, session) {
                    if (!session.isCompleted && nextQuestion == null) {
                        viewModel.startOnboardingPhase(OnboardingPhase.GUIDED_READY)
                    }
                }

                nextQuestion?.let { question ->
                    CheckinQuestionCard(
                        question = question,
                        questionNumber = min(session.itemsAnswered.size + 1, 6),
                        showLetters = profile.band == AgeBand.SECONDARY,
                        onSubmit = { selected -> viewModel.submitDiagnosticItem(question, selected) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckinQuestionCard(
    question: QuestionItem,
    questionNumber: Int,
    showLetters: Boolean,
    onSubmit: (selectedIndex: Int) -> Unit
) {
    var selectedOption by remember(question.id) { mutableIntStateOf(-1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = TealAccentSubtle) {
                    Text(
                        text = "Question $questionNumber",
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text("No marks here", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = question.prompt,
                style = if (showLetters) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (showLetters) 24.sp else 32.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            question.options.forEachIndexed { optIndex, optionText ->
                val isSelected = selectedOption == optIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) TealAccentSubtle else NearBlackBg,
                    border = BorderStroke(1.dp, if (isSelected) TealAccent else SubtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable { selectedOption = optIndex }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showLetters) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) TealAccent else NearBlackElevated,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = ('A' + optIndex).toString(),
                                        color = if (isSelected) NearBlackBg else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (selectedOption != -1) {
                        onSubmit(selectedOption)
                        selectedOption = -1
                    }
                },
                enabled = selectedOption != -1,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = NearBlackBg)
            ) {
                Text("Next", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1d — "You're all set"
// ---------------------------------------------------------------------------

@Composable
private fun GuidedReadyPhase(viewModel: OLevelViewModel, onFinish: () -> Unit) {
    val state by viewModel.onboardingState.collectAsState()
    val profile = state.profile
    val planFlow by viewModel.activePlan.collectAsState()
    val plan = planFlow // local copy: smart-cast safe
    val session by viewModel.diagnosticSession.collectAsState()

    val name = state.learnerName.trim()
    val gapsCount = if (session.isCompleted) session.identifiedGaps.size else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = CircleShape,
            color = StepCorrect.copy(alpha = 0.15f),
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StepCorrect, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (name.isNotBlank()) "${profile.readyTitle} ${name}!" else profile.readyTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.readySub,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        if (plan != null) {
            Card(
                modifier = Modifier.fillMaxWidth(0.94f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Here's where we'll start",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "First up: ${plan.targetConceptName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = UiCopy.formatReplan(plan.interventionType),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    plan.planStages.take(3).forEach { stageItem ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TealAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stageItem.stageTitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (gapsCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "We'll build on $gapsCount small idea${if (gapsCount == 1) "" else "s"} as we go — no pressure, no marks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(0.94f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Here's how we'll work",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "I'll shape your plan as we learn together — your first activities give me what I need to point you the right way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(0.94f),
            colors = ButtonDefaults.buttonColors(containerColor = StepCorrect, contentColor = NearBlackBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Let's get started", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { viewModel.startOnboardingPhase(OnboardingPhase.WELCOME) }) {
            Text("Change my choices", color = TextMuted)
        }
    }
}
