package com.saleemkhan.myshopmanager.bridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.saleemkhan.myshopmanager.audio.GeminiAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/**
 * High-Quality Cloud-Based Text-to-Speech & Speech Recognition Android WebView Bridge.
 *
 * Replaces the default device-level robotic TTS with Google Gemini natural cloud voices
 * (gemini-2.5-flash-preview-tts & gemini-3.1-flash-tts-preview with warm human voice models).
 *
 * Exposes methods to JavaScript:
 * - speakText(text: String, isUrdu: Boolean)
 * - speak(text: String, isUrdu: Boolean)
 * - playCloudSpeech(text: String, isUrdu: Boolean)
 * - stopSpeech()
 * - stop()
 * - isSpeaking(): Boolean
 * - startSpeechRecognition(isUrdu: Boolean)
 * - stopSpeechRecognition()
 * - getEngineStatus(): String
 *
 * Dispatches events back to the WebView via JavaScript callbacks:
 * - window.onAndroidTtsStart()
 * - window.onAndroidTtsDone()
 * - window.onAndroidTtsError(errorMessage)
 * - window.onAndroidSpeechStart()
 * - window.onAndroidSpeechResult(transcript)
 * - window.onAndroidSpeechPartial(partialText)
 * - window.onAndroidSpeechEnd()
 * - window.onAndroidSpeechError(errorCode)
 */
