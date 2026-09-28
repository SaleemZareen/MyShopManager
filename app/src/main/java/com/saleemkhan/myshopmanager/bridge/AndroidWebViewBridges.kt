package com.saleemkhan.myshopmanager.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.saleemkhan.myshopmanager.data.AppStateRepository
import com.saleemkhan.myshopmanager.data.GeminiAdvisorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Supporting Android Bridges to ensure the Web App / WebView operates smoothly.
 */
class AndroidPermissionBridge(private val context: Context) {
    @JavascriptInterface
    fun checkPermission(permissionName: String?): String {
        return "GRANTED"
    }

    @JavascriptInterface
    fun requestPermission(permissionName: String?): String {
        return "GRANTED"
    }
}

class AndroidAuthBridge(private val context: Context) {
    @JavascriptInterface
    fun authenticateBiometrics() {
        Log.d("AndroidAuthBridge", "authenticateBiometrics called")
    }
}

class AndroidBackupBridge(
    private val context: Context,
    private val webView: WebView? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @JavascriptInterface
    fun connectGoogleAccount() {
        Log.d("AndroidBackupBridge", "connectGoogleAccount called")
    }

    @JavascriptInterface
    fun disconnectGoogleAccount() {
        Log.d("AndroidBackupBridge", "disconnectGoogleAccount called")
    }

    @JavascriptInterface
    fun downloadFromGoogleDrive() {
        Log.d("AndroidBackupBridge", "downloadFromGoogleDrive called")
    }

    @JavascriptInterface
    fun uploadToGoogleDrive(data: String?) {
        Log.d("AndroidBackupBridge", "uploadToGoogleDrive called")
    }

    @JavascriptInterface
    fun openExternalUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            mainHandler.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("AndroidBackupBridge", "Failed to open URL: ${e.message}")
                }
            }
        }
    }

    @JavascriptInterface
    fun getAppSigningInfo(): String {
        return """{"flavor":"production","package":"com.saleemkhan.myshopmanager"}"""
    }

    @JavascriptInterface
    fun getSyncConfig(): String {
        return """{"autoSync":true,"intervalMinutes":60}"""
    }

    @JavascriptInterface
    fun updateCurrentAppState(jsonState: String?) {
        if (!jsonState.isNullOrBlank()) {
            try {
                val repo = AppStateRepository.getInstance(context)
                repo.importJson(jsonState)
            } catch (e: Exception) {
                Log.w("AndroidBackupBridge", "Failed to update app state: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun saveLocalBackup(data: String?) {
        Log.d("AndroidBackupBridge", "saveLocalBackup called")
    }

    @JavascriptInterface
    fun pickAndRestoreLocalBackup() {
        Log.d("AndroidBackupBridge", "pickAndRestoreLocalBackup called")
    }

    @JavascriptInterface
    fun scheduleBackgroundSync() {
        Log.d("AndroidBackupBridge", "scheduleBackgroundSync called")
    }

    @JavascriptInterface
    fun selectLocalBackupFolder() {
        Log.d("AndroidBackupBridge", "selectLocalBackupFolder called")
    }

    @JavascriptInterface
    fun requestAiAssistant(payloadJson: String?) {
        Log.d("AndroidBackupBridge", "requestAiAssistant called with payload: $payloadJson")
        if (payloadJson.isNullOrBlank()) {
            notifyAiResponse(null, "Question cannot be empty")
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(payloadJson)
                val question = json.optString("question", "").trim()
                val isUrdu = json.optBoolean("isUrdu", true)
                if (question.isBlank()) {
                    notifyAiResponse(null, "Question is blank")
                    return@launch
                }
                val repo = AppStateRepository.getInstance(context)
                val appState = repo.appState.value
                val advisorService = GeminiAdvisorService(context)
                val metrics = advisorService.extractMetrics(appState)
                val reply = advisorService.askAdvisor(
                    question = question,
                    metrics = metrics,
                    isUrdu = isUrdu
                )
                notifyAiResponse(reply, null)
            } catch (e: Exception) {
                Log.e("AndroidBackupBridge", "AI Advisor error: ${e.message}", e)
                notifyAiResponse(null, e.message ?: "Failed to generate AI advice")
            }
        }
    }

    @JavascriptInterface
    fun requestAiAssistant() {
        Log.d("AndroidBackupBridge", "requestAiAssistant parameterless called")
    }

    private fun notifyAiResponse(reply: String?, error: String?) {
        mainHandler.post {
            try {
                val escapedReply = reply?.let { JSONObject.quote(it) } ?: "null"
                val escapedError = error?.let { JSONObject.quote(it) } ?: "null"
                val js = "if (typeof window.onNativeAiResponse === 'function') { window.onNativeAiResponse($escapedReply, $escapedError); }"
                webView?.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.w("AndroidBackupBridge", "Failed to dispatch onNativeAiResponse: ${e.message}")
            }
        }
    }
}
