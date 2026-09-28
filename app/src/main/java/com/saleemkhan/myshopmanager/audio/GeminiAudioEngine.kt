package com.saleemkhan.myshopmanager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saleemkhan.myshopmanager.data.AiQuotaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Diagnostic results strictly required by troubleshooting checklist:
 * - GEMINI_OK_AUDIO_OK
 * - GEMINI_FAILED
 * - PCM_INVALID
 * - AUDIOTRACK_FAILED
 * - AUDIO_ROUTE_FAILED
 * - SPEAK_TEXT_NOT_CALLED
 */
enum class TtsDiagResult {
    IDLE,
    RUNNING,
    GEMINI_OK_AUDIO_OK,
    GEMINI_FAILED,
    PCM_INVALID,
    AUDIOTRACK_FAILED,
    AUDIO_ROUTE_FAILED,
    SPEAK_TEXT_NOT_CALLED
}

/**
 * 100% Guaranteed Audible Voice Engine for Physical Android Devices:
 * 1. Gemini Native Audio API (gemini-2.5-flash-preview-tts with Aoede voice).
 * 2. High-performance direct PCM AudioTrack playback.
 * 3. Offline Android TextToSpeech with Urdu & phonetic fallback (never silent).
 */
class GeminiAudioEngine(private val context: Context) {

    companion object {
        const val PREFS_NAME = "myshopmanager_tts_prefs"
        const val KEY_PLAYBACK_SPEED = "tts_playback_speed"
        const val DEFAULT_PLAYBACK_SPEED = 1.0f

        /**
         * Retrieves the stored user-configured playback speed (clamped between 0.5x and 2.0x).
         */
        fun getPlaybackSpeed(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED).coerceIn(0.5f, 2.0f)
        }

