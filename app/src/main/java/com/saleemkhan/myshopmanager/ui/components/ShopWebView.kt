package com.saleemkhan.myshopmanager.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.saleemkhan.myshopmanager.audio.GeminiAudioEngine
import com.saleemkhan.myshopmanager.bridge.AndroidAuthBridge
import com.saleemkhan.myshopmanager.bridge.AndroidBackupBridge
import com.saleemkhan.myshopmanager.bridge.AndroidPermissionBridge
import com.saleemkhan.myshopmanager.bridge.AndroidSpeechBridge

/**
 * Reusable Android WebView configured with High-Quality Cloud Text-to-Speech bridges.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShopWebView(
    url: String = "file:///android_asset/index.html",
    modifier: Modifier = Modifier.fillMaxSize(),
    onPageFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val audioEngine = remember { GeminiAudioEngine(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                val speechBridge = AndroidSpeechBridge(ctx, this, audioEngine)
                addJavascriptInterface(speechBridge, "AndroidSpeech")
                addJavascriptInterface(speechBridge, "AndroidTtsBridge")
                addJavascriptInterface(speechBridge, "AndroidTts")
                addJavascriptInterface(AndroidPermissionBridge(ctx), "AndroidPermissionBridge")
                addJavascriptInterface(AndroidAuthBridge(ctx), "AndroidAuth")
                addJavascriptInterface(AndroidBackupBridge(ctx, this), "AndroidBackup")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                    }
                }

                webChromeClient = WebChromeClient()

                loadUrl(url)
            }
        },
        update = { webView ->
            // No-op for now
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stop()
        }
    }
}
