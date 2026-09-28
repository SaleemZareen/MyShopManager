package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.audio.GeminiAudioEngine
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.model.ShopProfile
import com.saleemkhan.myshopmanager.model.StoreMode
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.BiometricAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// RTL Auto Detection helper for Urdu / Arabic text
fun isRtlText(text: String): Boolean {
    if (text.isBlank()) return false
    val firstChar = text.trim().firstOrNull() ?: return false
    val direction = Character.getDirectionality(firstChar)
    return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    isUrdu: Boolean,
    onUpdateProfile: (ShopProfile) -> Unit,
    onResetData: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onLockApp: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form inputs initialized from profile
    var shopName by remember(state.profile) { mutableStateOf(state.profile.shopName) }
    var shopNameUrdu by remember(state.profile) { mutableStateOf(state.profile.shopNameUrdu ?: "") }
    var ownerName by remember(state.profile) { mutableStateOf(state.profile.ownerName) }
    var phone by remember(state.profile) { mutableStateOf(state.profile.phone) }
    var cnic by remember(state.profile) { mutableStateOf(state.profile.cnic) }
    var ntn by remember(state.profile) { mutableStateOf(state.profile.ntn) }
    var address by remember(state.profile) { mutableStateOf(state.profile.address) }
    var selectedStoreMode by remember(state.profile) { mutableStateOf(state.profile.storeMode) }

    // Security inputs
    var pinCodeInput by remember(state.profile) { mutableStateOf(state.profile.pinCode ?: "") }
    var passwordCodeInput by remember(state.profile) { mutableStateOf(state.profile.passwordCode ?: "") }
    var biometricsEnabled by remember(state.profile) { mutableStateOf(state.profile.biometricsEnabled) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Accordion active tab state: "profile", "mode", "security", "backup", "tts", "danger", or null
    var activeAccordionTab by remember { mutableStateOf<String?>("profile") }

    // Feedback message (auto-dismisses after 2.5 seconds)
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Reset Confirmation Dialog State
    var showWipeModal by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }

    // TTS Audio Settings
    val audioEngine = remember { GeminiAudioEngine(context) }
    var playbackSpeed by remember { mutableFloatStateOf(GeminiAudioEngine.getPlaybackSpeed(context)) }
    var isTestingAudio by remember { mutableStateOf(false) }

    val hasBiometricsHardware = remember { BiometricAuthManager.isBiometricAvailable(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                audioEngine.stop()
            } catch (_: Exception) {}
        }
    }

    // Auto-dismiss notification after 2.5s
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(2500)
            successMessage = null
        }
    }

    fun handleSave(messageUr: String, messageEn: String) {
        val updated = state.profile.copy(
            shopName = shopName.trim().ifBlank { "My Shop" },
            shopNameUrdu = shopNameUrdu.trim().ifBlank { null },
            ownerName = ownerName.trim(),
            phone = phone.trim(),
            cnic = cnic.trim(),
            ntn = ntn.trim(),
            address = address.trim(),
            storeMode = selectedStoreMode,
            pinCode = pinCodeInput.trim().ifBlank { null },
            passwordCode = passwordCodeInput.trim().ifBlank { null },
            biometricsEnabled = biometricsEnabled
        )
        onUpdateProfile(updated)
        successMessage = if (isUrdu) messageUr else messageEn
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // --- 0. SUCCESS BANNER (Green Notification Alert) ---
        if (successMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DukanGreenLight),
                    border = BorderStroke(1.dp, DukanGreenPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DukanGreenHover, modifier = Modifier.size(20.dp))
                        Text(
                            text = successMessage!!,
                            color = DukanGreenHover,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 1: SHOP PROFILE INFORMATION (دکان کا پروفائل)
        // =========================================================================
        item {
            val isExpanded = activeAccordionTab == "profile"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isExpanded) DukanGreenPrimary.copy(alpha = 0.5f) else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header Bar (Clickable Accordion Trigger)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeAccordionTab = if (isExpanded) null else "profile" }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(DukanGreenLight, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "دکان کا پروفائل (Shop Profile)" else "Shop Profile Information",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "دکان کا نام، مالک، این ٹی این اور رسیدوں کا پتہ" else "Shop name, owner, phone, NTN, CNIC and address",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (isExpanded) DukanGreenPrimary else Slate400
                        )
                    }

                    // Expandable Body
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = Slate100)

                            // Shop Name (English)
                            OutlinedTextField(
                                value = shopName,
                                onValueChange = { shopName = it },
                                label = { Text(if (isUrdu) "دکان کا نام (Shop Name - English)" else "Shop Name (English)") },
                                placeholder = { Text("e.g. Bismillah General Store") },
                                textStyle = LocalTextStyle.current.copy(textAlign = if (isRtlText(shopName)) TextAlign.Right else TextAlign.Left),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Shop Name (Urdu)
                            OutlinedTextField(
                                value = shopNameUrdu,
                                onValueChange = { shopNameUrdu = it },
                                label = { Text(if (isUrdu) "دکان کا نام (اردو)" else "Shop Name (Urdu)") },
                                placeholder = { Text("مثلاً بسم اللہ جنرل اسٹور") },
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Owner Name
                            OutlinedTextField(
                                value = ownerName,
                                onValueChange = { ownerName = it },
                                label = { Text(if (isUrdu) "مالک / پروپرائیٹر کا نام" else "Owner / Proprietor Name") },
                                textStyle = LocalTextStyle.current.copy(textAlign = if (isRtlText(ownerName)) TextAlign.Right else TextAlign.Left),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Phone & CNIC in row
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text(if (isUrdu) "موبائل فون نمبر" else "Mobile Number") },
                                    placeholder = { Text("03001234567") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = cnic,
                                    onValueChange = { cnic = it },
                                    label = { Text(if (isUrdu) "شناختی کارڈ (CNIC)" else "CNIC Number") },
                                    placeholder = { Text("12345-1234567-1") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // NTN Number
                            OutlinedTextField(
                                value = ntn,
                                onValueChange = { ntn = it },
                                label = { Text(if (isUrdu) "این ٹی این نمبر (NTN Number - ٹیکس ریکارڈ)" else "NTN Number (FBR Tax)") },
                                placeholder = { Text("e.g. 1234567-8") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Shop Address
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text(if (isUrdu) "دکان کا پتہ (Address)" else "Shop Address") },
                                textStyle = LocalTextStyle.current.copy(textAlign = if (isRtlText(address)) TextAlign.Right else TextAlign.Left),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Save Profile Button
                            Button(
                                onClick = { handleSave("دکان کا پروفائل کامیابی سے محفوظ ہو گیا!", "Shop profile saved successfully!") },
                                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isUrdu) "پروفائل کی ترتیبات محفوظ کریں" else "Save Profile Changes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 2: STORE OPERATING MODE (دکان کا ورک فلو موڈ)
        // =========================================================================
        item {
            val isExpanded = activeAccordionTab == "mode"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isExpanded) DukanGreenPrimary.copy(alpha = 0.5f) else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeAccordionTab = if (isExpanded) null else "mode" }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(BlueInfo.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = BlueInfo, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "دکان کا ورک فلو موڈ (Store Mode)" else "Store Operating Mode",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "سادہ ریٹیل موڈ بمقابلہ مخصوص کسٹمرز و بارکوڈ موڈ" else "Simple Retail mode vs Specialized wholesale/inventory mode",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (isExpanded) DukanGreenPrimary else Slate400
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = Slate100)

                            Text(
                                text = if (isUrdu) "دکان کی قسم منتخب کریں تاکہ ایپ کا انٹرفیس آپ کے کاروبار کے مطابق ڈھل سکے:" else "Select your store operating workflow:",
                                fontSize = 11.5.sp,
                                color = Slate600
                            )

                            // Mode 1: Simple Retail Mode
                            val isSimple = selectedStoreMode == StoreMode.SIMPLE
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSimple) DukanGreenLight else Slate50,
                                border = BorderStroke(if (isSimple) 2.dp else 1.dp, if (isSimple) DukanGreenPrimary else Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStoreMode = StoreMode.SIMPLE }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSimple,
                                        onClick = { selectedStoreMode = StoreMode.SIMPLE },
                                        colors = RadioButtonDefaults.colors(selectedColor = DukanGreenPrimary)
                                    )
                                    Column {
                                        Text(
                                            text = if (isUrdu) "سادہ ریٹیل موڈ (Simple Retail Mode)" else "Simple Retail Mode",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = if (isUrdu) "جنرل اسٹور، کریانہ، پرچون، گروسری دکان کے لیے انتہائی تیز اور سادہ کیش کاؤنٹر" else "Ideal for kiryana, grocery, and general stores. Fast cash-counter billing.",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                }
                            }

                            // Mode 2: Specialized Store Mode
                            val isSpecialized = selectedStoreMode == StoreMode.SPECIALIZED
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSpecialized) DukanGreenLight else Slate50,
                                border = BorderStroke(if (isSpecialized) 2.dp else 1.dp, if (isSpecialized) DukanGreenPrimary else Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStoreMode = StoreMode.SPECIALIZED }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSpecialized,
                                        onClick = { selectedStoreMode = StoreMode.SPECIALIZED },
                                        colors = RadioButtonDefaults.colors(selectedColor = DukanGreenPrimary)
                                    )
                                    Column {
                                        Text(
                                            text = if (isUrdu) "مخصوص اسٹور موڈ (Specialized Store Mode)" else "Specialized Store Mode",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = if (isUrdu) "موبائل شاپ، فارمیسی، کپڑے، ہارڈویئر کے لیے بارکوڈ ٹریکنگ، IMEI اور تفصیلی کسٹمر کھاتہ" else "For mobile, pharmacy, garments, hardware. Detailed ledgers, barcodes, IMEI.",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { handleSave("دکان کا آپریٹنگ موڈ کامیابی سے تبدیل ہو گیا!", "Store operational mode saved!") },
                                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Text(if (isUrdu) "موڈ کی ترتیبات محفوظ کریں" else "Save Operating Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 3: SECURITY PIN, PASSWORD & FINGERPRINT LOCK
        // =========================================================================
        item {
            val isExpanded = activeAccordionTab == "security"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isExpanded) DukanGreenPrimary.copy(alpha = 0.5f) else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeAccordionTab = if (isExpanded) null else "security" }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(PurpleAccent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "ایپ سیکیورٹی لاک اور فنگر پرنٹ" else "Security PIN, Password & Biometric Lock",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "4 ہندسوں کا پن کوڈ، حروف کا پاس ورڈ اور فنگر پرنٹ انلاک" else "4-digit PIN, alphanumeric password and fingerprint sensor",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (isExpanded) DukanGreenPrimary else Slate400
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = Slate100)

                            // 1. PIN Lock
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isUrdu) "4 ہندسوں کا سیکیورٹی پن (PIN Code):" else "4-Digit Numeric PIN Lock:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                Text(
                                    text = if (isUrdu) "ایپ اوپن ہونے پر یہ 4 ہندسوں کا کوڈ پوچھا جائے گا۔ خالی چھوڑنے پر غیر فعال ہو جائے گا۔" else "Prompted when opening the app. Leave empty to disable.",
                                    fontSize = 10.5.sp,
                                    color = Slate500
                                )
                                OutlinedTextField(
                                    value = pinCodeInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(4)
                                        pinCodeInput = digits
                                    },
                                    placeholder = { Text("1234", fontFamily = FontFamily.Monospace) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = 4.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 2. Alphanumeric Password Lock
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isUrdu) "سیکیورٹی پاس ورڈ (حروف اور نمبرز):" else "Alphanumeric Password Lock:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                Text(
                                    text = if (isUrdu) "حروف اور نمبرز پر مشتمل پاس ورڈ۔ خالی چھوڑنے پر غیر فعال ہو جائے گا۔" else "Letters and numbers password. Leave empty to disable.",
                                    fontSize = 10.5.sp,
                                    color = Slate500
                                )
                                OutlinedTextField(
                                    value = passwordCodeInput,
                                    onValueChange = { passwordCodeInput = it },
                                    placeholder = { Text("e.g. MyShop2026") },
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
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 3. Biometric / Fingerprint Sensor Toggle
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (biometricsEnabled) PurpleAccent.copy(alpha = 0.08f) else Slate50,
                                border = BorderStroke(1.dp, if (biometricsEnabled) PurpleAccent.copy(alpha = 0.4f) else Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(PurpleAccent.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(
                                                text = if (isUrdu) "اینڈرائیڈ فنگر پرنٹ انلاک" else "Android Fingerprint Unlock",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = if (hasBiometricsHardware) {
                                                    if (isUrdu) "موبائل فنگر پرنٹ سینسر سے فوری ایپ انلاک کریں" else "Unlock instantly using device fingerprint sensor"
                                                } else {
                                                    if (isUrdu) "ڈیوائس میں بائیو میٹرک سینسر فعال ہے" else "Biometrics supported on this device"
                                                },
                                                fontSize = 10.5.sp,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = biometricsEnabled,
                                        onCheckedChange = { biometricsEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = PurpleAccent
                                        )
                                    )
                                }
                            }

                            // Action buttons: Save Security and Lock Now
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { handleSave("سیکیورٹی ترتیبات محفوظ ہو گئیں!", "Security settings saved!") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isUrdu) "سیکیورٹی محفوظ کریں" else "Save Security", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                val canLock = pinCodeInput.isNotBlank() || passwordCodeInput.isNotBlank() || biometricsEnabled
                                if (canLock) {
                                    OutlinedButton(
                                        onClick = {
                                            handleSave("سیکیورٹی فعال - ایپ لاک ہو گئی", "Security enabled - locking app")
                                            onLockApp()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger),
                                        border = BorderStroke(1.dp, RedDanger),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isUrdu) "ابھی لاک کریں" else "Lock Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 4: BACKUP, RESTORE & AUTO SYNC PAGE LINK
        // (Per instructions: Do not implement internal backup logic here; keep as dedicated link)
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBackup() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(BlueInfo.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = BlueInfo, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isUrdu) "بیک اپ، ری سٹور اور سنک" else "Backup, Restore & Auto Sync",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BlueInfo.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isUrdu) "سرشار صفحہ" else "Page",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueInfo,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isUrdu) "گوگل ڈرائیو کلاؤڈ، لوکل اسٹوریج اور خودکار پس منظر سنک کا سرشار صفحہ کھولیں" else "Open dedicated Google Drive Cloud, Local Storage & Auto Sync page",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isUrdu) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                        contentDescription = "Navigate to Backup Page",
                        tint = BlueInfo
                    )
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 5: GEMINI NATURAL VOICE & PLAYBACK SPEED
        // =========================================================================
        item {
            val isExpanded = activeAccordionTab == "tts"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isExpanded) DukanGreenPrimary.copy(alpha = 0.5f) else Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tts_settings_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeAccordionTab = if (isExpanded) null else "tts" }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(DukanGreenLight, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "صوتی اسسٹنٹ و آواز کی رفتار" else "Voice Assistant & Playback Speed",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "جیمنائی نیچرل اردو و انگریزی وائس سپیڈ (${String.format(Locale.US, "%.2fx", playbackSpeed)})" else "Gemini Natural Urdu & English TTS (${String.format(Locale.US, "%.2fx", playbackSpeed)})",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (isExpanded) DukanGreenPrimary else Slate400
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = Slate100)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "پلے بیک رفتار کا سلائیڈر:" else "Playback Speed Slider:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fx", playbackSpeed),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DukanGreenPrimary
                                )
                            }

                            Slider(
                                value = playbackSpeed,
                                onValueChange = { newSpeed ->
                                    val rounded = (Math.round(newSpeed * 20f) / 20f).coerceIn(0.5f, 2.0f)
                                    playbackSpeed = rounded
                                    GeminiAudioEngine.setPlaybackSpeed(context, rounded)
                                    audioEngine.playbackSpeed = rounded
                                },
                                valueRange = 0.5f..2.0f,
                                steps = 29,
                                colors = SliderDefaults.colors(
                                    thumbColor = DukanGreenPrimary,
                                    activeTrackColor = DukanGreenPrimary,
                                    inactiveTrackColor = Slate200
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Preset chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { preset ->
                                    val isSelected = Math.abs(playbackSpeed - preset) < 0.03f
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            playbackSpeed = preset
                                            GeminiAudioEngine.setPlaybackSpeed(context, preset)
                                            audioEngine.playbackSpeed = preset
                                        },
                                        label = { Text("${preset}x", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DukanGreenPrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Test voice button
                            OutlinedButton(
                                onClick = {
                                    if (isTestingAudio) {
                                        audioEngine.stop()
                                        isTestingAudio = false
                                    } else {
                                        isTestingAudio = true
                                        coroutineScope.launch {
                                            val testPhrase = if (isUrdu) {
                                                "السلام علیکم! دکان مینیجر میں صوتی آواز کی رفتار اب ${String.format(Locale.US, "%.2f", playbackSpeed)} گنا ہے۔"
                                            } else {
                                                "Hello! Voice playback speed is set to ${String.format(Locale.US, "%.2f", playbackSpeed)}x."
                                            }
                                            audioEngine.speakText(
                                                text = testPhrase,
                                                isUrdu = isUrdu,
                                                onStart = { _ -> isTestingAudio = true },
                                                onDone = { isTestingAudio = false },
                                                onError = { isTestingAudio = false }
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isTestingAudio) RedDanger else DukanGreenPrimary
                                ),
                                border = BorderStroke(1.dp, if (isTestingAudio) RedDanger else DukanGreenPrimary),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(if (isTestingAudio) Icons.Default.Stop else Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isTestingAudio) "روکیں (Stop)" else if (isUrdu) "ٹیسٹ آواز سنیں" else "Test Voice Speed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ACCORDION SECTION 6: DANGER ZONE (خطرناک زون - ڈیٹا صفائی)
        // =========================================================================
        item {
            val isExpanded = activeAccordionTab == "danger"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isExpanded) RedDanger else RedDanger.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeAccordionTab = if (isExpanded) null else "danger" }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(RedDanger.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedDanger, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "خطرناک زون (Danger Zone - Wipe Data)" else "Danger Zone / Reset Application",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger
                                )
                                Text(
                                    text = if (isUrdu) "ایپلیکیشن کا تمام ڈیٹا صاف کریں اور نیا کاروبار شروع کریں" else "Wipe all shop data, transactions, inventory, and start fully fresh",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = RedDanger
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = RedDanger.copy(alpha = 0.2f))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RedDanger.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedDanger, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = if (isUrdu) "انتباہ (Irreversible Action):" else "CRITICAL WARNING:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RedDanger
                                        )
                                        Text(
                                            text = if (isUrdu)
                                                "اس بٹن پر کلک کرنے سے دکان کے تمام کھاتے، کسٹمرز کا ادھار، سپلائرز کی ادائیگیاں اور انوینٹری کا سارا اسٹاک مکمل ڈیلیٹ ہو جائے گا اور اسے واپس نہیں لایا جا سکے گا۔"
                                            else
                                                "This action cannot be undone. Clicking this will permanently delete all cash/bank ledgers, inventory, customer balances, and reset your shop to fresh installation stage.",
                                            fontSize = 11.sp,
                                            color = Slate700
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    wipeConfirmInput = ""
                                    showWipeModal = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isUrdu) "تمام ڈیٹا مستقل طور پر حذف کریں" else "Wipe All Data & Start Fresh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- SECURE DATA WIPE CONFIRMATION MODAL ---
    if (showWipeModal) {
        AlertDialog(
            onDismissRequest = { showWipeModal = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(RedDanger.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RedDanger, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    text = if (isUrdu) "حتمی تصدیق درکار ہے" else "Security Verification Required",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isUrdu)
                            "دکان کا تمام ڈیٹا مستقل طور پر صاف کرنے کے لیے نیچے دیے گئے خانے میں بڑے حروف میں DELETE لکھیں:"
                        else
                            "To confirm that you want to wipe all records permanently, please type DELETE in the box below:",
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = wipeConfirmInput,
                        onValueChange = { wipeConfirmInput = it },
                        placeholder = { Text("DELETE", fontFamily = FontFamily.Monospace) },
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeModal = false
                        onResetData()
                        successMessage = if (isUrdu) "تمام ڈیٹا صاف کر کے نیا آغاز کر دیا گیا ہے!" else "All data wiped and fresh application loaded!"
                    },
                    enabled = wipeConfirmInput.trim() == "DELETE",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedDanger,
                        disabledContainerColor = Slate300
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isUrdu) "صاف کریں (Wipe Data)" else "Confirm Wipe", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeModal = false }) {
                    Text(if (isUrdu) "منسوخ کریں" else "Cancel", color = Slate600)
                }
            }
        )
    }
}
