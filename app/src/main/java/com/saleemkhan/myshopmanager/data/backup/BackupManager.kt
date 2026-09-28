package com.saleemkhan.myshopmanager.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.saleemkhan.myshopmanager.BackupSyncWorker
import com.saleemkhan.myshopmanager.data.GoogleAuthManager
import com.saleemkhan.myshopmanager.model.AppState
import java.util.concurrent.TimeUnit

class BackupManager private constructor(
    val localProvider: LocalBackupProvider = LocalBackupProvider(),
    val driveProvider: GoogleDriveBackupProvider = GoogleDriveBackupProvider(),
    val serializer: BackupSerializer = BackupSerializer(),
    val validator: BackupValidator = BackupValidator(serializer),
    val restoreManager: RestoreManager = RestoreManager(localProvider)
) {

    companion object {
        private const val AUTO_SYNC_WORK_TAG = "myshopmanager_auto_sync_work"

        @Volatile
        private var INSTANCE: BackupManager? = null

        fun getInstance(): BackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupManager().also { INSTANCE = it }
            }
        }
    }

    suspend fun exportToStorage(context: Context, targetUri: Uri, state: AppState): Result<String> {
        val res = localProvider.exportToUri(context, targetUri, state)
        if (res.isSuccess) {
            GoogleAuthManager.getInstance(context).recordLocalBackup()
        }
        return res
    }

    suspend fun createQuickBackup(context: Context, state: AppState, isSafety: Boolean = false) =
        localProvider.createQuickDeviceBackup(context, state, isSafety)

    suspend fun listLocalBackups(context: Context) = localProvider.listLocalBackups(context)

    suspend fun deleteLocalBackup(filePath: String) = localProvider.deleteLocalBackup(filePath)

    fun validateJson(json: String): ValidationResult = validator.validate(json)

    suspend fun readUriContent(context: Context, uri: Uri) = localProvider.readFromUri(context, uri)

    suspend fun backupToGoogleDrive(context: Context, state: AppState): Result<DriveBackupInfo> {
        val result = driveProvider.uploadBackup(context, state)
        if (result.isSuccess) {
            GoogleAuthManager.getInstance(context).recordBackup()
        }
        return result
    }

    suspend fun listGoogleDriveBackups(context: Context) = driveProvider.listBackups(context)

    suspend fun downloadDriveBackup(context: Context, fileId: String) = driveProvider.downloadBackup(context, fileId)

    suspend fun deleteDriveBackup(context: Context, fileId: String) = driveProvider.deleteBackup(context, fileId)

    suspend fun restoreState(
        context: Context,
        backupState: AppState,
        mode: RestoreMode = RestoreMode.REPLACE_ALL,
        createSafetyBackup: Boolean = true
    ): RestoreResult = restoreManager.restore(context, backupState, mode, createSafetyBackup)

    fun scheduleAutoSync(
        context: Context,
        intervalHours: Int,
        destination: String = "BOTH",
        wifiOnly: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            workManager.cancelAllWorkByTag(AUTO_SYNC_WORK_TAG)
            return
        }

        val constraints = Constraints.Builder().apply {
            if (wifiOnly) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            setRequiresBatteryNotLow(true)
        }.build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<BackupSyncWorker>(
            intervalHours.toLong().coerceAtLeast(1L),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(AUTO_SYNC_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AUTO_SYNC_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWorkRequest
        )
    }

    fun cancelAutoSync(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(AUTO_SYNC_WORK_TAG)
    }
}