class AndroidSpeechBridge(
    private val context: Context,
    private val webView: WebView? = null,
    private val audioEngine: GeminiAudioEngine = GeminiAudioEngine(context)
) {
    private val tag = "AndroidSpeechBridge"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isCurrentlySpeaking = false
    private var isGeminiNative = false

    // =========================================================================
    // JAVASCRIPT INTERFACE: HIGH-QUALITY CLOUD TEXT-TO-SPEECH
    // =========================================================================

    /**
     * Synthesizes and plays natural cloud-based speech for AI assistant responses.
     * Prioritizes Gemini's native audio stream over any fallback local TTS engine.
     */
    @JavascriptInterface
    fun speakText(text: String?, isUrdu: Boolean) {
        val safeText = text?.trim().orEmpty()
        Log.i(tag, "[BRIDGE_TTS] speakText requested: length=${safeText.length}, isUrdu=$isUrdu")

        if (safeText.isBlank()) {
            notifyJsTtsDone()
            return
        }

        // Stop any active audio before starting new utterance
        stopSpeech()

        isCurrentlySpeaking = true
        isGeminiNative = true // Prioritize Gemini native audio

        coroutineScope.launch(Dispatchers.IO) {
            try {
                audioEngine.speakText(
                    text = safeText,
                    isUrdu = isUrdu,
                    onStart = { isNative ->
                        isCurrentlySpeaking = true
                        isGeminiNative = isNative
                        notifyJsTtsStart(isGeminiNative = isNative)
                    },
                    onDone = {
                        isCurrentlySpeaking = false
                        isGeminiNative = false
                        notifyJsTtsDone()
                    },
                    onError = { error ->
                        isCurrentlySpeaking = false
                        isGeminiNative = false
                        Log.e(tag, "[BRIDGE_TTS] Cloud TTS playback error: $error")
                        notifyJsTtsError(error)
                    }
                )
            } catch (e: Exception) {
                isCurrentlySpeaking = false
                isGeminiNative = false
                Log.e(tag, "[BRIDGE_TTS] Exception calling cloud audio engine: ${e.message}", e)
                notifyJsTtsError(e.message ?: "Cloud TTS synthesis failed")
            }
        }
    }

    /**
     * Directly prioritizes Gemini's native audio stream over fallback local TTS engine.
     */
    @JavascriptInterface
    fun streamSpeech(text: String?, isUrdu: Boolean) {
        speakText(text, isUrdu)
    }

    /**
     * Handles the 'audio' response format from the Gemini API and updates the UI playback state
     * to signal that Gemini's native voice is active.
     *
     * Supports:
     * 1. Full Gemini API response: { "candidates": [ { "content": { "parts": [ { "inlineData": { "data": "BASE64" } } ] } } ] }
     * 2. Stream chunk format: { "chunk": "BASE64", "sampleRate": 24000 }
     * 3. Simple audio format: { "audioBase64": "BASE64", "sampleRate": 24000 }
     * 4. Raw Base64 audio string (PCM 24kHz Mono 16-bit or WAV).
     */
    @JavascriptInterface
    fun handleAudioResponse(responsePayload: String?) {
        val payload = responsePayload?.trim().orEmpty()
        if (payload.isBlank()) {
            Log.w(tag, "[BRIDGE_TTS] handleAudioResponse received empty payload")
            notifyJsTtsDone()
            return
        }

        try {
            var base64Data: String? = null

            if (payload.startsWith("{")) {
                try {
                    val root = JSONObject(payload)
                    // Check for Gemini API full response structure
                    if (root.has("candidates")) {
                        val candidates = root.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val inlineData = part.optJSONObject("inlineData")
                                    if (inlineData != null && inlineData.has("data")) {
                                        base64Data = inlineData.optString("data")
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (base64Data.isNullOrBlank()) {
                        base64Data = when {
                            root.has("audioBase64") -> root.optString("audioBase64")
                            root.has("chunk") -> root.optString("chunk")
                            root.has("data") -> root.optString("data")
                            root.has("audio") -> root.optString("audio")
                            else -> null
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "[BRIDGE_TTS] JSON parsing failed in handleAudioResponse, treating as raw string: ${e.message}")
                }
            }

            if (base64Data.isNullOrBlank()) {
                base64Data = payload
            }

            // Strip any whitespace or data URI prefix
            val cleanBase64 = base64Data.substringAfter("base64,").trim()
            val rawBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            if (rawBytes == null || rawBytes.isEmpty()) {
                Log.e(tag, "[BRIDGE_TTS] Decoded audio bytes are empty in handleAudioResponse")
                notifyJsTtsError("Empty audio data")
                return
            }

            stopSpeech()
            isCurrentlySpeaking = true
            isGeminiNative = true
            notifyJsTtsStart(isGeminiNative = true)

            audioEngine.playGeminiAudioBytes(
                rawBytes = rawBytes,
                onStart = { isNative ->
                    isCurrentlySpeaking = true
                    isGeminiNative = isNative
                    notifyJsTtsStart(isGeminiNative = isNative)
                },
                onDone = {
                    isCurrentlySpeaking = false
                    isGeminiNative = false
                    notifyJsTtsDone()
                },
                onError = { err ->
                    isCurrentlySpeaking = false
                    isGeminiNative = false
                    Log.e(tag, "[BRIDGE_TTS] Error playing Gemini audio response: $err")
                    notifyJsTtsError(err)
                }
            )
        } catch (e: Exception) {
            isCurrentlySpeaking = false
            isGeminiNative = false
            Log.e(tag, "[BRIDGE_TTS] Failed to process Gemini audio response: ${e.message}", e)
            notifyJsTtsError(e.message ?: "Failed to process audio response")
        }
    }

    /**
     * Feeds a streaming audio chunk directly from Gemini's native audio stream.
     */
    @JavascriptInterface
    fun playAudioStream(chunkBase64: String?, sampleRate: Int) {
        handleAudioResponse(chunkBase64)
    }

    /**
     * Plays raw or base64 audio data.
     */
    @JavascriptInterface
    fun playAudioData(audioBase64: String?, mimeType: String?, sampleRate: Int) {
        handleAudioResponse(audioBase64)
    }

    /**
     * Checks if Gemini's native voice is currently actively playing.
     */
    @JavascriptInterface
    fun isGeminiNativeVoiceActive(): Boolean {
        return isGeminiNative && isCurrentlySpeaking
    }

    /**
     * Returns real-time JSON audio playback state for UI binding.
     */
    @JavascriptInterface
    fun getAudioPlaybackState(): String {
        val provider = when {
            isGeminiNative && isCurrentlySpeaking -> "gemini-native"
            isCurrentlySpeaking -> "local-tts"
            else -> "idle"
        }
        return """{"isPlaying":$isCurrentlySpeaking,"isGeminiNative":$isGeminiNative,"provider":"$provider","status":"${if (isCurrentlySpeaking) "PLAYING" else "IDLE"}"}"""
    }

    /**
     * Alias for speakText to ensure compatibility with various web caller conventions.
     */
    @JavascriptInterface
    fun speak(text: String?, isUrdu: Boolean) {
        speakText(text, isUrdu)
    }

    /**
     * Explicit cloud speech invocation alias.
     */
    @JavascriptInterface
    fun playCloudSpeech(text: String?, isUrdu: Boolean) {
        speakText(text, isUrdu)
    }

    /**
     * Stops active speech playback immediately (barge-in support).
     */
    @JavascriptInterface
    fun stopSpeech() {
        Log.d(tag, "[BRIDGE_TTS] stopSpeech called")
        isCurrentlySpeaking = false
        try {
            audioEngine.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping audio engine: ${e.message}")
        }
        notifyJsTtsDone()
    }

    /**
     * Alias for stopSpeech.
     */
    @JavascriptInterface
    fun stop() {
        stopSpeech()
    }

    /**
     * Query whether speech playback is currently active.
     */
    @JavascriptInterface
    fun isSpeaking(): Boolean {
        return isCurrentlySpeaking
    }

    /**
     * Set playback speed of cloud-based TTS (0.5x to 2.0x).
     */
    @JavascriptInterface
    fun setPlaybackSpeed(speed: Float) {
        Log.d(tag, "[BRIDGE_TTS] setPlaybackSpeed called: $speed")
        audioEngine.playbackSpeed = speed
    }

    /**
     * Get active playback speed of cloud-based TTS.
     */
    @JavascriptInterface
    fun getPlaybackSpeed(): Float {
        return audioEngine.playbackSpeed
    }

    /**
     * Provides diagnostic status of the Cloud TTS Engine.
     */
    @JavascriptInterface
    fun getEngineStatus(): String {
        return """{"provider":"Google Gemini Cloud Audio","status":"READY","lastDiagResult":"${audioEngine.lastDiagResult}","detail":"${audioEngine.lastDiagDetail}"}"""
    }

    // =========================================================================
    // JAVASCRIPT INTERFACE: NATIVE SPEECH RECOGNITION (STT)
    // =========================================================================

    /**
     * Starts native speech recognition for Urdu / English voice commands.
     */
    @JavascriptInterface
    fun startSpeechRecognition(isUrdu: Boolean) {
        mainHandler.post {
            try {
                if (isListening) {
                    stopSpeechRecognitionInternal()
                }

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    notifyJsSpeechError("Speech recognition not available on this device")
                    return@post
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            notifyJsSpeechStart()
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                            notifyJsSpeechEnd()
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val errCode = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                                else -> "error-$error"
                            }
                            notifyJsSpeechError(errCode)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim().orEmpty()
                            notifyJsSpeechResult(text)
                            notifyJsSpeechEnd()
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim().orEmpty()
                            if (text.isNotBlank()) {
                                notifyJsSpeechPartial(text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    if (isUrdu) {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ur", "ur-PK", "en-US"))
                    } else {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    }
                }

                recognizer.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                Log.e(tag, "Failed to start speech recognition: ${e.message}", e)
                notifyJsSpeechError(e.message ?: "Recognition init failed")
            }
        }
    }

    /**
     * Stops speech recognition.
     */
    @JavascriptInterface
    fun stopSpeechRecognition() {
        mainHandler.post {
            stopSpeechRecognitionInternal()
        }
    }

    private fun stopSpeechRecognitionInternal() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            notifyJsSpeechEnd()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping recognizer: ${e.message}")
        }
    }

    // =========================================================================
    // JAVASCRIPT EVENT DISPATCHERS
    // =========================================================================

    private fun runJs(jsCode: String) {
        mainHandler.post {
            try {
                webView?.evaluateJavascript(jsCode, null)
            } catch (e: Exception) {
                Log.w(tag, "Failed to evaluate JS in WebView: ${e.message}")
            }
        }
    }

    private fun notifyJsTtsStart(isGeminiNative: Boolean = true) {
        val provider = if (isGeminiNative) "gemini-native" else "local-tts"
        runJs("""
            try {
                window.__isGeminiNativeVoiceActive = $isGeminiNative;
                window.__isAudioPlaying = true;
                window.__currentAudioProvider = '$provider';
                if (typeof window.onAndroidTtsStart === 'function') {
                    window.onAndroidTtsStart($isGeminiNative);
                }
                if (typeof window.onAndroidVoiceActive === 'function') {
                    window.onAndroidVoiceActive($isGeminiNative, '$provider');
                }
                if (typeof window.onAndroidAudioPlaybackStateChanged === 'function') {
                    window.onAndroidAudioPlaybackStateChanged(true, $isGeminiNative, '$provider');
                }
                window.dispatchEvent(new CustomEvent('geminiVoiceStateChange', {
                    detail: { active: true, isGeminiNative: $isGeminiNative, provider: '$provider' }
                }));
                window.dispatchEvent(new CustomEvent('androidAudioPlaybackState', {
                    detail: { isPlaying: true, isGeminiNative: $isGeminiNative, provider: '$provider' }
                }));
            } catch(e) { console.error('onAndroidTtsStart error', e); }
        """.trimIndent())
    }

    private fun notifyJsTtsDone() {
        runJs("""
            try {
                window.__isGeminiNativeVoiceActive = false;
                window.__isAudioPlaying = false;
                window.__currentAudioProvider = 'idle';
                if (typeof window.onAndroidTtsDone === 'function') {
                    window.onAndroidTtsDone();
                }
                if (typeof window.onAndroidVoiceActive === 'function') {
                    window.onAndroidVoiceActive(false, 'idle');
                }
                if (typeof window.onAndroidAudioPlaybackStateChanged === 'function') {
                    window.onAndroidAudioPlaybackStateChanged(false, false, 'idle');
                }
                window.dispatchEvent(new CustomEvent('geminiVoiceStateChange', {
                    detail: { active: false, isGeminiNative: false, provider: 'idle' }
                }));
                window.dispatchEvent(new CustomEvent('androidAudioPlaybackState', {
                    detail: { isPlaying: false, isGeminiNative: false, provider: 'idle' }
                }));
            } catch(e) { console.error('onAndroidTtsDone error', e); }
        """.trimIndent())
    }

    private fun notifyJsTtsError(message: String) {
        val safeMsg = escapeForJs(message)
        runJs("""
            try {
                window.__isGeminiNativeVoiceActive = false;
                window.__isAudioPlaying = false;
                window.__currentAudioProvider = 'error';
                if (typeof window.onAndroidTtsError === 'function') {
                    window.onAndroidTtsError('$safeMsg');
                }
                if (typeof window.onAndroidVoiceActive === 'function') {
                    window.onAndroidVoiceActive(false, 'error');
                }
                if (typeof window.onAndroidAudioPlaybackStateChanged === 'function') {
                    window.onAndroidAudioPlaybackStateChanged(false, false, 'error');
                }
                window.dispatchEvent(new CustomEvent('geminiVoiceStateChange', {
                    detail: { active: false, isGeminiNative: false, provider: 'error', error: '$safeMsg' }
                }));
                window.dispatchEvent(new CustomEvent('androidAudioPlaybackState', {
                    detail: { isPlaying: false, isGeminiNative: false, provider: 'error', error: '$safeMsg' }
                }));
            } catch(e) { console.error('onAndroidTtsError error', e); }
        """.trimIndent())
    }

    private fun notifyJsSpeechStart() {
        runJs("""
            try {
                if (typeof window.onAndroidSpeechStart === 'function') {
                    window.onAndroidSpeechStart();
                }
            } catch(e) { console.error('onAndroidSpeechStart error', e); }
        """.trimIndent())
    }

    private fun notifyJsSpeechResult(transcript: String) {
        val safeText = escapeForJs(transcript)
        runJs("""
            try {
                if (typeof window.onAndroidSpeechResult === 'function') {
                    window.onAndroidSpeechResult('$safeText');
                }
            } catch(e) { console.error('onAndroidSpeechResult error', e); }
        """.trimIndent())
    }

    private fun notifyJsSpeechPartial(transcript: String) {
        val safeText = escapeForJs(transcript)
        runJs("""
            try {
                if (typeof window.onAndroidSpeechPartial === 'function') {
                    window.onAndroidSpeechPartial('$safeText');
                }
            } catch(e) { console.error('onAndroidSpeechPartial error', e); }
        """.trimIndent())
    }

    private fun notifyJsSpeechEnd() {
        runJs("""
            try {
                if (typeof window.onAndroidSpeechEnd === 'function') {
                    window.onAndroidSpeechEnd();
                }
            } catch(e) { console.error('onAndroidSpeechEnd error', e); }
        """.trimIndent())
    }

    private fun notifyJsSpeechError(errorCode: String) {
        val safeCode = escapeForJs(errorCode)
        runJs("""
            try {
                if (typeof window.onAndroidSpeechError === 'function') {
                    window.onAndroidSpeechError('$safeCode');
                }
            } catch(e) { console.error('onAndroidSpeechError error', e); }
        """.trimIndent())
    }

    private fun escapeForJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    fun destroy() {
        stopSpeech()
        stopSpeechRecognitionInternal()
    }
}
