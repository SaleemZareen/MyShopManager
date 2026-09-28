package com.saleemkhan.myshopmanager.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saleemkhan.myshopmanager.ui.theme.*

private enum class PermissionStep {
    INTRO,
    COMPLETED
}

private data class PermissionItemUi(
    val key: String,
    val titleUrdu: String,
    val titleEn: String,
    val descUrdu: String,
    val descEn: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBgColor: Color,
    val permissionManifestKey: String?
)

@Composable
fun PermissionsSetupDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    isUrdu: Boolean
) {
    if (!isOpen) return

    val context = LocalContext.current
    var step by remember { mutableStateOf(PermissionStep.INTRO) }

    // Live permission states
    fun isPermissionGranted(permission: String?): Boolean {
        if (permission == null) return true
        if (permission == Manifest.permission.POST_NOTIFICATIONS) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    var micGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.RECORD_AUDIO)) }
    var cameraGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.CAMERA)) }
    var locationGranted by remember { mutableStateOf(isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var storageGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        )
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        )
    }

    fun refreshAllStatuses() {
        micGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
        cameraGranted = isPermissionGranted(Manifest.permission.CAMERA)
        locationGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        if (micGranted && cameraGranted && locationGranted && notificationsGranted) {
            step = PermissionStep.COMPLETED
        }
    }

    // Launchers for individual permissions
    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshAllStatuses()
    }

    // Launcher for bulk permissions
    val bulkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllStatuses()
        if (micGranted && cameraGranted && locationGranted && notificationsGranted) {
            step = PermissionStep.COMPLETED
        }
    }

    val permissionItems = remember {
        listOf(
            PermissionItemUi(
                key = "microphone",
                titleUrdu = "مائیکروفون (آواز سے انٹری)",
                titleEn = "Voice Entry / Microphone",
                descUrdu = "بول کر خودکار کھاتہ درج کرنے کے لیے",
                descEn = "Used for speech-to-text automated entries",
                icon = Icons.Default.Mic,
                iconColor = Color(0xFF2563EB),
                iconBgColor = Color(0xFFEFF6FF),
                permissionManifestKey = Manifest.permission.RECORD_AUDIO
            ),
            PermissionItemUi(
                key = "camera",
                titleUrdu = "کیمرہ (بارکوڈ اسکینر)",
                titleEn = "Camera / Barcode Scanner",
                descUrdu = "پروڈکٹس کے بارکوڈ اسکین کے لیے",
                descEn = "Used for product barcode scanning and attachments",
                icon = Icons.Default.CameraAlt,
                iconColor = Color(0xFF059669),
                iconBgColor = Color(0xFFECFDF5),
                permissionManifestKey = Manifest.permission.CAMERA
            ),
            PermissionItemUi(
                key = "location",
                titleUrdu = "لوکیشن (انوائس اسٹیمپ)",
                titleEn = "GPS Location / Geolocation",
                descUrdu = "رسیدوں پر دکان کے نقشے کے لیے",
                descEn = "Used to stamp shop locations on customer invoices",
                icon = Icons.Default.LocationOn,
                iconColor = Color(0xFFD97706),
                iconBgColor = Color(0xFFFEF3C7),
                permissionManifestKey = Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PermissionItemUi(
                key = "storage",
                titleUrdu = "مستقل اسٹوریج (آف لائن تحفظ)",
                titleEn = "Persistent Storage Data",
                descUrdu = "آف لائن دکان کا ڈیٹا فون میں محفوظ رکھنے کے لیے",
                descEn = "Guarantees shop database safety from browser clears",
                icon = Icons.Default.Storage,
                iconColor = Color(0xFF4F46E5),
                iconBgColor = Color(0xFFEEF2FF),
                permissionManifestKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ),
            PermissionItemUi(
                key = "notifications",
                titleUrdu = "پش نوٹیفیکیشنز (ادھار الرٹس)",
                titleEn = "Push Notification Alerts",
                descUrdu = "ادھار ادائیگی اور یاد دہانی کے الرٹس",
                descEn = "Notifies you for pending udhaar payments and reminders",
                icon = Icons.Default.Notifications,
                iconColor = Color(0xFF9333EA),
                iconBgColor = Color(0xFFFAF5FF),
                permissionManifestKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null
            )
        )
    }

    Dialog(
        onDismissRequest = {
            onFinish()
            onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0FDF4))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFD1FAE5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = Color(0xFF126A49),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isUrdu) "موبائل پرمیشنز کی باضابطہ تصدیق" else "Verified Android Permissions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                        Text(
                            text = if (isUrdu) "دکان مینیجر کی موبائل اسکرین پرمیشنز" else "Native permission status live check",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }

                    IconButton(
                        onClick = {
                            onFinish()
                            onClose()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate500,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Divider(color = Slate200, thickness = 0.5.dp)

                // Modal Body Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (step == PermissionStep.INTRO) {
                        // Notice banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Sparkles",
                                    tint = Color(0xFF126A49),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isUrdu)
                                        "دکان مینیجر کو آواز سے انٹری، بارکوڈ اسکیننگ اور نقشہ پرنٹنگ کے لیے باضابطہ اینڈرائیڈ پرمیشنز کی ضرورت ہے۔ ذیل میں ہر آئٹم کی اصل اینڈرائیڈ حالت دکھائی جا رہی ہے۔"
                                    else
                                        "Real physical Android settings status is shown live below. If any permission is denied, tap Allow or Settings to enable it in your phone.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF126A49),
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Permission Items List
                        permissionItems.forEach { item ->
                            val isGranted = when (item.key) {
                                "microphone" -> micGranted
                                "camera" -> cameraGranted
                                "location" -> locationGranted
                                "storage" -> storageGranted
                                "notifications" -> notificationsGranted
                                else -> false
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Slate200)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(item.iconBgColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.titleEn,
                                                tint = item.iconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isUrdu) item.titleUrdu else item.titleEn,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = if (isUrdu) item.descUrdu else item.descEn,
                                                fontSize = 10.5.sp,
                                                color = Slate600,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Status Badge or Allow Button
                                    if (isGranted) {
                                        Surface(
                                            color = Color(0xFFD1FAE5),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (isUrdu) "✓ فعال ہے" else "✓ Allowed",
                                                color = Color(0xFF065F46),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (item.permissionManifestKey != null) {
                                                    singleLauncher.launch(item.permissionManifestKey)
                                                } else {
                                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                text = if (isUrdu) "اجازت دیں" else "Allow",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // COMPLETED VIEW
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFD1FAE5), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = if (isUrdu) "اینڈرائیڈ پرمیشنز اوکے ہیں!" else "Permissions Fully Sync'd!",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (isUrdu)
                                    "تمام ضروری اجازتوں کی کارروائی باضابطہ طور پر مکمل ہو چکی ہے۔ اب مائیک ان پٹ، بارکوڈ اسکینر، کوآرڈینیٹس اور اطلاعات کا الرٹ سسٹم بالکل بہترین اور فزیکل اینڈرائیڈ رن ٹائم کے مطابق کام کرے گا۔"
                                else
                                    "Awesome! All required permissions have been verified. Shop Manager is fully authorized for microphone, camera, geolocation, and push notification access.",
                                fontSize = 12.sp,
                                color = Slate600,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // Bottom action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (step == PermissionStep.INTRO) {
                            OutlinedButton(
                                onClick = {
                                    onFinish()
                                    onClose()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Slate700
                                )
                            ) {
                                Text(
                                    text = if (isUrdu) "بعد میں کریں" else "Skip / Later",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val listToRequest = mutableListOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        listToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                                        listToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                                    } else {
                                        listToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    bulkLauncher.launch(listToRequest.toTypedArray())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "تمام اجازتیں دیں" else "Grant All Access",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    onFinish()
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "شروع کریں" else "Get Started",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
