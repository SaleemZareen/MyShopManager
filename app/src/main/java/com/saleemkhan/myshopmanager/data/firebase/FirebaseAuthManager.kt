package com.saleemkhan.myshopmanager.data.firebase

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FirebaseUserSession(
    val uid: String = "",
    val isAnonymous: Boolean = true,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiresAtEpochMs: Long = 0L,
    val isLinkedWithGoogle: Boolean = false
) {
    val isLoggedInGoogle: Boolean
        get() = !isAnonymous && !email.isNullOrBlank()
}

class FirebaseAuthManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _userSession = MutableStateFlow(loadSession())
    val userSession: StateFlow<FirebaseUserSession> = _userSession.asStateFlow()

    companion object {
        private const val PREFS_NAME = "myshopmanager_firebase_auth"
        private const val KEY_UID = "auth_uid"
        private const val KEY_IS_ANONYMOUS = "auth_is_anonymous"
        private const val KEY_DISPLAY_NAME = "auth_display_name"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_PHOTO_URL = "auth_photo_url"
        private const val KEY_ID_TOKEN = "auth_id_token"
        private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        private const val KEY_EXPIRES_AT = "auth_expires_at"

        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        // Ensure an anonymous session exists immediately on start
        if (_userSession.value.uid.isBlank()) {
            ensureAnonymousSession()
        }
    }

    private fun loadSession(): FirebaseUserSession {
        var uid = prefs.getString(KEY_UID, "") ?: ""
        val isAnon = prefs.getBoolean(KEY_IS_ANONYMOUS, true)
        val name = prefs.getString(KEY_DISPLAY_NAME, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val photo = prefs.getString(KEY_PHOTO_URL, null)
        val idToken = prefs.getString(KEY_ID_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (uid.isBlank()) {
            uid = "anon_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_UID, uid).putBoolean(KEY_IS_ANONYMOUS, true).apply()
        }

        return FirebaseUserSession(
            uid = uid,
            isAnonymous = isAnon,
            displayName = name,
            email = email,
            photoUrl = photo,
            idToken = idToken,
            refreshToken = refreshToken,
            tokenExpiresAtEpochMs = expiresAt,
            isLinkedWithGoogle = !isAnon && !email.isNullOrBlank()
        )
    }

    private fun saveSession(session: FirebaseUserSession) {
        prefs.edit()
            .putString(KEY_UID, session.uid)
            .putBoolean(KEY_IS_ANONYMOUS, session.isAnonymous)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_PHOTO_URL, session.photoUrl)
            .putString(KEY_ID_TOKEN, session.idToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.tokenExpiresAtEpochMs)
            .apply()

        _userSession.value = session
    }

    fun ensureAnonymousSession() {
        scope.launch {
            val current = _userSession.value
            if (current.idToken != null && System.currentTimeMillis() < current.tokenExpiresAtEpochMs - 60_000L) {
                return@launch
            }

            try {
                val config = FirebaseConfig.get(context)
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${config.apiKey}"
                val bodyJson = JsonObject().apply {
                    addProperty("returnSecureToken", true)
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val json = JsonParser.parseString(respStr).asJsonObject
                    val localId = json.get("localId")?.asString ?: current.uid
                    val idToken = json.get("idToken")?.asString
                    val refreshToken = json.get("refreshToken")?.asString
                    val expiresIn = json.get("expiresIn")?.asString?.toLongOrNull() ?: 3600L

                    val newSession = current.copy(
                        uid = localId,
                        isAnonymous = true,
                        idToken = idToken,
                        refreshToken = refreshToken,
                        tokenExpiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                    saveSession(newSession)
                }
            } catch (e: Exception) {
                // Offline fallback: Persistent local anonymous session is retained
                e.printStackTrace()
            }
        }
    }

    suspend fun getValidIdToken(): String? = withContext(Dispatchers.IO) {
        val current = _userSession.value
        val now = System.currentTimeMillis()
        if (current.idToken != null && now < current.tokenExpiresAtEpochMs - 60_000L) {
            return@withContext current.idToken
        }

        // Try refresh token if available
        if (!current.refreshToken.isNullOrBlank()) {
            try {
                val config = FirebaseConfig.get(context)
                val url = "https://securetoken.googleapis.com/v1/token?key=${config.apiKey}"
                val bodyJson = "grant_type=refresh_token&refresh_token=${current.refreshToken}"
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JsonParser.parseString(response.body?.string() ?: "").asJsonObject
                    val newIdToken = json.get("id_token")?.asString
                    val newRefreshToken = json.get("refresh_token")?.asString ?: current.refreshToken
                    val expiresIn = json.get("expires_in")?.asString?.toLongOrNull() ?: 3600L

                    val updated = current.copy(
                        idToken = newIdToken,
                        refreshToken = newRefreshToken,
                        tokenExpiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                    saveSession(updated)
                    return@withContext newIdToken
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Otherwise renew anonymous session
        if (current.isAnonymous) {
            try {
                val config = FirebaseConfig.get(context)
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${config.apiKey}"
                val request = Request.Builder()
                    .url(url)
                    .post("{\"returnSecureToken\":true}".toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JsonParser.parseString(response.body?.string() ?: "").asJsonObject
                    val localId = json.get("localId")?.asString ?: current.uid
                    val idToken = json.get("idToken")?.asString
                    val refreshToken = json.get("refreshToken")?.asString
                    val expiresIn = json.get("expiresIn")?.asString?.toLongOrNull() ?: 3600L

                    val newSession = current.copy(
                        uid = localId,
                        isAnonymous = true,
                        idToken = idToken,
                        refreshToken = refreshToken,
                        tokenExpiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                    saveSession(newSession)
                    return@withContext idToken
                }
            } catch (_: Exception) {}
        }

        return@withContext current.idToken
    }

    suspend fun linkOrSignInGoogle(
        googleIdToken: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ): Result<FirebaseUserSession> = withContext(Dispatchers.IO) {
        try {
            val config = FirebaseConfig.get(context)
            val current = _userSession.value
            val currentIdToken = getValidIdToken()

            // 1. If currently anonymous with an active idToken, attempt to LINK to preserve the UID
            if (current.isAnonymous && !currentIdToken.isNullOrBlank()) {
                val linkUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${config.apiKey}"
                val postBody = "id_token=$googleIdToken&providerId=google.com"

                val bodyObj = JsonObject().apply {
                    addProperty("idToken", currentIdToken)
                    addProperty("postBody", postBody)
                    addProperty("requestUri", "http://localhost")
                    addProperty("returnIdpCredential", true)
                    addProperty("returnSecureToken", true)
                }

                val request = Request.Builder()
                    .url(linkUrl)
                    .post(bodyObj.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val respStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JsonParser.parseString(respStr).asJsonObject
                    val localId = json.get("localId")?.asString ?: current.uid
                    val newIdToken = json.get("idToken")?.asString ?: currentIdToken
                    val refreshToken = json.get("refreshToken")?.asString ?: current.refreshToken
                    val emailReturned = json.get("email")?.asString ?: email
                    val nameReturned = json.get("displayName")?.asString ?: displayName
                    val photoReturned = json.get("photoUrl")?.asString ?: photoUrl
                    val expiresIn = json.get("expiresIn")?.asString?.toLongOrNull() ?: 3600L

                    val linkedSession = FirebaseUserSession(
                        uid = localId,
                        isAnonymous = false,
                        displayName = nameReturned,
                        email = emailReturned,
                        photoUrl = photoReturned,
                        idToken = newIdToken,
                        refreshToken = refreshToken,
                        tokenExpiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L),
                        isLinkedWithGoogle = true
                    )
                    saveSession(linkedSession)
                    return@withContext Result.success(linkedSession)
                } else {
                    // Check if error is because this Google account is already linked to another user
                    if (respStr.contains("FEDERATED_USER_ID_ALREADY_LINKED", ignoreCase = true) ||
                        respStr.contains("EMAIL_EXISTS", ignoreCase = true)) {
                        // Sign in directly to that existing account to avoid data loss
                        return@withContext signInDirectGoogle(googleIdToken, displayName, email, photoUrl)
                    }
                }
            }

            // 2. Direct sign-in with Google ID token
            return@withContext signInDirectGoogle(googleIdToken, displayName, email, photoUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    private suspend fun signInDirectGoogle(
        googleIdToken: String,
        fallbackName: String?,
        fallbackEmail: String?,
        fallbackPhoto: String?
    ): Result<FirebaseUserSession> = withContext(Dispatchers.IO) {
        try {
            val config = FirebaseConfig.get(context)
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${config.apiKey}"
            val postBody = "id_token=$googleIdToken&providerId=google.com"

            val bodyObj = JsonObject().apply {
                addProperty("postBody", postBody)
                addProperty("requestUri", "http://localhost")
                addProperty("returnIdpCredential", true)
                addProperty("returnSecureToken", true)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JsonParser.parseString(respStr).asJsonObject
                val localId = json.get("localId")?.asString ?: "google_${(fallbackEmail ?: "").hashCode()}"
                val idToken = json.get("idToken")?.asString
                val refreshToken = json.get("refreshToken")?.asString
                val email = json.get("email")?.asString ?: fallbackEmail
                val name = json.get("displayName")?.asString ?: fallbackName
                val photo = json.get("photoUrl")?.asString ?: fallbackPhoto
                val expiresIn = json.get("expiresIn")?.asString?.toLongOrNull() ?: 3600L

                val session = FirebaseUserSession(
                    uid = localId,
                    isAnonymous = false,
                    displayName = name,
                    email = email,
                    photoUrl = photo,
                    idToken = idToken,
                    refreshToken = refreshToken,
                    tokenExpiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L),
                    isLinkedWithGoogle = true
                )
                saveSession(session)
                Result.success(session)
            } else {
                Result.failure(Exception("Google Sign-In failed: $respStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        val newAnonUid = "anon_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val freshAnon = FirebaseUserSession(
            uid = newAnonUid,
            isAnonymous = true,
            displayName = null,
            email = null,
            photoUrl = null,
            idToken = null,
            refreshToken = null,
            tokenExpiresAtEpochMs = 0L,
            isLinkedWithGoogle = false
        )
        saveSession(freshAnon)
        ensureAnonymousSession()
    }
}
