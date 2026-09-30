package com.saleemkhan.myshopmanager.data.firebase

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

data class FirebaseAppConfig(
    val projectId: String = "optimistic-envoy-5ds98",
    val appId: String = "1:718510575552:web:0ee256cf002eee907d0225",
    val apiKey: String = "AIzaSyB9BNCbcdsDzcctZxZ4uDNdpSS3FipOKZk",
    val authDomain: String = "optimistic-envoy-5ds98.firebaseapp.com",
    val firestoreDatabaseId: String = "ai-studio-remixmyshopmanag-467f2006-634c-4347-bb49-33d973a470e5",
    val storageBucket: String = "optimistic-envoy-5ds98.firebasestorage.app",
    val messagingSenderId: String = "718510575552",
    val oAuthClientId: String = "718510575552-v236dv2t28ptc91tp81ta87gb72mieu9.apps.googleusercontent.com"
)

object FirebaseConfig {
    @Volatile
    private var cachedConfig: FirebaseAppConfig? = null

    fun get(context: Context): FirebaseAppConfig {
        cachedConfig?.let { return it }

        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("firebase-applet-config.json")
            val jsonElement = JsonParser.parseReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonObject = jsonElement.asJsonObject

            val config = FirebaseAppConfig(
                projectId = jsonObject.get("projectId")?.asString ?: "optimistic-envoy-5ds98",
                appId = jsonObject.get("appId")?.asString ?: "1:718510575552:web:0ee256cf002eee907d0225",
                apiKey = jsonObject.get("apiKey")?.asString ?: "AIzaSyB9BNCbcdsDzcctZxZ4uDNdpSS3FipOKZk",
                authDomain = jsonObject.get("authDomain")?.asString ?: "optimistic-envoy-5ds98.firebaseapp.com",
                firestoreDatabaseId = jsonObject.get("firestoreDatabaseId")?.asString ?: "ai-studio-remixmyshopmanag-467f2006-634c-4347-bb49-33d973a470e5",
                storageBucket = jsonObject.get("storageBucket")?.asString ?: "optimistic-envoy-5ds98.firebasestorage.app",
                messagingSenderId = jsonObject.get("messagingSenderId")?.asString ?: "718510575552",
                oAuthClientId = jsonObject.get("oAuthClientId")?.asString ?: "718510575552-v236dv2t28ptc91tp81ta87gb72mieu9.apps.googleusercontent.com"
            )
            cachedConfig = config
            config
        } catch (_: Exception) {
            val fallback = FirebaseAppConfig()
            cachedConfig = fallback
            fallback
        }
    }
}
