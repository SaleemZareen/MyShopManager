package com.saleemkhan.myshopmanager.data.backup

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.saleemkhan.myshopmanager.data.GoogleAuthManager
import com.saleemkhan.myshopmanager.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleDriveAuthException(
    val recoveryIntent: Intent?,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class GoogleDriveBackupProvider(
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(serializer)
) {

    companion object {
        private const val DRIVE_OAUTH_SCOPES =
            "oauth2:https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_FILE_PREFIX = "myshopmanager_backup"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
        private val MEDIA_TYPE_RELATED = "multipart/related".toMediaType()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Throws(GoogleDriveAuthException::class)
    private fun getOAuthToken(context: Context, invalidatePreviousToken: String? = null): String {
        val profile = GoogleAuthManager.getInstance(context).getProfile()
        if (!profile.isLoggedIn || profile.email.isBlank()) {
            throw GoogleDriveAuthException(
                recoveryIntent = null,
                message = "گوگل اکاؤنٹ لاگ ان نہیں ہے / Not signed in with Google"
            )
        }

        val email = profile.email.trim()

        if (!invalidatePreviousToken.isNullOrBlank()) {
            try {
                GoogleAuthUtil.clearToken(context, invalidatePreviousToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val lastSignedIn = try {
            GoogleSignIn.getLastSignedInAccount(context)
        } catch (_: Exception) {
            null
        }

        val googleAccount = if (lastSignedIn?.account != null && lastSignedIn.email.equals(email, ignoreCase = true)) {
            lastSignedIn.account!!
        } else {
            Account(email, "com.google")
        }

        return try {
            GoogleAuthUtil.getToken(context, googleAccount, DRIVE_OAUTH_SCOPES)
        } catch (e: UserRecoverableAuthException) {
            throw GoogleDriveAuthException(
                recoveryIntent = e.intent,
                message = "گوگل ڈرائیو کی اجازت درکار ہے / Google Drive authorization required",
                cause = e
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw GoogleDriveAuthException(
                recoveryIntent = null,
                message = "گوگل ٹوکن حاصل کرنے میں خرابی (${e.javaClass.simpleName}): ${e.message}",
                cause = e
            )
        }
    }

    fun isGoogleLinked(context: Context): Boolean {
        return GoogleAuthManager.getInstance(context).getProfile().isLoggedIn
    }

    suspend fun uploadBackup(context: Context, state: AppState): Result<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            var token = try {
                getOAuthToken(context)
            } catch (e: GoogleDriveAuthException) {
                return@withContext Result.failure(e)
            }

            val jsonContent = serializer.serialize(state, pretty = false)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${BACKUP_FILE_PREFIX}_$timestamp.json"

            // 1. Try uploading to appDataFolder
            var result = performUpload(token, fileName, jsonContent, state, useAppDataFolder = true)

            // If 401 Unauthorized or 403 Insufficient Permissions (stale token), clear token and retry once
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Insufficient Permissions", ignoreCase = true)) {
                    try {
                        token = getOAuthToken(context, invalidatePreviousToken = token)
                        result = performUpload(token, fileName, jsonContent, state, useAppDataFolder = true)
                    } catch (e: GoogleDriveAuthException) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            // Fallback: If appDataFolder fails, retry uploading directly to Drive space
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (!errMsg.contains("authorization required", ignoreCase = true)) {
                    val fallbackRes = performUpload(token, fileName, jsonContent, state, useAppDataFolder = false)
                    if (fallbackRes.isSuccess) {
                        return@withContext fallbackRes
                    }
                }
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun performUpload(
        token: String,
        fileName: String,
        jsonContent: String,
        state: AppState,
        useAppDataFolder: Boolean
    ): Result<DriveBackupInfo> {
        return try {
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                if (useAppDataFolder) {
                    put("parents", JSONArray().put("appDataFolder"))
                }
                put("description", "MyShopManager cloud backup for ${state.profile.shopName}")
            }.toString()

            val requestBody = MultipartBody.Builder()
                .setType(MEDIA_TYPE_RELATED)
                .addPart(metadataJson.toRequestBody(MEDIA_TYPE_JSON))
                .addPart(jsonContent.toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cache-Control", "no-cache, no-store")
                .addHeader("Pragma", "no-cache")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val respObj = JSONObject(responseBody)
                    val fileId = respObj.optString("id", "")
                    val name = respObj.optString("name", fileName)
                    val modifiedTime = respObj.optString(
                        "modifiedTime",
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    )

                    val sizeBytes = jsonContent.toByteArray(Charsets.UTF_8).size.toLong()
                    val info = DriveBackupInfo(
                        fileId = fileId,
                        name = name,
                        modifiedTime = modifiedTime,
                        formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
                        sizeBytes = sizeBytes,
                        formattedSize = formatFileSize(sizeBytes),
                        isAppDataFolder = useAppDataFolder,
                        metadata = serializer.extractMetadata(state)
                    )

                    Result.success(info)
                } else {
                    val code = response.code
                    val parsedError = parseDriveError(code, responseBody)
                    Result.failure(Exception("Drive upload failed $parsedError"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBackups(context: Context): Result<List<DriveBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            var token = try {
                getOAuthToken(context)
            } catch (e: GoogleDriveAuthException) {
                return@withContext Result.failure(e)
            }

            var result = performList(token)
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Insufficient Permissions", ignoreCase = true)) {
                    try {
                        token = getOAuthToken(context, invalidatePreviousToken = token)
                        result = performList(token)
                    } catch (e: GoogleDriveAuthException) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun performList(token: String): Result<List<DriveBackupInfo>> {
        return try {
            val query = "name contains '$BACKUP_FILE_PREFIX' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder,drive&q=$encodedQuery&fields=files(id,name,size,modifiedTime,description)&orderBy=modifiedTime+desc"

            val request = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cache-Control", "no-cache, no-store")
                .addHeader("Pragma", "no-cache")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseText)
                    val files = json.optJSONArray("files") ?: JSONArray()
                    val list = mutableListOf<DriveBackupInfo>()

                    for (i in 0 until files.length()) {
                        val item = files.getJSONObject(i)
                        val id = item.optString("id")
                        val name = item.optString("name")
                        val size = item.optLong("size", 0L)
                        val modified = item.optString("modifiedTime")

                        val formattedDate = try {
                            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(modified.take(19))
                            if (parsed != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(parsed) else modified
                        } catch (_: Exception) {
                            modified
                        }

                        list.add(
                            DriveBackupInfo(
                                fileId = id,
                                name = name,
                                modifiedTime = modified,
                                formattedDate = formattedDate,
                                sizeBytes = size,
                                formattedSize = formatFileSize(size),
                                isAppDataFolder = true,
                                metadata = null
                            )
                        )
                    }

                    Result.success(list)
                } else {
                    val code = response.code
                    val parsedError = parseDriveError(code, responseText)
                    Result.failure(Exception("Failed to list Drive backups $parsedError"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(context: Context, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            var token = try {
                getOAuthToken(context)
            } catch (e: GoogleDriveAuthException) {
                return@withContext Result.failure(e)
            }

            var result = performDownload(token, fileId)
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Insufficient Permissions", ignoreCase = true)) {
                    try {
                        token = getOAuthToken(context, invalidatePreviousToken = token)
                        result = performDownload(token, fileId)
                    } catch (e: GoogleDriveAuthException) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun performDownload(token: String, fileId: String): Result<String> {
        return try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cache-Control", "no-cache, no-store")
                .addHeader("Pragma", "no-cache")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val content = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(content)
                } else {
                    val code = response.code
                    val parsedError = parseDriveError(code, content)
                    Result.failure(Exception("Failed to download file $parsedError"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(context: Context, fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var token = try {
                getOAuthToken(context)
            } catch (e: GoogleDriveAuthException) {
                return@withContext Result.failure(e)
            }

            var result = performDelete(token, fileId)
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Insufficient Permissions", ignoreCase = true)) {
                    try {
                        token = getOAuthToken(context, invalidatePreviousToken = token)
                        result = performDelete(token, fileId)
                    } catch (e: GoogleDriveAuthException) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun performDelete(token: String, fileId: String): Result<Boolean> {
        return try {
            val deleteUrl = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cache-Control", "no-cache, no-store")
                .addHeader("Pragma", "no-cache")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 204 || response.isSuccessful) {
                    Result.success(true)
                } else {
                    val err = response.body?.string() ?: ""
                    val parsedError = parseDriveError(code, err)
                    Result.failure(Exception("Failed to delete cloud backup $parsedError"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDriveError(responseCode: Int, responseBody: String): String {
        if (responseBody.isBlank()) return "HTTP $responseCode"
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: ""
            val errors = errorObj?.optJSONArray("errors")
            val reason = if (errors != null && errors.length() > 0) {
                errors.getJSONObject(0).optString("reason")
            } else ""

            when {
                reason == "insufficientPermissions" || message.contains("insufficient", ignoreCase = true) ->
                    "گوگل ڈرائیو کی مکمل اجازت درکار ہے (403: Insufficient Permissions)"
                reason == "accessNotConfigured" || message.contains("disabled", ignoreCase = true) ->
                    "گوگل کلاؤڈ کنسول میں Google Drive API آن نہیں ہے (403: API Disabled)"
                reason == "quotaExceeded" || message.contains("quota", ignoreCase = true) ->
                    "گوگل ڈرائیو اسٹوریج مکمل ہو چکی ہے (403: Quota Exceeded)"
                message.isNotBlank() -> "($responseCode): $message"
                else -> "HTTP $responseCode: ${responseBody.take(150)}"
            }
        } catch (_: Exception) {
            "HTTP $responseCode: ${responseBody.take(150)}"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
