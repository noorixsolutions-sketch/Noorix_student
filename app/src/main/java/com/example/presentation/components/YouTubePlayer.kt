package com.example.presentation.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()
                        return if (url != null && url.startsWith("https://www.youtube.com/")) {
                            false // allow
                        } else {
                            true // block everything else
                        }
                    }
                }
                webChromeClient = WebChromeClient()
            }
        },
        update = { webView ->
            val html = """
                <html>
                    <body style="margin:0;padding:0;">
                        <iframe width="100%" height="100%" src="https://www.youtube.com/embed/$videoId" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>
                    </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    )
}
