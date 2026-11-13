package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream

data class DecodedUpiQrData(
    val rawPayload: String,
    val upiId: String,
    val payeeName: String,
    val upiUri: String
)

object UpiQrCodeHelper {

    const val SAVED_QR_FILENAME = "custom_payment_qr.png"

    /**
     * Parses a raw UPI QR string (e.g. "upi://pay?pa=merchant@upi&pn=MD%20Finance...")
     * or a plain VPA ("merchant@upi") into structured UPI fields.
     */
    fun parseUpiPayload(rawText: String): DecodedUpiQrData? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("upi://", ignoreCase = true)) {
            val queryStart = trimmed.indexOf('?')
            val params = mutableMapOf<String, String>()
            if (queryStart != -1 && queryStart + 1 < trimmed.length) {
                val query = trimmed.substring(queryStart + 1)
                query.split('&').forEach { pair ->
                    val kv = pair.split('=', limit = 2)
                    if (kv.size == 2) {
                        val key = kv[0].trim().lowercase()
                        val value = try {
                            java.net.URLDecoder.decode(kv[1].trim(), "UTF-8")
                        } catch (_: Exception) {
                            kv[1].trim()
                        }
                        params[key] = value
                    }
                }
            }
            val pa = params["pa"].orEmpty()
            val pn = params["pn"].orEmpty()
            if (pa.isNotBlank()) {
                return DecodedUpiQrData(
                    rawPayload = trimmed,
                    upiId = pa,
                    payeeName = pn,
                    upiUri = trimmed
                )
            }
        }

        // Fallback: if the QR contains a direct VPA like "9440736893@ptyes"
        if (trimmed.contains("@") && !trimmed.contains(" ")) {
            val upiUri = "upi://pay?pa=$trimmed"
            return DecodedUpiQrData(
                rawPayload = trimmed,
                upiId = trimmed,
                payeeName = "",
                upiUri = upiUri
            )
        }

        return null
    }

    /**
     * Decodes a QR Bitmap using ZXing's MultiFormatReader.
     */
    fun decodeQrBitmap(bitmap: Bitmap): DecodedUpiQrData? {
        return try {
            val maxDim = 1024
            val workingBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val width = workingBitmap.width
            val height = workingBitmap.height
            val pixels = IntArray(width * height)
            workingBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            val rawText = result.text ?: return null
            parseUpiPayload(rawText) ?: DecodedUpiQrData(
                rawPayload = rawText,
                upiId = "",
                payeeName = "",
                upiUri = rawText
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a clean, borderless QR code Bitmap directly from a scanned QR payload string
     * using ZXing's QRCodeWriter so statements can paste the QR code directly without extra background clutter.
     */
    fun generateQrBitmapFromPayload(payload: String, sizePx: Int = 512): Bitmap? {
        val cleanPayload = payload.trim()
        if (cleanPayload.isEmpty()) return null
        return try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.MARGIN to 0
            )
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                cleanPayload,
                com.google.zxing.BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a camera-scanned QR code payload by generating a crisp QR PNG file in `filesDir/custom_payment_qr.png`
     * and returning both the saved File and decoded UPI metadata.
     */
    fun saveScannedQrPayload(context: Context, rawPayload: String): Pair<File?, DecodedUpiQrData?> {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return Pair(null, null)
        return try {
            val decoded = parseUpiPayload(trimmed) ?: DecodedUpiQrData(
                rawPayload = trimmed,
                upiId = "",
                payeeName = "",
                upiUri = trimmed
            )
            val qrBitmap = generateQrBitmapFromPayload(trimmed, 512) ?: return Pair(null, decoded)
            val destFile = File(context.filesDir, SAVED_QR_FILENAME)
            FileOutputStream(destFile).use { out ->
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val cacheFile = File(context.cacheDir, SAVED_QR_FILENAME)
            FileOutputStream(cacheFile).use { out ->
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Pair(destFile, decoded)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Loads and decodes a QR image from a content/file Uri, persists a clean PNG copy to
     * `filesDir/custom_payment_qr.png`, and returns both the saved File and any decoded UPI metadata.
     */
    fun saveAndDecodeQrFromUri(context: Context, uri: Uri): Pair<File?, DecodedUpiQrData?> {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return Pair(null, null)

            val decoded = decodeQrBitmap(bitmap)
            // If we decoded a QR payload, regenerate a clean borderless QR bitmap from the payload
            val finalBitmap = if (decoded != null && decoded.rawPayload.isNotBlank()) {
                generateQrBitmapFromPayload(decoded.rawPayload, 512) ?: bitmap
            } else {
                bitmap
            }

            val destFile = File(context.filesDir, SAVED_QR_FILENAME)
            FileOutputStream(destFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Pair(destFile, decoded)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Loads the saved custom payment QR bitmap if available in internal storage or SharedPreferences.
     */
    fun loadSavedQrBitmap(context: Context): Bitmap? {
        return try {
            val defaultFile = File(context.filesDir, SAVED_QR_FILENAME)
            if (defaultFile.exists() && defaultFile.length() > 0L) {
                BitmapFactory.decodeFile(defaultFile.absolutePath)?.let { return it }
            }
            val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
            val savedUriStr = prefs.getString("qr_image_uri", null)
            if (!savedUriStr.isNullOrBlank()) {
                val uri = Uri.parse(savedUriStr)
                if (uri.scheme == "file" || uri.scheme.isNullOrEmpty()) {
                    val path = uri.path ?: savedUriStr
                    val f = File(path)
                    if (f.exists()) {
                        return BitmapFactory.decodeFile(f.absolutePath)
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        return BitmapFactory.decodeStream(stream)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a generated Statement Bitmap to the device's public Downloads/MDFinance folder
     * (or internal files/statements fallback) and returns the user-facing path/status.
     */
    fun saveStatementToDownloads(
        context: Context,
        bitmap: Bitmap,
        customerName: String,
        subFolder: String = "MDFinance"
    ): String? {
        val cleanName = customerName.replace(Regex("[^a-zA-Z0-9]"), "_").ifBlank { "Customer" }
        val fileName = "Statement_${cleanName}_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    "Downloads/$subFolder/$fileName"
                } else {
                    null
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    subFolder
                )
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            }
        } catch (_: Exception) {
            try {
                val fallbackDir = File(context.filesDir, "saved_statements")
                fallbackDir.mkdirs()
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                fallbackFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }
}
