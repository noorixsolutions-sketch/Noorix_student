package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.RegisteredStudentEntity
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Clean Starter Screen — Student Onboarding & Family Group Registration.
 *
 * Captures:
 * 1. Student Full Name
 * 2. Parent Email (checks database for existing siblings to join parent family group or create one)
 * 3. Student Email (if any - uses student email as unique registration ID, or assigns auto-generated registration number)
 * 4. Student Age & Class / Grade
 *
 * Upon completing registration, loads Main Screen (Dashboard) directly without forcing any tests.
 */
@Composable
fun OnboardingScreen(viewModel: OLevelViewModel) {
    var studentName by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var studentEmail by remember { mutableStateOf("") }
    var selectedAge by remember { mutableIntStateOf(14) }
    var selectedClass by remember { mutableStateOf("Grade 9 (O-Level Year 1)") }

    var existingSiblings by remember { mutableStateOf<List<RegisteredStudentEntity>>(emptyList()) }
    var isCheckingParent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Live parent email lookup
    LaunchedEffect(parentEmail) {
        val trimmed = parentEmail.trim().lowercase()
        if (trimmed.contains("@") && trimmed.contains(".")) {
            isCheckingParent = true
            delay(300) // Debounce
            existingSiblings = viewModel.checkExistingKidsForParent(trimmed)
            isCheckingParent = false
        } else {
            existingSiblings = emptyList()
            isCheckingParent = false
        }
    }

    val classOptions = listOf(
        "Grade 6 (Lower Secondary)",
        "Grade 7 (Middle Secondary)",
        "Grade 8 (Pre-O-Level Prep)",
        "Grade 9 (O-Level Year 1)",
        "Grade 10 (O-Level Year 2)",
        "Grade 11 (O-Level Final Revision)"
    )

    val ageOptions = (10..18).toList()

    Scaffold(
        containerColor = NearBlackBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Banner
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, TealAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.size(78.dp)
                ) {}
                Image(
                    painter = painterResource(id = R.drawable.img_noorix_emblem_core),
                    contentDescription = "Noorix Solutions Logo",
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "NOORIX LEARNING",
                style = MaterialTheme.typography.labelMedium,
                color = CyberGold,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Student Registration",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your details to create your student account and connect your parent family group.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Student & Parent Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "STUDENT & FAMILY DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Student Full Name
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = {
                            studentName = it
                            errorMessage = null
                        },
                        label = { Text("Student Full Name") },
                        placeholder = { Text("e.g. Ayesha Malik") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = CyberGold)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CyberGold,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Parent Email
                    OutlinedTextField(
                        value = parentEmail,
                        onValueChange = {
                            parentEmail = it
                            errorMessage = null
                        },
                        label = { Text("Parent Email (Required)") },
                        placeholder = { Text("e.g. parent.malik@gmail.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = CyberGold)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parent_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CyberGold,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Live Parent Group Status Indicator
                    val isParentEmailValid = parentEmail.trim().contains("@") && parentEmail.trim().contains(".")
                    AnimatedVisibility(visible = isParentEmailValid) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (existingSiblings.isNotEmpty()) TealAccentSubtle else NearBlackSurface,
                            border = BorderStroke(
                                1.dp,
                                if (existingSiblings.isNotEmpty()) TealAccent.copy(alpha = 0.5f) else SubtleBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (existingSiblings.isNotEmpty()) Icons.Default.Groups else Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = if (existingSiblings.isNotEmpty()) TealAccent else CyberGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    if (existingSiblings.isNotEmpty()) {
                                        Text(
                                            text = "Family Group Found (${existingSiblings.size} sibling${if (existingSiblings.size > 1) "s" else ""})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TealAccent
                                        )
                                        Text(
                                            text = "Enrolling ${studentName.ifBlank { "student" }} alongside: ${existingSiblings.joinToString(", ") { it.name }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    } else {
                                        Text(
                                            text = "New Family Group",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "A new parent-email-based group will be created for $parentEmail",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Student Email (Optional)
                    OutlinedTextField(
                        value = studentEmail,
                        onValueChange = {
                            studentEmail = it
                            errorMessage = null
                        },
                        label = { Text("Student Email (Optional — if any)") },
                        placeholder = { Text("e.g. ayesha@school.edu") },
                        leadingIcon = {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = TextSecondary)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = SubtleBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CyberGold,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Registration ID Preview Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NearBlackSurface,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (studentEmail.isNotBlank()) Icons.Default.Badge else Icons.Default.Pin,
                                contentDescription = null,
                                tint = CyberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ASSIGNED REGISTRATION IDENTIFIER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (studentEmail.isNotBlank()) {
                                        studentEmail.trim()
                                    } else {
                                        "Auto-assigned Registration Number (e.g. REG-2026-XXXXX)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (studentEmail.isNotBlank()) TealAccent else CyberGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Age & Class Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "AGE & CLASS LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Age Selection
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Student Age",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$selectedAge Years Old",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyberGold,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ageOptions) { age ->
                                val isSelected = selectedAge == age
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedAge = age },
                                    label = { Text("$age") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberGold,
                                        selectedLabelColor = NearBlackBg,
                                        containerColor = NearBlackSurface,
                                        labelColor = TextPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) CyberGold else SubtleBorder
                                    )
                                )
                            }
                        }
                    }

                    Divider(color = SubtleBorder)

                    // Class / Grade Selection
                    Column {
                        Text(
                            text = "Which class are they in?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        classOptions.forEach { cls ->
                            val isSelected = selectedClass == cls
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) CyberGold.copy(alpha = 0.12f) else NearBlackSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) CyberGold else SubtleBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedClass = cls }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedClass = cls },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = CyberGold,
                                            unselectedColor = TextSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cls,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error display
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Red.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Submit & Complete Registration Button
            Button(
                onClick = {
                    val trimmedName = studentName.trim()
                    val trimmedParent = parentEmail.trim().lowercase()

                    if (trimmedName.isBlank()) {
                        errorMessage = "Please enter student's full name."
                        return@Button
                    }
                    if (trimmedParent.isBlank() || !trimmedParent.contains("@") || !trimmedParent.contains(".")) {
                        errorMessage = "Please enter a valid parent email address."
                        return@Button
                    }

                    isSubmitting = true
                    viewModel.registerStudent(
                        name = trimmedName,
                        parentEmail = trimmedParent,
                        studentEmail = studentEmail.trim().takeIf { it.isNotBlank() },
                        age = selectedAge,
                        studentClass = selectedClass,
                        onComplete = {
                            isSubmitting = false
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_registration_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGold,
                    contentColor = NearBlackBg
                ),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NearBlackBg,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Creating Student Account...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = "Complete Registration & Enter App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
