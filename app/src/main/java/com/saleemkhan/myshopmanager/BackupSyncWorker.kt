package com.saleemkhan.myshopmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saleemkhan.myshopmanager.data.AppStateRepository
import com.saleemkhan.myshopmanager.data.GoogleAuthManager
import com.saleemkhan.myshopmanager.data.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val authManager = GoogleAuthManager.getInstance(context)
            val autoSyncSettings = authManager.getAutoSyncSettings()

            if (!autoSyncSettings.isEnabled) {
                return@withContext Result.success()
            }

            val appState = AppStateRepository.getInstance(context).appState.value
            val backupManager = BackupManager.getInstance()

            val destination = autoSyncSettings.destination

            // 1. Local backup
            if (destination.equals("LOCAL", ignoreCase = true) || destination.equals("BOTH", ignoreCase = true)) {
                try {
                    val localResult = backupManager.createQuickBackup(context, appState, isSafety = false)
                    if (localResult.isSuccess) {
                        authManager.recordLocalBackup()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Google Drive backup
            if (destination.equals("DRIVE", ignoreCase = true) || destination.equals("BOTH", ignoreCase = true)) {
                if (authManager.getProfile().isLoggedIn) {
                    try {
                        backupManager.backupToGoogleDrive(context, appState)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
