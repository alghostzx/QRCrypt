package io.github.alghostzx.qrcrypt.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

object QrCodeDecoder {

    private const val MAX_IMAGE_DIMENSION = 1600

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.ALSO_INVERTED to true
    )

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride

        val yBytes = ByteArray(width * height)
        if (rowStride == width) {
            buffer.rewind()
            buffer.get(yBytes, 0, yBytes.size)
        } else {
            val rowData = ByteArray(rowStride)
            buffer.rewind()
            for (row in 0 until height) {
                val bytesToRead = minOf(rowStride, buffer.remaining())
                if (bytesToRead <= 0) break
                buffer.get(rowData, 0, bytesToRead)
                System.arraycopy(rowData, 0, yBytes, row * width, minOf(width, bytesToRead))
            }
        }

        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        return decodeLuminanceSource(source)
    }

    fun decodeFromUri(context: Context, uri: Uri): String? {
        val bitmap = loadSampledBitmap(context, uri) ?: return null
        return try {
            decodeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeLuminanceSource(source)
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val reader = MultiFormatReader().apply { setHints(decodeHints) }

        // 1. Try HybridBinarizer (best for uneven lighting / photos)
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            reader.reset()
        } catch (_: Exception) {
            reader.reset()
        }

        // 2. Fallback to GlobalHistogramBinarizer (better for clean digital screenshots)
        try {
            return reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
        } catch (_: NotFoundException) {
            reader.reset()
        } catch (_: Exception) {
            reader.reset()
        }

        return null
    }

    private fun loadSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / sampleSize > MAX_IMAGE_DIMENSION || rawH / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }
}
