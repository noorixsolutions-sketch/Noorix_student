package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.core.model.GraphStage
import com.example.presentation.OLevelViewModel
import com.example.ui.theme.*

@Composable
fun PackagesScreen(viewModel: OLevelViewModel) {
    val activePkg by viewModel.activePackage.collectAsState()
    val curriculumGraph by viewModel.curriculumGraph.collectAsState()
    var expandedStageId by remember { mutableStateOf<String?>("stage-9") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackElevated),
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
                                Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CURRICULUM SUPPLY CHAIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Signed Course Packages",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Official Cambridge curriculum packages are cryptographically signed offline using Ed25519. The runtime verifies content hashes and integrity before activating subjects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Active Package & Cryptographic Proof Card
        activePkg?.let { pkg ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE COURSE PACKAGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StepCorrect.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, StepCorrect)
                            ) {
                                Text(
                                    text = "ED25519 VERIFIED",
                                    color = StepCorrect,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = pkg.manifest.subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Course ID: ${pkg.manifest.courseId} • Version: ${pkg.manifest.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Concepts", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                    Text("${pkg.nodes.count { it.type.name == "CONCEPT" }}", color = TealAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Questions", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                    Text("${pkg.questions.size}", color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NearBlackBg,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Resources", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                    Text("${pkg.resources.size}", color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Content Hash: ${pkg.manifest.contentHash.take(24)}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // 3. Course Package Switcher
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AVAILABLE OFFICIAL PACKAGES",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.installMathCourse() },
                            colors = ButtonDefaults.buttonColors(containerColor = NearBlackElevated, contentColor = TealAccent),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Math 4024", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.installPhysicsCourse() },
                            colors = ButtonDefaults.buttonColors(containerColor = NearBlackElevated, contentColor = StepCorrect),
                            border = BorderStroke(1.dp, StepCorrect.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Physics 5054", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Cambridge Knowledge Graph Explorer
        curriculumGraph?.let { graph ->
            item {
                Text(
                    text = "CAMBRIDGE CURRICULUM GRAPH (${graph.stages.size} STAGES)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            items(graph.stages, key = { it.id }) { stage ->
                val isExpanded = expandedStageId == stage.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStageId = if (isExpanded) null else stage.id },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NearBlackSurface),
                    border = BorderStroke(1.dp, if (isExpanded) TealAccent.copy(alpha = 0.4f) else SubtleBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isExpanded) TealAccent else NearBlackElevated,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${stage.order}",
                                            color = if (isExpanded) NearBlackBg else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stage.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${stage.subjects.size} subjects",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                stage.subjects.forEach { subject ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NearBlackBg,
                                        border = BorderStroke(1.dp, SubtleBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(subject.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                if (subject.syllabusCode != null) {
                                                    Text("Code: ${subject.syllabusCode}", style = MaterialTheme.typography.labelSmall, color = TealAccent, fontSize = 10.sp)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = NearBlackElevated
                                            ) {
                                                Text(
                                                    text = "${subject.areas.size} areas",
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
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
    }
}
