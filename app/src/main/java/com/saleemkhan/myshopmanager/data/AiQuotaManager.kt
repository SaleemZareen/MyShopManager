package com.saleemkhan.myshopmanager.data

import android.content.Context
import android.content.SharedPreferences
import com.saleemkhan.myshopmanager.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Google Gemini Free Tier Quota & Rate Limits according to official Google AI Studio policy:
 * - Cost: $0 (No billing / credit card required)
 * - Rate Limits: 15 Requests Per Minute (RPM) & 1,500 Requests Per Day (RPD)
 * - Quota auto-renews daily at 00:00 UTC.
 * - Supports Bring-Your-Own-Key (BYOK) from aistudio.google.com for unlimited free daily usage.
 * - Zero-latency Phase A Local Intelligence operates offline with 100% dynamic calculations.
 */
class AiQuotaManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "dukan_ai_quota_prefs"
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val KEY_DAILY_CALLS_COUNT = "daily_calls_count"
        private const val KEY_LAST_USAGE_DATE = "last_usage_date"

        // Official Google AI Studio Free Tier standard limit for Flash models
        const val DAILY_FREE_TIER_LIMIT = 1500
        const val RPM_LIMIT = 15

        @Volatile
        private var instance: AiQuotaManager? = null

        fun getInstance(context: Context): AiQuotaManager {
            return instance ?: synchronized(this) {
                instance ?: AiQuotaManager(context).also { instance = it }
            }
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun ensureDailyReset() {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_USAGE_DATE, "")
        if (today != lastDate) {
            prefs.edit()
                .putString(KEY_LAST_USAGE_DATE, today)
                .putInt(KEY_DAILY_CALLS_COUNT, 0)
                .apply()
        }
    }

    /**
     * Checks if user entered their own personal Google Gemini API key from AI Studio.
     */
    fun hasCustomKey(): Boolean {
        return getCustomApiKey().isNotBlank()
    }

    fun getCustomApiKey(): String {
        return prefs.getString(KEY_CUSTOM_API_KEY, "")?.trim().orEmpty()
    }

    fun saveCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun clearCustomApiKey() {
        prefs.edit().remove(KEY_CUSTOM_API_KEY).apply()
    }

    /**
     * Calls used today (resets every 24 hours).
     */
    fun getTodayCallsCount(): Int {
        ensureDailyReset()
        return prefs.getInt(KEY_DAILY_CALLS_COUNT, 0)
    }

    /**
     * Calls remaining today from the free 1,500 daily quota.
     */
    fun getDailyRemaining(): Int {
        ensureDailyReset()
        val used = getTodayCallsCount()
        return maxOf(0, DAILY_FREE_TIER_LIMIT - used)
    }

    /**
     * Backward-compatible alias for UI components
     */
    fun getFreeTierRemaining(): Int = getDailyRemaining()

    fun isCloudAiAvailable(): Boolean {
        return hasCustomKey() || getDailyRemaining() > 0
    }

    /**
     * Resolves the active API key to use for Gemini calls.
     */
    fun getActiveApiKey(): String? {
        val custom = getCustomApiKey()
        if (custom.isNotBlank()) {
            val masked = if (custom.length > 8) "${custom.take(4)}...${custom.takeLast(4)}" else "len=${custom.length}"
            android.util.Log.d("AiQuotaManager", "[AI_TTS_DEBUG] AiQuotaManager: using custom API key ($masked, length=${custom.length})")
            return custom
        }

        // Check if BuildConfig has a valid Gemini key
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank()) {
            val masked = if (buildKey.length > 8) "${buildKey.take(4)}...${buildKey.takeLast(4)}" else "len=${buildKey.length}"
            android.util.Log.d("AiQuotaManager", "[AI_TTS_DEBUG] AiQuotaManager: using BuildConfig.GEMINI_API_KEY ($masked, length=${buildKey.length})")
            return buildKey
        }

        android.util.Log.w("AiQuotaManager", "[AI_TTS_DEBUG] AiQuotaManager: No API key available (both custom and BuildConfig are empty)")
        return null
    }

    /**
     * Increments today's call counter after a successful request.
     */
    fun recordSuccessfulCall() {
        ensureDailyReset()
        val current = prefs.getInt(KEY_DAILY_CALLS_COUNT, 0)
        prefs.edit().putInt(KEY_DAILY_CALLS_COUNT, current + 1).apply()
    }

    fun resetTodayQuota() {
        prefs.edit().putInt(KEY_DAILY_CALLS_COUNT, 0).apply()
    }
}
