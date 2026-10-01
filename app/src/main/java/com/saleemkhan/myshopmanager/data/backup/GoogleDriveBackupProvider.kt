package com.saleemkhan.myshopmanager.data.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.saleemkhan.myshopmanager.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleDriveBackupProvider(
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(serializer)
) {

    companion object {
        private const val DRIVE_OAUTH_SCOPES =
            "oauth2:https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_FILE_PREFIX = "myshopmanager_backup"
    }

    private fun getOAuthToken(context: Context): String? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: try {
                val email = com.saleemkhan.myshopmanager.data.GoogleAuthManager.getInstance(context).getProfile().email
                if (email.isNotBlank()) Account(email, "com.google") else null
            } catch (_: Exception) {
                null
            }
            ?: return null

        return try {
            GoogleAuthUtil.getToken(context, googleAccount, DRIVE_OAUTH_SCOPES)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isGoogleLinked(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null ||
                com.saleemkhan.myshopmanager.data.GoogleAuthManager.getInstance(context).getProfile().isLoggedIn
    }

    suspend fun uploadBackup(context: Context, state: AppState): Result<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val token = getOAuthToken(context)
                ?: return@withContext Result.failure(Exception("گوگل اکاؤنٹ لاگ ان نہیں ہے یا ٹوکن حاصل نہیں ہو سکا / Not signed in with Google"))

            val jsonContent = serializer.serialize(state, pretty = false)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${BACKUP_FILE_PREFIX}_$timestamp.json"

            val boundary = "==MyShopDriveBoundary" + System.currentTimeMillis() + "=="
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            val conn = URL(uploadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put("appDataFolder"))
                put("description", "MyShopManager cloud backup for ${state.profile.shopName}")
            }.toString()

            conn.outputStream.use { os ->
                val writer = PrintWriter(OutputStreamWriter(os, "UTF-8"), true)
                writer.append("--").append(boundary).append("\r\n")
                writer.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                writer.append(metadataJson).append("\r\n")
                writer.append("--").append(boundary).append("\r\n")
                writer.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                writer.append(jsonContent).append("\r\n")
                writer.append("--").append(boundary).append("--\r\n")
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val respObj = JSONObject(responseBody)
                val fileId = respObj.optString("id", "")
                val name = respObj.optString("name", fileName)
                val modifiedTime = respObj.optString("modifiedTime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

                val sizeBytes = jsonContent.toByteArray(Charsets.UTF_8).size.toLong()
                val info = DriveBackupInfo(
                    fileId = fileId,
                    name = name,
                    modifiedTime = modifiedTime,
                    formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
                    sizeBytes = sizeBytes,
                    formattedSize = formatFileSize(sizeBytes),
                    isAppDataFolder = true,
                    metadata = serializer.extractMetadata(state)
                )

                Result.success(info)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                conn.disconnect()
                Result.failure(Exception("Drive upload failed ($responseCode): $errorBody"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun listBackups(context: Context): Result<List<DriveBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val token = getOAuthToken(context)
                ?: return@withContext Result.failure(Exception("Google Sign-In required for cloud backups / گوگل اکاؤنٹ لاگ ان ضروری ہے"))

            val query = "name contains '$BACKUP_FILE_PREFIX' and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name,size,modifiedTime,description)&orderBy=modifiedTime+desc"

            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(responseText)
                val files = json.optJSONArray("files") ?: org.json.JSONArray()
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
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                conn.disconnect()
                Result.failure(Exception("Failed to list Drive backups ($responseCode): $err"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(context: Context, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getOAuthToken(context)
                ?: return@withContext Result.failure(Exception("Google Sign-In required"))

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val content = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                Result.success(content)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                conn.disconnect()
                Result.failure(Exception("Failed to download file ($responseCode): $err"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(context: Context, fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = getOAuthToken(context)
                ?: return@withContext Result.failure(Exception("Google Sign-In required"))

            val deleteUrl = "https://www.googleapis.com/drive/v3/files/$fileId"
            val conn = URL(deleteUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connect()

            val code = conn.responseCode
            conn.disconnect()
            if (code == 204 || code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete cloud backup ($code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
