package com.saleemkhan.myshopmanager.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.saleemkhan.myshopmanager.model.InventoryItem
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.BarcodeFeedbackHelper
import com.saleemkhan.myshopmanager.utils.FormatUtils
import java.util.concurrent.Executors

enum class ScannerTab {
    CAMERA,
    GALLERY,
    MANUAL
}

data class CameraLensItem(
    val id: String,
    val cameraIndex: Int,
    val titleEn: String,
    val titleUr: String,
    val subtitleEn: String,
    val subtitleUr: String,
    val icon: ImageVector,
    val targetZoom: Float? = null
)

@OptIn(ExperimentalGetImage::class, ExperimentalCamera2Interop::class)
@Composable
fun BarcodeScannerDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    inventory: List<InventoryItem>,
    onItemScanned: (InventoryItem) -> Unit,
    onQuickSale: ((InventoryItem) -> Unit)? = null,
    onQuickPurchase: ((InventoryItem) -> Unit)? = null,
    onAddNewItemWithBarcode: ((String) -> Unit)? = null,
    onManualCodeEntered: (String) -> Unit = {},
    isUrdu: Boolean = false,
    initialMode: ScannerTab = ScannerTab.CAMERA
) {
    if (!isOpen) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeTab by remember { mutableStateOf(initialMode) }
    var manualBarcode by remember { mutableStateOf("") }
    var matchedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var isUnmatched by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isScanningPaused by remember { mutableStateOf(false) }
    var galleryErrorMessage by remember { mutableStateOf<String?>(null) }

    // Multi-camera state: Strictly ONLY Back/Rear Cameras (Front is excluded)
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var availableBackCameras by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
    var selectedCameraIndex by remember { mutableIntStateOf(0) }
    var showCameraDropdown by remember { mutableStateOf(false) }
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var activeLensTitleEn by remember { mutableStateOf("Main Camera (1x)") }
    var activeLensTitleUr by remember { mutableStateOf("مین کیمرہ (1x)") }

    LaunchedEffect(cameraProviderFuture) {
        try {
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                // EXCLUDE FRONT CAMERA: Only filter rear/back cameras
                val backCams = provider.availableCameraInfos.filter {
                    it.lensFacing == CameraSelector.LENS_FACING_BACK
                }
                availableBackCameras = backCams
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("BarcodeScanner", "Failed to retrieve camera infos", e)
        }
    }

    // Helper to inspect camera characteristics for physical back cameras
    val detectedLensOptions = remember(availableBackCameras, isUrdu) {
        val list = mutableListOf<CameraLensItem>()

        // 1. Hardware Back Cameras detected
        availableBackCameras.forEachIndexed { index, camInfo ->
            var isWide = false
            var isMacro = false
            try {
                val cam2Info = Camera2CameraInfo.from(camInfo)
                val camId = cam2Info.cameraId
                val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val chars = camManager?.getCameraCharacteristics(camId)
                val focalLengths = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val minFocusDist = chars?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

                if (focalLengths != null && focalLengths.any { it < 3.0f }) {
                    isWide = true
                }
                if (minFocusDist != null && minFocusDist > 10.0f) {
                    isMacro = true
                }
            } catch (_: Exception) {}

            when {
                index == 0 -> {
                    list.add(
                        CameraLensItem(
                            id = "back_main",
                            cameraIndex = 0,
                            titleEn = "Main Camera (1x)",
                            titleUr = "مین کیمرہ (1x)",
                            subtitleEn = "Primary standard lens",
                            subtitleUr = "مرکزی ہائی ریزولوشن لینس",
                            icon = Icons.Default.CameraAlt,
                            targetZoom = 1.0f
                        )
                    )
                }
                isWide -> {
                    list.add(
                        CameraLensItem(
                            id = "back_wide_$index",
                            cameraIndex = index,
                            titleEn = "Ultra-Wide Camera",
                            titleUr = "الٹرا وائڈ کیمرہ",
                            subtitleEn = "Wide angle sensor",
                            subtitleUr = "چوڑا اینگل سینسر",
                            icon = Icons.Default.ZoomOutMap
                        )
                    )
                }
                isMacro -> {
                    list.add(
                        CameraLensItem(
                            id = "back_macro_$index",
                            cameraIndex = index,
                            titleEn = "Macro Camera",
                            titleUr = "میکرو کیمرہ",
                            subtitleEn = "Close-up detail lens",
                            subtitleUr = "قریبی باریک فوکس لینس",
                            icon = Icons.Default.CenterFocusStrong
                        )
                    )
                }
                else -> {
                    list.add(
                        CameraLensItem(
                            id = "back_aux_$index",
                            cameraIndex = index,
                            titleEn = "Rear Lens ${index + 1}",
                            titleUr = "پچھلا کیمرہ ${index + 1}",
                            subtitleEn = "Auxiliary back camera",
                            subtitleUr = "اضافی پچھلا لینس",
                            icon = Icons.Default.PhotoCamera
                        )
                    )
                }
            }
        }

        // 2. Optical Lens Presets for phones that control Ultra-Wide / Macro via Zoom
        // (Most Android phones with Ultra-Wide + Macro switch physical lenses via 0.5x / 2x zoom)
        list.add(
            CameraLensItem(
                id = "mode_ultrawide",
                cameraIndex = 0,
                titleEn = "Ultra-Wide Lens (0.5x)",
                titleUr = "الٹرا وائڈ لینس (0.5x)",
                subtitleEn = "Switches to physical ultra-wide sensor",
                subtitleUr = "الٹرا وائڈ سینسر پر سوئچ کریں",
                icon = Icons.Default.ZoomOutMap,
                targetZoom = 0.5f
            )
        )
        list.add(
            CameraLensItem(
                id = "mode_macro",
                cameraIndex = 0,
                titleEn = "Macro / Close-up Lens (2.0x)",
                titleUr = "میکرو لینس (2.0x قریبی)",
                subtitleEn = "Close-up focus for small barcodes",
                subtitleUr = "چھوٹے اور باریک بارکوڈز کے لیے",
                icon = Icons.Default.CenterFocusStrong,
                targetZoom = 2.0f
            )
        )

        list
    }

    // Check camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Common barcode processor
    fun processBarcodeResult(rawCode: String) {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) return

        BarcodeFeedbackHelper.playBeepAndVibrate(context)
        lastScannedBarcode = trimmed
        isScanningPaused = true

        val found = inventory.find {
            it.barcode.equals(trimmed, ignoreCase = true) ||
            it.sku.equals(trimmed, ignoreCase = true)
        }

        if (found != null) {
            matchedItem = found
            isUnmatched = false
            onItemScanned(found)
        } else {
            matchedItem = null
            isUnmatched = true
            onManualCodeEntered(trimmed)
        }
    }

    // Photo picker for scanning barcode from Gallery image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                galleryErrorMessage = null
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val first = barcodes.firstOrNull()?.rawValue
                        if (!first.isNullOrBlank()) {
                            processBarcodeResult(first)
                        } else {
                            galleryErrorMessage = if (isUrdu) {
                                "اس تصویر میں کوئی بارکوڈ نہیں ملا!"
                            } else {
                                "No barcode found in this image!"
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        galleryErrorMessage = if (isUrdu) {
                            "تصویر پڑھنے میں خرابی ہوئی"
                        } else {
                            "Failed to process image: ${e.localizedMessage ?: "Unknown error"}"
                        }
                    }
            } catch (e: Exception) {
                galleryErrorMessage = if (isUrdu) "تصویر لوڈ نہ ہو سکی" else "Could not load image"
            }
        }
    }

    // Reticle laser animation
    val infiniteTransition = rememberInfiniteTransition(label = "FullscreenLaser")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 210f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserOffset"
    )

    // Function to apply zoom safely
    fun applyZoom(ratio: Float) {
        currentZoomRatio = ratio
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value
            val minZ = zoomState?.minZoomRatio ?: 0.5f
            val maxZ = zoomState?.maxZoomRatio ?: 8.0f
            val clamped = ratio.coerceIn(minZ, maxZ)
            cam.cameraControl.setZoomRatio(clamped)
        }
    }

    // FULLSCREEN DIALOG
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. FULLSCREEN CAMERA PREVIEW (When in CAMERA tab)
                if (activeTab == ScannerTab.CAMERA) {
                    if (hasCameraPermission) {
                        key(selectedCameraIndex) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val cameraExecutor = Executors.newSingleThreadExecutor()

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                        val barcodeScanner = BarcodeScanning.getClient()
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null && !isScanningPaused) {
                                                        val image = InputImage.fromMediaImage(
                                                            mediaImage,
                                                            imageProxy.imageInfo.rotationDegrees
                                                        )
                                                        barcodeScanner.process(image)
                                                            .addOnSuccessListener { barcodes ->
                                                                val firstCode = barcodes.firstOrNull()?.rawValue
                                                                if (!firstCode.isNullOrBlank() && !isScanningPaused) {
                                                                    processBarcodeResult(firstCode)
                                                                }
                                                            }
                                                            .addOnFailureListener { e ->
                                                                Log.e("BarcodeScanner", "Scan error", e)
                                                            }
                                                            .addOnCompleteListener {
                                                                imageProxy.close()
                                                            }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }
                                            }

                                        val targetCameraInfo = availableBackCameras.getOrNull(selectedCameraIndex)
                                        val cameraSelector = if (targetCameraInfo != null) {
                                            CameraSelector.Builder()
                                                .addCameraFilter { list ->
                                                    val found = list.filter { it == targetCameraInfo }
                                                    if (found.isNotEmpty()) found else list.take(1)
                                                }
                                                .build()
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }

                                        try {
                                            cameraProvider.unbindAll()
                                            val cam = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageAnalysis
                                            )
                                            camera = cam
                                            if (isTorchOn) {
                                                cam.cameraControl.enableTorch(true)
                                            }
                                            if (currentZoomRatio != 1.0f) {
                                                cam.cameraControl.setZoomRatio(currentZoomRatio)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("BarcodeScanner", "Camera binding failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // RETICLE & SCANNING FRAME OVERLAY
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Viewfinder frame
                            Box(
                                modifier = Modifier
                                    .size(width = 270.dp, height = 210.dp)
                                    .border(
                                        BorderStroke(3.dp, DukanGreenPrimary),
                                        RoundedCornerShape(20.dp)
                                    )
                            ) {
                                // Animated Laser scanning line
                                if (!isScanningPaused) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .offset(y = laserOffsetY.dp)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color.Transparent, Color(0xFFEF4444), Color.Transparent)
                                                )
                                            )
                                    )
                                }
                            }

                            // Subtitle instruction under viewfinder
                            Text(
                                text = if (isUrdu) "بارکوڈ یا کیو آر کوڈ فریم کے اندر رکھیں" else "Align barcode or QR code inside frame",
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 36.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )

                            // Quick Lens Selector Chips (0.5x Ultra-Wide, 1x Main, 2x Macro)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 80.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple(0.5f, "0.5x Wide", "0.5x وائڈ"),
                                    Triple(1.0f, "1.0x Main", "1.0x مین"),
                                    Triple(2.0f, "2.0x Macro", "2.0x میکرو")
                                ).forEach { (zoom, labelEn, labelUr) ->
                                    val isSelected = kotlin.math.abs(currentZoomRatio - zoom) < 0.1f
                                    Surface(
                                        onClick = {
                                            applyZoom(zoom)
                                            activeLensTitleEn = when (zoom) {
                                                0.5f -> "Ultra-Wide Lens (0.5x)"
                                                2.0f -> "Macro Lens (2.0x)"
                                                else -> "Main Camera (1.0x)"
                                            }
                                            activeLensTitleUr = when (zoom) {
                                                0.5f -> "الٹرا وائڈ لینس (0.5x)"
                                                2.0f -> "میکرو لینس (2.0x)"
                                                else -> "مین کیمرہ (1.0x)"
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) DukanGreenPrimary else Color.Transparent,
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = if (isUrdu) labelUr else labelEn,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Camera Permission Missing UI
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Slate900.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Slate700),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Camera Permission",
                                        tint = DukanGreenPrimary,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = if (isUrdu) "بارکوڈ اسکین کے لیے کیمرہ کی اجازت درکار ہے" else "Camera permission is required to scan barcodes",
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            text = if (isUrdu) "کیمرہ کی اجازت دیں" else "Grant Camera Permission",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. GALLERY TAB CONTENT (Fullscreen)
                if (activeTab == ScannerTab.GALLERY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Slate900.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Pick Image",
                                    tint = DukanGreenPrimary,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = if (isUrdu) "فون کی گیلری سے بارکوڈ تصویر منتخب کریں" else "Select barcode image from gallery",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUrdu) "تصویر منتخب کریں" else "Choose Image",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                if (galleryErrorMessage != null) {
                                    Text(
                                        text = galleryErrorMessage!!,
                                        color = RedDanger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. MANUAL CODE ENTRY TAB (Fullscreen)
                if (activeTab == ScannerTab.MANUAL) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Slate900.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "ہاتھ سے بارکوڈ درج کریں" else "Enter Barcode Number Manually",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                OutlinedTextField(
                                    value = manualBarcode,
                                    onValueChange = { manualBarcode = it },
                                    label = { Text(if (isUrdu) "بارکوڈ / SKU نمبر" else "Barcode / SKU Number") },
                                    placeholder = { Text("e.g. 896400012345") },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Code", tint = DukanGreenPrimary)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedBorderColor = DukanGreenPrimary,
                                        unfocusedBorderColor = Slate600
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (manualBarcode.isNotBlank()) {
                                            processBarcodeResult(manualBarcode)
                                        }
                                    },
                                    enabled = manualBarcode.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Lookup", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUrdu) "بارکوڈ سرچ کریں" else "Lookup Barcode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. TOP CONTROLS BAR (Close, Lens Selection Dropdown, Flashlight Torch)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // CAMERA SELECTION DROPDOWN (ONLY REAR CAMERAS, FRONT EXCLUDED)
                    if (activeTab == ScannerTab.CAMERA && hasCameraPermission) {
                        Box {
                            Surface(
                                onClick = { showCameraDropdown = true },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, DukanGreenPrimary),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Camera Lens",
                                        tint = DukanGreenPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUrdu) activeLensTitleUr else activeLensTitleEn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Open Lens Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Material 3 Dropdown Menu for Camera / Lens Selection
                            DropdownMenu(
                                expanded = showCameraDropdown,
                                onDismissRequest = { showCameraDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E293B))
                                    .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(14.dp))
                                    .widthIn(min = 230.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "پچھلا کیمرہ و لینس منتخب کریں" else "Select Rear Camera / Lens",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DukanGreenPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                                HorizontalDivider(color = Color(0xFF334155))

                                detectedLensOptions.forEach { lens ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = lens.icon,
                                                        contentDescription = null,
                                                        tint = DukanGreenPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (isUrdu) lens.titleUr else lens.titleEn,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Text(
                                                    text = if (isUrdu) lens.subtitleUr else lens.subtitleEn,
                                                    fontSize = 10.sp,
                                                    color = Slate400,
                                                    modifier = Modifier.padding(start = 24.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            showCameraDropdown = false
                                            activeLensTitleEn = lens.titleEn
                                            activeLensTitleUr = lens.titleUr
                                            if (lens.cameraIndex != selectedCameraIndex) {
                                                selectedCameraIndex = lens.cameraIndex
                                            }
                                            if (lens.targetZoom != null) {
                                                applyZoom(lens.targetZoom)
                                            }
                                            isScanningPaused = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = if (isUrdu) "بارکوڈ اسکینر" else "Barcode Scanner",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Flashlight / Torch Button
                    if (activeTab == ScannerTab.CAMERA && hasCameraPermission) {
                        IconButton(
                            onClick = {
                                val next = !isTorchOn
                                isTorchOn = next
                                camera?.cameraControl?.enableTorch(next)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (isTorchOn) AmberWarning else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Torch",
                                tint = if (isTorchOn) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(38.dp))
                    }
                }

                // 5. BOTTOM BAR & MATCHED RESULTS (Floating above camera)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black)
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Result: Matched Product Card
                    if (matchedItem != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.5.dp, DukanGreenPrimary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = matchedItem!!.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Slate900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${matchedItem!!.category} • ${if (isUrdu) "اسٹاک:" else "Stock:"} ${matchedItem!!.quantity.toInt()} ${matchedItem!!.unit}",
                                            fontSize = 11.sp,
                                            color = Slate600
                                        )
                                    }
                                    Text(
                                        text = FormatUtils.formatCurrency(matchedItem!!.sellingPrice),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = DukanGreenHover
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (onQuickSale != null) {
                                        Button(
                                            onClick = {
                                                onQuickSale(matchedItem!!)
                                                onClose()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FlashOn, contentDescription = "Quick Sale", modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isUrdu) "فوری فروخت (Sale)" else "Quick Sale",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (onQuickPurchase != null) {
                                        Button(
                                            onClick = {
                                                onQuickPurchase(matchedItem!!)
                                                onClose()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.AddShoppingCart, contentDescription = "Purchase", modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isUrdu) "اسٹاک اضافہ (Buy)" else "Quick Buy",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isScanningPaused = false
                                            matchedItem = null
                                            isUnmatched = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Scan Again", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Result: Unmatched Barcode Card
                    if (isUnmatched && lastScannedBarcode != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFFECDD3))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "⚠️ یہ بارکوڈ اسٹاک میں موجود نہیں!" else "⚠️ Barcode not found in inventory!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = RedDanger
                                )
                                Text(
                                    text = "${if (isUrdu) "کوڈ:" else "Code:"} $lastScannedBarcode",
                                    fontSize = 12.sp,
                                    color = Slate800,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (onAddNewItemWithBarcode != null) {
                                        Button(
                                            onClick = {
                                                onAddNewItemWithBarcode(lastScannedBarcode!!)
                                                onClose()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isUrdu) "نیا پروڈکٹ درج کریں" else "Add New Item",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isScanningPaused = false
                                            isUnmatched = false
                                            lastScannedBarcode = null
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(text = if (isUrdu) "دوبارہ اسکین" else "Scan Again", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Tab Switcher: [Camera] | [Gallery] | [Manual]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple(ScannerTab.CAMERA, if (isUrdu) "کیمرہ" else "Camera", Icons.Default.CameraAlt),
                            Triple(ScannerTab.GALLERY, if (isUrdu) "گیلری" else "Gallery", Icons.Default.PhotoLibrary),
                            Triple(ScannerTab.MANUAL, if (isUrdu) "ہاتھ سے درج کریں" else "Manual", Icons.Default.Keyboard)
                        ).forEach { (tab, title, icon) ->
                            val isSelected = activeTab == tab
                            Surface(
                                onClick = {
                                    activeTab = tab
                                    if (tab == ScannerTab.CAMERA) {
                                        isScanningPaused = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) DukanGreenPrimary else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
