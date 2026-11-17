package io.github.alghostzx.qrcrypt

import android.app.Application
import io.github.alghostzx.qrcrypt.qr.QrExporter

class QRCryptApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QrExporter.cleanupSharedCache(this)
    }
}
