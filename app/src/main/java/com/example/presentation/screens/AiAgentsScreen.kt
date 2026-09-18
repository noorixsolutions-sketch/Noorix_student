package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.MathKeyboardStrip
import com.example.presentation.components.NoorixChatBubble
import com.example.presentation.components.NoorixStreamingBubble
import com.example.presentation.components.SuggestionChipsRow
import com.example.presentation.components.backspaceToken
import com.example.presentation.components.insertToken
import com.example.ui.theme.*

data class NoorixAgent(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val tag: String,
    val imageRes: Int,
    val accentColor: Color,
    val targetTab: AppNavigationTab,
    val promptStarter: String
)

@Composable
fun AiAgentsScreen(viewModel: OLevelViewModel) {
    val selectedAgentId by viewModel.selectedAgentId.collectAsState()
    val streamingText by viewModel.agentStreamingText.collectAsState()
    val isThinking by viewModel.isAgentThinking.collectAsState()

    val agents = remember {
        listOf(
            NoorixAgent(
                id = "danish",
                name = "DANISH",
                role = "Mathematics (4024)",
                description = "Specializes in algebraic step verification, quadratic factorisation & proofs.",
                tag = "Algebra / 4024",
                imageRes = R.drawable.img_danish_math,
                accentColor = TealAccent,
                targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
                promptStarter = "Hello Danish! Please explain how to factorise quadratic expressions step-by-step."
            ),
            NoorixAgent(
                id = "zoya",
                name = "ZOYA",
                role = "Sciences (5054)",
                description = "Expert in Physics kinematic graphs, Newton's laws & conceptual visualization.",
                tag = "Physics / 5054",
                imageRes = R.drawable.img_zoya_science,
                accentColor = StepCorrect,
                targetTab = AppNavigationTab.MEDIA_TRANSFER,
                promptStarter = "Hi Zoya! Can you give me a clear visual breakdown of Newton's laws of motion?"
            ),
            NoorixAgent(
                id = "ayesha",
                name = "AYESHA",
                role = "Language (1123)",
                description = "Enhances reading comprehension, summary writing & formal English essays.",
                tag = "English / 1123",
                imageRes = R.drawable.img_ayesha_language,
                accentColor = Color(0xFF38BDF8),
                targetTab = AppNavigationTab.LEARNING_HUB,
                promptStarter = "Hello Ayesha! How can I structure an effective 10-mark summary in Cambridge English?"
            ),
            NoorixAgent(
                id = "bilal",
                name = "BILAL",
                role = "Aptitude & Logic",
                description = "Builds logical reasoning, number patterns & competitive problem solving.",
                tag = "Logical Reasoning",
                imageRes = R.drawable.img_bilal_aptitude,
                accentColor = Color(0xFFA78BFA),
                targetTab = AppNavigationTab.LEARNING_HUB,
                promptStarter = "Hi Bilal! Can you give me a pattern reasoning challenge to sharpen my thinking?"
            ),
            NoorixAgent(
                id = "exam_coach",
                name = "EXAM COACH",
                role = "Exam Preparation",
                description = "Guides you with past paper tactics, mark schemes, method marks & exam timing.",
                tag = "Past Papers",
                imageRes = R.drawable.img_exam_coach,
                accentColor = CyberGold,
                targetTab = AppNavigationTab.DIAGNOSTIC,
                promptStarter = "Coach, what are the top common pitfalls in Cambridge Paper 1 exams?"
            ),
            NoorixAgent(
                id = "career_guide",
                name = "CAREER GUIDE",
                role = "Academic Advisor",
                description = "Helps you discover the right career paths and A-Level clusters based on strengths.",
                tag = "Career Pathways",
                imageRes = R.drawable.img_career_guide,
                accentColor = Color(0xFFF472B6),
                targetTab = AppNavigationTab.DASHBOARD,
                promptStarter = "Hello Advisor! What subject combination works best for engineering and computer science?"
            )
        )
    }

    val currentAgent = agents.find { it.id == selectedAgentId } ?: agents[0]

    // Chat history from Room Database
    val chatMessages by viewModel.getChatMessages(currentAgent.id).collectAsState(initial = emptyList())

    // When agent is opened, automatically send promptStarter if conversation is empty
    LaunchedEffect(currentAgent.id) {
        viewModel.onAgentOpened(currentAgent.id, currentAgent.promptStarter)
    }

    // TextFieldValue enables cursor-aware insertion from the math keyboard.
    var inputText by remember(currentAgent.id) { mutableStateOf(TextFieldValue("")) }
    var isMathVerificationMode by remember { mutableStateOf(false) }
    var showMathKeyboard by remember(currentAgent.id) { mutableStateOf(currentAgent.id == "danish") }
    val listState = rememberLazyListState()

    // Scroll to bottom when new message arrives or stream updates
    LaunchedEffect(chatMessages.size, streamingText) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
    ) {
        // 1. Agent Selection Header Carousel
        Surface(
            color = NearBlackSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "AI LEARNING AGENTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    letterSpacing = 1.1.sp
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(agents) { agent ->
                        val isSelected = agent.id == currentAgent.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) NearBlackElevated else NearBlackBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) agent.accentColor else SubtleBorder
                            ),
                            modifier = Modifier
                                .clickable { viewModel.selectAgent(agent.id) }
                                .testTag("agent_tab_${agent.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = agent.imageRes),
                                    contentDescription = agent.name,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .border(1.2.dp, if (isSelected) agent.accentColor else Color.Transparent, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) agent.accentColor else TextPrimary
                                    )
                                    Text(
                                        text = agent.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Agent Banner
        Surface(
            color = NearBlackElevated,
            border = BorderStroke(1.dp, currentAgent.accentColor.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = currentAgent.imageRes),
                        contentDescription = currentAgent.name,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, currentAgent.accentColor, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentAgent.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = currentAgent.accentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = currentAgent.accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = currentAgent.role,
                                    color = currentAgent.accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = currentAgent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.clearAgentChat(currentAgent.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Clear Chat",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Chat Messages Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            items(chatMessages, key = { it.messageId }) { message ->
                NoorixChatBubble(
                    message = message,
                    agentName = currentAgent.name,
                    agentAccentColor = currentAgent.accentColor,
                    agentImageRes = currentAgent.imageRes
                )
            }

            if (!streamingText.isNullOrEmpty()) {
                item {
                    NoorixStreamingBubble(
                        text = streamingText!!,
                        agentName = currentAgent.name,
                        agentAccentColor = currentAgent.accentColor,
                        agentImageRes = currentAgent.imageRes
                    )
                }
            } else if (isThinking) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NearBlackSurface,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = currentAgent.accentColor, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${currentAgent.name} is formulating response...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. Chat Input Section (suggestions + math keyboard accessory)
        val sendMessage: () -> Unit = {
            if (inputText.text.isNotBlank() && !isThinking) {
                val textToSend = inputText.text.trim()
                val verifyMode = isMathVerificationMode
                inputText = TextFieldValue("")
                viewModel.sendAgentMessage(
                    agentId = currentAgent.id,
                    text = textToSend,
                    isStepVerification = verifyMode
                )
            }
        }
        Surface(
            color = NearBlackSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SuggestionChipsRow(
                    suggestions = viewModel.agentSuggestions(currentAgent.id),
                    accentColor = currentAgent.accentColor,
                    onSuggestionClick = { suggestion ->
                        inputText = TextFieldValue(suggestion)
                    },
                    enabled = !isThinking
                )
                Spacer(modifier = Modifier.height(8.dp))
                MathKeyboardStrip(
                    expanded = showMathKeyboard,
                    onToggleExpanded = { showMathKeyboard = !showMathKeyboard },
                    onInsert = { token -> inputText = insertToken(inputText, token) },
                    onBackspace = { inputText = backspaceToken(inputText) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (currentAgent.id == "danish") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isMathVerificationMode,
                            onClick = { isMathVerificationMode = !isMathVerificationMode },
                            label = { Text("Deterministic Math Verifier Mode", fontSize = 10.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealAccent.copy(alpha = 0.2f),
                                selectedLabelColor = TealAccent
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agent_chat_input"),
                        placeholder = {
                            Text(
                                text = if (isMathVerificationMode && currentAgent.id == "danish") {
                                    "Enter problem & steps e.g.:\nx(x+2)\nx^2+2x"
                                } else {
                                    "Ask ${currentAgent.name} anything..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NearBlackBg,
                            unfocusedContainerColor = NearBlackBg,
                            focusedBorderColor = currentAgent.accentColor,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.text.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.text.isNotBlank()) currentAgent.accentColor else SubtleBorder)
                            .testTag("agent_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.text.isNotBlank()) NearBlackBg else TextMuted
                        )
                    }
                }
            }
        }
    }
}
