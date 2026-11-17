package io.github.alghostzx.qrcrypt.prefs

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var previewUriBeforeOpening: Boolean
        get() = prefs.getBoolean(KEY_PREVIEW_URI, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PREVIEW_URI, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "qrcrypt_prefs"
        private const val KEY_PREVIEW_URI = "preview_uri_before_opening"
    }
}
