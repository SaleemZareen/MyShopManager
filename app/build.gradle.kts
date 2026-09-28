import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun resolveGeminiApiKey(): String {
    val env = System.getenv("GEMINI_API_KEY")
    if (!env.isNullOrBlank()) return env

    val prop = project.findProperty("GEMINI_API_KEY")?.toString()
    if (!prop.isNullOrBlank()) return prop

    val rootEnv = rootProject.file(".env")
    if (rootEnv.exists()) {
        try {
            val p = Properties()
            FileInputStream(rootEnv).use { p.load(it) }
            val k = p.getProperty("GEMINI_API_KEY")?.toString()
            if (!k.isNullOrBlank()) return k.trim()
        } catch (_: Exception) {}
    }

    val appEnv = file(".env")
    if (appEnv.exists()) {
        try {
            val p = Properties()
            FileInputStream(appEnv).use { p.load(it) }
            val k = p.getProperty("GEMINI_API_KEY")?.toString()
            if (!k.isNullOrBlank()) return k.trim()
        } catch (_: Exception) {}
    }

    return ""
}

android {
    namespace = "com.saleemkhan.myshopmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saleemkhan.myshopmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${resolveGeminiApiKey()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
