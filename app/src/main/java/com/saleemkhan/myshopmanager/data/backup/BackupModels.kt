package com.saleemkhan.myshopmanager.data.backup

import android.net.Uri
import com.saleemkhan.myshopmanager.model.AppState

data class BackupHeader(
    val app: String = "MyShopManager",
    val formatVersion: String = "2.0",
    val version: Int = 2,
    val appVersion: String = "0.1.0",
    val createdAt: String = "",
    val deviceModel: String = "",
    val checksum: String = ""
)

data class BackupMetadata(
    val shopName: String = "",
    val shopNameUrdu: String? = null,
    val createdAt: String = "",
    val customerCount: Int = 0,
    val supplierCount: Int = 0,
    val inventoryCount: Int = 0,
    val transactionCount: Int = 0,
    val bankAccountCount: Int = 0,
    val loanCount: Int = 0,
    val assetCount: Int = 0,
    val totalInventoryValue: Double = 0.0,
    val totalCustomerCredit: Double = 0.0,
    val totalSupplierPayable: Double = 0.0
)

data class BackupPayload(
    val header: BackupHeader = BackupHeader(),
    val metadata: BackupMetadata = BackupMetadata(),
    val data: AppState = AppState()
)

data class BackupFileInfo(
    val fileName: String,
    val filePath: String,
    val uri: Uri? = null,
    val fileSizeBytes: Long = 0L,
    val formattedSize: String = "",
    val lastModifiedEpoch: Long = 0L,
    val formattedDate: String = "",
    val isSafetyBackup: Boolean = false,
    val metadata: BackupMetadata? = null
)

data class DriveBackupInfo(
    val fileId: String,
    val name: String,
    val modifiedTime: String,
    val formattedDate: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val isAppDataFolder: Boolean = true,
    val metadata: BackupMetadata? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val errorReason: String? = null,
    val metadata: BackupMetadata? = null,
    val payloadState: AppState? = null,
    val originalEnvelope: BackupPayload? = null
)

enum class RestoreMode {
    REPLACE_ALL,
    MERGE
}

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val safetyBackupPath: String? = null,
    val itemsRestoredCount: Int = 0
)
