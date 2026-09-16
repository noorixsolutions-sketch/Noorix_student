package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.parent.ParentPrefs
import com.example.presentation.AppNavigationTab
import com.example.presentation.MainScreen
import com.example.presentation.OLevelViewModel
import com.example.presentation.bubble.AishaBubbleService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deep links from the bubble / fan-out / Tutor Keyboard (§3 + §3.5).
        applyDeepLink(ViewModelProvider(this)[OLevelViewModel::class.java], intent)
        setContent {
            MyApplicationTheme {
                val viewModel: OLevelViewModel = viewModel()
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: reuse the live ViewModel instead of recreating the stack.
        applyDeepLink(ViewModelProvider(this)[OLevelViewModel::class.java], intent)
    }

    private fun applyDeepLink(viewModel: OLevelViewModel, intent: Intent?) {
        val tabName = intent?.getStringExtra(AishaBubbleService.EXTRA_TAB) ?: return
        val tab = runCatching { AppNavigationTab.valueOf(tabName) }.getOrNull() ?: return
        intent.getStringExtra(AishaBubbleService.EXTRA_AGENT)?.let { agentId ->
            if (agentId.isNotBlank()) viewModel.selectAgent(agentId)
        }
        viewModel.setTab(tab)
        if (tab == AppNavigationTab.AI_AGENTS) {
            // Opening chat consumes the waiting 💛 from a parent encouragement.
            ParentPrefs.setMessageWaiting(this, false)
        }
    }
}
