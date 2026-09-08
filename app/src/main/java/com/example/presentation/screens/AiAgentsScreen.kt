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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.presentation.AppNavigationTab
import com.example.presentation.OLevelViewModel
import com.example.presentation.components.NoorixCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
                role = "Mathematics 4024",
                description = "Specializes in algebraic step-by-step problem solving.",
                tag = "Algebra / 4024",
                imageRes = R.drawable.img_danish_math,
                accentColor = TealAccent,
                targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
                promptStarter = "Hello Danish! Please explain how to solve quadratic factorisation step-by-step."
            ),
            NoorixAgent(
                id = "zoya",
                name = "ZOYA",
                role = "Sciences 5054",
                description = "Expert in Physics (5054) concept visualization.",
                tag = "Physics / 5054",
                imageRes = R.drawable.img_zoya_science,
                accentColor = StepCorrect,
                targetTab = AppNavigationTab.MEDIA_TRANSFER,
                promptStarter = "Hi Zoya! Can you give me a clear breakdown of Newton's laws?"
            ),
            NoorixAgent(
                id = "exam_coach",
                name = "EXAM COACH",
                role = "Exam Strategy",
                description = "Guides you with past paper strategies & exam timing.",
                tag = "Past Papers",
                imageRes = R.drawable.img_exam_coach,
                accentColor = CyberGold,
                targetTab = AppNavigationTab.DIAGNOSTIC,
                promptStarter = "Coach, what are the top common pitfalls in Paper 1 exams?"
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

    var inputText by remember { mutableStateOf("") }
    var isMathVerificationMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to bottom when new message or stream arrives
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
        // 1. Agent Selection Horizontal Carousel
        Surface(
            color = NearBlackSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "AI TUTORS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = agent.imageRes),
                                    contentDescription = agent.name,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, if(isSelected) agent.accentColor else Color.Transparent, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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

        // 2. Active Agent Header Bar
        Surface(
            color = NearBlackElevated,
            border = BorderStroke(1.dp, currentAgent.accentColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TealAccent)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentAgent.role + " • " + currentAgent.tag,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.clearAgentChat(currentAgent.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear Chat",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Chat Messages List (Room-persisted)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            // Persisted Room messages
            items(chatMessages) { message ->
                ChatMessageBubble(message = message, agentAccentColor = currentAgent.accentColor)
            }

            // Real-time live streaming bubble
            if (isThinking && !streamingText.isNullOrEmpty()) {
                item {
                    StreamingMessageBubble(
                        text = streamingText ?: "",
                        agentAccentColor = currentAgent.accentColor
                    )
                }
            } else if (isThinking && streamingText.isNullOrEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = currentAgent.accentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${currentAgent.name} is typing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = currentAgent.accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 4. Quick Starter Chips (Prompt Starter)
        Surface(
            color = NearBlackSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = currentAgent.accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, currentAgent.accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.sendAgentMessage(currentAgent.id, currentAgent.promptStarter)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = currentAgent.accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Starter: ${currentAgent.promptStarter.take(42)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (currentAgent.id == "danish") {
                        FilterChip(
                            selected = isMathVerificationMode,
                            onClick = { isMathVerificationMode = !isMathVerificationMode },
                            label = { Text("Math Steps Verifier", fontSize = 10.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealAccent.copy(alpha = 0.2f),
                                selectedLabelColor = TealAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Input Text Field + Send Action
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
                                    "Enter problem & lines e.g.:\nx(x+2)=0\nx^2+2x=0"
                                } else {
                                    "Ask ${currentAgent.name} anything..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
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
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isThinking) {
                                val textToSend = inputText
                                val verifyMode = isMathVerificationMode
                                inputText = ""
                                viewModel.sendAgentMessage(
                                    agentId = currentAgent.id,
                                    text = textToSend,
                                    isStepVerification = verifyMode
                                )
                            }
                        },
                        enabled = inputText.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) currentAgent.accentColor else SubtleBorder)
                            .testTag("agent_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) NearBlackBg else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity, agentAccentColor: Color) {
    val isUser = message.sender == "student"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) TealAccent.copy(alpha = 0.15f) else NearBlackSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) TealAccent.copy(alpha = 0.5f) else agentAccentColor.copy(alpha = 0.4f)
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Sender label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else "TUTOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) TealAccent else agentAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )

                    if (message.isVerifiedMath) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StepCorrect.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, StepCorrect)
                        ) {
                            Text(
                                text = "VERIFIED",
                                color = StepCorrect,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )

                // Optional Symbolic trace attachment
                if (!message.mathVerificationTrace.isNullOrEmpty() && !isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NearBlackBg,
                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "VERIFIER TRACE:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message.mathVerificationTrace,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingMessageBubble(text: String, agentAccentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 2.dp, bottomEnd = 14.dp),
            color = NearBlackSurface,
            border = BorderStroke(1.dp, agentAccentColor),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(agentAccentColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TYPING...",
                        style = MaterialTheme.typography.labelSmall,
                        color = agentAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
