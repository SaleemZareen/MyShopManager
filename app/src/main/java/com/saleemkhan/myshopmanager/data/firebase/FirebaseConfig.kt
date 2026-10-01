package com.saleemkhan.myshopmanager.data.firebase

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

data class FirebaseAppConfig(
    val projectId: String = "myshopmanager",
    val appId: String = "1:129775070914:android:2304979ae4ade113ed955b",
    val apiKey: String = "AIzaSyDek7Kbxpvi40CcyfcfAHkYF1qkCmDeLtY",
    val authDomain: String = "myshopmanager.firebaseapp.com",
    val firestoreDatabaseId: String = "(default)",
    val storageBucket: String = "myshopmanager.firebasestorage.app",
    val messagingSenderId: String = "129775070914",
    val oAuthClientId: String = "129775070914-9qpsad7mmpp84jlbpg5rvblvfrs7jtua.apps.googleusercontent.com"
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
                projectId = jsonObject.get("projectId")?.asString ?: "myshopmanager",
                appId = jsonObject.get("appId")?.asString ?: "1:129775070914:android:2304979ae4ade113ed955b",
                apiKey = jsonObject.get("apiKey")?.asString ?: "AIzaSyDek7Kbxpvi40CcyfcfAHkYF1qkCmDeLtY",
                authDomain = jsonObject.get("authDomain")?.asString ?: "myshopmanager.firebaseapp.com",
                firestoreDatabaseId = jsonObject.get("firestoreDatabaseId")?.asString ?: "(default)",
                storageBucket = jsonObject.get("storageBucket")?.asString ?: "myshopmanager.firebasestorage.app",
                messagingSenderId = jsonObject.get("messagingSenderId")?.asString ?: "129775070914",
                oAuthClientId = jsonObject.get("oAuthClientId")?.asString ?: "129775070914-9qpsad7mmpp84jlbpg5rvblvfrs7jtua.apps.googleusercontent.com"
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
