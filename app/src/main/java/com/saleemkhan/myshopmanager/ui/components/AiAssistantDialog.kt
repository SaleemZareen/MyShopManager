package com.saleemkhan.myshopmanager.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.saleemkhan.myshopmanager.audio.GeminiAudioEngine
import com.saleemkhan.myshopmanager.audio.TtsDiagResult
import com.saleemkhan.myshopmanager.data.AiQuotaManager
import com.saleemkhan.myshopmanager.data.GeminiAdvisorService
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class AssistantChatMessage(
    val sender: String, // "AI" or "USER"
    val text: String,
    val timestamp: String = ""
)

enum class AssistantMode {
    LIVE_VOICE, // Gemini 3.8 Live API Full-Duplex Voice
    CHAT_ADVISOR // Gemini 3.8 Flash Business Chat & Analysis
}

enum class LiveVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpenVoiceEntry: () -> Unit = {},
    state: AppState,
    isUrdu: Boolean
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val advisorService = remember { GeminiAdvisorService(context) }
    val audioEngine = remember { GeminiAudioEngine(context) }
    val metrics = remember(state) { advisorService.extractMetrics(state) }
    val quotaManager = remember { AiQuotaManager.getInstance(context) }

    // Settings & Quota Dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var customKeyInput by remember { mutableStateOf(quotaManager.getCustomApiKey()) }
    var hasCustomKey by remember { mutableStateOf(quotaManager.hasCustomKey()) }
    var freeRemaining by remember { mutableStateOf(quotaManager.getFreeTierRemaining()) }
    var ttsPlaybackSpeed by remember { mutableFloatStateOf(GeminiAudioEngine.getPlaybackSpeed(context)) }

    // Primary UI Mode
    var currentMode by remember { mutableStateOf(AssistantMode.LIVE_VOICE) }

    // Chat State
    var promptInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isGeminiNativeVoiceActive by remember { mutableStateOf(false) }
    var activeSpeakingText by remember { mutableStateOf<String?>(null) }
    var autoVoiceEnabled by remember { mutableStateOf(true) }

    // Live Voice Mode State (Gemini 3.8 Live API Experience)
    var liveVoiceState by remember { mutableStateOf(LiveVoiceState.IDLE) }
    var continuousLiveMode by remember { mutableStateOf(true) } // Hands-free continuous loop
    var liveUserTranscript by remember { mutableStateOf("") }
    var liveAiTranscript by remember { mutableStateOf("") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isListening by remember { mutableStateOf(false) }

    // Diagnostics & Audio test state
    var showDiagnosticsSection by remember { mutableStateOf(false) }
    var diagResultText by remember { mutableStateOf("IDLE") }
    var diagDetailText by remember { mutableStateOf("System Ready") }
    var isDiagRunning by remember { mutableStateOf(false) }

    // Initial Welcome Message
    val initialAiMessage = remember(isUrdu, metrics) {
        if (isUrdu) {
            "السلام علیکم ${metrics.ownerName}! میں آپ کا \"دکان اے آئی اسسٹنٹ\" ہوں، جو Google Gemini 3.8 لائیو اور فلیش انجن پر کام کرتا ہے۔ میں آپ کی دکان کے تمام مالیات، نقد دراز، ادھار کھاتوں، منافع اور سیٹنگز پر مکمل عبور رکھتا ہوں۔ آپ مجھ سے بول کر یا لکھ کر کچھ بھی پوچھ سکتے ہیں!"
        } else {
            "Assalam-o-Alaikum ${metrics.ownerName}! I am your Dukan AI Assistant powered by Google Gemini 3.8 Live & Flash Engine. I have complete command over your shop's financials, cash drawer, credit ledger, inventory, and settings. Talk or chat with me anytime!"
        }
    }

    var messages by remember {
        mutableStateOf(listOf(AssistantChatMessage(sender = "AI", text = initialAiMessage)))
    }

    // Audio Cleanup
    fun stopAudioPlayback() {
        try {
            audioEngine.stop()
        } catch (_: Exception) {}
        isSpeaking = false
        isGeminiNativeVoiceActive = false
        activeSpeakingText = null
        if (liveVoiceState == LiveVoiceState.SPEAKING) {
            liveVoiceState = LiveVoiceState.IDLE
        }
    }

    // Scroll chat to bottom when message or speaking state changes
    LaunchedEffect(messages.size, isLoading, isSpeaking) {
        if (messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Back handler cleans up audio & speech
    BackHandler {
        stopAudioPlayback()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        onClose()
    }

    // Audio Playback with Gemini Natural Voice
    fun speakAiResponse(text: String, onFinished: (() -> Unit)? = null) {
        if (text.isBlank()) return
        stopAudioPlayback()

        coroutineScope.launch {
            try {
                audioEngine.speakText(
                    text = text,
                    isUrdu = isUrdu,
                    onStart = { isNative ->
                        isSpeaking = true
                        isGeminiNativeVoiceActive = isNative
                        activeSpeakingText = text
                        if (currentMode == AssistantMode.LIVE_VOICE) {
                            liveVoiceState = LiveVoiceState.SPEAKING
                        }
                    },
                    onDone = {
                        isSpeaking = false
                        isGeminiNativeVoiceActive = false
                        activeSpeakingText = null
                        if (currentMode == AssistantMode.LIVE_VOICE) {
                            liveVoiceState = LiveVoiceState.IDLE
                            onFinished?.invoke()
                        }
                    },
                    onError = { errMsg ->
                        isSpeaking = false
                        isGeminiNativeVoiceActive = false
                        activeSpeakingText = null
                        if (currentMode == AssistantMode.LIVE_VOICE) {
                            liveVoiceState = LiveVoiceState.IDLE
                        }
                        diagDetailText = errMsg
                    }
                )
            } catch (e: Exception) {
                isSpeaking = false
                isGeminiNativeVoiceActive = false
                activeSpeakingText = null
            }
        }
    }

    // Forward declaration of triggerLiveSpeechRecognizer
    var triggerSpeechListening: (() -> Unit)? by remember { mutableStateOf(null) }

    // Core Prompt Sender (Powered by Gemini 3.8 Flash & Live Engine)
    fun handleSendPrompt(textToSend: String) {
        val text = textToSend.trim()
        if (text.isBlank() || isLoading) return

        stopAudioPlayback()

        // Stop listening if recognizer active
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false

        liveUserTranscript = text
        val updatedMessages = messages + AssistantChatMessage(sender = "USER", text = text)
        messages = updatedMessages
        promptInput = ""
        isLoading = true

        if (currentMode == AssistantMode.LIVE_VOICE) {
            liveVoiceState = LiveVoiceState.THINKING
            liveAiTranscript = if (isUrdu) "Gemini 3.8 لائیو جواب تیار کر رہا ہے..." else "Gemini 3.8 Live is processing..."
        }

        coroutineScope.launch {
            try {
                val reply = advisorService.askAdvisor(
                    question = text,
                    metrics = metrics,
                    isUrdu = isUrdu
                )
                messages = updatedMessages + AssistantChatMessage(sender = "AI", text = reply)
                liveAiTranscript = reply
                freeRemaining = quotaManager.getFreeTierRemaining()
                hasCustomKey = quotaManager.hasCustomKey()
                isLoading = false

                if (autoVoiceEnabled || currentMode == AssistantMode.LIVE_VOICE) {
                    speakAiResponse(reply) {
                        // Continuous Conversation Loop for Gemini 3.8 Live
                        if (continuousLiveMode && currentMode == AssistantMode.LIVE_VOICE) {
                            coroutineScope.launch {
                                delay(750) // Generous 750ms pause before mic opens so speech output never collides or truncates
                                triggerSpeechListening?.invoke()
                            }
                        }
                    }
                } else {
                    if (currentMode == AssistantMode.LIVE_VOICE) {
                        liveVoiceState = LiveVoiceState.IDLE
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                if (currentMode == AssistantMode.LIVE_VOICE) {
                    liveVoiceState = LiveVoiceState.IDLE
                }
                val errorMsg = if (isUrdu) {
                    "معذرت، رابطہ قائم نہیں ہو سکا۔ برائے مہربانی اپنا سوال دوبارہ پوچھیے۔"
                } else {
                    "Connection issue. Please retry your question."
                }
                messages = updatedMessages + AssistantChatMessage(sender = "AI", text = errorMsg)
            }
        }
    }

    // Android Speech Recognition Setup
    val speechIntentFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (currentMode == AssistantMode.LIVE_VOICE && liveVoiceState == LiveVoiceState.LISTENING) {
            liveVoiceState = LiveVoiceState.IDLE
        }
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            val sanitized = FormatUtils.sanitizePakistaniCurrency(spoken).trim()
            if (sanitized.isNotBlank()) {
                handleSendPrompt(sanitized)
            }
        }
    }

    fun startSpeechRecognizer() {
        stopAudioPlayback()

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        val recognizerContext = (context as? android.app.Activity) ?: context
        if (!SpeechRecognizer.isRecognitionAvailable(recognizerContext)) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isUrdu) "ur-PK" else "en-PK")
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isUrdu) "Gemini 3.8 لائیو کے ساتھ بولیں..." else "Talk with Gemini 3.8 Live...")
            }
            speechIntentFallbackLauncher.launch(intent)
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(recognizerContext)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isUrdu) "ur-PK" else "en-PK")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (isUrdu) "ur-PK" else "en-PK")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prevent premature cut-off: allow natural pauses up to 2.5-3.0 seconds so full sentences are heard
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                liveVoiceState = LiveVoiceState.LISTENING
            }
            override fun onBeginningOfSpeech() {
                stopAudioPlayback()
                isListening = true
                liveVoiceState = LiveVoiceState.LISTENING
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                // If user spoke something and partial results recorded it, do not lose it on timeout
                val fallbackSpoken = liveUserTranscript.trim()
                if (fallbackSpoken.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    liveUserTranscript = ""
                    handleSendPrompt(fallbackSpoken)
                    return
                }
                if (currentMode == AssistantMode.LIVE_VOICE) {
                    liveVoiceState = LiveVoiceState.IDLE
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spokenRaw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
                val spokenText = FormatUtils.sanitizePakistaniCurrency(spokenRaw).trim()
                val candidateText = if (spokenText.isNotBlank()) spokenText else liveUserTranscript.trim()
                if (candidateText.isNotBlank()) {
                    liveUserTranscript = candidateText
                    handleSendPrompt(candidateText)
                } else if (currentMode == AssistantMode.LIVE_VOICE) {
                    liveVoiceState = LiveVoiceState.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partialRaw = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
                val partial = FormatUtils.sanitizePakistaniCurrency(partialRaw).trim()
                if (partial.isNotBlank()) {
                    liveUserTranscript = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognizer()
        } else {
            Toast.makeText(
                context,
                if (isUrdu) "براہ کرم مائیکروفون کی اجازت دیں" else "Microphone permission required",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestAndStartListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognizer()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    triggerSpeechListening = {
        requestAndStartListening()
    }

    // Pulsing Animation for Live Orb
    val infiniteTransition = rememberInfiniteTransition(label = "LiveOrbTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (liveVoiceState == LiveVoiceState.LISTENING || liveVoiceState == LiveVoiceState.SPEAKING) 1.25f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (liveVoiceState) {
                    LiveVoiceState.LISTENING -> 800
                    LiveVoiceState.SPEAKING -> 600
                    LiveVoiceState.THINKING -> 500
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (liveVoiceState != LiveVoiceState.IDLE) 0.85f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloAlpha"
    )

    // Quick Business Questions Chips
    val quickQuestions = remember(isUrdu) {
        if (isUrdu) {
            listOf(
                "📊 آج کا منافع و سیلز",
                "👥 ادھار کھاتہ دار",
                "📦 کم اسٹاک کی اشیاء",
                "💵 کیش دراز بیلنس",
                "🧾 واٹس ایپ بل گائیڈ",
                "💡 سیلز بڑھانے کے ٹپس",
                "⚙️ بیک اپ کیسے لیں؟"
            )
        } else {
            listOf(
                "📊 Today's Sales & Profit",
                "👥 Top Debtors (Udhaar)",
                "📦 Low Stock Alert",
                "💵 Cash in Drawer",
                "🧾 WhatsApp Bill Guide",
                "💡 Tips to Grow Sales",
                "⚙️ How to Backup?"
            )
        }
    }

    // ==========================================
    // UI SURFACE & MAIN DIALOG
    // ==========================================
    CompositionLocalProvider(
        LocalLayoutDirection provides (if (isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f),
            color = Color(0xFF041D16) // Deep Dukan Forest Night canvas
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    // Dukan Emerald & Gold Gradient Top App Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF072C21), Color(0xFF041D16))
                                )
                            )
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Back & Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        stopAudioPlayback()
                                        try { speechRecognizer?.destroy() } catch (_: Exception) {}
                                        onClose()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (isUrdu) "دکان اے آئی لائیو اسسٹنٹ" else "Dukan AI Live Assistant",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = DukanGreenPrimary.copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, DukanGoldSecondary.copy(alpha = 0.65f))
                                        ) {
                                            Text(
                                                text = "Gemini 3.8",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = DukanGoldSecondary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${metrics.shopName} • ${metrics.ownerName}",
                                        fontSize = 10.5.sp,
                                        color = DukanGreenLight.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Actions: Settings ⚙️ and Voice Output Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Voice Toggle Button with dynamic mini-wave indicator when speaking
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) stopAudioPlayback()
                                        autoVoiceEnabled = !autoVoiceEnabled
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.GraphicEq else if (autoVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "Voice Output",
                                        tint = if (isSpeaking) DukanGoldSecondary else if (autoVoiceEnabled) Color(0xFF34D399) else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Settings & Key Button
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (hasCustomKey) Color(0xFF34D399) else DukanGoldSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Mode Switcher Tab (Live Voice vs Smart Chat in Dukan theme)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .background(Color(0xFF07271D), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF0E4333), RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Live Voice Mode Tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (currentMode == AssistantMode.LIVE_VOICE) DukanGreenPrimary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        stopAudioPlayback()
                                        currentMode = AssistantMode.LIVE_VOICE
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (currentMode == AssistantMode.LIVE_VOICE) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isUrdu) "🎙️ لائیو صوتی گفتگو" else "🎙️ Live Voice",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentMode == AssistantMode.LIVE_VOICE) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Business Chat Mode Tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (currentMode == AssistantMode.CHAT_ADVISOR) DukanGreenHover else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        stopAudioPlayback()
                                        currentMode = AssistantMode.CHAT_ADVISOR
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = if (currentMode == AssistantMode.CHAT_ADVISOR) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isUrdu) "💬 بزنس چیٹ و تجزیہ" else "💬 Business Chat",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentMode == AssistantMode.CHAT_ADVISOR) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Compact Financial Quick Glance Strip in Dukan Palette
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF06221A))
                                .border(1.dp, Color(0xFF0C382A))
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isUrdu) "سیلز" else "Sales"}: Rs. ${FormatUtils.formatNumber(metrics.todaySales)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                            Text(
                                text = "${if (isUrdu) "منافع" else "Profit"}: Rs. ${FormatUtils.formatNumber(metrics.todayProfit)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DukanGoldSecondary
                            )
                            Text(
                                text = "${if (isUrdu) "ادھار" else "Udhaar"}: Rs. ${FormatUtils.formatNumber(metrics.totalCreditUdhaar)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                            Text(
                                text = "${if (isUrdu) "سٹاک" else "Stock"}: ${metrics.inventoryCount}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentMode) {
                        // ==================================================
                        // 1. GEMINI 3.8 LIVE VOICE CONVERSATION MODE
                        // ==================================================
                        AssistantMode.LIVE_VOICE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Status Headline in Dukan Theme
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = when (liveVoiceState) {
                                            LiveVoiceState.LISTENING -> Color(0xFF78350F)
                                            LiveVoiceState.THINKING -> Color(0xFF064E3B)
                                            LiveVoiceState.SPEAKING -> Color(0xFF064E3B)
                                            LiveVoiceState.IDLE -> Color(0xFF07271D)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            when (liveVoiceState) {
                                                LiveVoiceState.LISTENING -> DukanGoldSecondary
                                                LiveVoiceState.THINKING -> DukanGreenPrimary
                                                LiveVoiceState.SPEAKING -> DukanGoldSecondary
                                                LiveVoiceState.IDLE -> Color(0xFF0E4333)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        when (liveVoiceState) {
                                                            LiveVoiceState.LISTENING -> DukanGoldSecondary
                                                            LiveVoiceState.THINKING -> Color(0xFF34D399)
                                                            LiveVoiceState.SPEAKING -> DukanGoldSecondary
                                                            LiveVoiceState.IDLE -> Color(0xFFA7F3D0)
                                                        },
                                                        CircleShape
                                                    )
                                            )
                                            Text(
                                                text = when (liveVoiceState) {
                                                    LiveVoiceState.LISTENING -> if (isUrdu) "آپ کی بات سن رہا ہوں... فرمائیے" else "Listening... Please speak"
                                                    LiveVoiceState.THINKING -> if (isUrdu) "Gemini 3.8 Live سوچ رہا ہے..." else "Gemini 3.8 Live is thinking..."
                                                    LiveVoiceState.SPEAKING -> if (isGeminiNativeVoiceActive) {
                                                        if (isUrdu) "Gemini قدرتی لائیو آواز فعال ہے (روکنے کے لیے ٹیپ کریں)" else "Gemini Native Voice Active (Tap to stop)"
                                                    } else {
                                                        if (isUrdu) "بول رہا ہوں... (روکنے کے لیے ٹیپ کریں)" else "Speaking... (Tap to interrupt)"
                                                    }
                                                    LiveVoiceState.IDLE -> if (isUrdu) "بات کرنے کے لیے مائیک دبائیں" else "Tap microphone to talk"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Interactive Glowing Pulsing Orb Centerpiece (Dukan Green & Gold Palette)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(220.dp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    // Outer Glowing Halo Wave 2
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .scale(if (liveVoiceState != LiveVoiceState.IDLE) pulseScale * 1.1f else 1f)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        when (liveVoiceState) {
                                                            LiveVoiceState.LISTENING -> DukanGoldSecondary.copy(alpha = haloAlpha * 0.45f)
                                                            LiveVoiceState.THINKING -> DukanGreenPrimary.copy(alpha = haloAlpha * 0.45f)
                                                            LiveVoiceState.SPEAKING -> DukanGoldSecondary.copy(alpha = haloAlpha * 0.5f)
                                                            LiveVoiceState.IDLE -> Color(0xFF0E4333).copy(alpha = 0.25f)
                                                        },
                                                        Color.Transparent
                                                    )
                                                ),
                                                CircleShape
                                            )
                                    )

                                    // Outer Glowing Halo Wave 1
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .scale(pulseScale)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        when (liveVoiceState) {
                                                            LiveVoiceState.LISTENING -> DukanGoldLight.copy(alpha = haloAlpha * 0.7f)
                                                            LiveVoiceState.THINKING -> Color(0xFF34D399).copy(alpha = haloAlpha * 0.7f)
                                                            LiveVoiceState.SPEAKING -> DukanGreenPrimary.copy(alpha = haloAlpha * 0.75f)
                                                            LiveVoiceState.IDLE -> Color(0xFF135A43).copy(alpha = 0.35f)
                                                        },
                                                        Color.Transparent
                                                    )
                                                ),
                                                CircleShape
                                            )
                                    )

                                    // Core Interactive Orb Button
                                    Surface(
                                        shape = CircleShape,
                                        color = when (liveVoiceState) {
                                            LiveVoiceState.LISTENING -> DukanGoldSecondary
                                            LiveVoiceState.THINKING -> DukanGreenHover
                                            LiveVoiceState.SPEAKING -> DukanGreenPrimary
                                            LiveVoiceState.IDLE -> Color(0xFF0A3326)
                                        },
                                        border = BorderStroke(
                                            2.dp,
                                            when (liveVoiceState) {
                                                LiveVoiceState.LISTENING -> DukanGoldLight
                                                LiveVoiceState.THINKING -> Color(0xFFA7F3D0)
                                                LiveVoiceState.SPEAKING -> DukanGoldSecondary
                                                LiveVoiceState.IDLE -> Color(0xFF135A43)
                                            }
                                        ),
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clickable {
                                                if (isSpeaking) {
                                                    stopAudioPlayback()
                                                } else if (isListening) {
                                                    try { speechRecognizer?.stopListening() } catch (_: Exception) {}
                                                    isListening = false
                                                    liveVoiceState = LiveVoiceState.IDLE
                                                } else {
                                                    requestAndStartListening()
                                                }
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = when (liveVoiceState) {
                                                    LiveVoiceState.SPEAKING -> Icons.Default.GraphicEq
                                                    LiveVoiceState.THINKING -> Icons.Default.AutoAwesome
                                                    LiveVoiceState.LISTENING -> Icons.Default.Mic
                                                    LiveVoiceState.IDLE -> Icons.Default.Mic
                                                },
                                                contentDescription = "Voice Orb",
                                                tint = Color.White,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }
                                }

                                // ==============================================================
                                // LIVE VISUAL SOUND WAVE ANIMATION FEEDBACK
                                // Provides real-time acoustic wave animations whenever AI speaks
                                // ==============================================================
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF07271D),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING)
                                            DukanGoldSecondary.copy(alpha = 0.65f)
                                        else Color(0xFF0E4333)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("ai_live_soundwave_feedback_panel")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MiniSoundWaveIndicator(
                                                isSpeaking = (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING),
                                                barColor = DukanGoldSecondary
                                            )
                                            Text(
                                                text = if (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING) {
                                                    if (isGeminiNativeVoiceActive) {
                                                        if (isUrdu) "Gemini قدرتی آواز جاری ہے (Gemini Native Audio Stream)" else "Gemini Native Voice Active (Cloud Stream)"
                                                    } else {
                                                        if (isUrdu) "آواز جاری ہے (Live Soundwave Feedback)" else "AI Speaking (Live Soundwave Feedback)"
                                                    }
                                                } else if (liveVoiceState == LiveVoiceState.LISTENING) {
                                                    if (isUrdu) "سن رہا ہوں..." else "Listening to your voice..."
                                                } else {
                                                    if (isUrdu) "دکان صوتی انجن تیار ہے" else "Voice Engine Ready"
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING)
                                                    DukanGoldSecondary
                                                else Color(0xFFA7F3D0)
                                            )
                                            MiniSoundWaveIndicator(
                                                isSpeaking = (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING),
                                                barColor = DukanGoldSecondary
                                            )
                                        }

                                        // Multi-Bar Acoustic Sound Wave Visualizer
                                        SoundWaveVisualizer(
                                            isSpeaking = (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING),
                                            barCount = 24,
                                            minHeight = 5.dp,
                                            maxHeight = 32.dp,
                                            primaryColor = DukanGreenPrimary,
                                            secondaryColor = DukanGoldSecondary,
                                            modifier = Modifier.height(34.dp)
                                        )
                                    }
                                }

                                // Live Conversation Bubble (Real-time transcript)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF07271D),
                                    border = BorderStroke(1.dp, Color(0xFF0E4333)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // User speech
                                        if (liveUserTranscript.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isUrdu) "آپ:" else "You:",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DukanGoldSecondary
                                                )
                                                Text(
                                                    text = liveUserTranscript,
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        // AI live reply with active mini soundwave
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Gemini 3.8:",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF34D399)
                                                )
                                                if (isSpeaking || liveVoiceState == LiveVoiceState.SPEAKING) {
                                                    MiniSoundWaveIndicator(
                                                        isSpeaking = true,
                                                        barColor = DukanGoldSecondary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = liveAiTranscript.ifBlank {
                                                    if (isUrdu) "السلام علیکم! اپنے کاروبار، سیلز یا کسی بھی سیٹنگ کے بارے میں براہ راست سوال پوچھیں۔" else "Assalam-o-Alaikum! Ask me anything directly about your shop sales, profit, or ledger."
                                                },
                                                fontSize = 12.5.sp,
                                                color = Color(0xFFCBD5E1),
                                                lineHeight = 18.sp,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Bottom Controls for Live Voice
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Hands-free Continuous Conversation Switch
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF07271D), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFF0E4333), RoundedCornerShape(12.dp))
                                            .clickable { continuousLiveMode = !continuousLiveMode }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Headphones,
                                                contentDescription = null,
                                                tint = if (continuousLiveMode) DukanGoldSecondary else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isUrdu) "ہینڈز فری مسلسل صوتی گفتگو (Continuous Live Mode)" else "Hands-Free Continuous Conversation",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Switch(
                                            checked = continuousLiveMode,
                                            onCheckedChange = { continuousLiveMode = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = DukanGoldSecondary,
                                                checkedTrackColor = DukanGreenPrimary,
                                                uncheckedThumbColor = Color(0xFF94A3B8),
                                                uncheckedTrackColor = Color(0xFF0A3326)
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }

                                    // Quick Voice Questions Pills in Dukan Theme
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        quickQuestions.take(4).forEach { q ->
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color(0xFF0A3326),
                                                border = BorderStroke(1.dp, Color(0xFF135A43)),
                                                modifier = Modifier.clickable {
                                                    handleSendPrompt(q.substringAfter(" "))
                                                }
                                            ) {
                                                Text(
                                                    text = q,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = DukanGreenLight,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==================================================
                        // 2. GEMINI 3.8 FLASH BUSINESS CHAT & ADVISOR
                        // ==================================================
                        AssistantMode.CHAT_ADVISOR -> {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Messages Stream
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(messages) { index, msg ->
                                        val isAi = (msg.sender == "AI")
                                        val isCurrentSpeaking = (isSpeaking && activeSpeakingText == msg.text)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (isAi) 4.dp else 16.dp,
                                                    bottomEnd = if (isAi) 16.dp else 4.dp
                                                ),
                                                color = if (isAi) Color(0xFF07271D) else DukanGreenPrimary,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isCurrentSpeaking) DukanGoldSecondary
                                                    else if (isAi) Color(0xFF0E4333)
                                                    else DukanGreenHover
                                                ),
                                                modifier = Modifier.widthIn(max = 320.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    // Header inside Bubble
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isAi) Icons.Default.AutoAwesome else Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = if (isAi) DukanGoldSecondary else DukanGoldLight,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (isAi) "Gemini 3.8 Advisor" else metrics.ownerName,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAi) DukanGoldSecondary else DukanGoldLight
                                                        )
                                                        if (isAi && isCurrentSpeaking) {
                                                            MiniSoundWaveIndicator(
                                                                isSpeaking = true,
                                                                barColor = DukanGoldSecondary
                                                            )
                                                        }
                                                    }

                                                    // Message Body
                                                    Text(
                                                        text = msg.text,
                                                        fontSize = 13.sp,
                                                        color = Color.White,
                                                        lineHeight = 19.sp
                                                    )

                                                    // AI Action Buttons: Listen & Copy
                                                    if (isAi) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // Listen / Stop Button
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (isCurrentSpeaking) Color(0xFF0A3326) else Color(0xFF041D16),
                                                                border = BorderStroke(
                                                                    1.dp,
                                                                    if (isCurrentSpeaking) DukanGoldSecondary else Color(0xFF135A43)
                                                                ),
                                                                modifier = Modifier.clickable {
                                                                    if (isCurrentSpeaking) {
                                                                        stopAudioPlayback()
                                                                    } else {
                                                                        speakAiResponse(msg.text)
                                                                    }
                                                                }
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    if (isCurrentSpeaking) {
                                                                        MiniSoundWaveIndicator(
                                                                            isSpeaking = true,
                                                                            barColor = DukanGoldSecondary
                                                                        )
                                                                    }
                                                                    Icon(
                                                                        imageVector = if (isCurrentSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                                        contentDescription = null,
                                                                        tint = if (isCurrentSpeaking) DukanGoldSecondary else DukanGreenLight,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Text(
                                                                        text = if (isCurrentSpeaking) (if (isUrdu) "روکیں" else "Stop") else (if (isUrdu) "سنیں" else "Listen"),
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isCurrentSpeaking) DukanGoldSecondary else DukanGreenLight
                                                                    )
                                                                }
                                                            }

                                                            // Copy Button
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = Color(0xFF041D16),
                                                                border = BorderStroke(1.dp, Color(0xFF135A43)),
                                                                modifier = Modifier.clickable {
                                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                    val clip = ClipData.newPlainText("AI Response", msg.text)
                                                                    clipboard.setPrimaryClip(clip)
                                                                    Toast.makeText(context, if (isUrdu) "کاپی ہو گیا" else "Copied", Toast.LENGTH_SHORT).show()
                                                                }
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = null,
                                                                        tint = DukanGreenLight,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Text(
                                                                        text = if (isUrdu) "کاپی" else "Copy",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        color = DukanGreenLight
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Loading indicator
                                    if (isLoading) {
                                        item {
                                            Row(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    color = DukanGoldSecondary,
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = if (isUrdu) "Gemini 3.8 سوچ رہا ہے..." else "Gemini 3.8 is analyzing...",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFA7F3D0)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Active Sound Wave Bar in Chat Mode when Speaking
                                if (isSpeaking) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF07271D),
                                        border = BorderStroke(1.dp, DukanGoldSecondary.copy(alpha = 0.75f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                            .testTag("ai_chat_speaking_soundwave_bar")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                MiniSoundWaveIndicator(isSpeaking = true, barColor = DukanGoldSecondary)
                                                Text(
                                                    text = if (isUrdu) "آواز جاری ہے..." else "AI Speaking...",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DukanGoldSecondary
                                                )
                                                SoundWaveVisualizer(
                                                    isSpeaking = true,
                                                    barCount = 18,
                                                    minHeight = 4.dp,
                                                    maxHeight = 22.dp,
                                                    primaryColor = DukanGreenPrimary,
                                                    secondaryColor = DukanGoldSecondary,
                                                    modifier = Modifier
                                                        .height(24.dp)
                                                        .weight(1f)
                                                )
                                            }
                                            IconButton(
                                                onClick = { stopAudioPlayback() },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop",
                                                    tint = DukanGoldSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Suggestions Chips in Dukan theme
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    quickQuestions.forEach { q ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF07271D),
                                            border = BorderStroke(1.dp, Color(0xFF0E4333)),
                                            modifier = Modifier.clickable {
                                                handleSendPrompt(q.substringAfter(" "))
                                            }
                                        ) {
                                            Text(
                                                text = q,
                                                fontSize = 10.5.sp,
                                                color = DukanGreenLight,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Chat Bottom Input Bar in Dukan Theme
                                Surface(
                                    color = Color(0xFF072C21),
                                    border = BorderStroke(1.dp, Color(0xFF0E4333)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Mic Button
                                        IconButton(
                                            onClick = { requestAndStartListening() },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (isListening) Color(0xFFDC2626) else Color(0xFF0A3326),
                                                    CircleShape
                                                )
                                                .border(1.dp, Color(0xFF135A43), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Voice Input",
                                                tint = if (isListening) Color.White else DukanGoldSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Text Input Field
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFF041D16),
                                            border = BorderStroke(1.dp, Color(0xFF0E4333)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            BasicTextField(
                                                value = promptInput,
                                                onValueChange = { promptInput = it },
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                ),
                                                cursorBrush = SolidColor(DukanGoldSecondary),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions.Default,
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        if (promptInput.isNotBlank()) {
                                                            handleSendPrompt(promptInput)
                                                        }
                                                    }
                                                ),
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        if (promptInput.isEmpty()) {
                                                            Text(
                                                                text = if (isUrdu) "اے آئی سے کچھ بھی پوچھیں..." else "Ask AI anything...",
                                                                fontSize = 12.5.sp,
                                                                color = DukanGreenLight.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                        }

                                        // Send Button
                                        IconButton(
                                            onClick = {
                                                if (promptInput.isNotBlank()) {
                                                    handleSendPrompt(promptInput)
                                                }
                                            },
                                            enabled = promptInput.isNotBlank() && !isLoading,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (promptInput.isNotBlank()) DukanGreenPrimary else Color(0xFF0A3326),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = if (promptInput.isNotBlank()) DukanGoldSecondary else Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. SETTINGS & CONFIGURATION DIALOG
        // ==========================================
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = Color(0xFF07271D),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = DukanGoldSecondary
                        )
                        Text(
                            text = if (isUrdu) "اے آئی اسسٹنٹ سیٹنگز" else "AI Assistant Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Model Info Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF041D16),
                            border = BorderStroke(1.dp, Color(0xFF0E4333)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = DukanGoldSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Google Gemini 3.8 Flash & Live Engine",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isUrdu) {
                                        "ماڈل: gemini-3.8-flash (لائیو گفتگو اور ذہین کاروباری تجزیہ)۔ کوٹہ: $freeRemaining / 1500 مفت یومیہ کالز۔"
                                    } else {
                                        "Model: gemini-3.8-flash (Live voice conversation and business analysis). Quota: $freeRemaining / 1500 free daily calls."
                                    },
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }

                        // Cloud TTS Playback Speed Control Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF041D16),
                            border = BorderStroke(1.dp, Color(0xFF0E4333)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_settings_tts_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = DukanGoldSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isUrdu) "کلاؤڈ صوتی رفتار (TTS Speed)" else "Cloud TTS Playback Speed",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF07271D),
                                        border = BorderStroke(1.dp, DukanGoldSecondary.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.2fx", ttsPlaybackSpeed),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = DukanGoldSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Slider(
                                    value = ttsPlaybackSpeed,
                                    onValueChange = { newSpeed ->
                                        val rounded = (Math.round(newSpeed * 20f) / 20f).coerceIn(0.5f, 2.0f)
                                        ttsPlaybackSpeed = rounded
                                        GeminiAudioEngine.setPlaybackSpeed(context, rounded)
                                        audioEngine.playbackSpeed = rounded
                                    },
                                    valueRange = 0.5f..2.0f,
                                    steps = 29,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DukanGoldSecondary,
                                        activeTrackColor = DukanGreenPrimary,
                                        inactiveTrackColor = Color(0xFF0E4333),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .testTag("ai_settings_tts_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { preset ->
                                        val isSel = Math.abs(ttsPlaybackSpeed - preset) < 0.03f
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) DukanGreenPrimary else Color(0xFF07271D),
                                            border = BorderStroke(1.dp, if (isSel) DukanGoldSecondary else Color(0xFF0E4333)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    ttsPlaybackSpeed = preset
                                                    GeminiAudioEngine.setPlaybackSpeed(context, preset)
                                                    audioEngine.playbackSpeed = preset
                                                }
                                        ) {
                                            Text(
                                                text = String.format(java.util.Locale.US, "%.2fx", preset),
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) Color.White else Color(0xFFA7F3D0),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // API Key Input
                        Text(
                            text = if (isUrdu) "ذاتی مفت Gemini API Key (1,500 کالز کے لیے):" else "Personal Free Gemini API Key (for 1,500 calls):",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = DukanGreenLight
                        )

                        OutlinedTextField(
                            value = customKeyInput,
                            onValueChange = { customKeyInput = it },
                            placeholder = { Text("AIzaSy...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DukanGoldSecondary,
                                unfocusedBorderColor = Color(0xFF0E4333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // 1-Tap Link to get free key
                        Button(
                            onClick = {
                                try {
                                    val browserIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://aistudio.google.com/app/apikey")
                                    )
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "براؤزر کھولنے میں ناکامی", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF041D16)),
                            border = BorderStroke(1.dp, Color(0xFF135A43)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Get Key", tint = DukanGoldSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUrdu) "گوگل اے آئی اسٹوڈیو سے مفت کی لیں ($0)" else "Get Free Key from Google AI Studio ($0)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DukanGoldSecondary
                            )
                        }

                        // Collapsible Diagnostics Section
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF041D16),
                            border = BorderStroke(1.dp, Color(0xFF0E4333)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDiagnosticsSection = !showDiagnosticsSection }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isUrdu) "🔧 صوتی ٹیسٹنگ اور ڈائیگناسٹکس" else "🔧 Audio Diagnostics & Tests",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DukanGreenLight
                                    )
                                    Icon(
                                        imageVector = if (showDiagnosticsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DukanGreenLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (showDiagnosticsSection) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Status: $diagDetailText",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFFA7F3D0)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                isDiagRunning = true
                                                coroutineScope.launch {
                                                    audioEngine.testEnglishVoice(
                                                        onStart = { diagDetailText = "English Playing" },
                                                        onDone = { diagDetailText = "English Done"; isDiagRunning = false },
                                                        onError = { err -> diagDetailText = err; isDiagRunning = false }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary)
                                        ) {
                                            Text("English Test", fontSize = 9.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                isDiagRunning = true
                                                coroutineScope.launch {
                                                    audioEngine.testUrduVoice(
                                                        onStart = { diagDetailText = "Urdu Playing" },
                                                        onDone = { diagDetailText = "Urdu Done"; isDiagRunning = false },
                                                        onError = { err -> diagDetailText = err; isDiagRunning = false }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenHover)
                                        ) {
                                            Text("Urdu Test", fontSize = 9.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            quotaManager.saveCustomApiKey(customKeyInput.trim())
                            hasCustomKey = quotaManager.hasCustomKey()
                            freeRemaining = quotaManager.getFreeTierRemaining()
                            showSettingsDialog = false
                            Toast.makeText(
                                context,
                                if (isUrdu) "سیٹنگز محفوظ ہو گئیں" else "Settings saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary)
                    ) {
                        Text(text = if (isUrdu) "محفوظ کریں" else "Save", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text(text = if (isUrdu) "بند کریں" else "Close", color = DukanGreenLight)
                    }
                }
            )
        }
    }
}

/**
 * High-craft dynamic acoustic sound wave visualizer.
 * Activates whenever the AI assistant is speaking to provide live feedback,
 * rendered using harmonic waveforms in the Dukan Green & Gold brand theme.
 */
@Composable
fun SoundWaveVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 22,
    minHeight: Dp = 6.dp,
    maxHeight: Dp = 38.dp,
    primaryColor: Color = DukanGreenPrimary,
    secondaryColor: Color = DukanGoldSecondary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundWaveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "soundWavePhase"
    )

    // Smooth amplitude transition so bars grow and subside gracefully
    val activeAmplitude by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0.12f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "waveAmplitude"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val minHeightPx = with(LocalDensity.current) { minHeight.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val heightRange = maxHeightPx - minHeightPx

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()
            // Bell curve envelope so center bars are naturally more prominent
            val envelope = (4f * normalizedIndex * (1f - normalizedIndex)).coerceIn(0.25f, 1f)
            val wave1 = kotlin.math.sin(phase * 1.6f + i * 0.45f)
            val wave2 = kotlin.math.cos(phase * 2.2f - i * 0.35f)
            val wave3 = kotlin.math.sin(phase * 0.9f + i * 0.7f)
            val combinedWave = ((wave1 * 0.45f + wave2 * 0.35f + wave3 * 0.20f) + 1f) / 2f
            val dynamicFactor = (envelope * 0.6f + 0.4f) * combinedWave

            val dynamicHeightDp = minHeight + with(LocalDensity.current) {
                (heightRange * dynamicFactor * activeAmplitude).toDp()
            }

            val barColor = androidx.compose.ui.graphics.lerp(
                primaryColor,
                secondaryColor,
                (dynamicFactor * activeAmplitude).coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(dynamicHeightDp.coerceIn(minHeight, maxHeight))
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                barColor.copy(alpha = if (isSpeaking) 1f else 0.35f),
                                barColor.copy(alpha = if (isSpeaking) 0.8f else 0.18f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Compact 5-bar live sound wave equalizer indicator for badges, chips, and headers.
 */
@Composable
fun MiniSoundWaveIndicator(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = DukanGoldSecondary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "miniWaveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "miniPhase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = listOf(0.45f, 0.95f, 0.65f, 1.0f, 0.55f)
        heights.forEachIndexed { i, factor ->
            val wave = (kotlin.math.sin(phase + i * 0.85f) + 1f) / 2f
            val activeHeight = (3.dp + (10.dp * wave * factor)).coerceIn(3.dp, 14.dp)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(if (isSpeaking) activeHeight else 3.dp)
                    .clip(CircleShape)
                    .background(if (isSpeaking) barColor else barColor.copy(alpha = 0.35f))
            )
        }
    }
}
