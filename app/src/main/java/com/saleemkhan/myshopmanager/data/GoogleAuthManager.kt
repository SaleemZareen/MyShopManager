package com.saleemkhan.myshopmanager.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GoogleUserProfile(
    val isLoggedIn: Boolean,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val lastBackupDate: String?,
    val lastLocalBackupDate: String? = null,
    val accountId: String? = null
)

data class AutoSyncSettings(
    val isEnabled: Boolean = false,
    val intervalHours: Int = 24,
    val destination: String = "BOTH", // "DRIVE", "LOCAL", "BOTH"
    val wifiOnly: Boolean = false
)

class GoogleAuthManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "google_auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHOTO_URL = "user_photo_url"
        private const val KEY_ACCOUNT_ID = "user_account_id"
        private const val KEY_LAST_BACKUP = "last_backup_date"
        private const val KEY_LAST_LOCAL_BACKUP = "last_local_backup_date"

        // Auto Sync keys
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_AUTO_SYNC_INTERVAL = "auto_sync_interval_hours"
        private const val KEY_AUTO_SYNC_DESTINATION = "auto_sync_destination"
        private const val KEY_AUTO_SYNC_WIFI_ONLY = "auto_sync_wifi_only"

        @Volatile
        private var instance: GoogleAuthManager? = null

        fun getInstance(context: Context): GoogleAuthManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleAuthManager(context).also { instance = it }
            }
        }
    }

    fun getProfile(): GoogleUserProfile {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        val lastBackup = prefs.getString(KEY_LAST_BACKUP, null)
        val lastLocalBackup = prefs.getString(KEY_LAST_LOCAL_BACKUP, null)
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null)
        return GoogleUserProfile(
            isLoggedIn = isLoggedIn,
            name = name,
            email = email,
            photoUrl = photoUrl,
            lastBackupDate = lastBackup,
            lastLocalBackupDate = lastLocalBackup,
            accountId = accountId
        )
    }

    fun saveLogin(name: String, email: String, photoUrl: String? = null, accountId: String? = null) {
        val stableId = accountId ?: "google_${email.trim().lowercase().hashCode()}"
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_NAME, name.trim())
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PHOTO_URL, photoUrl)
            .putString(KEY_ACCOUNT_ID, stableId)
            .apply()
    }

    fun signOut() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_PHOTO_URL)
            .remove(KEY_ACCOUNT_ID)
            .apply()
    }

    fun recordBackup() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val now = dateFormat.format(Date())
        prefs.edit().putString(KEY_LAST_BACKUP, now).apply()
    }

    fun recordLocalBackup() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val now = dateFormat.format(Date())
        prefs.edit().putString(KEY_LAST_LOCAL_BACKUP, now).apply()
    }

    fun getAutoSyncSettings(): AutoSyncSettings {
        return AutoSyncSettings(
            isEnabled = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false),
            intervalHours = prefs.getInt(KEY_AUTO_SYNC_INTERVAL, 24),
            destination = prefs.getString(KEY_AUTO_SYNC_DESTINATION, "BOTH") ?: "BOTH",
            wifiOnly = prefs.getBoolean(KEY_AUTO_SYNC_WIFI_ONLY, false)
        )
    }

    fun saveAutoSyncSettings(settings: AutoSyncSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_SYNC_ENABLED, settings.isEnabled)
            .putInt(KEY_AUTO_SYNC_INTERVAL, settings.intervalHours)
            .putString(KEY_AUTO_SYNC_DESTINATION, settings.destination)
            .putBoolean(KEY_AUTO_SYNC_WIFI_ONLY, settings.wifiOnly)
            .apply()
    }
}
