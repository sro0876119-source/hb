package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCameraScannerDialog(
    onDismiss: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF059669).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Scan UPI QR Code",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Point camera at your GPay / PhonePe / Paytm QR",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .size(36.dp)
                            .testTag("close_qr_scanner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Scanner",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Camera Preview Area
                if (hasCameraPermission) {
                    var torchEnabled by remember { mutableStateOf(false) }
                    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
                    val hasScanned = remember { AtomicBoolean(false) }
                    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

                    DisposableEffect(Unit) {
                        onDispose {
                            try {
                                analysisExecutor.shutdown()
                            } catch (_: Exception) {}
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color(0xFF10B981), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val qrReader = MultiFormatReader().apply {
                                            setHints(
                                                mapOf(
                                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                                    DecodeHintType.TRY_HARDER to true,
                                                    DecodeHintType.CHARACTER_SET to "UTF-8"
                                                )
                                            )
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                    processQrImageProxy(
                                                        imageProxy = imageProxy,
                                                        reader = qrReader,
                                                        onDetected = { rawQrText ->
                                                            if (hasScanned.compareAndSet(false, true)) {
                                                                previewView.post {
                                                                    onQrCodeScanned(rawQrText)
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                        cameraProvider.unbindAll()
                                        boundCamera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )

                        // Viewfinder Guide Box
                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .border(
                                    width = 3.dp,
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )

                        // Torch Toggle Button
                        if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = {
                                    torchEnabled = !torchEnabled
                                    boundCamera?.cameraControl?.enableTorch(torchEnabled)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flash",
                                    tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White
                                )
                            }
                        }
                    }
                } else {
                    // Permission Request Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Camera Permission Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Allow camera access to scan your UPI QR code and automatically configure your UPI ID and statement QR.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Grant Camera Permission", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom Footer Instructions
                Text(
                    text = "Scanning automatically extracts your UPI ID, generates a clean QR code for statements, and syncs it to Cloud.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

private fun processQrImageProxy(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
    onDetected: (String) -> Unit
) {
    try {
        val plane = imageProxy.planes.firstOrNull()
        if (plane != null) {
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val width = imageProxy.width
            val height = imageProxy.height
            val source = PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decodeWithState(binaryBitmap)
                val text = result.text?.trim()
                if (!text.isNullOrEmpty()) {
                    onDetected(text)
                }
            } catch (_: Exception) {
                // Also try rotated 90 degrees for portrait camera sensors
                try {
                    val rotatedData = ByteArray(data.size)
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val newIdx = x * height + (height - y - 1)
                            val oldIdx = y * width + x
                            if (newIdx in rotatedData.indices && oldIdx in data.indices) {
                                rotatedData[newIdx] = data[oldIdx]
                            }
                        }
                    }
                    val rotatedSource = PlanarYUVLuminanceSource(
                        rotatedData,
                        height,
                        width,
                        0,
                        0,
                        height,
                        width,
                        false
                    )
                    val rotatedResult = reader.decodeWithState(BinaryBitmap(HybridBinarizer(rotatedSource)))
                    val rotatedText = rotatedResult.text?.trim()
                    if (!rotatedText.isNullOrEmpty()) {
                        onDetected(rotatedText)
                    }
                } catch (_: Exception) {}
            } finally {
                reader.reset()
            }
        }
    } catch (_: Exception) {
    } finally {
        imageProxy.close()
    }
}
