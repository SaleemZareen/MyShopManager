package com.saleemkhan.myshopmanager.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.BiometricAuthManager

@Composable
fun SecurityLockScreen(
    expectedPin: String? = null,
    expectedPassword: String? = null,
    isBiometricsEnabled: Boolean = false,
    shopName: String = "My Shop Manager",
    isUrdu: Boolean = false,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var enteredPin by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasPin = !expectedPin.isNullOrBlank()
    val hasPassword = !expectedPassword.isNullOrBlank()

    var lockMode by remember(hasPin, hasPassword) {
        mutableStateOf(if (hasPin) "PIN" else "PASSWORD")
    }

    fun triggerBiometrics() {
        if (activity != null && isBiometricsEnabled && BiometricAuthManager.isBiometricAvailable(context)) {
            BiometricAuthManager.showBiometricPrompt(
                activity = activity,
                title = if (isUrdu) "فنگر پرنٹ کی تصدیق" else "Fingerprint Unlock",
                subtitle = if (isUrdu) "دکان کے کھاتے کھولنے کے لیے فنگر پرنٹ لگائیں" else "Touch fingerprint sensor to access shop records",
                negativeButtonText = if (isUrdu) "پن یا پاس ورڈ استعمال کریں" else "Use PIN / Password",
                onSuccess = { onUnlockSuccess() },
                onError = { err -> errorMessage = err }
            )
        }
    }

    // Auto-prompt biometrics once on screen display if enabled
    LaunchedEffect(isBiometricsEnabled) {
        if (isBiometricsEnabled) {
            triggerBiometrics()
        }
    }

    fun handlePinKeyPress(key: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + key
            enteredPin = newPin
            errorMessage = null
            if (newPin.length == 4) {
                if (newPin == expectedPin) {
                    onUnlockSuccess()
                } else {
                    errorMessage = if (isUrdu) "غلط پن کوڈ درج کیا گیا ہے!" else "Incorrect PIN entered!"
                    enteredPin = ""
                }
            }
        }
    }

    fun handlePinDelete() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    fun handlePasswordSubmit() {
        if (enteredPassword.trim() == expectedPassword?.trim()) {
            onUnlockSuccess()
        } else {
            errorMessage = if (isUrdu) "غلط پاس ورڈ درج کیا گیا ہے!" else "Incorrect Password entered!"
            enteredPassword = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon with Glow
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(DukanGreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = DukanGreenPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = shopName.ifBlank { if (isUrdu) "دکان مینیجر" else "Shop Manager" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isUrdu) "ایپلیکیشن مقفل ہے (سیکیورٹی لاک)" else "Application Secured (Security Lock)",
                fontSize = 12.sp,
                color = Slate400,
                textAlign = TextAlign.Center
            )

            // Mode Switcher (If both PIN and Password are set)
            if (hasPin && hasPassword) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .background(Slate800, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = lockMode == "PIN",
                        onClick = {
                            lockMode = "PIN"
                            errorMessage = null
                        },
                        label = { Text(if (isUrdu) "پن کوڈ" else "PIN Code", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DukanGreenPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Slate400
                        ),
                        border = null
                    )

                    FilterChip(
                        selected = lockMode == "PASSWORD",
                        onClick = {
                            lockMode = "PASSWORD"
                            errorMessage = null
                        },
                        label = { Text(if (isUrdu) "پاس ورڈ" else "Password", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DukanGreenPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Slate400
                        ),
                        border = null
                    )
                }
            }

            // Biometric Hint
            if (isBiometricsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isUrdu) "یا فنگر پرنٹ سینسر ٹچ کریں" else "Touch fingerprint sensor to unlock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PurpleAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 1. PIN MODE ---
            if (lockMode == "PIN" && hasPin) {
                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) DukanGreenPrimary else Slate700)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = RedDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Keypad Layout (1 to 9, Biometric/Clear, 0, Backspace)
                val keypad = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (isBiometricsEnabled) "FP" else "C", "0", "⌫")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keypad.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "C" -> {
                                                enteredPin = ""
                                                errorMessage = null
                                            }
                                            "FP" -> triggerBiometrics()
                                            "⌫" -> handlePinDelete()
                                            else -> handlePinKeyPress(key)
                                        }
                                    },
                                    shape = CircleShape,
                                    color = when (key) {
                                        "FP" -> PurpleAccent.copy(alpha = 0.2f)
                                        "C", "⌫" -> Slate800
                                        else -> Slate800.copy(alpha = 0.85f)
                                    },
                                    border = BorderStroke(1.dp, if (key == "FP") PurpleAccent.copy(alpha = 0.5f) else Slate700),
                                    modifier = Modifier.size(62.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "FP") {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Fingerprint",
                                                tint = PurpleAccent,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 2. PASSWORD MODE ---
            if (lockMode == "PASSWORD" || (!hasPin && hasPassword)) {
                Column(
                    modifier = Modifier.widthIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = {
                            enteredPassword = it
                            errorMessage = null
                        },
                        placeholder = { Text(if (isUrdu) "پاس ورڈ درج کریں..." else "Enter Password...", color = Slate500) },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Slate400
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handlePasswordSubmit() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = DukanGreenPrimary,
                            unfocusedBorderColor = Slate700,
                            unfocusedContainerColor = Slate800,
                            focusedContainerColor = Slate800
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = RedDanger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { handlePasswordSubmit() },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(if (isUrdu) "تصدیق کریں" else "Unlock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (isBiometricsEnabled) {
                            FilledTonalButton(
                                onClick = { triggerBiometrics() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = PurpleAccent.copy(alpha = 0.2f),
                                    contentColor = PurpleAccent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .size(46.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
