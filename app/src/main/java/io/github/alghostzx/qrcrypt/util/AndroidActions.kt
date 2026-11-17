package io.github.alghostzx.qrcrypt.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.github.alghostzx.qrcrypt.payload.WifiPayload
import io.github.alghostzx.qrcrypt.payload.WifiSecurity

object AndroidActions {

    /**
     * Launches ACTION_VIEW for the given URI. Returns true if an external app handled it,
     * false if no handler is available or if it points back to QRCrypt itself.
     */
    fun openUri(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // Retry without BROWSABLE category for schemes like otpauth:// or custom apps
            try {
                val uri = Uri.parse(uriString)
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun shareText(context: Context, text: String, chooserTitle: String): Boolean {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openWifiSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * On Android 11+ (API 30+), opens the native Android system dialog to save/connect to the Wi-Fi
     * network using ACTION_WIFI_ADD_NETWORKS without requiring any runtime permissions.
     * On Android 10 (API 29) or unsupported devices, falls back to opening system Wi-Fi settings.
     */
    fun promptSaveWifiNetwork(context: Context, wifi: WifiPayload): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.security != WifiSecurity.WEP) {
            try {
                val builder = WifiNetworkSuggestion.Builder()
                    .setSsid(wifi.ssid)
                    .setIsHiddenSsid(wifi.hidden)

                if (wifi.security == WifiSecurity.WPA && wifi.password.isNotEmpty()) {
                    builder.setWpa2Passphrase(wifi.password)
                }

                val suggestions = arrayListOf(builder.build())
                val bundle = Bundle().apply {
                    putParcelableArrayList(Settings.EXTRA_WIFI_NETWORK_LIST, suggestions)
                }
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putExtras(bundle)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Fall through to Wi-Fi settings
            }
        }
        return openWifiSettings(context)
    }
}
