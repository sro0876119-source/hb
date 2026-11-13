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
     * Loads and decodes a QR image from a content/file Uri, persists a clean PNG copy to
     * `filesDir/custom_payment_qr.png`, and returns both the saved File and any decoded UPI metadata.
     */
    fun saveAndDecodeQrFromUri(context: Context, uri: Uri): Pair<File?, DecodedUpiQrData?> {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return Pair(null, null)

            val destFile = File(context.filesDir, SAVED_QR_FILENAME)
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val decoded = decodeQrBitmap(bitmap)
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
