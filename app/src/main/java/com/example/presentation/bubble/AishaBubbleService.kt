package com.example.presentation.bubble

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwners
import com.example.R
import com.example.domain.bubble.BubblePresentation
import com.example.domain.bubble.BubbleStateMapper
import com.example.domain.bubble.BubbleVisualState
import com.example.domain.overlay.OverlayStatsProvider
import com.example.domain.parent.ParentPrefs
import com.example.presentation.AppNavigationTab
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AishaLayerService (v1) — ADDITIVE (§3–§5 Goal Architecture).
 *
 * A single foreground service hosting the persistent 56dp Bubble
 * (SYSTEM_ALERT_WINDOW overlay) plus the fan-out, quick-HUD and canvas
 * windows. Gestures per the goal contract:
 * tap → tutor chat · double-tap → fan-out · drag → snap to edge ·
 * swipe up → quick HUD (auto-dismiss 4s).
 *
 * Badge/state input is derived from the existing Room event log, so the
 * overlay can never disagree with the main app.
 */
class AishaBubbleService : Service(), LifecycleOwner {

    companion object {
        const val ACTION_START = "com.example.bubble.START"
        const val ACTION_STOP = "com.example.bubble.STOP"

        /** Deep-link extras understood by MainActivity. */
        const val EXTRA_TAB = "noorix_tab"
        const val EXTRA_AGENT = "noorix_agent"

        private const val CHANNEL_ID = "aisha_bubble"
        private const val NOTIFICATION_ID = 41
        private const val PREFS = "aisha_bubble"
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"
        private const val BUBBLE_WINDOW_DP = 72

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    // Overlay state (Compose-observed).
    private var presentation by mutableStateOf(BubblePresentation(BubbleVisualState.IDLE))
    private var streakDays by mutableStateOf(0)
    private var quickHud by mutableStateOf<QuickHudData?>(null)
    private var fanAnchor by mutableStateOf(DpOffset(300.dp, 400.dp))
    private val canvasStrokes: SnapshotStateList<List<Offset>> = mutableStateListOf()

    // Windows + bubble position (px).
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var overlayView: ComposeView? = null // fan-out OR quick HUD (full/wrap window)
    private var canvasDrawView: ComposeView? = null
    private var canvasToolbarView: ComposeView? = null
    private var bubbleXPx: Int = -1
    private var bubbleYPx: Int = -1

    private val hudDismissRunnable = Runnable { collapseOverlay() }

    private val parentPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshStats() }

    // ------------------------------------------------------------------
    // Service lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        getSharedPreferences("parent_gate", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(parentPrefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setEnabled(false)
                collapseAll()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START or system restart.
                setEnabled(true)
                startForegroundInternal()
                showBubble()
                refreshStats()
                isRunning = true
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        getSharedPreferences("parent_gate", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(parentPrefListener)
        mainHandler.removeCallbacks(hudDismissRunnable)
        collapseAll()
        scope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
    }

    // ------------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------------

    private fun startForegroundInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aisha Bubble",
                NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, AishaBubbleService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = openIntent?.let {
            PendingIntent.getActivity(
                this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aisha Bubble is active")
            .setContentText("Tap the bubble from any app to summon your tutor.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Hide bubble", stopPending)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    // ------------------------------------------------------------------
    // Bubble window
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubbleView != null) return
        val metrics = resources.displayMetrics
        val bubblePx = (BUBBLE_WINDOW_DP * metrics.density).toInt()
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        bubbleXPx = prefs.getInt(KEY_X, metrics.widthPixels - bubblePx - (12 * metrics.density).toInt())
        bubbleYPx = prefs.getInt(KEY_Y, metrics.heightPixels / 2)

        val params = baseParams().apply {
            width = bubblePx
            height = bubblePx
            gravity = Gravity.TOP or Gravity.START
            x = bubbleXPx
            y = bubbleYPx
        }
        val view = composeView().apply {
            setContent {
                MyApplicationTheme {
                    DraggableBubble(
                        presentation = presentation,
                        streakDays = streakDays,
                        onTap = {
                            // Tap → tutor chat; consume any waiting 💛.
                            ParentPrefs.setMessageWaiting(this@AishaBubbleService, false)
                            openApp(AppNavigationTab.AI_AGENTS, agentId = null)
                            refreshStats()
                        },
                        onDoubleTap = { showFanOut() },
                        onDrag = { dx, dy -> moveBubbleBy(dx, dy) },
                        onDragEnd = { snapBubble() },
                        onSwipeUp = { showQuickHud() }
                    )
                }
            }
        }
        bubbleView = view
        bubbleParams = params
        windowManager.addView(view, params)
    }

    private fun moveBubbleBy(dx: Float, dy: Float) {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        bubbleXPx = (bubbleXPx + dx).toInt().coerceAtLeast(0)
        bubbleYPx = (bubbleYPx + dy).toInt().coerceAtLeast(0)
        params.x = bubbleXPx
        params.y = bubbleYPx
        windowManager.updateViewLayout(view, params)
    }

