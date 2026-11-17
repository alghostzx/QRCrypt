package io.github.alghostzx.qrcrypt.qr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QrExporter {

    private const val SHARED_DIR = "shared_qr"

    fun savePngToGallery(context: Context, bitmap: Bitmap, label: String? = null): Uri? {
        val fileName = buildFileName(label)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QRCrypt")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("PNG compression failed")
                }
            } ?: throw IllegalStateException("Could not open MediaStore output stream")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun sharePng(context: Context, bitmap: Bitmap, label: String? = null, chooserTitle: String): Boolean {
        return try {
            val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
            val file = File(dir, buildFileName(label))
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return false
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupSharedCache(context: Context) {
        val dir = File(context.cacheDir, SHARED_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun buildFileName(label: String?): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeLabel = label
            ?.trim()
            ?.replace(Regex("[^a-zA-Z0-9_-]+"), "_")
            ?.trim('_')
            ?.take(32)
            ?.takeIf { it.isNotEmpty() }

        return if (safeLabel != null) {
            "QRCrypt_${safeLabel}_$timestamp.png"
        } else {
            "QRCrypt_$timestamp.png"
        }
    }
}
