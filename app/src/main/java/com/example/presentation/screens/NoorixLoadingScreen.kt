package com.example.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackElevated
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.StepCorrect
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Bespoke Noorix Learning Splash & Runtime Initialization Screen.
 *
 * Uses the custom-generated Noorix NX emblem (`R.drawable.img_noorix_emblem_core`) and
 * dark geometric backdrop (`R.drawable.img_noorix_splash_bg`) derived directly from
 * the official Noorix NX logo (`R.drawable.img_noorix_logo`).
 */
@Composable
fun NoorixLoadingScreen(
    modifier: Modifier = Modifier,
    indexedTopicCount: Int = 108,
    eventLedgerCount: Int = 0,
    isIndexing: Boolean = false,
    onLoadingComplete: () -> Unit = {}
) {
    var progress by remember { mutableFloatStateOf(0.08f) }
    var stageLabel by remember { mutableStateOf("MOUNTING ROOM K-12 SYLLABUS INDEX") }

    val infiniteTransition = rememberInfiniteTransition(label = "NoorixSplashTransition")

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitAngle"
    )

    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloPulse"
    )

    LaunchedEffect(Unit) {
        val steps = 45
        for (i in 1..steps) {
            progress = i / steps.toFloat()
            stageLabel = when {
                progress < 0.35f -> "INDEXING K-12 & O-LEVEL SYLLABUS ($indexedTopicCount TOPICS)"
                progress < 0.70f -> "VERIFYING SHA-256 EVENTLOGGER HASH CHAIN"
                progress < 0.92f -> "CALIBRATING DIAGNOSTIC MASTERY ENGINE"
                else -> "NOORIX RUNTIME READY"
            }
            delay(24)
        }
        delay(160)
        onLoadingComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NearBlackBg)
            .clickable { onLoadingComplete() }
            .testTag("noorix_loading_screen")
    ) {
        // 1. Custom Generated Noorix NX Geometric Splash Backdrop
        Image(
            painter = painterResource(id = R.drawable.img_noorix_splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.72f
        )

        // 2. Deep Vignette Overlay so typography and centerpiece pop cleanly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NearBlackBg.copy(alpha = 0.65f),
                             Color.Transparent,
                            NearBlackBg.copy(alpha = 0.88f),
                            NearBlackBg
                        )
                    )
                )
        )

        // 3. Main Vertical Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Runtime Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = NearBlackSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_noorix_logo),
                        contentDescription = "Noorix Crest",
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOORIX ACADEMIC ENGINE · V2.5",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp
                    )
                }
            }

            // Centerpiece: Generated Noorix NX Emblem + Animated Orbital Telemetry Ring
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(268.dp)
                ) {
                    // Animated Orbital Canvas around the Noorix NX Emblem
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.minDimension / 2f - 6.dp.toPx()
                        val innerRadius = outerRadius - 14.dp.toPx()

                        // Subtle radial glow behind logo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    TealAccent.copy(alpha = 0.24f * haloPulse),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = outerRadius * 1.15f
                            ),
                            radius = outerRadius * 1.15f,
                            center = center
                        )

                        // Outer precision ring
                        drawCircle(
                            color = SubtleBorder.copy(alpha = 0.9f),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )

                        // Rotating dual cyan-teal arcs
                        rotate(degrees = orbitAngle, pivot = center) {
                            drawArc(
                                color = TealAccent.copy(alpha = 0.9f),
                                startAngle = -35f,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                                size = Size(outerRadius * 2f, outerRadius * 2f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = StepCorrect.copy(alpha = 0.75f),
                                startAngle = 145f,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                                size = Size(outerRadius * 2f, outerRadius * 2f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Progress arc hugging the inner ring
                        drawArc(
                            color = TealAccent.copy(alpha = 0.35f),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2f, innerRadius * 2f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Generated 3D Noorix NX Emblem Core (derived from img_noorix_logo.png)
                    Surface(
                        shape = CircleShape,
                        color = NearBlackBg,
                        border = BorderStroke(1.5.dp, TealAccent.copy(alpha = 0.65f * haloPulse)),
                        modifier = Modifier.size(214.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_noorix_emblem_core),
                            contentDescription = "Noorix NX Emblem",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "NOORIX",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 7.sp,
                    fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "CAMBRIDGE O-LEVEL & K-12 MASTERY SYSTEM",
                    style = MaterialTheme.typography.labelMedium,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Real Telemetry & Progress Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Subsystem Telemetry Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplashTelemetryChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.School,
                        label = "SYLLABUS",
                        value = "$indexedTopicCount Topics",
                        isReady = progress >= 0.3f
                    )
                    SplashTelemetryChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VerifiedUser,
                        label = "EVENT LEDGER",
                        value = "SHA-256 OK",
                        isReady = progress >= 0.65f
                    )
                    SplashTelemetryChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        label = "STUDIO AI",
                        value = if (isIndexing) "Indexing" else "Online",
                        isReady = progress >= 0.9f
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sleek Minimal Progress Bar + Percentage
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NearBlackSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stageLabel,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = TealAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NearBlackElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(TealAccent, StepCorrect)
                                        )
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tap anywhere to enter workspace",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SplashTelemetryChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isReady: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NearBlackSurface.copy(alpha = 0.88f),
        border = BorderStroke(
            1.dp,
            if (isReady) TealAccent.copy(alpha = 0.45f) else SubtleBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else icon,
                    contentDescription = label,
                    tint = if (isReady) TealAccent else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                color = if (isReady) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
