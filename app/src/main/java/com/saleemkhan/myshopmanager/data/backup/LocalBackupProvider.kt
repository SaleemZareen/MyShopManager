package com.saleemkhan.myshopmanager.data.backup

import android.content.Context
import android.net.Uri
import com.saleemkhan.myshopmanager.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalBackupProvider(
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(serializer)
) {

    fun getDefaultExportFileName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        return "MyShopManager_Backup_$timestamp.json"
    }

    private fun getBackupsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun exportToUri(context: Context, destinationUri: Uri, state: AppState): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = serializer.serialize(state, pretty = true)
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { os: OutputStream ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.failure(Exception("Cannot open destination output stream"))

            Result.success("Export successful")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun readFromUri(context: Context, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(sourceUri)?.use { isStream: InputStream ->
                isStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Cannot open file input stream"))

            Result.success(content)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun createQuickDeviceBackup(
        context: Context,
        state: AppState,
        isSafetyBackup: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = getBackupsDirectory(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (isSafetyBackup) "safety_backup_" else "dukan_backup_"
            val file = File(dir, "$prefix$timestamp.json")

            val json = serializer.serialize(state, pretty = true)
            file.writeText(json, Charsets.UTF_8)

            // Keep only latest 10 safety backups to save device space
            if (isSafetyBackup) {
                pruneOldBackups(dir, prefix, maxToKeep = 10)
            }

            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun listLocalBackups(context: Context): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        try {
            val dir = getBackupsDirectory(context)
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
                ?: return@withContext emptyList()

            val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

            files.sortedByDescending { it.lastModified() }.map { file ->
                val sizeBytes = file.length()
                val formattedSize = formatFileSize(sizeBytes)
                val isSafety = file.name.startsWith("safety_", ignoreCase = true)

                // Read small snippet to extract metadata if possible
                var metadata: BackupMetadata? = null
                try {
                    val content = file.readText(Charsets.UTF_8)
                    val validation = validator.validate(content)
                    if (validation.isValid) {
                        metadata = validation.metadata
                    }
                } catch (_: Exception) {}

                BackupFileInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    uri = null,
                    fileSizeBytes = sizeBytes,
                    formattedSize = formattedSize,
                    lastModifiedEpoch = file.lastModified(),
                    formattedDate = dateFormatter.format(Date(file.lastModified())),
                    isSafetyBackup = isSafety,
                    metadata = metadata
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteLocalBackup(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun pruneOldBackups(dir: File, prefix: String, maxToKeep: Int) {
        val matches = dir.listFiles { f -> f.name.startsWith(prefix) }?.sortedByDescending { it.lastModified() }
        if (matches != null && matches.size > maxToKeep) {
            for (i in maxToKeep until matches.size) {
                try {
                    matches[i].delete()
                } catch (_: Exception) {}
            }
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
