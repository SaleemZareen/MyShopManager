package com.saleemkhan.myshopmanager.data.backup

import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saleemkhan.myshopmanager.model.AppState
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BackupSerializer {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private val compactGson: Gson = GsonBuilder()
        .create()

    fun extractMetadata(state: AppState): BackupMetadata {
        val totalInvVal = state.inventory.sumOf { it.quantity * it.purchasePrice }
        val totalCustCredit = state.customers.sumOf { it.totalCredit }
        val totalSuppPayable = state.suppliers.sumOf { it.totalPayable }

        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        return BackupMetadata(
            shopName = state.profile.shopName.ifBlank { "My Shop" },
            shopNameUrdu = state.profile.shopNameUrdu,
            createdAt = isoDate,
            customerCount = state.customers.size,
            supplierCount = state.suppliers.size,
            inventoryCount = state.inventory.size,
            transactionCount = state.transactions.size,
            bankAccountCount = state.bankAccounts.size,
            loanCount = state.loans.size,
            assetCount = state.businessAssets.size + state.personalAssets.size,
            totalInventoryValue = totalInvVal,
            totalCustomerCredit = totalCustCredit,
            totalSupplierPayable = totalSuppPayable
        )
    }

    fun calculateSha256(data: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun serialize(state: AppState, pretty: Boolean = true): String {
        val metadata = extractMetadata(state)
        val dataJson = compactGson.toJson(state)
        val checksum = calculateSha256(dataJson)

        val header = BackupHeader(
            app = "MyShopManager",
            formatVersion = "2.0",
            version = 2,
            appVersion = "0.1.0",
            createdAt = metadata.createdAt,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            checksum = checksum
        )

        val payload = BackupPayload(
            header = header,
            metadata = metadata,
            data = state
        )

        return if (pretty) gson.toJson(payload) else compactGson.toJson(payload)
    }

    fun deserialize(jsonString: String): BackupPayload? {
        if (jsonString.isBlank()) return null

        return try {
            val jsonElement = JsonParser.parseString(jsonString)
            if (!jsonElement.isJsonObject) return null

            val rootObj = jsonElement.asJsonObject

            // Check if it's the structured envelope (has "header" and "data")
            if (rootObj.has("header") && rootObj.has("data")) {
                val payload = gson.fromJson(jsonString, BackupPayload::class.java)
                if (payload != null && payload.data != null) {
                    return payload
                }
            }

            // Legacy format check: Root contains "profile", "transactions", etc.
            if (rootObj.has("profile") || rootObj.has("inventory") || rootObj.has("transactions")) {
                val legacyState = gson.fromJson(jsonString, AppState::class.java)
                if (legacyState != null) {
                    val metadata = extractMetadata(legacyState)
                    val header = BackupHeader(
                        app = "MyShopManager",
                        formatVersion = "1.0",
                        version = 1,
                        appVersion = "0.1.0",
                        createdAt = metadata.createdAt,
                        deviceModel = "Legacy Backup",
                        checksum = ""
                    )
                    return BackupPayload(header = header, metadata = metadata, data = legacyState)
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
