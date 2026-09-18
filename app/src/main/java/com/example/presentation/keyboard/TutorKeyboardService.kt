package com.example.presentation.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.example.core.model.GamificationSnapshot
import com.example.domain.gamification.GamificationEngine
import com.example.domain.overlay.OverlayStatsProvider
import com.example.presentation.AppNavigationTab
import com.example.presentation.bubble.AishaBubbleService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TutorInputMethodService (v1) — ADDITIVE (§3.5 Goal Architecture).
 *
 * The second primary surface: a student-aware system keyboard with the
 * glyph strip (streak · XP · level), the 6-persona Tutor row, and
 * swappable input modes (QWERTY / symbols / Math / Quiz in v1).
 *
 * Persona taps deep-link into the matching tutor chat with the app's
 * existing agent stack — the keyboard never duplicates agent logic.
 */
class TutorKeyboardService : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var glyphStats by mutableStateOf<GamificationSnapshot>(GamificationEngine.emptySnapshot())

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            view.setContent {
                MyApplicationTheme {
                    TutorKeyboardRoot(
                        stats = glyphStats,
                        onCommit = { text -> commitText(text) },
                        onBackspace = { backspace() },
                        onEnter = { enter() },
                        onPersona = { agentId -> openPersona(agentId) }
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        scope.launch {
            val stats = OverlayStatsProvider.load(this@TutorKeyboardService)
            withContext(Dispatchers.Main) {
                glyphStats = stats.gamification
            }
        }
    }

    /** Never take over the screen with a fullscreen extract view. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        scope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Editing operations on the target app's text field.
    // ------------------------------------------------------------------

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        // Prefer the editor's own action (search/go/send), else newline.
        if (!ic.performEditorAction(EditorInfo.IME_ACTION_GO)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun openPersona(agentId: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.putExtra(AishaBubbleService.EXTRA_TAB, AppNavigationTab.AI_AGENTS.name)
        launch.putExtra(AishaBubbleService.EXTRA_AGENT, agentId)
        startActivity(launch)
    }
}
