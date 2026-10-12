package com.saleemkhan.myshopmanager.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.saleemkhan.myshopmanager.data.db.MyShopDatabase
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppStateRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("myshopmanager_state_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val database = MyShopDatabase.getInstance(appContext)

    private val _appState = MutableStateFlow(loadState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _dismissedNotifications = MutableStateFlow(loadDismissedNotifications())
    val dismissedNotifications: StateFlow<Set<String>> = _dismissedNotifications.asStateFlow()

    companion object {
        private const val STORAGE_KEY = "my_shop_manager_app_state_v2"
        private const val DISMISSED_NOTIFS_KEY = "my_shop_dismissed_notifs"
        private const val PERMISSIONS_SETUP_DONE_KEY = "permissions_setup_done"
        private const val DB_MIGRATED_KEY = "sqlite_db_migrated_v1"

        @Volatile
        private var INSTANCE: AppStateRepository? = null

        fun getInstance(context: Context): AppStateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppStateRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadDismissedNotifications(): Set<String> {
        return try {
            prefs.getStringSet(DISMISSED_NOTIFS_KEY, emptySet()) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun dismissNotification(id: String) {
        val current = _dismissedNotifications.value.toMutableSet()
        current.add(id)
        prefs.edit().putStringSet(DISMISSED_NOTIFS_KEY, current).apply()
        _dismissedNotifications.value = current
    }

    fun dismissAllNotifications(ids: Collection<String>) {
        val current = _dismissedNotifications.value.toMutableSet()
        current.addAll(ids)
        prefs.edit().putStringSet(DISMISSED_NOTIFS_KEY, current).apply()
        _dismissedNotifications.value = current
    }

    fun clearDismissedNotifications() {
        prefs.edit().remove(DISMISSED_NOTIFS_KEY).apply()
        _dismissedNotifications.value = emptySet()
    }

    fun isPermissionsSetupDone(): Boolean {
        return prefs.getBoolean(PERMISSIONS_SETUP_DONE_KEY, false)
    }

    fun setPermissionsSetupDone(done: Boolean) {
        prefs.edit().putBoolean(PERMISSIONS_SETUP_DONE_KEY, done).apply()
    }

    private fun loadState(): AppState {
        val isMigrated = prefs.getBoolean(DB_MIGRATED_KEY, false)
        if (isMigrated) {
            return database.loadAppState()
        }

        // Load legacy JSON state & perform safe atomic migration to SQLite DB
        val jsonState = try {
            val json = prefs.getString(STORAGE_KEY, null)
            if (!json.isNullOrBlank()) {
                val parsed = gson.fromJson(json, AppState::class.java)
                parsed ?: FormatUtils.DEFAULT_APP_STATE
            } else {
                FormatUtils.DEFAULT_APP_STATE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FormatUtils.DEFAULT_APP_STATE
        }

        // Save into SQLite database atomically
        val success = database.saveAppStateAtomic(jsonState)
        if (success) {
            prefs.edit().putBoolean(DB_MIGRATED_KEY, true).apply()
        }
        return jsonState
    }

    fun saveState(state: AppState) {
        _appState.value = state
        // 1. Transactional write to SQLite database
        database.saveAppStateAtomic(state)
        prefs.edit().putBoolean(DB_MIGRATED_KEY, true).apply()

        // 2. Secondary backup write to SharedPreferences JSON
        try {
            val json = gson.toJson(state)
            prefs.edit().putString(STORAGE_KEY, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateState(transform: (AppState) -> AppState) {
        val current = _appState.value
        val updated = transform(current)
        saveState(updated)
    }

    fun resetState() {
        saveState(FormatUtils.DEFAULT_APP_STATE)
    }

    fun importJson(json: String): Boolean {
        return try {
            val parsed = gson.fromJson(json, AppState::class.java)
            if (parsed != null) {
                saveState(parsed)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
