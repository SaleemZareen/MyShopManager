package com.saleemkhan.myshopmanager.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.saleemkhan.myshopmanager.data.AiQuotaManager
import com.saleemkhan.myshopmanager.data.GoogleAuthManager
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.ui.theme.*

@Composable
fun BackupSyncScreen(
    state: AppState,
    isUrdu: Boolean
) {
    val context = LocalContext.current
    val authManager = remember { GoogleAuthManager.getInstance(context) }
    val quotaManager = remember { AiQuotaManager.getInstance(context) }

    var profile by remember { mutableStateOf(authManager.getProfile()) }
    var isSigningIn by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 1. Android Native Account Picker result (AccountManager)
            val chosenEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!chosenEmail.isNullOrBlank()) {
                val rawName = chosenEmail.substringBefore("@").replace(".", " ")
                val formattedName = rawName.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                authManager.saveLogin(formattedName, chosenEmail, null)
                profile = authManager.getProfile()
                Toast.makeText(
                    context,
                    if (isUrdu) "گوگل اکاؤنٹ ($chosenEmail) کامیابی سے سائن ان ہو گیا!" else "Google Account ($chosenEmail) signed in successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                return@rememberLauncherForActivityResult
            }

            // 2. Try Google Play Services account result
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = if (task.isSuccessful) task.result else null
                val email = account?.email
                if (!email.isNullOrBlank()) {
                    val name = account.displayName?.ifBlank { null } ?: email.substringBefore("@")
                    val photo = account.photoUrl?.toString()
                    authManager.saveLogin(name, email, photo)
                    profile = authManager.getProfile()
                    Toast.makeText(
                        context,
                        if (isUrdu) "گوگل اکاؤنٹ ($email) کامیابی سے سائن ان ہو گیا!" else "Google Account ($email) signed in successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@rememberLauncherForActivityResult
                }
            } catch (_: Exception) {}

            // 3. Fallback: inspect any String extra in result.data containing email address
            result.data?.extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    val candidate = bundle.getString(key)
                    if (!candidate.isNullOrBlank() && candidate.contains("@") && candidate.contains(".")) {
                        val name = candidate.substringBefore("@")
                        authManager.saveLogin(name, candidate, null)
                        profile = authManager.getProfile()
                        Toast.makeText(
                            context,
                            if (isUrdu) "گوگل اکاؤنٹ ($candidate) کامیابی سے سائن ان ہو گیا!" else "Google Account ($candidate) signed in successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@rememberLauncherForActivityResult
                    }
                }
            }
        }
    }

    val launchNativeGoogleAccountPicker = {
        isSigningIn = true
        try {
            // First try Android's standard account chooser - directly lists all Google accounts logged in on device
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            googleSignInLauncher.launch(intent)
        } catch (_: Exception) {
            try {
                // Fallback to Google Play Services sign-in intent
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                isSigningIn = false
                Toast.makeText(
                    context,
                    if (isUrdu) "گوگل سائن ان ونڈو کھولنے میں مسئلہ: ${e.message}" else "Sign-in error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var customKeyInput by remember { mutableStateOf(quotaManager.getCustomApiKey()) }
    var hasCustomKey by remember { mutableStateOf(quotaManager.hasCustomKey()) }
    var dailyRemaining by remember { mutableStateOf(quotaManager.getDailyRemaining()) }

    var isBackingUp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
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
                    // Header Bar
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
                                text = if (isUrdu) "گوگل اکاؤنٹ اور کلاؤڈ سنک" else "Google Account & Cloud Sync",
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
                                    if (isUrdu) "منسلک ہے ✓" else "Connected ✓"
                                } else {
                                    if (isUrdu) "غیر منسلک" else "Not Linked"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (profile.isLoggedIn) Color(0xFF065F46) else Color(0xFF64748B)
                            )
                        }
                    }

                    if (profile.isLoggedIn) {
                        // Connected Profile Display Card with Profile Photo
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
                                // Profile Avatar / Photo
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
                                            text = if (isUrdu) "گوگل ڈرائیو اور جیمنائی سنک فعال ہے" else "Drive & Gemini Sync Active",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Connected Actions
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
                                    try {
                                        googleSignInClient.signOut()
                                    } catch (_: Exception) {}
                                    authManager.signOut()
                                    profile = authManager.getProfile()
                                    Toast.makeText(
                                        context,
                                        if (isUrdu) "اکاؤنٹ لاگ آؤٹ کر دیا گیا" else "Account signed out",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                        // Unlinked Instructions & Interactive Sign-In Button
                        Text(
                            text = if (isUrdu) {
                                "اپنے موبائل میں موجود کسی بھی گوگل اکاؤنٹ سے لاگ ان کریں تاکہ دکان کا کلاؤڈ بیک اپ محفوظ رہے اور جیمنائی مشیر سنک رہے۔"
                            } else {
                                "Sign in with your Google account on this device to backup your shop data and sync Gemini AI Advisor."
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
                                Text(text = if (isUrdu) "اکاؤنٹ لوڈ ہو رہا ہے..." else "Opening Google Accounts...", fontSize = 13.sp)
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
                                        text = if (isUrdu) "گوگل سے سائن ان کریں" else "Sign In with Google",
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

        // 2. Google Gemini AI Assistant & Official AI Studio Free Tier Card
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

                    // Official Google AI Studio Free Tier Policy Explanation
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

        // 3. Google Drive Cloud Backup Section (Tied directly to the linked account)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "گوگل ڈرائیو اور کلاؤڈ بیک اپ" else "Google Drive & Cloud Backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Linked Account Notice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (profile.isLoggedIn) Color(0xFFF1F5F9) else Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, if (profile.isLoggedIn) Color(0xFFE2E8F0) else Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (profile.isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (profile.isLoggedIn) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (profile.isLoggedIn) {
                                    if (isUrdu) "بیک اپ اکاؤنٹ: ${profile.email}" else "Backup Account: ${profile.email}"
                                } else {
                                    if (isUrdu) "بیک اپ کے لیے پہلے اوپر گوگل اکاؤنٹ منسلک کریں" else "Link Google account above for Drive backup"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (profile.isLoggedIn) Slate800 else Color(0xFF92400E)
                            )
                        }
                    }

                    if (profile.lastBackupDate != null) {
                        Text(
                            text = if (isUrdu) "آخری کلاؤڈ بیک اپ: ${profile.lastBackupDate}" else "Last Cloud Backup: ${profile.lastBackupDate}",
                            fontSize = 11.5.sp,
                            color = Slate600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (!profile.isLoggedIn) {
                                launchNativeGoogleAccountPicker()
                                return@Button
                            }
                            isBackingUp = true
                            authManager.recordBackup()
                            profile = authManager.getProfile()
                            isBackingUp = false
                            Toast.makeText(
                                context,
                                if (isUrdu) "گوگل ڈرائیو (${profile.email}) پر بیک اپ محفوظ ہو گیا!" else "Backup saved to Google Drive (${profile.email})!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isUrdu) "بیک اپ لیا جا رہا ہے..." else "Backing up...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Backup", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUrdu) "ابھی گوگل ڈرائیو پر بیک اپ لیں" else "Backup to Google Drive Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            Toast.makeText(
                                context,
                                if (isUrdu) "لوکل فائل محفوظ ہو گئی (Downloads/dukan_backup.json)" else "Exported to Downloads/dukan_backup.json",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isUrdu) "لوکل فائل محفوظ کریں (JSON)" else "Export Local Backup (JSON)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
