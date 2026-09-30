package com.saleemkhan.myshopmanager.data.firebase

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saleemkhan.myshopmanager.data.backup.BackupMetadata
import com.saleemkhan.myshopmanager.data.backup.BackupSerializer
import com.saleemkhan.myshopmanager.data.backup.BackupValidator
import com.saleemkhan.myshopmanager.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class CloudBackupInfo(
    val id: String = "latest",
    val shopName: String = "",
    val updatedAtIso: String = "",
    val formattedDate: String = "",
    val sizeBytes: Long = 0L,
    val formattedSize: String = "",
    val checksum: String = "",
    val customerCount: Int = 0,
    val inventoryCount: Int = 0,
    val transactionCount: Int = 0,
    val totalReceivables: Double = 0.0,
    val totalPayables: Double = 0.0,
    val isAnonymousUser: Boolean = false,
    val userEmail: String? = null
)

class FirebaseCloudBackupProvider(
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(serializer)
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getFirestoreDocumentUrl(context: Context, uid: String): String {
        val config = FirebaseConfig.get(context)
        return "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/users/$uid/backups/latest"
    }

    suspend fun uploadBackup(context: Context, state: AppState): Result<CloudBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val authManager = FirebaseAuthManager.getInstance(context)
            val session = authManager.userSession.value
            val idToken = authManager.getValidIdToken()
                ?: return@withContext Result.failure(Exception("Cloud authentication token not available / کلاؤڈ کنکشن دستیاب نہیں"))

            val uid = session.uid
            if (uid.isBlank()) {
                return@withContext Result.failure(Exception("User identity not initialized"))
            }

            val jsonContent = serializer.serialize(state, pretty = false)
            val checksum = serializer.calculateSha256(jsonContent)
            val meta = serializer.extractMetadata(state)
            val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val fieldsObj = JsonObject().apply {
                add("shopName", JsonObject().apply { addProperty("stringValue", meta.shopName) })
                add("checksum", JsonObject().apply { addProperty("stringValue", checksum) })
                add("updatedAt", JsonObject().apply { addProperty("stringValue", isoNow) })
                add("version", JsonObject().apply { addProperty("integerValue", "2") })
                add("customerCount", JsonObject().apply { addProperty("integerValue", meta.customerCount.toString()) })
                add("supplierCount", JsonObject().apply { addProperty("integerValue", meta.supplierCount.toString()) })
                add("inventoryCount", JsonObject().apply { addProperty("integerValue", meta.inventoryCount.toString()) })
                add("transactionCount", JsonObject().apply { addProperty("integerValue", meta.transactionCount.toString()) })
                add("totalCustomerCredit", JsonObject().apply { addProperty("doubleValue", meta.totalCustomerCredit) })
                add("totalSupplierPayable", JsonObject().apply { addProperty("doubleValue", meta.totalSupplierPayable) })
                add("payloadJson", JsonObject().apply { addProperty("stringValue", jsonContent) })
                add("isAnonymous", JsonObject().apply { addProperty("booleanValue", session.isAnonymous) })
                if (!session.email.isNullOrBlank()) {
                    add("userEmail", JsonObject().apply { addProperty("stringValue", session.email) })
                }
            }

            val docRoot = JsonObject().apply {
                add("fields", fieldsObj)
            }

            val url = getFirestoreDocumentUrl(context, uid)
            val request = Request.Builder()
                .url(url)
                .patch(docRoot.toString().toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer $idToken")
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val sizeBytes = jsonContent.toByteArray(Charsets.UTF_8).size.toLong()
                val info = CloudBackupInfo(
                    id = "latest",
                    shopName = meta.shopName,
                    updatedAtIso = isoNow,
                    formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
                    sizeBytes = sizeBytes,
                    formattedSize = formatFileSize(sizeBytes),
                    checksum = checksum,
                    customerCount = meta.customerCount,
                    inventoryCount = meta.inventoryCount,
                    transactionCount = meta.transactionCount,
                    totalReceivables = meta.totalCustomerCredit,
                    totalPayables = meta.totalSupplierPayable,
                    isAnonymousUser = session.isAnonymous,
                    userEmail = session.email
                )
                Result.success(info)
            } else {
                Result.failure(Exception("Cloud upload failed (${response.code}): $respStr"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getCloudBackupInfo(context: Context): Result<CloudBackupInfo?> = withContext(Dispatchers.IO) {
        try {
            val authManager = FirebaseAuthManager.getInstance(context)
            val session = authManager.userSession.value
            val idToken = authManager.getValidIdToken()
                ?: return@withContext Result.failure(Exception("Not authenticated / لاگ ان یا ٹوکن دستیاب نہیں"))

            val uid = session.uid
            val url = getFirestoreDocumentUrl(context, uid)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $idToken")
                .build()

            val response = client.newCall(request).execute()
            if (response.code == 404) {
                return@withContext Result.success(null)
            }

            val respStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val doc = JsonParser.parseString(respStr).asJsonObject
                val fields = doc.getAsJsonObject("fields") ?: return@withContext Result.success(null)

                val shopName = fields.getAsJsonObject("shopName")?.get("stringValue")?.asString ?: "My Shop"
                val updatedAt = fields.getAsJsonObject("updatedAt")?.get("stringValue")?.asString ?: ""
                val checksum = fields.getAsJsonObject("checksum")?.get("stringValue")?.asString ?: ""
                val custCount = fields.getAsJsonObject("customerCount")?.get("integerValue")?.asString?.toIntOrNull() ?: 0
                val invCount = fields.getAsJsonObject("inventoryCount")?.get("integerValue")?.asString?.toIntOrNull() ?: 0
                val txCount = fields.getAsJsonObject("transactionCount")?.get("integerValue")?.asString?.toIntOrNull() ?: 0
                val custCredit = fields.getAsJsonObject("totalCustomerCredit")?.get("doubleValue")?.asDouble ?: 0.0
                val suppPayable = fields.getAsJsonObject("totalSupplierPayable")?.get("doubleValue")?.asDouble ?: 0.0
                val payloadJson = fields.getAsJsonObject("payloadJson")?.get("stringValue")?.asString ?: ""
                val isAnon = fields.getAsJsonObject("isAnonymous")?.get("booleanValue")?.asBoolean ?: session.isAnonymous
                val email = fields.getAsJsonObject("userEmail")?.get("stringValue")?.asString ?: session.email

                val formattedDate = try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(updatedAt)
                    if (parsed != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(parsed) else updatedAt
                } catch (_: Exception) {
                    updatedAt
                }

                val sizeBytes = payloadJson.toByteArray(Charsets.UTF_8).size.toLong()
                val info = CloudBackupInfo(
                    id = "latest",
                    shopName = shopName,
                    updatedAtIso = updatedAt,
                    formattedDate = formattedDate,
                    sizeBytes = sizeBytes,
                    formattedSize = formatFileSize(sizeBytes),
                    checksum = checksum,
                    customerCount = custCount,
                    inventoryCount = invCount,
                    transactionCount = txCount,
                    totalReceivables = custCredit,
                    totalPayables = suppPayable,
                    isAnonymousUser = isAnon,
                    userEmail = email
                )
                Result.success(info)
            } else {
                Result.failure(Exception("Failed to check cloud backup (${response.code})"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val authManager = FirebaseAuthManager.getInstance(context)
            val session = authManager.userSession.value
            val idToken = authManager.getValidIdToken()
                ?: return@withContext Result.failure(Exception("Authentication token not available"))

            val uid = session.uid
            val url = getFirestoreDocumentUrl(context, uid)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $idToken")
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val doc = JsonParser.parseString(respStr).asJsonObject
                val fields = doc.getAsJsonObject("fields")
                    ?: return@withContext Result.failure(Exception("Invalid cloud backup document"))

                val payloadJson = fields.getAsJsonObject("payloadJson")?.get("stringValue")?.asString
                if (payloadJson.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Cloud backup contains empty payload"))
                }

                Result.success(payloadJson)
            } else {
                Result.failure(Exception("Failed to download cloud backup (${response.code}): $respStr"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteCloudBackup(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val authManager = FirebaseAuthManager.getInstance(context)
            val session = authManager.userSession.value
            val idToken = authManager.getValidIdToken()
                ?: return@withContext Result.failure(Exception("Authentication token not available"))

            val uid = session.uid
            val url = getFirestoreDocumentUrl(context, uid)
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $idToken")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 404) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete cloud backup (${response.code})"))
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
