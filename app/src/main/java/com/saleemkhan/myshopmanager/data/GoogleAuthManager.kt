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
    val lastBackupDate: String?
)

class GoogleAuthManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // SharedPreferences initialized
    }

    companion object {
        private const val PREFS_NAME = "google_auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHOTO_URL = "user_photo_url"
        private const val KEY_LAST_BACKUP = "last_backup_date"

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
        return GoogleUserProfile(
            isLoggedIn = isLoggedIn,
            name = name,
            email = email,
            photoUrl = photoUrl,
            lastBackupDate = lastBackup
        )
    }

    fun saveLogin(name: String, email: String, photoUrl: String? = null) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_NAME, name.trim())
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PHOTO_URL, photoUrl)
            .apply()
    }

    fun signOut() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_PHOTO_URL)
            .apply()
    }

    fun recordBackup() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val now = dateFormat.format(Date())
        prefs.edit().putString(KEY_LAST_BACKUP, now).apply()
    }
}
