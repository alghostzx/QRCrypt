package io.github.alghostzx.qrcrypt.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alghostzx.qrcrypt.R
import io.github.alghostzx.qrcrypt.crypto.QRCryptEngine
import io.github.alghostzx.qrcrypt.prefs.AppPreferences
import io.github.alghostzx.qrcrypt.ui.components.DecryptContent
import io.github.alghostzx.qrcrypt.ui.theme.QRCryptTheme

class DecryptActivity : ComponentActivity() {

    private var currentUriState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val uri = extractQrUri(intent)
        if (uri == null) {
            Toast.makeText(this, getString(R.string.err_invalid_qrcrypt), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentUriState = uri

        val prefs = AppPreferences(this)

        setContent {
            QRCryptTheme {
                val activeUri = currentUriState
                if (activeUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { finish() }
                            )
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ElevatedCard(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* consume clicks inside card */ }
                                )
                        ) {
                            DecryptContent(
                                qrUri = activeUri,
                                previewUriBeforeOpening = prefs.previewUriBeforeOpening,
                                onDismiss = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = extractQrUri(intent)
        if (uri != null) {
            currentUriState = uri
        }
    }

    private fun extractQrUri(intent: Intent?): String? {
        val raw = intent?.dataString ?: return null
        return raw.takeIf { QRCryptEngine.isQRCryptUri(it) }
    }
}
