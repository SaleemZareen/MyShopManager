package com.saleemkhan.myshopmanager.data.firebase

import android.content.Context
import com.google.gson.JsonParser
import java.io.InputStreamReader

data class FirebaseAppConfig(
    val projectId: String = "",
    val appId: String = "",
    val apiKey: String = "",
    val authDomain: String = "",
    val firestoreDatabaseId: String = "(default)",
    val storageBucket: String = "",
    val messagingSenderId: String = "",
    val oAuthClientId: String = ""
) {
    val isValid: Boolean get() = apiKey.isNotBlank() && projectId.isNotBlank()
}

object FirebaseConfig {
    @Volatile
    private var cachedConfig: FirebaseAppConfig? = null

    fun resetCache() {
        cachedConfig = null
    }

    fun get(context: Context): FirebaseAppConfig {
        cachedConfig?.let { return it }

        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("firebase-applet-config.json")
            val jsonElement = JsonParser.parseReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonObject = jsonElement.asJsonObject

            val config = FirebaseAppConfig(
                projectId = jsonObject.get("projectId")?.asString ?: "",
                appId = jsonObject.get("appId")?.asString ?: "",
                apiKey = jsonObject.get("apiKey")?.asString ?: "",
                authDomain = jsonObject.get("authDomain")?.asString ?: "",
                firestoreDatabaseId = jsonObject.get("firestoreDatabaseId")?.asString ?: "(default)",
                storageBucket = jsonObject.get("storageBucket")?.asString ?: "",
                messagingSenderId = jsonObject.get("messagingSenderId")?.asString ?: "",
                oAuthClientId = jsonObject.get("oAuthClientId")?.asString ?: ""
            )
            cachedConfig = config
            config
        } catch (_: Exception) {
            val emptyConfig = FirebaseAppConfig()
            cachedConfig = emptyConfig
            emptyConfig
        }
    }
}
