package com.saleemkhan.myshopmanager.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.saleemkhan.myshopmanager.data.AiQuotaManager
import com.saleemkhan.myshopmanager.data.AppStateRepository
import com.saleemkhan.myshopmanager.data.AutoSyncSettings
import com.saleemkhan.myshopmanager.data.GoogleAuthManager
import com.saleemkhan.myshopmanager.data.backup.*
import com.saleemkhan.myshopmanager.data.firebase.*
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncScreen(
    state: AppState,
    isUrdu: Boolean,
    onBack: (() -> Unit)? = null,
    onRestoreState: ((AppState) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { GoogleAuthManager.getInstance(context) }
    val firebaseAuth = remember { FirebaseAuthManager.getInstance(context) }
    val firebaseSession by firebaseAuth.userSession.collectAsState()
    val quotaManager = remember { AiQuotaManager.getInstance(context) }
    val backupManager = remember { BackupManager.getInstance() }
    val repository = remember { AppStateRepository.getInstance(context) }

    var profile by remember { mutableStateOf(authManager.getProfile()) }
    var autoSyncSettings by remember { mutableStateOf(authManager.getAutoSyncSettings()) }
    var isSigningIn by remember { mutableStateOf(false) }

    // Backup & Restore states
    var isExporting by remember { mutableStateOf(false) }
    var isBackingUpDrive by remember { mutableStateOf(false) }
    var isBackingUpFirebase by remember { mutableStateOf(false) }
    var isLoadingDriveList by remember { mutableStateOf(false) }
    var isLoadingFirebaseCloudInfo by remember { mutableStateOf(false) }
    var firebaseCloudBackupInfo by remember { mutableStateOf<CloudBackupInfo?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    var localBackupsList by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    var driveBackupsList by remember { mutableStateOf<List<DriveBackupInfo>>(emptyList()) }
    var showLocalBackupsList by remember { mutableStateOf(false) }
    var showDriveBackupsList by remember { mutableStateOf(false) }

    // Restore dialog state
    var pendingRestoreState by remember { mutableStateOf<AppState?>(null) }
    var pendingValidation by remember { mutableStateOf<ValidationResult?>(null) }
    var pendingRestoreSourceTitle by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf(RestoreMode.REPLACE_ALL) }
    var createSafetyBackupBeforeRestore by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Firebase & Auth Config
    val fbConfig = remember { FirebaseConfig.get(context) }

    fun refreshLists() {
        coroutineScope.launch {
            localBackupsList = backupManager.listLocalBackups(context)
            isLoadingFirebaseCloudInfo = true
            val fbInfoRes = backupManager.getFirebaseCloudBackupInfo(context)
            if (fbInfoRes.isSuccess) {
                firebaseCloudBackupInfo = fbInfoRes.getOrNull()
            }
            isLoadingFirebaseCloudInfo = false

            if (profile.isLoggedIn) {
                isLoadingDriveList = true
                val driveRes = backupManager.listGoogleDriveBackups(context)
                if (driveRes.isSuccess) {
                    driveBackupsList = driveRes.getOrDefault(emptyList())
                }
                isLoadingDriveList = false
            }
        }
    }

    LaunchedEffect(Unit) {
        firebaseAuth.ensureAnonymousSession()
        refreshLists()
    }

    val launchNativeGoogleAccountPicker = {
        isSigningIn = true
        coroutineScope.launch {
            try {
                // Safely unwrap Activity context from LocalContext
                var act: Activity? = null
                var ctx: Context = context
                while (ctx is ContextWrapper) {
                    if (ctx is Activity) {
                        act = ctx
                        break
                    }
                    ctx = ctx.baseContext
                }
                val targetActivity = act ?: (context as? Activity)

                if (targetActivity == null) {
                    isSigningIn = false
                    Toast.makeText(context, "Activity context is required for Google Sign-In", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val credentialManager = CredentialManager.create(targetActivity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(fbConfig.oAuthClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = targetActivity
                )
                isSigningIn = false
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val email = googleIdTokenCredential.id
                    val name = googleIdTokenCredential.displayName?.ifBlank { null } ?: email.substringBefore("@")
                    val photo = googleIdTokenCredential.profilePictureUri?.toString()
                    val accountId = "google_${email.hashCode()}"

                    if (idToken.isNotBlank()) {
                        val linkRes = firebaseAuth.linkOrSignInGoogle(idToken, name, email, photo)
                        if (linkRes.isSuccess) {
                            authManager.saveLogin(name, email, photo, accountId)
                            profile = authManager.getProfile()
                            Toast.makeText(
                                context,
                                if (isUrdu) "گوگل اکاؤنٹ ($email) فائر بیس سے کامیابی سے منسلک ہو گیا!" else "Google Account ($email) linked with Firebase successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val errMsg = linkRes.exceptionOrNull()?.message ?: "Firebase authentication failed"
                            Toast.makeText(
                                context,
                                if (isUrdu) "فائر بیس لنکنگ خرابی: $errMsg" else "Firebase Linking Error: $errMsg",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        refreshLists()
                    } else {
                        Toast.makeText(
                            context,
                            if (isUrdu) "گوگل سائن ان: ID ٹوکن موصول نہیں ہوا" else "Google Sign-In: ID token was empty",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        if (isUrdu) "غیر متوقع اسناد موصول ہوئیں: ${credential.type}" else "Unexpected credential type: ${credential.type}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: GetCredentialCancellationException) {
                isSigningIn = false
                Toast.makeText(context, if (isUrdu) "سائن ان منسوخ کر دیا گیا" else "Sign-in cancelled", Toast.LENGTH_SHORT).show()
            } catch (e: GoogleIdTokenParsingException) {
                isSigningIn = false
                Toast.makeText(context, "Google ID token parsing error: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: GetCredentialException) {
                isSigningIn = false
                val causeStr = e.cause?.message?.let { " | Cause: $it" } ?: ""
                val className = e::class.java.simpleName
                Toast.makeText(
                    context,
                    if (isUrdu) "گوگل سائن ان خرابی ($className / ${e.type}): ${e.message}$causeStr" else "Google Sign-In Error ($className / ${e.type}): ${e.message}$causeStr",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                isSigningIn = false
                val causeStr = e.cause?.message?.let { " | Cause: $it" } ?: ""
                Toast.makeText(
                    context,
                    if (isUrdu) "گوگل سائن ان میں مسئلہ: ${e.message}$causeStr" else "Sign-In Error: ${e.message}$causeStr",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // SAF Create Document Launcher (Export to storage)
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isExporting = true
                val result = backupManager.exportToStorage(context, uri, state)
                isExporting = false
                if (result.isSuccess) {
                    profile = authManager.getProfile()
                    Toast.makeText(
                        context,
                        if (isUrdu) "بیک اپ فائل کامیابی سے منتخب کردہ فولڈر میں محفوظ ہو گئی!" else "Backup saved to chosen folder successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        if (isUrdu) "فائل سیو کرنے میں خرابی: ${result.exceptionOrNull()?.message}" else "Export failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // SAF Open Document Launcher (Restore from user-selected file)
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isRestoring = true
                val readResult = backupManager.readUriContent(context, uri)
                isRestoring = false
                if (readResult.isSuccess) {
                    val json = readResult.getOrNull() ?: ""
                    val validation = backupManager.validateJson(json)
                    if (validation.isValid && validation.payloadState != null) {
                        pendingRestoreState = validation.payloadState
                        pendingValidation = validation
                        pendingRestoreSourceTitle = if (isUrdu) "منتخب کردہ لوکل فائل" else "Selected Local File"
                        showRestoreDialog = true
                    } else {
                        validationErrorMessage = validation.errorReason ?: "Invalid file structure"
                    }
                } else {
                    Toast.makeText(
                        context,
                        if (isUrdu) "فائل پڑھنے میں ناکامی ہوئی" else "Failed to read backup file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Gemini AI key states
    var customKeyInput by remember { mutableStateOf(quotaManager.getCustomApiKey()) }
    var hasCustomKey by remember { mutableStateOf(quotaManager.hasCustomKey()) }
    var dailyRemaining by remember { mutableStateOf(quotaManager.getDailyRemaining()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate800)
                        }
                    }
                    Column {
                        Text(
                            text = if (isUrdu) "بیک اپ، کلاؤڈ سنک اور ڈیٹا مینجمنٹ" else "Backup, Cloud Sync & Data",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = if (isUrdu) "گوگل اکاؤنٹ، ڈرائیو کلاؤڈ اور فون اسٹوریج کا مکمل نظام" else "Google Account, Drive Cloud & Local Storage System",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }

                IconButton(onClick = { refreshLists() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DukanGreenPrimary)
                }
            }
        }

        // 1. Google Account & Live Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (profile.isLoggedIn) Color(0xFFA7F3D0) else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF4285F4))
                                }
                            }

                            Text(
                                text = if (isUrdu) "گوگل اکاؤنٹ (Google Account)" else "Google Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (profile.isLoggedIn) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (profile.isLoggedIn) Color(0xFFA7F3D0) else Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = if (profile.isLoggedIn) {
                                    if (isUrdu) "لاگ ان ہے ✓" else "Signed In ✓"
                                } else {
                                    if (isUrdu) "غیر منسلک" else "Not Linked"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (profile.isLoggedIn) Color(0xFF065F46) else Color(0xFF64748B)
                            )
                        }
                    }

                    if (profile.isLoggedIn) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.name.take(1).ifBlank { profile.email.take(1) }.ifBlank { "U" }.uppercase(),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name.ifBlank { if (isUrdu) "گوگل صارف" else "Google User" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = profile.email,
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isUrdu) "گوگل ڈرائیو بیک اپ فعال ہے" else "Drive Cloud Backup Ready",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { launchNativeGoogleAccountPicker() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                enabled = !isSigningIn
                            ) {
                                Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "اکاؤنٹ تبدیل کریں" else "Switch Account",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    authManager.signOut()
                                    firebaseAuth.signOut()
                                    profile = authManager.getProfile()
                                    driveBackupsList = emptyList()
                                    Toast.makeText(
                                        context,
                                        if (isUrdu) "گوگل اکاؤنٹ سائن آؤٹ کر دیا گیا" else "Google Account signed out",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshLists()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "لاگ آؤٹ" else "Sign Out",
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isUrdu) {
                                "اپنے فون کے گوگل اکاؤنٹ سے سائن ان کریں تاکہ آپ کی دکان کا کھاتہ، اسٹاک اور بل گوگل ڈرائیو پر محفوظ رہیں اور فون تبدیل کرنے کی صورت میں 1 منٹ میں بحال ہو سکیں۔"
                            } else {
                                "Sign in with your Google Account to automatically protect your shop data on Google Drive with 1-click cloud restore."
                            },
                            fontSize = 12.sp,
                            color = Slate600,
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = { launchNativeGoogleAccountPicker() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled = !isSigningIn
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = if (isUrdu) "اکاؤنٹ لوڈ ہو رہا ہے..." else "Opening Accounts...", fontSize = 13.sp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("G", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF4285F4))
                                    }
                                    Text(
                                        text = if (isUrdu) "گوگل سے سائن ان کریں (Sign In)" else "Sign In with Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Local Backup & Storage Card (SAF Document Picker + Device Snapshots)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SdStorage, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isUrdu) "لوکل فون اسٹوریج بیک اپ و ری اسٹور" else "Local Storage Backup & Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        if (profile.lastLocalBackupDate != null) {
                            Text(
                                text = profile.lastLocalBackupDate ?: "",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }

                    Text(
                        text = if (isUrdu) {
                            "اپنے فون کے کسی بھی فولڈر (Downloads, Documents, SD Card) میں محفوظ کریں یا پرانی بیک اپ فائل منتخب کر کے کھاتہ دوبارہ حاصل کریں۔"
                        } else {
                            "Export your entire shop data to any folder or SD card via Android File Picker, or select an existing backup file to restore."
                        },
                        fontSize = 11.5.sp,
                        color = Slate600
                    )

                    // Action buttons: Export and Restore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val defaultName = backupManager.localProvider.getDefaultExportFileName()
                                exportFileLauncher.launch(defaultName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isUrdu) "ایکسپورٹ بیک اپ" else "Export File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                importFileLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isUrdu) "ری اسٹور فائل" else "Restore File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Quick Device Snapshot
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val quickRes = backupManager.createQuickBackup(context, state, isSafety = false)
                                if (quickRes.isSuccess) {
                                    authManager.recordLocalBackup()
                                    profile = authManager.getProfile()
                                    refreshLists()
                                    Toast.makeText(
                                        context,
                                        if (isUrdu) "فوری اسنیپ شاٹ فون اسٹوریج میں بن گیا!" else "Quick device snapshot saved!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error: ${quickRes.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrdu) "فوری محفوظ اسنیپ شاٹ بنائیں (Quick Snapshot)" else "Take Instant Safety Snapshot",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Expandable list of local saved snapshots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLocalBackupsList = !showLocalBackupsList }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "محفوظ شدہ لوکل اسنیپ شاٹس (${localBackupsList.size})" else "Device Saved Snapshots (${localBackupsList.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Icon(
                            imageVector = if (showLocalBackupsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Slate600
                        )
                    }

                    AnimatedVisibility(visible = showLocalBackupsList) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (localBackupsList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF8FAFC),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isUrdu) "فی الوقت کوئی لوکل اسنیپ شاٹ موجود نہیں۔" else "No device snapshots found yet.",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                localBackupsList.forEach { fileInfo ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (fileInfo.isSafetyBackup) Color(0xFFFFFBEB) else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (fileInfo.isSafetyBackup) Color(0xFFFDE68A) else Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (fileInfo.isSafetyBackup) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFFFEF3C7)
                                                        ) {
                                                            Text(
                                                                text = if (isUrdu) "حفاظتی" else "Safety",
                                                                fontSize = 9.sp,
                                                                color = Color(0xFFB45309),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = fileInfo.fileName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Slate900,
                                                        maxLines = 1
                                                    )
                                                }
                                                Text(
                                                    text = "${fileInfo.formattedDate} • ${fileInfo.formattedSize}" +
                                                            (if (fileInfo.metadata != null) " • ${fileInfo.metadata.customerCount} Khata, ${fileInfo.metadata.inventoryCount} Stock" else ""),
                                                    fontSize = 10.sp,
                                                    color = Slate500
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            try {
                                                                val file = java.io.File(fileInfo.filePath)
                                                                val content = file.readText(Charsets.UTF_8)
                                                                val validation = backupManager.validateJson(content)
                                                                if (validation.isValid && validation.payloadState != null) {
                                                                    pendingRestoreState = validation.payloadState
                                                                    pendingValidation = validation
                                                                    pendingRestoreSourceTitle = fileInfo.fileName
                                                                    showRestoreDialog = true
                                                                } else {
                                                                    validationErrorMessage = validation.errorReason ?: "Corrupt snapshot"
                                                                }
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            backupManager.deleteLocalBackup(fileInfo.filePath)
                                                            refreshLists()
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Firebase Cloud Backup & Restore Card (Foundation)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF0F8A5F), modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isUrdu) "فائر بیس آن لائن کلاؤڈ بیک اپ" else "Firebase Cloud Backup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (firebaseSession.isAnonymous) Color(0xFFF1F5F9) else Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, if (firebaseSession.isAnonymous) Color(0xFFCBD5E1) else Color(0xFFA7F3D0))
                        ) {
                            Text(
                                text = if (firebaseSession.isAnonymous) {
                                    if (isUrdu) "گمنام موڈ (Anonymous)" else "Anonymous Cloud"
                                } else {
                                    if (isUrdu) "گوگل منسلک ✓" else "Google Linked ✓"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (firebaseSession.isAnonymous) Color(0xFF64748B) else Color(0xFF065F46)
                            )
                        }
                    }

                    Text(
                        text = if (isUrdu) {
                            "آپ کا ڈیٹا فائر بیس کلاؤڈ میں آپ کی منفرد یوزر آئی ڈی (${firebaseSession.uid.take(12)}...) کے تحت 100% محفوظ طریقے سے محفوظ کیا جاتا ہے۔"
                        } else {
                            "Your encrypted shop state is backed up securely under your private Firebase UID (${firebaseSession.uid.take(12)}...)."
                        },
                        fontSize = 11.5.sp,
                        color = Slate600
                    )

                    // Cloud state info box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isLoadingFirebaseCloudInfo) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = DukanGreenPrimary)
                                    Text(text = if (isUrdu) "کلاؤڈ ریکارڈ چیک ہو رہا ہے..." else "Checking cloud backup status...", fontSize = 11.sp, color = Slate600)
                                }
                            } else if (firebaseCloudBackupInfo != null) {
                                val info = firebaseCloudBackupInfo!!
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${info.shopName} • ${info.formattedSize}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "${info.formattedDate} • ${info.customerCount} Khata, ${info.inventoryCount} Stock",
                                            fontSize = 10.5.sp,
                                            color = Slate500
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val delRes = backupManager.deleteFirebaseCloudBackup(context)
                                                if (delRes.isSuccess) {
                                                    firebaseCloudBackupInfo = null
                                                    Toast.makeText(context, if (isUrdu) "کلاؤڈ بیک اپ حذف کر دیا گیا" else "Cloud backup deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isUrdu) "سرور پر فی الوقت کوئی محفوظ شدہ کلاؤڈ بیک اپ موجود نہیں۔" else "No cloud backup found on server yet.",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }

                    // Action buttons: Backup Now and Restore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isBackingUpFirebase = true
                                    val result = backupManager.backupToFirebaseCloud(context, state)
                                    isBackingUpFirebase = false
                                    if (result.isSuccess) {
                                        firebaseCloudBackupInfo = result.getOrNull()
                                        Toast.makeText(
                                            context,
                                            if (isUrdu) "کلاؤڈ بیک اپ کامیابی سے محفوظ ہو گیا!" else "Backed up to cloud successfully!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isUrdu) "کلاؤڈ بیک اپ میں خرابی: ${result.exceptionOrNull()?.message}" else "Cloud backup failed: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !isBackingUpFirebase
                        ) {
                            if (isBackingUpFirebase) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isUrdu) "اپلوڈ ہو رہا ہے..." else "Uploading...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isUrdu) "کلاؤڈ بیک اپ لیں" else "Backup Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isRestoring = true
                                    val downloadRes = backupManager.downloadFirebaseCloudBackup(context)
                                    isRestoring = false
                                    if (downloadRes.isSuccess) {
                                        val json = downloadRes.getOrNull() ?: ""
                                        val validation = backupManager.validateJson(json)
                                        if (validation.isValid && validation.payloadState != null) {
                                            pendingRestoreState = validation.payloadState
                                            pendingValidation = validation
                                            pendingRestoreSourceTitle = if (isUrdu) "فائر بیس آن لائن کلاؤڈ بیک اپ" else "Firebase Cloud Backup"
                                            showRestoreDialog = true
                                        } else {
                                            validationErrorMessage = validation.errorReason ?: "Corrupted cloud backup payload"
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isUrdu) "کلاؤڈ سے ڈاؤنلوڈ میں مسئلہ: ${downloadRes.exceptionOrNull()?.message}" else "Download failed: ${downloadRes.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !isRestoring && firebaseCloudBackupInfo != null
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isUrdu) "کلاؤڈ سے ری اسٹور" else "Restore Cloud", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Google Drive Cloud Backup Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isUrdu) "گوگل ڈرائیو کلاؤڈ بیک اپ" else "Google Drive Cloud Backup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        if (profile.lastBackupDate != null) {
                            Text(
                                text = profile.lastBackupDate ?: "",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }

                    if (!profile.isLoggedIn) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (isUrdu) "گوگل ڈرائیو بیک اپ استعمال کرنے کے لیے اوپر دیے گئے بٹن سے گوگل اکاؤنٹ لاگ ان کریں۔" else "Please sign in with your Google account above to use Google Drive backup.",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isBackingUpDrive = true
                                    val result = backupManager.backupToGoogleDrive(context, state)
                                    isBackingUpDrive = false
                                    if (result.isSuccess) {
                                        profile = authManager.getProfile()
                                        refreshLists()
                                        Toast.makeText(
                                            context,
                                            if (isUrdu) "گوگل ڈرائیو پر بیک اپ کامیابی سے محفوظ ہو گیا!" else "Backup saved to Google Drive successfully!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isUrdu) "ڈرائیو بیک اپ میں مسئلہ: ${result.exceptionOrNull()?.message}" else "Drive backup error: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled = !isBackingUpDrive
                        ) {
                            if (isBackingUpDrive) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = if (isUrdu) "گوگل ڈرائیو پر اپلوڈ ہو رہا ہے..." else "Uploading to Google Drive...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isUrdu) "ابھی گوگل ڈرائیو پر بیک اپ لیں (Upload Now)" else "Backup to Google Drive Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Drive Backups Explorer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDriveBackupsList = !showDriveBackupsList }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isUrdu) "ڈرائیو پر موجود بیک اپس (${driveBackupsList.size})" else "Google Drive Backups (${driveBackupsList.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                if (isLoadingDriveList) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                }
                            }
                            Icon(
                                imageVector = if (showDriveBackupsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Slate600
                            )
                        }

                        AnimatedVisibility(visible = showDriveBackupsList) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (driveBackupsList.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF8FAFC),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isUrdu) "گوگل ڈرائیو پر فی الوقت کوئی بیک اپ فائل نہیں ہے۔" else "No backups found in Google Drive yet.",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    driveBackupsList.forEach { driveItem ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF0FDF4),
                                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = driveItem.name,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Slate900,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${driveItem.formattedDate} • ${driveItem.formattedSize}",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                isRestoring = true
                                                                val downloadRes = backupManager.downloadDriveBackup(context, driveItem.fileId)
                                                                isRestoring = false
                                                                if (downloadRes.isSuccess) {
                                                                    val content = downloadRes.getOrNull() ?: ""
                                                                    val validation = backupManager.validateJson(content)
                                                                    if (validation.isValid && validation.payloadState != null) {
                                                                        pendingRestoreState = validation.payloadState
                                                                        pendingValidation = validation
                                                                        pendingRestoreSourceTitle = "Google Drive (${driveItem.name})"
                                                                        showRestoreDialog = true
                                                                    } else {
                                                                        validationErrorMessage = validation.errorReason ?: "Corrupt Drive file"
                                                                    }
                                                                } else {
                                                                    Toast.makeText(context, "Download failed: ${downloadRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.CloudDownload, contentDescription = "Restore", tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                val delRes = backupManager.deleteDriveBackup(context, driveItem.fileId)
                                                                if (delRes.isSuccess) {
                                                                    refreshLists()
                                                                } else {
                                                                    Toast.makeText(context, "Delete failed: ${delRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Automatic Backup & Auto Sync Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isUrdu) "خودکار بیک اپ اور شیڈول (Auto Sync)" else "Automatic Backup & Schedule",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        Switch(
                            checked = autoSyncSettings.isEnabled,
                            onCheckedChange = { enabled ->
                                val updated = autoSyncSettings.copy(isEnabled = enabled)
                                autoSyncSettings = updated
                                authManager.saveAutoSyncSettings(updated)
                                if (enabled) {
                                    backupManager.scheduleAutoSync(context, updated.intervalHours, updated.destination, updated.wifiOnly)
                                } else {
                                    backupManager.cancelAutoSync(context)
                                }
                            }
                        )
                    }

                    Text(
                        text = if (isUrdu) {
                            "خودکار بیک اپ کو فعال کرنے سے ایپ پس منظر میں روزانہ یا ہر 6 گھنٹے بعد آپ کا کھاتہ اور بل کلاؤڈ پر محفوظ کر دیتی ہے۔"
                        } else {
                            "Auto-sync periodically uploads your changes in the background without interrupting your work."
                        },
                        fontSize = 11.5.sp,
                        color = Slate600
                    )

                    AnimatedVisibility(visible = autoSyncSettings.isEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Frequency selector
                            Text(
                                text = if (isUrdu) "بیک اپ شیڈول فریکوئنسی:" else "Backup Frequency:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    6 to if (isUrdu) "6 گھنٹے" else "6 Hours",
                                    24 to if (isUrdu) "روزانہ (Daily)" else "Daily",
                                    168 to if (isUrdu) "ہفتہ وار" else "Weekly"
                                ).forEach { (hrs, label) ->
                                    val isSelected = autoSyncSettings.intervalHours == hrs
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val updated = autoSyncSettings.copy(intervalHours = hrs)
                                            autoSyncSettings = updated
                                            authManager.saveAutoSyncSettings(updated)
                                            backupManager.scheduleAutoSync(context, hrs, updated.destination, updated.wifiOnly)
                                        },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF7C3AED),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Destination selector
                            Text(
                                text = if (isUrdu) "بیک اپ لوکیشن:" else "Destination Target:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "BOTH" to if (isUrdu) "دونوں (سفارش)" else "Both (Best)",
                                    "DRIVE" to if (isUrdu) "گوگل ڈرائیو" else "Google Drive",
                                    "LOCAL" to if (isUrdu) "صرف فون" else "Phone Only"
                                ).forEach { (dest, label) ->
                                    val isSelected = autoSyncSettings.destination == dest
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val updated = autoSyncSettings.copy(destination = dest)
                                            autoSyncSettings = updated
                                            authManager.saveAutoSyncSettings(updated)
                                            backupManager.scheduleAutoSync(context, updated.intervalHours, dest, updated.wifiOnly)
                                        },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Wi-Fi only toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "صرف وائی فائی پر بیک اپ لیں (Wi-Fi Only)" else "Upload Only on Wi-Fi",
                                    fontSize = 12.sp,
                                    color = Slate800
                                )
                                Switch(
                                    checked = autoSyncSettings.wifiOnly,
                                    onCheckedChange = { wifi ->
                                        val updated = autoSyncSettings.copy(wifiOnly = wifi)
                                        autoSyncSettings = updated
                                        authManager.saveAutoSyncSettings(updated)
                                        backupManager.scheduleAutoSync(context, updated.intervalHours, updated.destination, wifi)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Google Gemini AI Assistant & Official AI Studio Free Tier Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (hasCustomKey) Color(0xFFA7F3D0) else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isUrdu) "جیمنائی اے آئی و فری ٹیئر سیٹنگز" else "Gemini AI & Free Tier Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasCustomKey) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, if (hasCustomKey) Color(0xFFA7F3D0) else Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = if (hasCustomKey) {
                                    if (isUrdu) "ذاتی کی فعال (1,500/دن)" else "Active (1,500/day)"
                                } else {
                                    if (isUrdu) "$dailyRemaining / 1500 آج باقی" else "$dailyRemaining / 1500 Today"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasCustomKey) Color(0xFF065F46) else Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isUrdu) "💡 گوگل اے آئی اسٹوڈیو مفت کوٹہ پالیسی ($0 فری):" else "💡 Google AI Studio Free Tier Policy ($0 Free):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = if (isUrdu) {
                                    "Google AI Studio بغیر کسی کریڈٹ کارڈ کے Flash ماڈلز کے لیے 15 کالز فی منٹ (RPM) اور روزانہ 1,500 کالز تک 100% مفت فراہم کرتا ہے، جو ہر 24 گھنٹے بعد خودکار رینیو ہوتی ہیں۔"
                                } else {
                                    "Google AI Studio provides 15 RPM and 1,500 daily requests 100% free with zero billing required, auto-renewing every 24 hours."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF15803D),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/app/apikey")
                                )
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "براؤزر کھولنے میں مسئلہ ہوا", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Get Free Key", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrdu) "Google AI Studio سے مفت کی حاصل کریں" else "Get Free Key from Google AI Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }

                    OutlinedTextField(
                        value = customKeyInput,
                        onValueChange = { customKeyInput = it },
                        label = {
                            Text(
                                text = if (isUrdu) "ذاتی جیمنائی API Key درج کریں" else "Personal Gemini API Key",
                                fontSize = 11.sp
                            )
                        },
                        placeholder = {
                            Text("AIzaSy...", fontSize = 11.sp, color = Color.Gray)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                quotaManager.saveCustomApiKey(customKeyInput.trim())
                                hasCustomKey = quotaManager.hasCustomKey()
                                dailyRemaining = quotaManager.getDailyRemaining()
                                Toast.makeText(
                                    context,
                                    if (isUrdu) "جیمنائی کی فعال ہو گئی!" else "Gemini API Key activated!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "محفوظ اور فعال کریں" else "Save & Activate",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasCustomKey) {
                            OutlinedButton(
                                onClick = {
                                    quotaManager.clearCustomApiKey()
                                    customKeyInput = ""
                                    hasCustomKey = false
                                    dailyRemaining = quotaManager.getDailyRemaining()
                                    Toast.makeText(
                                        context,
                                        if (isUrdu) "کی ہٹا دی گئی" else "API Key removed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(text = if (isUrdu) "ہٹائیں" else "Remove")
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Inspection Preview & Confirmation Dialog
    if (showRestoreDialog && pendingRestoreState != null && pendingValidation != null) {
        val meta = pendingValidation!!.metadata
        val restoreTarget = pendingRestoreState!!

        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                pendingRestoreState = null
                pendingValidation = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.RestorePage, contentDescription = null, tint = DukanGreenPrimary)
                    Text(
                        text = if (isUrdu) "ڈیٹا بحالی کی پیشگی جانچ (Restore Preview)" else "Restore Data Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUrdu) "بیک اپ سورس: $pendingRestoreSourceTitle" else "Source: $pendingRestoreSourceTitle",
                        fontSize = 12.sp,
                        color = Slate600,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "🏢 ${if (isUrdu) "دکان کا نام" else "Shop Name"}: ${meta?.shopName ?: restoreTarget.profile.shopName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            if (!meta?.createdAt.isNullOrBlank()) {
                                Text(
                                    text = "📅 ${if (isUrdu) "تاریخ" else "Date"}: ${meta?.createdAt?.take(10)}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Slate200)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "📦 ${if (isUrdu) "اسٹاک آئٹمز" else "Stock Items"}:", fontSize = 11.sp, color = Slate700)
                                Text(text = "${meta?.inventoryCount ?: restoreTarget.inventory.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "👥 ${if (isUrdu) "کسٹمر کھاتے" else "Customers"}:", fontSize = 11.sp, color = Slate700)
                                Text(text = "${meta?.customerCount ?: restoreTarget.customers.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "🏭 ${if (isUrdu) "سپلائرز" else "Suppliers"}:", fontSize = 11.sp, color = Slate700)
                                Text(text = "${meta?.supplierCount ?: restoreTarget.suppliers.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "💳 ${if (isUrdu) "ٹرانزیکشنز" else "Transactions"}:", fontSize = 11.sp, color = Slate700)
                                Text(text = "${meta?.transactionCount ?: restoreTarget.transactions.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Safety Backup Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { createSafetyBackupBeforeRestore = !createSafetyBackupBeforeRestore }
                    ) {
                        Checkbox(
                            checked = createSafetyBackupBeforeRestore,
                            onCheckedChange = { createSafetyBackupBeforeRestore = it }
                        )
                        Text(
                            text = if (isUrdu) "بحالی سے قبل موجودہ ڈیٹا کا خودکار حفاظتی اسنیپ شاٹ بنائیں (سفارش)" else "Create safety snapshot of current data before restore",
                            fontSize = 11.5.sp,
                            color = Slate800,
                            lineHeight = 15.sp
                        )
                    }

                    // Restore Mode Selector
                    Text(
                        text = if (isUrdu) "بحالی کا طریقہ منتخب کریں:" else "Select Restore Mode:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = restoreMode == RestoreMode.REPLACE_ALL,
                            onClick = { restoreMode = RestoreMode.REPLACE_ALL },
                            label = { Text(if (isUrdu) "مکمل تبدیلی (Replace)" else "Replace All", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDC2626),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = restoreMode == RestoreMode.MERGE,
                            onClick = { restoreMode = RestoreMode.MERGE },
                            label = { Text(if (isUrdu) "ضم کریں (Merge)" else "Merge", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DukanGreenPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = backupManager.restoreState(
                                context = context,
                                backupState = restoreTarget,
                                mode = restoreMode,
                                createSafetyBackup = createSafetyBackupBeforeRestore
                            )
                            showRestoreDialog = false
                            pendingRestoreState = null
                            pendingValidation = null
                            refreshLists()

                            if (result.success) {
                                onRestoreState?.invoke(repository.appState.value)
                                Toast.makeText(
                                    context,
                                    if (isUrdu) "کامیابی: ${result.message} (${result.itemsRestoredCount} آئٹمز)" else "Success: ${result.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Error: ${result.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary)
                ) {
                    Text(if (isUrdu) "تصدیق اور بحال کریں" else "Confirm & Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRestoreDialog = false
                    pendingRestoreState = null
                    pendingValidation = null
                }) {
                    Text(if (isUrdu) "منسوخ" else "Cancel")
                }
            }
        )
    }

    // Validation Error Dialog
    if (validationErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { validationErrorMessage = null },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text(if (isUrdu) "فائل میں خرابی (Invalid File)" else "Validation Failed") },
            text = { Text(validationErrorMessage ?: "") },
            confirmButton = {
                Button(onClick = { validationErrorMessage = null }) {
                    Text(if (isUrdu) "ٹھیک ہے" else "OK")
                }
            }
        )
    }
}