    private fun snapBubble() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val metrics = resources.displayMetrics
        val bubblePx = (BUBBLE_WINDOW_DP * metrics.density).toInt()
        bubbleXPx = if (bubbleXPx + bubblePx / 2 < metrics.widthPixels / 2) {
            0
        } else {
            (metrics.widthPixels - bubblePx).coerceAtLeast(0)
        }
        bubbleYPx = bubbleYPx.coerceIn(0, (metrics.heightPixels - bubblePx).coerceAtLeast(0))
        params.x = bubbleXPx
        params.y = bubbleYPx
        windowManager.updateViewLayout(view, params)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_X, bubbleXPx)
            .putInt(KEY_Y, bubbleYPx)
            .apply()
    }

    // ------------------------------------------------------------------
    // Fan-out / quick HUD overlay window
    // ------------------------------------------------------------------

    private fun bubbleCenterDp(): DpOffset {
        val density = resources.displayMetrics.density
        val half = BUBBLE_WINDOW_DP / 2f
        return DpOffset(
            x = (bubbleXPx / density + half).dp,
            y = (bubbleYPx / density + half).dp
        )
    }

    private fun showFanOut() {
        collapseOverlay()
        fanAnchor = bubbleCenterDp()
        val params = baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
        }
        val view = composeView().apply {
            setContent {
                MyApplicationTheme {
                    FanOutMenu(
                        anchor = fanAnchor,
                        onPetal = { petal ->
                            collapseOverlay()
                            if (petal.opensCanvas) {
                                showCanvas()
                            } else if (petal.tab != null) {
                                openApp(petal.tab, petal.agentId)
                            }
                        },
                        onDismiss = { collapseOverlay() }
                    )
                }
            }
        }
        overlayView = view
        windowManager.addView(view, params)
    }

    private fun showQuickHud() {
        collapseOverlay()
        val data = quickHud ?: return
        val density = resources.displayMetrics.density
        val params = baseParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            // Anchor beside the bubble, clamped on-screen.
            x = (bubbleXPx - 220 * density).toInt().coerceAtLeast((8 * density).toInt())
            y = (bubbleYPx + 80 * density).toInt()
        }
        val view = composeView().apply {
            setContent {
                MyApplicationTheme {
                    QuickHudCards(data = data)
                }
            }
        }
        overlayView = view
        windowManager.addView(view, params)
        mainHandler.removeCallbacks(hudDismissRunnable)
        mainHandler.postDelayed(hudDismissRunnable, 4000)
    }

    private fun collapseOverlay() {
        mainHandler.removeCallbacks(hudDismissRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    // ------------------------------------------------------------------
    // Canvas windows (draw region + toolbar; touches elsewhere pass through)
    // ------------------------------------------------------------------

    private fun showCanvas() {
        if (canvasDrawView != null) return
        val metrics = resources.displayMetrics
        val drawParams = baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = (metrics.heightPixels * 0.62).toInt()
            gravity = Gravity.TOP
        }
        val drawView = composeView().apply {
            setContent {
                MyApplicationTheme {
                    CanvasDrawArea(strokes = canvasStrokes)
                }
            }
        }
        val toolbarParams = baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
        }
        val toolbarView = composeView().apply {
            setContent {
                MyApplicationTheme {
                    CanvasToolbar(
                        onClear = { canvasStrokes.clear() },
                        onClose = { collapseCanvas() },
                        strokeCount = canvasStrokes.size
                    )
                }
            }
        }
        canvasDrawView = drawView
        canvasToolbarView = toolbarView
        windowManager.addView(drawView, drawParams)
        windowManager.addView(toolbarView, toolbarParams)
    }

    private fun collapseCanvas() {
        canvasDrawView?.let { runCatching { windowManager.removeView(it) } }
        canvasToolbarView?.let { runCatching { windowManager.removeView(it) } }
        canvasDrawView = null
        canvasToolbarView = null
    }

    private fun collapseAll() {
        collapseOverlay()
        collapseCanvas()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
    }

    // ------------------------------------------------------------------
    // Stats → presentation (§3 state contract)
    // ------------------------------------------------------------------

    private fun refreshStats() {
        scope.launch {
            val stats = OverlayStatsProvider.load(this@AishaBubbleService)

            val gam = stats.gamification
            val accuracy = stats.accuracyPercent
            val confusion = stats.confusionDetected
            withContext(Dispatchers.Main) {
                presentation = BubbleStateMapper.map(
                    streakDays = gam.streakDays,
                    studiedToday = gam.todayMinutesEstimated > 0,
                    focusMode = false,
                    confusionDetected = confusion,
                    // Mock-week feed arrives with the cloud Parent Dashboard; default off.
                    mockWithin14Days = false,
                    parentMessageWaiting = ParentPrefs.isMessageWaiting(this@AishaBubbleService)
                )
                streakDays = gam.streakDays
                quickHud = QuickHudData(
                    todayMinutes = gam.todayMinutesEstimated,
                    dailyTargetMinutes = gam.dailyTargetMinutes,
                    streakDays = gam.streakDays,
                    accuracyPercent = accuracy,
                    level = gam.level
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Deep link into the main app
    // ------------------------------------------------------------------

    private fun openApp(tab: AppNavigationTab, agentId: String?) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.putExtra(EXTRA_TAB, tab.name)
        if (agentId != null) launch.putExtra(EXTRA_AGENT, agentId)
        startActivity(launch)
    }

    // ------------------------------------------------------------------
    // Window helpers
    // ------------------------------------------------------------------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun baseParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

    private fun composeView(): ComposeView =
        ComposeView(this).also { view ->
            ViewTreeLifecycleOwners.set(view, this)
            view.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
        }
}
