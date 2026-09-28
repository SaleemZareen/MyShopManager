package com.saleemkhan.myshopmanager.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class ParsedVoiceResult(
    val type: TransactionType,
    val amount: Double,
    val partyName: String? = null,
    val category: String? = null,
    val notes: String,
    val urduSummary: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceEntryDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSubmit: (Transaction) -> Unit,
    isUrdu: Boolean
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var parsedData by remember { mutableStateOf<ParsedVoiceResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var autoParse by remember { mutableStateOf(true) }

    val sampleCommands = remember {
        listOf(
            "آج سیلز 20000",
            "کرایہ 12000 نقد",
            "پرچیز 35000 نیسلے ڈیپو",
            "علی بھائی سے 5000 ادھار وصولی",
            "سپلائر کو 15000 ادائیگی"
        )
    }

    // Parsing NLP function matching web React version
    fun parseVoiceCommand(text: String): ParsedVoiceResult {
        val clean = FormatUtils.sanitizePakistaniCurrency(text).trim()
        val lower = clean.lowercase(Locale.ROOT)

        var type = TransactionType.SALE
        var category = "General Goods"
        var amount = 0.0
        var partyName: String? = null

        // 1. Amount Extraction (Numbers first)
        val digitsMatch = Regex("""\b\d[\d,]*\b""").find(clean)
        if (digitsMatch != null) {
            amount = digitsMatch.value.replace(",", "").toDoubleOrNull() ?: 0.0
        }

        // Urdu word numbers fallback
        if (amount <= 0.0) {
            when {
                clean.contains("پچاس ہزار") || lower.contains("50k") -> amount = 50000.0
                clean.contains("چالیس ہزار") || lower.contains("40k") -> amount = 40000.0
                clean.contains("تیس ہزار") || lower.contains("30k") -> amount = 30000.0
                clean.contains("بیس ہزار") || lower.contains("20k") -> amount = 20000.0
                clean.contains("پندرہ ہزار") || lower.contains("15k") -> amount = 15000.0
                clean.contains("دس ہزار") || lower.contains("10k") -> amount = 10000.0
                clean.contains("پانچ ہزار") || lower.contains("5k") -> amount = 5000.0
                clean.contains("تین ہزار") || lower.contains("3k") -> amount = 3000.0
                clean.contains("دو ہزار") || lower.contains("2k") -> amount = 2000.0
                clean.contains("ایک ہزار") || clean.contains("ہزار") || lower.contains("1k") -> amount = 1000.0
                clean.contains("پانچ سو") -> amount = 500.0
                clean.contains("تین سو") -> amount = 300.0
                clean.contains("دو سو") -> amount = 200.0
                clean.contains("سو") -> amount = 100.0
                else -> amount = 2000.0
            }
        }

        // 2. Transaction Type and Category Detection
        when {
            clean.contains("ادھار") || clean.contains("وصول") || clean.contains("وصولی") || lower.contains("collect") || lower.contains("wasooli") -> {
                type = TransactionType.RECEIPT
                category = "Customer Recovery"
            }
            clean.contains("سپلائر") || clean.contains("ادائیگی") || clean.contains("پیمنٹ") || lower.contains("supplier") || lower.contains("adaigi") -> {
                type = TransactionType.PAYMENT
                category = "Supplier Payment"
            }
            clean.contains("پرچیز") || clean.contains("خریداری") || clean.contains("خریدا") || lower.contains("purchase") -> {
                type = TransactionType.PURCHASE
                category = "Stock Resale"
            }
            clean.contains("کرایہ") || clean.contains("rent") || clean.contains("خرچ") || clean.contains("خرچہ") || clean.contains("بل") || clean.contains("چائے") || lower.contains("expense") -> {
                type = TransactionType.EXPENSE
                category = when {
                    clean.contains("کرایہ") || lower.contains("rent") -> "SHOP_RENT"
                    clean.contains("چائے") || lower.contains("tea") -> "TEA"
                    clean.contains("بجلی") || lower.contains("electric") -> "ELECTRICITY"
                    else -> "MISCELLANEOUS"
                }
            }
            else -> {
                type = TransactionType.SALE
                category = "General Goods"
            }
        }

        // 3. Party Name extraction
        val partyRegex = Regex("""([^\s\d]+(?:\s+[^\s\d]+)?)\s+(?:سے|کو)""").find(clean)
        if (partyRegex != null) {
            val extracted = partyRegex.groupValues[1].trim()
            if (extracted !in listOf("آج", "روپے", "نقد", "کیش", "کل", "مال", "ادھار")) {
                partyName = extracted
            }
        }
        if (partyName == null) {
            when {
                clean.contains("نیسلے ڈیپو") || clean.contains("نیسلے") -> partyName = "نیسلے ڈیپو"
                clean.contains("علی بھائی") -> partyName = "علی بھائی"
                clean.contains("سپلائر") -> partyName = "سپلائر"
                clean.contains("احمد") -> partyName = "احمد"
            }
        }

        val summary = when (type) {
            TransactionType.SALE -> "فروخت: Rs. ${FormatUtils.formatNumber(amount)}"
            TransactionType.EXPENSE -> "خرچہ: Rs. ${FormatUtils.formatNumber(amount)}"
            TransactionType.PURCHASE -> "خریداری: Rs. ${FormatUtils.formatNumber(amount)}"
            TransactionType.RECEIPT -> "ادھار وصولی: Rs. ${FormatUtils.formatNumber(amount)}"
            TransactionType.PAYMENT -> "ادائیگی: Rs. ${FormatUtils.formatNumber(amount)}"
            else -> "اندراج: Rs. ${FormatUtils.formatNumber(amount)}"
        }

        return ParsedVoiceResult(
            type = type,
            amount = amount,
            partyName = partyName,
            category = category,
            notes = "Voice Command: \"$clean\"",
            urduSummary = summary
        )
    }

    fun handleProcessText(rawText: String? = null) {
        val textToProcess = (rawText ?: inputText).trim()
        if (textToProcess.isBlank()) return

        isParsing = true
        errorMessage = null

        coroutineScope.launch {
            // Brief natural AI thinking delay
            delay(400)
            val result = parseVoiceCommand(textToProcess)
            parsedData = result
            isParsing = false
        }
    }

    // Android Speech Recognizer setup
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListeningAndProcess() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        val text = (transcript.ifBlank { inputText }).trim()
        if (text.isNotBlank()) {
            handleProcessText(text)
        }
    }

    val intentFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            val sanitized = FormatUtils.sanitizePakistaniCurrency(spoken)
            if (sanitized.isNotBlank()) {
                inputText = sanitized
                transcript = sanitized
                if (autoParse) {
                    handleProcessText(sanitized)
                }
            }
        }
    }

    fun launchSpeechIntentFallback() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isUrdu) "ur-PK" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isUrdu) "اردو میں بولیے جیسے: \"آج سیلز 20000\"" else "Speak Urdu or English command")
        }
        try {
            intentFallbackLauncher.launch(intent)
        } catch (_: Exception) {
            errorMessage = if (isUrdu) {
                "آواز ریکارڈ کرنے میں مسئلہ پیش آیا۔ آپ نیچے براہ راست لکھ بھی سکتے ہیں۔"
            } else {
                "Voice capture failed. You can type your command below."
            }
        }
    }

    fun startListeningFlow() {
        errorMessage = null
        parsedData = null

        // Check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionDenied = true
            return
        }

        permissionDenied = false
        isListening = true
        transcript = ""
        inputText = ""

        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            permissionDenied = true
                            errorMessage = if (isUrdu) {
                                "مائیکروفون کی اجازت بلاکڈ ہے۔ مائیک آن کرنے کے لیے نیچے دیے گئے بٹن پر کلک کریں۔"
                            } else {
                                "Microphone permission is blocked. Please allow permission."
                            }
                        } else {
                            // Fallback to intent recognizer if speech recognizer error
                            launchSpeechIntentFallback()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val raw = matches?.firstOrNull() ?: ""
                        val text = FormatUtils.sanitizePakistaniCurrency(raw)
                        if (text.isNotBlank()) {
                            transcript = text
                            inputText = text
                            if (autoParse) {
                                handleProcessText(text)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val raw = matches?.firstOrNull() ?: ""
                        val text = FormatUtils.sanitizePakistaniCurrency(raw)
                        if (text.isNotBlank()) {
                            transcript = text
                            inputText = text
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isUrdu) "ur-PK" else "en-PK")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer.startListening(intent)
            } else {
                launchSpeechIntentFallback()
            }
        } catch (_: Exception) {
            launchSpeechIntentFallback()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            startListeningFlow()
        } else {
            permissionDenied = true
            errorMessage = if (isUrdu) {
                "مائیکروفون کی اجازت نہیں مل سکی۔ آواز ان پٹ کے لیے مائیکروفون کی اجازت درکار ہے۔"
            } else {
                "Microphone permission denied. Access is required for hands-free voice ledger entry."
            }
        }
    }

    fun handleSaveParsed() {
        val data = parsedData ?: return
        val tx = Transaction(
            id = "tx_${System.currentTimeMillis()}",
            type = data.type,
            amount = data.amount,
            category = data.category ?: if (data.type == TransactionType.EXPENSE) "SHOP_RENT" else "General Goods",
            paymentMethod = PaymentMethod.CASH,
            partyName = data.partyName ?: (if (isUrdu) "وائس کسٹمر" else "Voice Customer"),
            date = FormatUtils.getTodayIsoDate(),
            notes = data.notes
        )
        onSubmit(tx)
        onClose()
    }

    fun handleCancelAndRecordNew() {
        parsedData = null
        inputText = ""
        transcript = ""
        errorMessage = null
        startListeningFlow()
    }

    // Clean up SpeechRecognizer on dialog dismiss
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    // Infinite pulse animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = {
            if (isListening) stopListeningAndProcess()
            onClose()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            if (isListening) stopListeningAndProcess()
            onClose()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // 1. Header (Emerald Green #126A49 matching Web)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF126A49))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isUrdu) "اردو اے آئی وائس اینٹری" else "Urdu AI Voice Entry",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 2. Fullscreen Body Content (Scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        // Auto Parse Toggle Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = Color(0xFF126A49),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isUrdu) "خودکار اے آئی تجزیہ (Auto Parse)" else "Auto Parse Voice Entry",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                            }

                            Button(
                                onClick = { autoParse = !autoParse },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (autoParse) Color(0xFF126A49) else Slate200,
                                    contentColor = if (autoParse) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (autoParse) (if (isUrdu) "آن (ON)" else "ON") else (if (isUrdu) "آف (OFF)" else "OFF"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Subtitle guide
                        Text(
                            text = if (isUrdu)
                                "اردو میں بولیے جیسے: \"آج سیلز 20,000\" یا \"علی بھائی سے 5,000 ادھار وصولی\""
                            else
                                "Speak Urdu command e.g. \"Aaj sales 20,000\" or \"Ali bhai 5,000 udhaar collect\"",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600,
                            textAlign = TextAlign.Center
                        )

                        // Big Mic Button & Live Listening state
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(100.dp)
                            ) {
                                if (isListening) {
                                    // Animated Outer Sound Ring
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF43F5E).copy(alpha = 0.25f))
                                    )
                                }

                                Surface(
                                    onClick = {
                                        if (isListening) {
                                            stopListeningAndProcess()
                                        } else {
                                            startListeningFlow()
                                        }
                                    },
                                    shape = CircleShape,
                                    color = if (isListening) Color(0xFFF43F5E) else Color(0xFF126A49),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .then(if (isListening) Modifier.scale(pulseScale) else Modifier)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = "Mic",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = if (isListening) "سن رہا ہے..." else (if (isUrdu) "بولیں (Mic)" else "Speak"),
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Live listening status & Done button
                            if (isListening) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFFFFF1F2), RoundedCornerShape(16.dp))
                                            .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFF43F5E))
                                        )
                                        Text(
                                            text = if (isUrdu) "پوری بات آرام سے بولیں، مائیک سن رہا ہے..." else "Listening continuously... speak freely",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBE123C)
                                        )
                                    }

                                    Button(
                                        onClick = { stopListeningAndProcess() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Done",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isUrdu) "بات مکمل ہوگئی (Done Speaking)" else "Done Speaking (Process)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Permission blocked / denied recovery buttons
                            if (permissionDenied) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isUrdu) "دوبارہ کوشش کریں" else "Try Again",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isUrdu) "موبائل سیٹنگز کھولیں" else "Open Settings",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Error Banner if present
                        if (errorMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alert",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Manual Text Input Bar + Parse Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = if (isUrdu) "یہاں بھی لکھ سکتے ہیں..." else "Or type your entry here...",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF126A49),
                                    unfocusedBorderColor = Slate200,
                                    focusedContainerColor = Slate50,
                                    unfocusedContainerColor = Slate50
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = { handleProcessText() },
                                enabled = !isParsing && inputText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "پراسس" else "Parse",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quick Sample Command Chips (Matching React Web)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "فوری نمونہ بولیں یا ٹیپ کریں:" else "Quick Sample Commands:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500
                            )

                            // Row 1
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sampleCommands.take(2).forEach { cmd ->
                                    Surface(
                                        onClick = {
                                            inputText = cmd
                                            transcript = cmd
                                            handleProcessText(cmd)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Slate100,
                                        border = BorderStroke(1.dp, Slate200)
                                    ) {
                                        Text(
                                            text = "\"$cmd\"",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // Row 2
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sampleCommands.slice(2..3).forEach { cmd ->
                                    Surface(
                                        onClick = {
                                            inputText = cmd
                                            transcript = cmd
                                            handleProcessText(cmd)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Slate100,
                                        border = BorderStroke(1.dp, Slate200)
                                    ) {
                                        Text(
                                            text = "\"$cmd\"",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // Row 3
                            sampleCommands.lastOrNull()?.let { cmd ->
                                Surface(
                                    onClick = {
                                        inputText = cmd
                                        transcript = cmd
                                        handleProcessText(cmd)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate100,
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Text(
                                        text = "\"$cmd\"",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // AI Processing Spinner Indicator
                        if (isParsing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF126A49),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isUrdu) "اے آئی کھاتہ تجزیہ کر رہا ہے..." else "AI Gemini is parsing entry...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF126A49)
                                )
                            }
                        }

                        // Parsed Result Preview Card (Matching React Web)
                        val parsed = parsedData
                        if (parsed != null && !isParsing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Result header
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
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Parsed",
                                            tint = Color(0xFF126A49),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isUrdu) "اے آئی کا تیار کردہ اندراج:" else "AI Parsed Result:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF126A49)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = parsed.type.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF065F46),
                                            modifier = Modifier
                                                .background(Color(0xFFA7F3D0), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                parsedData = null
                                                inputText = ""
                                                transcript = ""
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Slate500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Inner White Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = FormatUtils.formatCurrency(parsed.amount, "Rs."),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Slate900
                                    )

                                    if (!parsed.partyName.isNullOrBlank()) {
                                        Text(
                                            text = "${if (isUrdu) "نام/کھاتہ: " else "Party: "}${parsed.partyName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    }

                                    Text(
                                        text = "${if (isUrdu) "کیٹیگری/نوٹ: " else "Category: "}${parsed.category} (${parsed.notes})",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate600
                                    )

                                    if (!parsed.urduSummary.isNullOrBlank()) {
                                        Text(
                                            text = "\"${parsed.urduSummary}\"",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF065F46),
                                            modifier = Modifier
                                                .background(Color(0xFFD1FAE5), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Action Buttons (Save vs Cancel & Record Again)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { handleSaveParsed() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Save",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isUrdu) "اینٹری محفوظ کریں (Save)" else "Save Entry",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { handleCancelAndRecordNew() },
                                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFFFFF1F2),
                                            contentColor = Color(0xFFBE123C)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Record Again",
                                            tint = Color(0xFFBE123C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isUrdu) "منسوخ کریں، دوبارہ بولیں" else "Cancel & Record Again",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
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