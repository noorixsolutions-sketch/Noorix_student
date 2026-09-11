package com.example.presentation.bubble

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.bubble.BubblePresentation
import com.example.domain.bubble.BubbleVisualState
import com.example.presentation.AppNavigationTab
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NearBlackBg
import com.example.ui.theme.NearBlackSurface
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import android.view.ViewConfiguration

// ------------------------------------------------------------------
// DraggableBubble — the 56dp summon surface (§3).
//
// One manual gesture state machine (tap / double-tap / drag / swipe-up)
// so detectors never fight each other inside the overlay window.
// ------------------------------------------------------------------

@Composable
fun DraggableBubble(
    presentation: BubblePresentation,
    streakDays: Int,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDrag: (dxPx: Float, dyPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val touchSlopPx = remember(view) { ViewConfiguration.get(view.context).scaledTouchSlop.toFloat() }
    val swipeThresholdPx = remember(density) { with(density) { 80.dp.toPx() } }

    val latestTap by rememberUpdatedState(onTap)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestSwipeUp by rememberUpdatedState(onSwipeUp)
    var lastTapTime by remember { mutableLongStateOf(0L) }

    fun resolveTap() {
        scope.launch {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTime < 350) {
                lastTapTime = 0L
                latestDoubleTap()
            } else {
                lastTapTime = now
                delay(350)
                if (lastTapTime == now) {
                    lastTapTime = 0L
                    latestTap()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .pointerInput(touchSlopPx, swipeThresholdPx) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val downTime = down.uptimeMillis
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragging = false
                        var finished = false
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change == null) {
                                finished = true
                            } else if (change.pressed) {
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy
                                if (!dragging && hypot(totalDx, totalDy) > touchSlopPx) {
                                    dragging = true
                                }
                                if (dragging) latestDrag(dx, dy)
                                change.consume()
                            } else {
                                val heldMs = change.uptimeMillis - downTime
                                when {
                                    dragging -> latestDragEnd()
                                    totalDy < -swipeThresholdPx && heldMs < 500 &&
                                        abs(totalDx) < abs(totalDy) -> latestSwipeUp()
                                    heldMs < 300 -> resolveTap()
                                    // Long-press without movement: no-op (hold + move = drag).
                                    else -> Unit
                                }
                                change.consume()
                                finished = true
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BubbleFace(presentation = presentation, streakDays = streakDays)
    }
}

@Composable
private fun BubbleFace(presentation: BubblePresentation, streakDays: Int) {
    val transition = rememberInfiniteTransition(label = "bubble-breath")
    // Calm Aisha: soft breathing scale on the avatar.
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breath"
    )
    // Amber halo pulse at 1.2s when the streak is at risk.
    val haloAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "halo"
    )

    val ringColor = when (presentation.state) {
        BubbleVisualState.FOCUS -> Color(0xFF38BDF8)
        BubbleVisualState.STREAK_AT_RISK -> CyberGold.copy(alpha = haloAlpha)
        BubbleVisualState.CONFUSED -> CyberGold.copy(alpha = 0.7f)
        BubbleVisualState.IDLE -> TealAccent
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
        if (presentation.state == BubbleVisualState.STREAK_AT_RISK) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(CyberGold.copy(alpha = 0.12f * haloAlpha))
            )
        }
        Image(
            painter = painterResource(id = R.drawable.img_noorix_circuit_emblem),
            contentDescription = "Aisha bubble",
            modifier = Modifier
                .size(56.dp)
                .scale(breath)
                .clip(CircleShape)
                .border(2.dp, ringColor, CircleShape),
            contentScale = ContentScale.Crop
        )
        // Streak badge (bottom-left) when a streak is alive.
        if (streakDays > 0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NearBlackBg.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ringColor),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = "🔥$streakDays",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        // 🎯 mock-week badge (top-right).
        if (presentation.showTargetBadge) {
            Surface(
                shape = CircleShape,
                color = NearBlackBg.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🎯", fontSize = 13.sp)
                }
            }
        }
        // 💛 parent-message badge (top-start).
        if (presentation.showHeartBadge) {
            Surface(
                shape = CircleShape,
                color = CyberGold.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "💛", fontSize = 12.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// FanOutMenu — the 8-petal radial mode picker (§4).
// ------------------------------------------------------------------

data class FanPetal(
    val id: String,
    val emoji: String,
    val label: String,
    val tab: AppNavigationTab?,
    val agentId: String? = null,
    val opensCanvas: Boolean = false
)

val FAN_PETALS: List<FanPetal> = listOf(
    FanPetal("math", "📐", "Math", AppNavigationTab.SYMBOLIC_TUTOR),
    FanPetal("writing", "✍️", "Writing", AppNavigationTab.AI_AGENTS, agentId = "ayesha"),
    FanPetal("quiz", "📝", "Quiz", AppNavigationTab.DIAGNOSTIC),
    FanPetal("canvas", "🎨", "Canvas", tab = null, opensCanvas = true),
    FanPetal("reference", "📚", "Library", AppNavigationTab.LEARNING_HUB),
    FanPetal("notes", "🗒️", "Notes", AppNavigationTab.LEARNING_HUB),
    FanPetal("voice", "🎙️", "Voice", AppNavigationTab.AI_AGENTS),
    FanPetal("media", "🎬", "Media", AppNavigationTab.MEDIA_TRANSFER)
)

@Composable
fun FanOutMenu(
    anchor: DpOffset,
    onPetal: (FanPetal) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cx = anchor.x.coerceIn(150.dp, (maxWidth - 150.dp).coerceAtLeast(150.dp))
            val cy = anchor.y.coerceIn(190.dp, (maxHeight - 190.dp).coerceAtLeast(190.dp))
            val radius = 132.dp
            FAN_PETALS.forEachIndexed { index, petal ->
                val radians = Math.toRadians(-90.0 + index * 45.0)
                val x = cx + radius * cos(radians).toFloat()
                val y = cy + radius * sin(radians).toFloat()
                FanPetalView(
                    petal = petal,
                    onClick = { onPetal(petal) },
                    modifier = Modifier.offset(x = x - 30.dp, y = y - 30.dp)
                )
            }
        }
    }
}

