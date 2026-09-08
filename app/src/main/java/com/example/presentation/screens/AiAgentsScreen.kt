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
                description = "Specializes in algebraic step-by-step problem solving, concept clarity & Cambridge Math 4024 mastery.",
                tag = "Algebra / 4024",
                imageRes = R.drawable.img_danish_math,
                accentColor = NeonCyan,
                targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
                promptStarter = "Hello Danish! Please explain how to solve quadratic factorisation step-by-step without sign errors."
            ),
            NoorixAgent(
                id = "zoya",
                name = "ZOYA",
                role = "Sciences 5054",
                description = "Expert in Physics (5054), Chemistry & Biology with concept visualization and high-yield intuition.",
                tag = "Physics / 5054",
                imageRes = R.drawable.img_zoya_science,
                accentColor = CyberGreen,
                targetTab = AppNavigationTab.MEDIA_TRANSFER,
                promptStarter = "Hi Zoya! Can you give me a clear breakdown of Newton's laws and acceleration graphs?"
            ),
            NoorixAgent(
                id = "exam_coach",
                name = "EXAM COACH",
                role = "Exam Strategy",
                description = "Guides you with past paper strategies, Cambridge scoring mark schemes & exam timing.",
                tag = "Past Papers / IRT",
                imageRes = R.drawable.img_exam_coach,
                accentColor = CyberGold,
                targetTab = AppNavigationTab.DIAGNOSTIC,
                promptStarter = "Coach, what are the top common pitfalls in Cambridge O-Level Paper 1 exams?"
            ),
            NoorixAgent(
                id = "ayesha",
                name = "AYESHA",
                role = "Language 1123",
                description = "Enhances reading comprehension, formal writing & Cambridge English communication skills.",
                tag = "English / 1123",
                imageRes = R.drawable.img_student_avatar,
                accentColor = Color(0xFF64B5F6),
                targetTab = AppNavigationTab.MEDIA_TRANSFER,
                promptStarter = "Hello Ayesha! How can I structure an effective persuasive speech in English 1123?"
            ),
            NoorixAgent(
                id = "bilal",
                name = "BILAL",
                role = "Aptitude & Logic",
                description = "Builds quantitative reasoning, pattern sequences and structural problem solving.",
                tag = "Reasoning",
                imageRes = R.drawable.img_danish_math,
                accentColor = Color(0xFFBA68C8),
                targetTab = AppNavigationTab.SYMBOLIC_TUTOR,
                promptStarter = "Bilal, help me solve arithmetic and geometric sequence proof patterns."
            ),
            NoorixAgent(
                id = "career_guide",
                name = "CAREER GUIDE",
                role = "Academic Advisor",
                description = "Discovers subject combinations and university paths based on your academic strengths.",
                tag = "Career Paths",
                imageRes = R.drawable.img_student_avatar,
                accentColor = Color(0xFFFF8A65),
                targetTab = AppNavigationTab.DASHBOARD,
                promptStarter = "Which O-Level subjects should I focus on for Software Engineering and Artificial Intelligence?"
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
            .background(CyberDarkBg)
    ) {
        // 1. Agent Selection Horizontal Carousel
        Surface(
            color = CyberPanel,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "CAMBRIDGE AI TUTORS (FIREBASE AI / GEMINI 2.5 FLASH)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
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
                            color = if (isSelected) agent.accentColor.copy(alpha = 0.15f) else CyberDarkBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) agent.accentColor else CyberBorder
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
                                        .border(1.dp, agent.accentColor, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) agent.accentColor else CyberTextPrimary
                                    )
                                    Text(
                                        text = agent.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberTextSecondary,
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
            color = CyberPanelElevated,
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
                                fontWeight = FontWeight.Black,
                                color = currentAgent.accentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CyberGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE LLM",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentAgent.role + " • " + currentAgent.tag,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberTextSecondary,
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
                        tint = CyberTextSecondary,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
        ) {
            // Description & Specialty Intro
            item {
                NoorixCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = currentAgent.accentColor.copy(alpha = 0.3f),
                    containerColor = CyberPanel
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = currentAgent.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TUTOR PROFILE & PEDAGOGY",
                                style = MaterialTheme.typography.labelSmall,
                                color = currentAgent.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentAgent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberTextSecondary
                        )
                    }
                }
            }

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
                            text = "${currentAgent.name} is streaming response...",
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
            color = CyberPanel,
            border = BorderStroke(1.dp, CyberBorder),
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
                                color = CyberTextPrimary,
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
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
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
                                color = CyberTextSecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberDarkBg,
                            unfocusedContainerColor = CyberDarkBg,
                            focusedBorderColor = currentAgent.accentColor,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = CyberTextPrimary,
                            unfocusedTextColor = CyberTextPrimary
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
                            .background(if (inputText.isNotBlank()) currentAgent.accentColor else CyberBorder)
                            .testTag("agent_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) CyberDarkBg else CyberTextSecondary
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
            color = if (isUser) NeonCyan.copy(alpha = 0.15f) else CyberPanel,
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) NeonCyan.copy(alpha = 0.5f) else agentAccentColor.copy(alpha = 0.4f)
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
                        text = if (isUser) "YOU (STUDENT)" else "AI TUTOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) NeonCyan else agentAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )

                    if (message.isVerifiedMath) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyberGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyberGreen)
                        ) {
                            Text(
                                text = "DETERMINISTIC VERIFIED",
                                color = CyberGreen,
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
                    color = CyberTextPrimary,
                    lineHeight = 20.sp
                )

                // Optional Symbolic trace attachment
                if (!message.mathVerificationTrace.isNullOrEmpty() && !isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyberDarkBg,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "SYMBOLIC VERIFIER TRACE:",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message.mathVerificationTrace,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberTextSecondary,
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
            color = CyberPanel,
            border = BorderStroke(1.dp, agentAccentColor),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(agentAccentColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GENERATING STREAM...",
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
                    color = CyberTextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