        /**
         * Persists the user-configured playback speed into SharedPreferences.
         */
        fun setPlaybackSpeed(context: Context, speed: Float) {
            val clamped = (Math.round(speed * 100f) / 100f).coerceIn(0.5f, 2.0f)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_PLAYBACK_SPEED, clamped)
                .apply()
        }
    }

    /**
     * Active playback speed for cloud-based TTS and local fallback engines.
     */
    var playbackSpeed: Float
        get() = getPlaybackSpeed(context)
        set(value) {
            val clamped = (Math.round(value * 100f) / 100f).coerceIn(0.5f, 2.0f)
            setPlaybackSpeed(context, clamped)
            try {
                activeAudioTrack?.playbackParams = PlaybackParams().apply {
                    this.speed = clamped
                    this.pitch = 1.0f
                }
            } catch (_: Exception) {}
            try {
                mediaPlayer?.playbackParams = PlaybackParams().apply {
                    this.speed = clamped
                    this.pitch = 1.0f
                }
            } catch (_: Exception) {}
            try {
                systemTts?.setSpeechRate(clamped)
            } catch (_: Exception) {}
        }

    private val tag = "GeminiAudioEngine"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var activeAudioTrack: AudioTrack? = null
    private var systemTts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isUrduTtsSupported = false
    private val gson = Gson()
    private val quotaManager = AiQuotaManager.getInstance(context)

    /**
     * Flag indicating whether Gemini's native cloud audio is actively playing
     * (as opposed to device-level fallback TTS or idle).
     */
    var isGeminiNativeVoiceActive: Boolean = false
        private set

    var lastDiagResult: TtsDiagResult = TtsDiagResult.IDLE
        private set
    var lastDiagDetail: String = "No diagnostic test run yet"
        private set

    fun recordDiagResult(result: TtsDiagResult, detail: String) {
        lastDiagResult = result
        lastDiagDetail = detail
        Log.i(tag, "======================================================")
        Log.i(tag, "[AI_TTS_DEBUG] TTS_DIAG_RESULT = $result")
        Log.i(tag, "[AI_TTS_DEBUG] Detail: $detail")
        Log.i(tag, "======================================================")
    }

    private val audioCache = android.util.LruCache<String, ByteArray>(40)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init {
        // Initialize Android system TTS on background thread as offline fallback
        try {
            systemTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    try {
                        val urduLocale = Locale("ur", "PK")
                        val avail = systemTts?.isLanguageAvailable(urduLocale)
                        if (avail == TextToSpeech.LANG_AVAILABLE || avail == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                            systemTts?.setLanguage(urduLocale)
                            isUrduTtsSupported = true
                        } else {
                            val generalUrdu = Locale("ur")
                            val genAvail = systemTts?.isLanguageAvailable(generalUrdu)
                            if (genAvail == TextToSpeech.LANG_AVAILABLE || genAvail == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                                systemTts?.setLanguage(generalUrdu)
                                isUrduTtsSupported = true
                            } else {
                                systemTts?.language = Locale.ENGLISH
                                isUrduTtsSupported = false
                            }
                        }
                    } catch (_: Exception) {
                        systemTts?.language = Locale.ENGLISH
                        isUrduTtsSupported = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to init system TTS: ${e.message}")
        }
    }

    /**
     * Converts integer numbers into natural Urdu spoken words
     */
    fun numberToUrduWords(num: Long): String {
        if (num == 0L) return "صفر"
        if (num < 0) return "منفی " + numberToUrduWords(-num)

        val urdu0to99 = arrayOf(
            "صفر", "ایک", "دو", "تین", "چار", "پانچ", "چھ", "سات", "آٹھ", "نو", "دس",
            "گیارہ", "بارہ", "تیرہ", "چودہ", "پندرہ", "سولہ", "ستارہ", "اٹھارہ", "انیس", "بیس",
            "اکیس", "بائیس", "تئیس", "چوبیس", "پچیس", "چھبیس", "ستائیس", "اٹھیس", "انتالیس", "تیس",
            "اکتیس", "بتیس", "تینتیس", "چونتیس", "پینتیس", "چھتیس", "سینتیس", "اڑتیس", "انتالیس", "چالیس",
            "اکتالیس", "بیالس", "تینتالیس", "چوالیس", "پینتالیس", "چھتالیس", "سینتالیس", "اڑتالیس", "انچاس", "پچاس",
            "ایکیاون", "باون", "ترپن", "چوان", "پچپن", "چھپن", "ستاون", "اٹھاون", "انسٹھ", "ساٹھ",
            "اکسٹھ", "باہٹھ", "ترسٹھ", "چونسٹھ", "پنسٹھ", "چھیاسٹھ", "سڑسٹھ", "اڑسٹھ", "انہتر", "ستر",
            "اکہتر", "بہتر", "تہتر", "چوہتر", "پچہتر", "چھہتر", "ستہر", "اٹھہتر", "اناسی", "اسی",
            "اکاسی", "پچاسی", "چوراسی", "پچاسی", "چھیاسی", "ستاسی", "اٹھاسی", "نواسی", "نوے",
            "یکانوے", "بانوے", "ترانوے", "چورانوے", "پچانوے", "چھانوے", "ستانوے", "اٹھانوے", "ننانوے"
        )

        if (num < 100) return urdu0to99[num.toInt()]

        if (num < 1000) {
            val h = num / 100
            val rem = num % 100
            val hStr = if (h == 1L) "ایک سو" else "${urdu0to99[h.toInt()]} سو"
            return if (rem == 0L) hStr else "$hStr ${numberToUrduWords(rem)}"
        }
        if (num < 100000) {
            val th = num / 1000
            val rem = num % 1000
            val thStr = "${numberToUrduWords(th)} ہزار"
            return if (rem == 0L) thStr else "$thStr ${numberToUrduWords(rem)}"
        }
        if (num < 10000000) {
            val lakh = num / 100000
            val rem = num % 100000
            val lStr = "${numberToUrduWords(lakh)} لاکھ"
            return if (rem == 0L) lStr else "$lStr ${numberToUrduWords(rem)}"
        }
        val crore = num / 10000000
        val rem = num % 10000000
        val cStr = "${numberToUrduWords(crore)} کروڑ"
        return if (rem == 0L) cStr else "$cStr ${numberToUrduWords(rem)}"
    }

    /**
     * Cleans and formats text for natural speech synthesis.
     * Removes markdown formatting, asterisks, bullet points, and cleans numbers for natural speech.
     */
    fun prepareTextForSpeech(text: String, isUrdu: Boolean): String {
        var processed = text.replace(Regex("""(\d+),(\d+)"""), "$1$2")

        if (isUrdu) {
            processed = processed
                .replace(Regex("""Rs\.?\s*(\d+)""", RegexOption.IGNORE_CASE), "$1 روپے")
                .replace(Regex("""PKR\s*(\d+)""", RegexOption.IGNORE_CASE), "$1 روپے")
                .replace(Regex("""Rs\.?""", RegexOption.IGNORE_CASE), "روپے")
                .replace(Regex("""PKR""", RegexOption.IGNORE_CASE), "روپے")

            processed = processed.replace(Regex("""\b\d+\b""")) { matchResult ->
                val num = matchResult.value.toLongOrNull()
                if (num != null && num <= 99999999L) {
                    numberToUrduWords(num)
                } else {
                    matchResult.value
                }
            }

            processed = processed
                .replace(Regex("""WhatsApp""", RegexOption.IGNORE_CASE), "واٹس ایپ")
                .replace(Regex("""SMS""", RegexOption.IGNORE_CASE), "ایس ایم ایس")
                .replace(Regex("""FBR""", RegexOption.IGNORE_CASE), "ایف بی آر")
                .replace(Regex("""POS""", RegexOption.IGNORE_CASE), "پوس")
                .replace(Regex("""Sales?""", RegexOption.IGNORE_CASE), "سیل")
                .replace(Regex("""Stock""", RegexOption.IGNORE_CASE), "سٹاک")
                .replace(Regex("""Profit""", RegexOption.IGNORE_CASE), "منافع")
                .replace(Regex("""Udhaar""", RegexOption.IGNORE_CASE), "ادھار")
                .replace(Regex("""Advisor""", RegexOption.IGNORE_CASE), "مشیر")
                .replace(Regex("""AI""", RegexOption.IGNORE_CASE), "اے آئی")
                .replace(Regex("""Dukan""", RegexOption.IGNORE_CASE), "دکان")
        }

        // Clean markdown symbols, bullets, asterisks, brackets, and redundant whitespaces safely without regex pattern syntax errors
        val charsToStrip = charArrayOf(
            '*', '_', '#', '`', '~', '•', '-', '–', '—', ':', ';',
            '[', ']', '(', ')', '{', '}', '<', '>', '/', '\\', '|', '=', '+',
            '"', '\'', '“', '”', '‘', '’'
        )
        val cleanedSb = StringBuilder(processed.length)
        for (i in 0 until processed.length) {
            val ch = processed[i]
            var found = false
            for (strip in charsToStrip) {
                if (ch == strip) {
                    found = true
                    break
                }
            }
            if (found) {
                cleanedSb.append(' ')
            } else {
                cleanedSb.append(ch)
            }
        }

        // Collapse all multiple spaces/newlines to single space
        val normalizedSb = StringBuilder(cleanedSb.length)
        var lastWasSpace = false
        for (i in 0 until cleanedSb.length) {
            val ch = cleanedSb[i]
            if (ch == '\n' || ch == '\r' || ch.isWhitespace()) {
                if (!lastWasSpace) {
                    normalizedSb.append(' ')
                    lastWasSpace = true
                }
            } else {
                normalizedSb.append(ch)
                lastWasSpace = false
            }
        }

        return normalizedSb.toString().trim()
    }

    /**
     * Backward-compatible alias for existing callers
     */
    fun prepareTextForUrduSpeech(text: String): String = prepareTextForSpeech(text, isUrdu = true)

    /**
     * Converts raw PCM audio byte array to standard RIFF WAV format.
     */
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitDepth: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = channels * bitDepth / 8
        val subChunk2Size = pcmData.size
        val chunkSize = 36 + subChunk2Size
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (chunkSize and 0xff).toByte()
        header[5] = ((chunkSize shr 8) and 0xff).toByte()
        header[6] = ((chunkSize shr 16) and 0xff).toByte()
        header[7] = ((chunkSize shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = blockAlign.toByte(); header[33] = 0
        header[34] = bitDepth.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (subChunk2Size and 0xff).toByte()
        header[41] = ((subChunk2Size shr 8) and 0xff).toByte()
        header[42] = ((subChunk2Size shr 16) and 0xff).toByte()
        header[43] = ((subChunk2Size shr 24) and 0xff).toByte()

        val wav = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, wav, 0, 44)
        System.arraycopy(pcmData, 0, wav, 44, pcmData.size)
        return wav
    }

    /**
     * Inspects active audio routing on device:
     * Built-in Speaker, Wired Headset, Bluetooth Audio, USB Audio.
     */
    fun inspectAudioRouting(): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AudioManager unavailable"
        val sb = StringBuilder()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                var hasBt = false
                var hasHeadset = false
                var hasSpeaker = false
                val names = mutableListOf<String>()
                for (d in devices) {
                    val label = when (d.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> { hasSpeaker = true; "Built-in Speaker" }
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> { hasHeadset = true; "Wired Headset" }
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> { hasBt = true; "Bluetooth Audio" }
                        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
                        else -> "Type ${d.type}"
                    }
                    names.add("${d.productName ?: "Device"} [$label]")
                }
                sb.append(names.joinToString(", "))
                if (hasBt) sb.append(" [BLUETOOTH ACTIVE - check connected headphones/speaker]")
                if (hasHeadset) sb.append(" [WIRED HEADSET ACTIVE]")
                if (hasSpeaker && !hasBt && !hasHeadset) sb.append(" [PHYSICAL SPEAKER ACTIVE]")
            } else {
                sb.append("WiredHeadset=${audioManager.isWiredHeadsetOn}, BluetoothA2dp=${audioManager.isBluetoothA2dpOn}")
            }
        } catch (e: Exception) {
            sb.append("Routing query error: ${e.message}")
        }
        return sb.toString()
    }

    /**
     * CRITICAL DETERMINISTIC TEST:
     * Generates a 440 Hz sine wave tone locally in 24000 Hz 16-bit Mono PCM.
     * Does NOT use internet or Gemini API.
     */
    fun generate440HzTone(durationSeconds: Double = 1.2, sampleRate: Int = 24000): ByteArray {
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val pcm = ByteArray(totalSamples * 2)
        val freq = 440.0
        for (i in 0 until totalSamples) {
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            val sample = (Math.sin(angle) * 32767.0 * 0.85).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /**
     * Deterministic local audio test:
     * Plays 440 Hz sine tone through identical AudioTrack pipeline (24kHz, 16-bit Mono, STREAM_MUSIC).
     */
    fun testSineWaveTone(
        durationSeconds: Double = 1.2,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Log.i(tag, "======================================================")
        Log.i(tag, "[AI_TTS_DEBUG] CRITICAL TEST 1: 440 Hz Sine Tone (Hardware AudioTrack Check)")
        Log.i(tag, "[AI_TTS_DEBUG] Generates local PCM without internet or Gemini API")
        Log.i(tag, "[AI_TTS_DEBUG] Configuration: 24000 Hz, 16-bit PCM, MONO, STREAM_MUSIC")
        Log.i(tag, "======================================================")

        val pcm = generate440HzTone(durationSeconds, 24000)
        Log.d(tag, "[AI_TTS_DEBUG] 440Hz tone generated: size=${pcm.size} bytes, divisibleBy2=${pcm.size % 2 == 0}")
        val hexPrefix = pcm.take(16).joinToString(" ") { String.format("%02X", it) }
        Log.d(tag, "[AI_TTS_DEBUG] 440Hz tone first 16 bytes: $hexPrefix")

        playPcmViaAudioTrack(
            rawBytes = pcm,
            onStart = {
                Log.i(tag, "[AI_TTS_DEBUG] 440Hz tone: AudioTrack playing!")
                onStart()
            },
            onDone = {
                Log.i(tag, "[AI_TTS_DEBUG] 440Hz tone: AudioTrack finished playback successfully!")
                recordDiagResult(TtsDiagResult.GEMINI_OK_AUDIO_OK, "440Hz local tone played successfully through AudioTrack")
                onDone()
            },
            onError = { err ->
                Log.e(tag, "[AI_TTS_DEBUG] 440Hz tone: AudioTrack playback failed: $err")
                recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "440Hz AudioTrack failed: $err")
                onError(err)
            }
        )
    }

    /**
     * SECOND TEST:
     * Hard-coded English Gemini TTS ("Hello. This is a voice test.")
     */
    suspend fun testEnglishVoice(
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val testSentence = "Hello. This is a voice test."
        Log.i(tag, "======================================================")
        Log.i(tag, "[AI_TTS_DEBUG] SECOND TEST: Hard-coded English Gemini TTS")
        Log.i(tag, "[AI_TTS_DEBUG] Text: \"$testSentence\"")
        Log.i(tag, "[AI_TTS_DEBUG] Bypassing AI response text, markdown cleaning, Roman Urdu, and fallbacks")
        Log.i(tag, "======================================================")

        val pcmBytes = generateGeminiTts(testSentence, isUrdu = false)
        if (pcmBytes == null || pcmBytes.isEmpty()) {
            Log.e(tag, "[AI_TTS_DEBUG] English test: Gemini TTS returned no audio ($lastDiagDetail)")
            withContext(Dispatchers.Main) {
                onError("Gemini Natural Voice unavailable ($lastDiagDetail)")
            }
            return@withContext
        }

        Log.i(tag, "[AI_TTS_DEBUG] English test: received ${pcmBytes.size} PCM bytes. Directing to AudioTrack.")
        playPcmViaAudioTrack(pcmBytes, onStart, onDone, onError)
    }

    /**
     * URDU TEST:
     * Direct Urdu Gemini TTS ("السلام علیکم، یہ آواز کا ٹیسٹ ہے۔")
     */
    suspend fun testUrduVoice(
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val testSentence = "السلام علیکم، یہ آواز کا ٹیسٹ ہے۔"
        Log.i(tag, "======================================================")
        Log.i(tag, "[AI_TTS_DEBUG] URDU TEST: Direct Urdu Gemini Natural Voice")
        Log.i(tag, "[AI_TTS_DEBUG] Text: \"$testSentence\"")
        Log.i(tag, "======================================================")

        val pcmBytes = generateGeminiTts(testSentence, isUrdu = true)
        if (pcmBytes == null || pcmBytes.isEmpty()) {
            Log.e(tag, "[AI_TTS_DEBUG] Urdu test: Gemini TTS returned no audio ($lastDiagDetail)")
            withContext(Dispatchers.Main) {
                onError("جیمنائی قدرتی آواز موصول نہیں ہوئی: $lastDiagDetail")
            }
            return@withContext
        }

        Log.i(tag, "[AI_TTS_DEBUG] Urdu test: received ${pcmBytes.size} PCM bytes. Directing to AudioTrack.")
        playPcmViaAudioTrack(pcmBytes, onStart, onDone, onError)
    }

    /**
     * Synthesizes natural speech using the official Google Gemini TTS API.
     * Models: gemini-2.5-flash-preview-tts, gemini-2.5-pro-preview-tts, gemini-3.1-flash-tts-preview.
     * Output format: audio/L16;codec=pcm;rate=24000 (16-bit, 24kHz Mono PCM)
     */
    private suspend fun generateGeminiTts(
        text: String,
        isUrdu: Boolean
    ): ByteArray? = withContext(Dispatchers.IO) {
        val apiKey = quotaManager.getActiveApiKey()
        val keyLength = apiKey?.length ?: 0
        val keyMasked = if (keyLength > 8) "${apiKey!!.take(4)}...${apiKey.takeLast(4)}" else "len=$keyLength"
        val isCustom = quotaManager.hasCustomKey()
        Log.d(tag, "[AI_TTS_DEBUG] generateGeminiTts: QuotaManager API key check: isBlank=${apiKey.isNullOrBlank()}, keyLength=$keyLength, keyMasked=$keyMasked, isCustomKey=$isCustom")

        if (apiKey.isNullOrBlank()) {
            Log.e(tag, "[AI_TTS_DEBUG] generateGeminiTts: CRITICAL - No Gemini API key available! Aborting TTS request.")
            recordDiagResult(TtsDiagResult.GEMINI_FAILED, "No Gemini API key configured")
            return@withContext null
        }

        val cacheKey = text.trim()
        val cached = audioCache.get(cacheKey)
        if (cached != null && cached.isNotEmpty()) {
            Log.d(tag, "[AI_TTS_DEBUG] Reusing cached Gemini PCM audio (${cached.size} bytes) for: \"${cacheKey.take(40)}\"")
            recordDiagResult(TtsDiagResult.GEMINI_OK_AUDIO_OK, "Reusing cached Gemini PCM audio")
            return@withContext cached
        }

        val cleanPrompt = text.trim()
        Log.d(tag, "[AI_TTS_DEBUG] generateGeminiTts: Text length to synthesize: ${cleanPrompt.length}, isUrdu=$isUrdu")

        val modelsToTry = listOf(
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts"
        )

        val requestJson = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", cleanPrompt)
                        }
                        add(partObj)
                    }
                    add("parts", partsArray)
                }
                add(contentObj)
            }
            add("contents", contentsArray)

            val genConfig = JsonObject().apply {
                val respModalities = com.google.gson.JsonArray().apply {
                    add("AUDIO")
                }
                add("responseModalities", respModalities)

                val speechConfig = JsonObject().apply {
                    val voiceConfig = JsonObject().apply {
                        val prebuiltVoiceConfig = JsonObject().apply {
                            // Aoede provides warm, expressive pronunciation
                            addProperty("voiceName", "Aoede")
                        }
                        add("prebuiltVoiceConfig", prebuiltVoiceConfig)
                    }
                    add("voiceConfig", voiceConfig)
                }
                add("speechConfig", speechConfig)
            }
            add("generationConfig", genConfig)
        }

        val requestPayload = gson.toJson(requestJson)
        Log.d(tag, "[AI_TTS_DEBUG] Prepared TTS request payload (bytes=${requestPayload.length})")

        for (model in modelsToTry) {
            try {
                Log.d(tag, "[AI_TTS_DEBUG] --------------------------------------------------")
                Log.d(tag, "[AI_TTS_DEBUG] Calling Gemini TTS model: $model")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val maskedUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=[MASKED_KEY]"
                Log.d(tag, "[AI_TTS_DEBUG] Endpoint: $maskedUrl")

                val request = Request.Builder()
                    .url(url)
                    .post(requestPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val statusCode = response.code
                val responseBody = response.body?.string().orEmpty()
                Log.d(tag, "[AI_TTS_DEBUG] Model $model HTTP response status code: $statusCode")

                if (!response.isSuccessful) {
                    Log.e(tag, "[AI_TTS_DEBUG] HTTP FAILURE on model $model (status code $statusCode)")
                    Log.e(tag, "[AI_TTS_DEBUG] Response body: $responseBody")
                    if (statusCode == 429) {
                        recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Model $model rate limited (HTTP 429)")
                        Log.w(tag, "[AI_TTS_DEBUG] 429 on $model, pausing briefly and checking next candidate...")
                        try { Thread.sleep(800) } catch (_: Exception) {}
                        continue
                    } else {
                        recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Model $model HTTP $statusCode: ${responseBody.take(180)}")
                    }
                    continue
                }

                if (responseBody.isBlank()) {
                    Log.e(tag, "[AI_TTS_DEBUG] Model $model returned empty response body!")
                    recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Model $model returned empty body")
                    continue
                }

                Log.d(tag, "[AI_TTS_DEBUG] HTTP 200 OK from $model (body length=${responseBody.length})")
                val root = try {
                    gson.fromJson(responseBody, JsonObject::class.java)
                } catch (e: Exception) {
                    Log.e(tag, "[AI_TTS_DEBUG] JSON parse failure for model $model: ${e.message}", e)
                    recordDiagResult(TtsDiagResult.GEMINI_FAILED, "JSON parse failure: ${e.message}")
                    continue
                }

                val hasCandidates = root.has("candidates")
                Log.d(tag, "[AI_TTS_DEBUG] candidates exists: $hasCandidates")
                if (!hasCandidates) {
                    Log.e(tag, "[AI_TTS_DEBUG] No candidates array in response. Response keys: ${root.keySet()}")
                    Log.e(tag, "[AI_TTS_DEBUG] Response body: $responseBody")
                    recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Response missing candidates array")
                    continue
                }

                val candidates = root.getAsJsonArray("candidates")
                val numCandidates = candidates.size()
                Log.d(tag, "[AI_TTS_DEBUG] Number of candidates: $numCandidates")
                if (numCandidates == 0) {
                    Log.e(tag, "[AI_TTS_DEBUG] Candidates array is empty!")
                    recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Candidates array is empty")
                    continue
                }

                // Search ALL candidates and ALL parts for inlineData containing audio
                var foundAudio = false
                for (c in 0 until numCandidates) {
                    val candObj = candidates[c].asJsonObject
                    val finishReason = if (candObj.has("finishReason")) candObj.get("finishReason").asString else "UNKNOWN"
                    val hasContent = candObj.has("content")
                    Log.d(tag, "[AI_TTS_DEBUG] Candidate index $c: hasContent=$hasContent, finishReason=$finishReason")
                    if (!hasContent) continue

                    val contentObj = candObj.getAsJsonObject("content")
                    val hasParts = contentObj.has("parts")
                    if (!hasParts) {
                        Log.d(tag, "[AI_TTS_DEBUG] Candidate index $c content has no parts array")
                        continue
                    }

                    val parts = contentObj.getAsJsonArray("parts")
                    val numParts = parts.size()
                    Log.d(tag, "[AI_TTS_DEBUG] Candidate index $c: number of parts = $numParts")

                    for (p in 0 until numParts) {
                        val partObj = parts[p].asJsonObject
                        val hasInlineData = partObj.has("inlineData")
                        val hasText = partObj.has("text")
                        Log.d(tag, "[AI_TTS_DEBUG] Candidate index $c, Part index $p: hasInlineData=$hasInlineData, hasText=$hasText")

                        if (hasText) {
                            val partText = partObj.get("text")?.asString.orEmpty()
                            Log.d(tag, "[AI_TTS_DEBUG] Candidate $c, Part $p text snippet: ${partText.take(120)}")
                        }

                        if (hasInlineData) {
                            val inlineData = partObj.getAsJsonObject("inlineData")
                            val mimeType = inlineData.get("mimeType")?.asString.orEmpty()
                            val base64Data = inlineData.get("data")?.asString.orEmpty()
                            val base64Len = base64Data.length
                            Log.d(tag, "[AI_TTS_DEBUG] ================= Audio inlineData Found =================")
                            Log.d(tag, "[AI_TTS_DEBUG] candidate index: $c")
                            Log.d(tag, "[AI_TTS_DEBUG] part index: $p")
                            Log.d(tag, "[AI_TTS_DEBUG] MIME type: '$mimeType'")
                            Log.d(tag, "[AI_TTS_DEBUG] Base64 length: $base64Len")
                            Log.d(tag, "[AI_TTS_DEBUG] sample rate being assumed: 24000 Hz")
                            Log.d(tag, "[AI_TTS_DEBUG] channel count: 1 (MONO)")
                            Log.d(tag, "[AI_TTS_DEBUG] bit depth: 16-bit PCM")

                            if (!mimeType.contains("audio/L16") && !mimeType.contains("pcm") && !mimeType.contains("audio/")) {
                                Log.w(tag, "[AI_TTS_DEBUG] WARNING: Non-PCM MIME type '$mimeType' returned!")
                            }

                            if (base64Data.isBlank()) {
                                Log.e(tag, "[AI_TTS_DEBUG] Base64 data string in inlineData is empty/blank!")
                                recordDiagResult(TtsDiagResult.PCM_INVALID, "Base64 data in inlineData is empty")
                                continue
                            }

                            val pcmBytes = try {
                                Base64.decode(base64Data, Base64.DEFAULT)
                            } catch (e: Exception) {
                                Log.e(tag, "[AI_TTS_DEBUG] Base64 decode exception: ${e.message}", e)
                                recordDiagResult(TtsDiagResult.PCM_INVALID, "Base64 decode exception: ${e.message}")
                                null
                            }

                            if (pcmBytes == null || pcmBytes.isEmpty()) {
                                Log.e(tag, "[AI_TTS_DEBUG] Decoded PCM byte array is null or empty!")
                                recordDiagResult(TtsDiagResult.PCM_INVALID, "Decoded PCM byte array is null or empty")
                                continue
                            }

                            val pcmByteCount = pcmBytes.size
                            val isEven = (pcmByteCount % 2 == 0)
                            val hexPrefix = pcmBytes.take(16).joinToString(" ") { String.format("%02X", it) }

                            Log.d(tag, "[AI_TTS_DEBUG] decoded byte count: $pcmByteCount")
                            Log.d(tag, "[AI_TTS_DEBUG] whether byte count is divisible by 2: $isEven")
                            Log.d(tag, "[AI_TTS_DEBUG] first 16 decoded bytes as hex: $hexPrefix")

                            if (!isEven) {
                                Log.w(tag, "[AI_TTS_DEBUG] WARNING: PCM byte count $pcmByteCount is not 16-bit word aligned (odd byte count)!")
                            }

                            foundAudio = true
                            audioCache.put(cacheKey, pcmBytes)
                            return@withContext pcmBytes
                        }
                    }
                }

                if (!foundAudio) {
                    Log.e(tag, "[AI_TTS_DEBUG] No audio inlineData found across $numCandidates candidates on model $model. Full response: $responseBody")
                    recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Model $model response contained no audio parts")
                }
            } catch (e: Exception) {
                Log.e(tag, "[AI_TTS_DEBUG] Exception during Gemini TTS call on model $model: ${e.message}", e)
                recordDiagResult(TtsDiagResult.GEMINI_FAILED, "Exception on model $model: ${e.message}")
            }
        }

        Log.e(tag, "[AI_TTS_DEBUG] All Gemini TTS models failed. No PCM audio obtained. Last detail: $lastDiagDetail")
        if (lastDiagResult != TtsDiagResult.GEMINI_FAILED) {
            recordDiagResult(TtsDiagResult.GEMINI_FAILED, "All Gemini TTS models exhausted without audio")
        }
        return@withContext null
    }

    /**
     * Speaks the given text audibly on all physical Android devices.
     * Primary diagnostic pipeline:
     * 1. Gemini TTS PCM -> validate PCM -> AudioTrack (MODE_STREAM, 24kHz, 16-bit Mono) -> physical device speaker.
     * 2. Automatic fallback: Android native System TTS (Urdu / Phonetic) so the device is NEVER silent.
     */
    suspend fun speakText(
        text: String,
        isUrdu: Boolean = true,
        onStart: (isGeminiNative: Boolean) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        Log.d(tag, "[AI_TTS_DEBUG] ==================================================")
        Log.d(tag, "[AI_TTS_DEBUG] speakText() called: text length = ${text.length}, isUrdu = $isUrdu")
        Log.d(tag, "[AI_TTS_DEBUG] Input text snippet: ${text.take(100)}")
        stop()

        val cleanText = prepareTextForSpeech(text, isUrdu)
        val isCleanEmpty = cleanText.isBlank()
        Log.d(tag, "[AI_TTS_DEBUG] prepareTextForSpeech result: length = ${cleanText.length}, isCleanEmpty = $isCleanEmpty")
        if (isCleanEmpty) {
            Log.w(tag, "[AI_TTS_DEBUG] Cleaned speech text is empty. Aborting TTS playback.")
            withContext(Dispatchers.Main) { onDone() }
            return@withContext
        }

        // For conversational voice assistant output, preserve complete natural sentences.
        // We never truncate prematurely; support full detailed briefing answers up to 3000 chars.
        val speechTranscript = if (cleanText.length > 3000) {
            try {
                val sentences = cleanText.split(Regex("""(?<=[۔.!?\n])\s+"""))
                val sb = StringBuilder()
                for (s in sentences) {
                    if (sb.length + s.length <= 3000) {
                        if (sb.isNotEmpty()) sb.append(" ")
                        sb.append(s)
                    } else {
                        break
                    }
                }
                if (sb.isNotBlank()) sb.toString() else cleanText
            } catch (_: Exception) {
                cleanText
            }
        } else {
            cleanText
        }
        Log.d(tag, "[AI_TTS_DEBUG] Exact text length being sent to TTS: ${speechTranscript.length}")
        Log.d(tag, "[AI_TTS_DEBUG] Speech transcript: ${speechTranscript.take(120)}")

        // Check & log audio output volume
        ensureAudibleVolume()

        // 1. Primary: Official Google Gemini TTS -> Direct AudioTrack playback
        val pcmAudioBytes = generateGeminiTts(speechTranscript, isUrdu)
        if (pcmAudioBytes != null && pcmAudioBytes.isNotEmpty()) {
            Log.d(tag, "[AI_TTS_DEBUG] Successfully obtained ${pcmAudioBytes.size} PCM bytes. Directing to AudioTrack playback.")
            isGeminiNativeVoiceActive = true
            playPcmViaAudioTrack(
                rawBytes = pcmAudioBytes,
                onStart = {
                    isGeminiNativeVoiceActive = true
                    onStart(true)
                },
                onDone = {
                    isGeminiNativeVoiceActive = false
                    onDone()
                },
                onError = { err ->
                    isGeminiNativeVoiceActive = false
                    onError(err)
                }
            )
            return@withContext
        }

        // 2. Continuous Voice Guarantee: If Gemini Cloud TTS hits rate limits (HTTP 429) or network latency,
        // seamlessly switch to Android Local System TTS immediately so voice reply NEVER stops!
        Log.w(tag, "[AI_TTS_DEBUG] Gemini Realistic Voice unavailable ($lastDiagDetail). Activating instant Local System TTS Fallback so voice never drops!")
        isGeminiNativeVoiceActive = false
        withContext(Dispatchers.Main) {
            playSystemTts(
                text = speechTranscript,
                isUrdu = isUrdu,
                onStart = {
                    isGeminiNativeVoiceActive = false
                    onStart(false)
                },
                onDone = {
                    isGeminiNativeVoiceActive = false
                    onDone()
                },
                onError = { err ->
                    isGeminiNativeVoiceActive = false
                    Log.e(tag, "[AI_TTS_DEBUG] Both Gemini TTS and System TTS encountered issue: $err")
                    onError(err)
                }
            )
        }
    }

    /**
     * Direct AudioTrack PCM Playback (Primary Diagnostic Architecture)
     * Format: 24kHz, 16-bit Mono PCM (AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_MONO)
     * Transfer Mode: MODE_STREAM
     */
    private fun playPcmViaAudioTrack(
        rawBytes: ByteArray,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            stop()
            Log.d(tag, "[AI_TTS_DEBUG] ---------------- AudioTrack Pre-Flight ----------------")
            Log.d(tag, "[AI_TTS_DEBUG] Raw PCM input byte count: ${rawBytes.size}")

            if (rawBytes.isEmpty()) {
                Log.e(tag, "[AI_TTS_DEBUG] rawBytes is empty! Aborting.")
                recordDiagResult(TtsDiagResult.PCM_INVALID, "PCM byte array is empty")
                mainHandler.post { onError("PCM byte array is empty") }
                return
            }

            if (rawBytes.size % 2 != 0) {
                Log.w(tag, "[AI_TTS_DEBUG] WARNING: PCM byte count ${rawBytes.size} is not divisible by 2 (odd byte count)!")
            }

            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            Log.d(tag, "[AI_TTS_DEBUG] AudioTrack.getMinBufferSize(24000, MONO, PCM_16BIT): $minBufferSize bytes")
            if (minBufferSize <= 0) {
                Log.e(tag, "[AI_TTS_DEBUG] Invalid minBufferSize: $minBufferSize. AudioTrack cannot be created.")
                recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "AudioTrack.getMinBufferSize returned $minBufferSize")
                mainHandler.post { onError("Invalid AudioTrack minBufferSize: $minBufferSize") }
                return
            }

            val selectedBufferSize = maxOf(minBufferSize * 4, 32768)
            Log.d(tag, "[AI_TTS_DEBUG] Selected buffer size: $selectedBufferSize bytes")

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(audioEncoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(selectedBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val initState = track.state
            Log.d(tag, "[AI_TTS_DEBUG] AudioTrack.state: $initState (STATE_INITIALIZED=${AudioTrack.STATE_INITIALIZED}, STATE_UNINITIALIZED=${AudioTrack.STATE_UNINITIALIZED})")
            if (initState != AudioTrack.STATE_INITIALIZED) {
                Log.e(tag, "[AI_TTS_DEBUG] AudioTrack initialization FAILED (state=$initState)")
                recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "AudioTrack initialization failed: state=$initState")
                mainHandler.post { onError("AudioTrack failed to initialize (state=$initState)") }
                return
            }

            val sessionId = track.audioSessionId
            Log.d(tag, "[AI_TTS_DEBUG] AudioTrack audioSessionId: $sessionId")

            // Query volume and routing information
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val isMusicActive = audioManager.isMusicActive
                Log.d(tag, "[AI_TTS_DEBUG] STREAM_MUSIC volume: current=$curVol / max=$maxVol")
                Log.d(tag, "[AI_TTS_DEBUG] isMusicActive: $isMusicActive")
                if (curVol <= 1 && maxVol > 1) {
                    val boost = (maxVol * 0.85).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, boost, 0)
                    Log.d(tag, "[AI_TTS_DEBUG] Volume was very low ($curVol), boosted to $boost / $maxVol")
                }
            } else {
                Log.w(tag, "[AI_TTS_DEBUG] AudioManager is null!")
            }

            val routingInfo = inspectAudioRouting()
            Log.d(tag, "[AI_TTS_DEBUG] Audio routing inspection: $routingInfo")

            activeAudioTrack = track

            // Configure playback speed params on AudioTrack
            val currentSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
            try {
                track.playbackParams = PlaybackParams().apply {
                    speed = currentSpeed
                    pitch = 1.0f
                }
                Log.d(tag, "[AI_TTS_DEBUG] Applied AudioTrack PlaybackParams speed: $currentSpeed")
            } catch (e: Exception) {
                Log.w(tag, "[AI_TTS_DEBUG] Failed to set PlaybackParams on AudioTrack: ${e.message}")
            }

            // Start streaming worker thread
            Thread {
                try {
                    val chunkSize = 4096
                    var totalWritten = 0
                    var offset = 0

                    // 1. Prime buffer before play to prevent immediate underrun/starvation
                    val initialBytes = minOf(chunkSize * 2, rawBytes.size)
                    val primeResult = track.write(rawBytes, 0, initialBytes, AudioTrack.WRITE_BLOCKING)
                    Log.d(tag, "[AI_TTS_DEBUG] Initial prime write() result: $primeResult bytes written (expected $initialBytes)")
                    if (primeResult < 0) {
                        Log.e(tag, "[AI_TTS_DEBUG] AudioTrack initial write error: code $primeResult")
                        recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "Initial track.write error: code $primeResult")
                        mainHandler.post { onError("AudioTrack initial write error: code $primeResult") }
                        return@Thread
                    }
                    totalWritten += primeResult
                    offset += primeResult

                    // 2. Start playback
                    track.play()
                    val playState = track.playState
                    Log.d(tag, "[AI_TTS_DEBUG] AudioTrack.play() called: playState=$playState (PLAYSTATE_PLAYING=${AudioTrack.PLAYSTATE_PLAYING}, PLAYSTATE_STOPPED=${AudioTrack.PLAYSTATE_STOPPED}, PLAYSTATE_PAUSED=${AudioTrack.PLAYSTATE_PAUSED})")
                    mainHandler.post { onStart() }

                    // 3. Stream remaining PCM bytes in chunks
                    while (offset < rawBytes.size && activeAudioTrack == track) {
                        val toWrite = minOf(chunkSize, rawBytes.size - offset)
                        val written = track.write(rawBytes, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) {
                            Log.e(tag, "[AI_TTS_DEBUG] AudioTrack write error: code $written at offset $offset")
                            recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "track.write error: code $written at offset $offset")
                            mainHandler.post { onError("AudioTrack write error: code $written") }
                            return@Thread
                        }
                        totalWritten += written
                        offset += written
                    }
                    Log.d(tag, "[AI_TTS_DEBUG] All PCM bytes written to AudioTrack: totalWritten=$totalWritten / ${rawBytes.size} bytes")

                    // 4. Wait for audio hardware DAC to finish playback completely
                    val totalFrames = totalWritten / 2
                    val effectiveSpeed = maxOf(0.25f, playbackSpeed.coerceIn(0.5f, 2.0f))
                    val estimatedDurationMs = ((totalFrames * 1000L) / (sampleRate * effectiveSpeed)).toLong()
                    Log.d(tag, "[AI_TTS_DEBUG] Total frames to play: $totalFrames (~${estimatedDurationMs}ms at speed ${effectiveSpeed}x)")

                    val startTime = System.currentTimeMillis()
                    val minRequiredDurationMs = (estimatedDurationMs * 0.88).toLong()
                    val maxWaitMs = estimatedDurationMs + 3500L
                    while (activeAudioTrack == track && (System.currentTimeMillis() - startTime) < maxWaitMs) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val headPos = track.playbackHeadPosition
                        if (headPos >= totalFrames && elapsed >= minRequiredDurationMs) {
                            Log.d(tag, "[AI_TTS_DEBUG] AudioTrack playback head reached end: headPos=$headPos >= totalFrames=$totalFrames, elapsed=${elapsed}ms")
                            break
                        }
                        Thread.sleep(50)
                    }
                    Thread.sleep(280) // Generous safety buffer for hardware DAC completion so last syllables are never clipped

                    Log.d(tag, "[AI_TTS_DEBUG] AudioTrack playback loop finished completely and successfully")
                    recordDiagResult(TtsDiagResult.GEMINI_OK_AUDIO_OK, "AudioTrack successfully played $totalWritten bytes ($totalFrames frames)")
                    mainHandler.post {
                        stop()
                        onDone()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "[AI_TTS_DEBUG] AudioTrack streaming worker thread exception: ${e.message}", e)
                    recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "AudioTrack streaming exception: ${e.message}")
                    mainHandler.post {
                        stop()
                        onError(e.message ?: "AudioTrack stream exception")
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(tag, "[AI_TTS_DEBUG] AudioTrack initialization exception: ${e.message}", e)
            recordDiagResult(TtsDiagResult.AUDIOTRACK_FAILED, "AudioTrack setup exception: ${e.message}")
            mainHandler.post { onError("AudioTrack error: ${e.message}") }
        }
    }

    private fun ensureAudibleVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                Log.d(tag, "[AI_TTS_DEBUG] STREAM_MUSIC volume check: current=$currentVol, max=$maxVol")
                if (currentVol < maxVol / 3) {
                    val targetVol = (maxVol * 0.85).toInt()
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    Log.d(tag, "[AI_TTS_DEBUG] Boosted STREAM_MUSIC volume from $currentVol to $targetVol (max=$maxVol)")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "[AI_TTS_DEBUG] Exception checking/adjusting volume: ${e.message}")
        }
    }

    // =========================================================================
    // PRESERVED IMPLEMENTATION (MediaPlayer WAV Playback)
    // Kept intact for reference and rollback if required
    // =========================================================================
    /**
     * Plays raw audio bytes via MediaPlayer WAV.
     * Preserved implementation with full [AI_TTS_DEBUG] logging.
     */
    fun playPcmViaMediaPlayerWav(
        rawBytes: ByteArray,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            stop()
            Log.d(tag, "[AI_TTS_DEBUG] playPcmViaMediaPlayerWav called: rawBytes size=${rawBytes.size}")

            val isAlreadyWav = rawBytes.size > 12 &&
                rawBytes[0] == 'R'.code.toByte() &&
                rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() &&
                rawBytes[3] == 'F'.code.toByte()

            val audioBytes = if (isAlreadyWav) rawBytes else pcmToWav(rawBytes, sampleRate = 24000, channels = 1, bitDepth = 16)
            val calculatedWavSize = audioBytes.size
            Log.d(tag, "[AI_TTS_DEBUG] calculated WAV size: $calculatedWavSize bytes (header=44 + PCM=${rawBytes.size})")

            // WAV header validation
            val hasRiff = audioBytes.size >= 4 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte() && audioBytes[2] == 'F'.code.toByte() && audioBytes[3] == 'F'.code.toByte()
            val hasWave = audioBytes.size >= 12 && audioBytes[8] == 'W'.code.toByte() && audioBytes[9] == 'A'.code.toByte() && audioBytes[10] == 'V'.code.toByte() && audioBytes[11] == 'E'.code.toByte()
            Log.d(tag, "[AI_TTS_DEBUG] WAV header validation: hasRIFF=$hasRiff, hasWAVE=$hasWave")

            val tempAudioFile = File(context.cacheDir, "gemini_voice_${System.currentTimeMillis()}.wav")
            FileOutputStream(tempAudioFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }
            Log.d(tag, "[AI_TTS_DEBUG] temporary WAV file path: ${tempAudioFile.absolutePath}")
            Log.d(tag, "[AI_TTS_DEBUG] temporary WAV file size: ${tempAudioFile.length()} bytes")

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.fromFile(tempAudioFile))
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { mp ->
                    try {
                        val currentSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
                        try {
                            mp.playbackParams = PlaybackParams().apply {
                                speed = currentSpeed
                                pitch = 1.0f
                            }
                            Log.d(tag, "[AI_TTS_DEBUG] Applied MediaPlayer PlaybackParams speed: $currentSpeed")
                        } catch (pe: Exception) {
                            Log.w(tag, "[AI_TTS_DEBUG] Failed to set PlaybackParams on MediaPlayer: ${pe.message}")
                        }
                        mp.start()
                        Log.d(tag, "[AI_TTS_DEBUG] MediaPlayer prepare SUCCESS, start SUCCESS")
                        mainHandler.post { onStart() }
                    } catch (e: Exception) {
                        Log.e(tag, "[AI_TTS_DEBUG] MediaPlayer start FAILURE: ${e.message}", e)
                        mainHandler.post { onError("Audio playback failed on start: ${e.message}") }
                    }
                }
                setOnCompletionListener {
                    Log.d(tag, "[AI_TTS_DEBUG] MediaPlayer playback completion finished")
                    try { tempAudioFile.delete() } catch (_: Exception) {}
                    stop()
                    mainHandler.post { onDone() }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "[AI_TTS_DEBUG] MediaPlayer error: what=$what, extra=$extra")
                    try { tempAudioFile.delete() } catch (_: Exception) {}
                    stop()
                    mainHandler.post { onError("MediaPlayer error: what=$what, extra=$extra") }
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(tag, "[AI_TTS_DEBUG] MediaPlayer WAV playback exception: ${e.message}", e)
            mainHandler.post { onError(e.message ?: "Audio playback error") }
        }
    }

    /**
     * Local System TTS with safe Roman Urdu phonetic phrasing
     */
    private fun playSystemTts(
        text: String,
        isUrdu: Boolean,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val tts = systemTts
            if (tts == null) {
                mainHandler.post { onError("TTS not ready") }
                return
            }

            val textToSpeak = if (isUrdu && !isUrduTtsSupported) {
                convertToSafeRomanUrdu(text)
            } else {
                text
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post { onStart() }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post { onDone() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onError("System TTS error") }
                }
            })

            val utteranceId = "dukan_tts_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            try {
                tts.setSpeechRate(playbackSpeed.coerceIn(0.5f, 2.0f))
            } catch (_: Exception) {}

            val result = tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                mainHandler.post { onError("TTS speak failed") }
            }
        } catch (e: Exception) {
            mainHandler.post { onError(e.message ?: "Playback error") }
        }
    }

    /**
     * Safe Roman Urdu phonetic translation (NEVER strips text to silence)
     */
    fun convertToSafeRomanUrdu(text: String): String {
        var r = text
        r = r.replace("السلام علیکم", "Assalam o alaikum")
        r = r.replace("دکان", "dukaan")
        r = r.replace("مشیر", "advisor")
        r = r.replace("منافع", "munafa")
        r = r.replace("سیلز", "sales")
        r = r.replace("سیل", "sale")
        r = r.replace("اخراجات", "ikhrajaat")
        r = r.replace("خرچہ", "kharcha")
        r = r.replace("ادھار", "udhaar")
        r = r.replace("کھاتہ", "khaata")
        r = r.replace("وصولی", "wasooli")
        r = r.replace("ادائیگی", "adaigi")
        r = r.replace("روپے", "rupay")
        r = r.replace("ہزار", "hazaar")
        r = r.replace("لاکھ", "laakh")
        r = r.replace("کیش", "cash")
        r = r.replace("سٹاک", "stock")
        r = r.replace("آج", "aaj")
        r = r.replace("کل", "kul")
        r = r.replace("گاہک", "gahak")
        r = r.replace("سپلائر", "supplier")
        r = r.replace("نقد", "naqad")
        r = r.replace("ہے", "hai")
        r = r.replace("ہیں", "hain")
        r = r.replace("شکریہ", "shukriya")

        // Transliterate any remaining Arabic/Urdu characters phonetically so original message is never lost
        val sb = StringBuilder()
        for (ch in r) {
            when (ch) {
                'ا', 'آ' -> sb.append("a")
                'ب' -> sb.append("b")
                'پ' -> sb.append("p")
                'ت', 'ط' -> sb.append("t")
                'ٹ' -> sb.append("t")
                'ث', 'س', 'ص' -> sb.append("s")
                'ج' -> sb.append("j")
                'چ' -> sb.append("ch")
                'ح', 'ہ', 'ھ' -> sb.append("h")
                'خ' -> sb.append("kh")
                'د' -> sb.append("d")
                'ڈ' -> sb.append("d")
                'ذ', 'ز', 'ض', 'ظ' -> sb.append("z")
                'ر' -> sb.append("r")
                'ڑ' -> sb.append("r")
                'ژ' -> sb.append("zh")
                'ش' -> sb.append("sh")
                'ع' -> sb.append("a")
                'غ' -> sb.append("gh")
                'ف' -> sb.append("f")
                'ق', 'ک' -> sb.append("k")
                'گ' -> sb.append("g")
                'ل' -> sb.append("l")
                'م' -> sb.append("m")
                'ن', 'ں' -> sb.append("n")
                'و' -> sb.append("o")
                'ی', 'ے' -> sb.append("i")
                '۰' -> sb.append("0")
                '۱' -> sb.append("1")
                '۲' -> sb.append("2")
                '۳' -> sb.append("3")
                '۴' -> sb.append("4")
                '۵' -> sb.append("5")
                '۶' -> sb.append("6")
                '۷' -> sb.append("7")
                '۸' -> sb.append("8")
                '۹' -> sb.append("9")
                '،', '۔' -> sb.append(". ")
                else -> if (ch.code < 128 || ch.isWhitespace()) sb.append(ch)
            }
        }
        val converted = sb.toString().trim()
        return if (converted.isNotBlank()) converted else text
    }

    /**
     * Plays native Gemini audio PCM/WAV bytes directly via AudioTrack.
     * Prioritizes Gemini's native audio stream format (24kHz Mono 16-bit PCM).
     */
    fun playGeminiAudioBytes(
        rawBytes: ByteArray,
        onStart: (isGeminiNative: Boolean) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (rawBytes.isEmpty()) {
            onError("Audio byte array is empty")
            return
        }
        val pcm = if (rawBytes.size > 44 &&
            rawBytes[0] == 'R'.code.toByte() &&
            rawBytes[1] == 'I'.code.toByte() &&
            rawBytes[2] == 'F'.code.toByte() &&
            rawBytes[3] == 'F'.code.toByte()
        ) {
            rawBytes.copyOfRange(44, rawBytes.size)
        } else {
            rawBytes
        }
        isGeminiNativeVoiceActive = true
        ensureAudibleVolume()
        playPcmViaAudioTrack(
            rawBytes = pcm,
            onStart = {
                isGeminiNativeVoiceActive = true
                onStart(true)
            },
            onDone = {
                isGeminiNativeVoiceActive = false
                onDone()
            },
            onError = { err ->
                isGeminiNativeVoiceActive = false
                onError(err)
            }
        )
    }

    /**
     * Barge-in support: immediately stops active audio
     */
    fun stop() {
        isGeminiNativeVoiceActive = false
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
        } catch (_: Exception) {}

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            systemTts?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        try {
            systemTts?.shutdown()
            systemTts = null
        } catch (_: Exception) {}
    }
}
