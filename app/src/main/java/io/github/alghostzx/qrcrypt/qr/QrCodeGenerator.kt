package io.github.alghostzx.qrcrypt.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    private const val QR_SIZE_PX = 1024
    private const val QUIET_ZONE_MODULES = 4

    fun generateBitmap(content: String, visibleLabel: String? = null): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES
        )

        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            hints
        )

        val matrixW = bitMatrix.width
        val matrixH = bitMatrix.height
        val scale = (QR_SIZE_PX / maxOf(matrixW, matrixH)).coerceAtLeast(1)
        val scaledW = matrixW * scale
        val scaledH = matrixH * scale
        val qrCanvasSize = maxOf(QR_SIZE_PX, maxOf(scaledW, scaledH))
        val offsetX = (qrCanvasSize - scaledW) / 2
        val offsetY = (qrCanvasSize - scaledH) / 2

        val cleanLabel = visibleLabel?.trim()?.takeIf { it.isNotEmpty() }

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 44f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val labelHorizontalPadding = 64
        val labelMaxWidth = qrCanvasSize - (labelHorizontalPadding * 2)
        val staticLayout = cleanLabel?.let { text ->
            StaticLayout.Builder.obtain(text, 0, text.length, labelPaint, labelMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setIncludePad(false)
                .build()
        }

        val extraHeight = if (staticLayout != null) {
            staticLayout.height + 72
        } else {
            0
        }

        val totalHeight = qrCanvasSize + extraHeight
        val bitmap = Bitmap.createBitmap(qrCanvasSize, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val modulePaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
            style = Paint.Style.FILL
        }

        for (y in 0 until matrixH) {
            val top = (offsetY + y * scale).toFloat()
            val bottom = top + scale
            for (x in 0 until matrixW) {
                if (bitMatrix.get(x, y)) {
                    val left = (offsetX + x * scale).toFloat()
                    canvas.drawRect(left, top, left + scale, bottom, modulePaint)
                }
            }
        }

        if (staticLayout != null) {
            canvas.save()
            canvas.translate(
                labelHorizontalPadding.toFloat(),
                (qrCanvasSize - 12).toFloat()
            )
            staticLayout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }
}
