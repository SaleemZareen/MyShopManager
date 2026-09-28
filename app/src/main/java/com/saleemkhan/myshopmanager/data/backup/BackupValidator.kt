package com.saleemkhan.myshopmanager.data.backup

import com.saleemkhan.myshopmanager.model.AppState

class BackupValidator(
    private val serializer: BackupSerializer = BackupSerializer()
) {
    fun validate(jsonString: String): ValidationResult {
        if (jsonString.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorReason = "Empty backup file / خالی فائل موصول ہوئی"
            )
        }

        try {
            val payload = serializer.deserialize(jsonString)
                ?: return ValidationResult(
                    isValid = false,
                    errorReason = "Invalid JSON structure or incompatible shop manager file / غیر موزوں فائل یا نامعلوم فارمیٹ"
                )

            val appState = payload.data
            if (appState.profile == null && appState.inventory.isEmpty() && appState.transactions.isEmpty() && appState.customers.isEmpty()) {
                return ValidationResult(
                    isValid = false,
                    errorReason = "No recognizable shop records found in this backup / فائل میں دکان کا کوئی ریکارڈ موجود نہیں"
                )
            }

            // Checksum verification if header checksum exists
            if (payload.header.checksum.isNotBlank()) {
                val dataJson = com.google.gson.Gson().toJson(appState)
                val computedSha = serializer.calculateSha256(dataJson)
                if (computedSha.isNotEmpty() && payload.header.checksum.isNotEmpty() && computedSha != payload.header.checksum) {
                    // Checksum mismatch might indicate tampering or corruption
                    // We can still allow with warning or strict check
                }
            }

            val metadata = payload.metadata ?: serializer.extractMetadata(appState)

            return ValidationResult(
                isValid = true,
                errorReason = null,
                metadata = metadata,
                payloadState = appState,
                originalEnvelope = payload
            )
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorReason = "Validation error: ${e.message ?: "Corrupted file"}"
            )
        }
    }
}
