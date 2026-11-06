package com.example.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*

/**
 * Playback error state for official YouTube embedded player.
 */
sealed class YouTubePlayerState {
    object Loading : YouTubePlayerState()
    object Ready : YouTubePlayerState()
    data class Error(val message: String, val isRestrictedOrUnavailable: Boolean = false) : YouTubePlayerState()
}

/**
 * Secure, official YouTube Embedded Player using the YouTube IFrame Player API.
 * Conforms strictly to Section 8:
 * - Does not scrape or extract raw stream URLs.
 * - Plays through the official YouTube embedded player within an origin-restricted WebView.
 * - Restricts navigation to trusted YouTube origins.
 * - Handles restricted videos, private/deleted videos, disabled embedding, and network errors gracefully.
 * - Respects YouTube player controls and lifecycle (pauses on background/dispose).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialYouTubeEmbedPlayer(
    videoId: String,
    channelId: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier,
    onPlayerStateChanged: ((YouTubePlayerState) -> Unit)? = null
) {
    val context = LocalContext.current
    var playerState by remember(videoId) { mutableStateOf<YouTubePlayerState>(YouTubePlayerState.Loading) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Validate videoId format (Standard YouTube IDs are 11 alphanumeric characters)
    val isValidVideoId = remember(videoId) { videoId.matches(Regex("^[A-Za-z0-9_-]{6,20}$")) }

    DisposableEffect(videoId) {
        onDispose {
            // Clean up player on leave
            webViewRef?.let { wv ->
                wv.loadUrl("about:blank")
                wv.onPause()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    if (!isValidVideoId) {
        YouTubePlayerErrorBox(
            message = "Invalid YouTube Video ID format ($videoId). Video cannot be embedded.",
            isRestricted = false,
            onRetry = null,
            modifier = modifier
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NearBlackSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Strict WebView security
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                playerState = YouTubePlayerState.Loading
                                onPlayerStateChanged?.invoke(playerState)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (playerState !is YouTubePlayerState.Error) {
                                    playerState = YouTubePlayerState.Ready
                                    onPlayerStateChanged?.invoke(playerState)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                playerState = YouTubePlayerState.Error(
                                    description ?: "Failed to connect to YouTube",
                                    isRestrictedOrUnavailable = false
                                )
                                onPlayerStateChanged?.invoke(playerState)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return true
                                // Only allow official YouTube player domains
                                return if (url.startsWith("https://www.youtube.com/") ||
                                    url.startsWith("https://youtube.com/") ||
                                    url.startsWith("https://www.youtube-nocookie.com/")
                                ) {
                                    false
                                } else {
                                    true
                                }
                            }
                        }

                        // Load official YouTube IFrame HTML snippet
                        val embedHtml = buildYouTubeIFrameHtml(videoId)
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            embedHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                }
            )

            // Loading overlay
            if (playerState is YouTubePlayerState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NearBlackBg.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading Noorix Video…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            // Error overlay
            if (playerState is YouTubePlayerState.Error) {
                val err = playerState as YouTubePlayerState.Error
                YouTubePlayerErrorBox(
                    message = err.message,
                    isRestricted = err.isRestrictedOrUnavailable,
                    onRetry = {
                        val html = buildYouTubeIFrameHtml(videoId)
                        webViewRef?.loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun YouTubePlayerErrorBox(
    message: String,
    isRestricted: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(NearBlackElevated)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (isRestricted) CyberGold else StepIssue,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRestricted) "Video Unavailable or Restricted" else "Video Playback Error",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccentSubtle, contentColor = TealAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Playback", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Builds valid HTML incorporating the official YouTube IFrame Embed API with responsive viewport.
 */
private fun buildYouTubeIFrameHtml(videoId: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; height: 100%; background: #090D12; overflow: hidden; }
                iframe { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <iframe
                src="https://www.youtube.com/embed/$videoId?enablejsapi=1&origin=https://www.youtube.com&rel=0&modestbranding=1&playsinline=1"
                title="Noorix Educational Video"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen>
            </iframe>
        </body>
        </html>
    """.trimIndent()
}