@Composable
private fun FanPetalView(petal: FanPetal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(NearBlackSurface.copy(alpha = 0.96f))
            .border(1.5.dp, TealAccent.copy(alpha = 0.7f), CircleShape)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = petal.emoji, fontSize = 20.sp)
        Text(
            text = petal.label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

// ------------------------------------------------------------------
// QuickHud — swipe-up ambient cards, auto-dismissed by the service.
// ------------------------------------------------------------------

data class QuickHudData(
    val todayMinutes: Int,
    val dailyTargetMinutes: Int,
    val streakDays: Int,
    val accuracyPercent: Int,
    val level: Int
)

@Composable
fun QuickHudCards(data: QuickHudData, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NearBlackSurface.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
        modifier = modifier.width(210.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "QUICK HUD",
                style = MaterialTheme.typography.labelSmall,
                color = TealAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            QuickHudRow("📊 Today", "${data.todayMinutes}/${data.dailyTargetMinutes} min")
            QuickHudRow("🔥 Streak", "${data.streakDays} days")
            QuickHudRow("🎯 Accuracy", "${data.accuracyPercent}%")
            QuickHudRow("⭐ Level", "${data.level}")
        }
    }
}

@Composable
private fun QuickHudRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------
// CanvasDrawArea — draw on top of any app (§5.1, v1: strokes + clear).
// The service hosts this in a top-anchored window so touches outside
// the drawing region pass through to the underlying app.
// ------------------------------------------------------------------

@Composable
fun CanvasDrawArea(
    strokes: SnapshotStateList<List<Offset>>,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> current = listOf(offset) },
                    onDrag = { change, _ ->
                        current = current + change.position
                        change.consume()
                    },
                    onDragEnd = {
                        if (current.size > 1) strokes.add(current.toList())
                        current = emptyList()
                    },
                    onDragCancel = { current = emptyList() }
                )
            }
    ) {
        val style = Stroke(width = 6f)
        strokes.forEach { stroke ->
            if (stroke.size > 1) {
                drawPath(pathFrom(stroke), color = TealAccent, style = style)
            }
        }
        if (current.size > 1) {
            drawPath(pathFrom(current), color = TealAccent, style = style)
        }
    }
}

private fun pathFrom(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        lineTo(points[i].x, points[i].y)
    }
}

@Composable
fun CanvasToolbar(
    onClear: () -> Unit,
    onClose: () -> Unit,
    strokeCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NearBlackSurface.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🎨 Canvas • $strokeCount stroke(s)",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                androidx.compose.material3.TextButton(onClick = onClear) {
                    Text("Clear", color = TextMuted, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                androidx.compose.material3.Button(
                    onClick = onClose,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = TealAccent,
                        contentColor = NearBlackBg
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

