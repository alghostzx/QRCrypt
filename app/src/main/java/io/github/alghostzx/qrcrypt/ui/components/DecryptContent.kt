package io.github.alghostzx.qrcrypt.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.alghostzx.qrcrypt.R
import io.github.alghostzx.qrcrypt.crypto.QRCryptEngine
import io.github.alghostzx.qrcrypt.crypto.QRCryptException
import io.github.alghostzx.qrcrypt.payload.InspectedPayload
import io.github.alghostzx.qrcrypt.payload.PayloadInspector
import io.github.alghostzx.qrcrypt.payload.WifiSecurity
import io.github.alghostzx.qrcrypt.util.AndroidActions
import io.github.alghostzx.qrcrypt.util.SensitiveClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DecryptContent(
    qrUri: String,
    previewUriBeforeOpening: Boolean,
    onDismiss: () -> Unit,
    onRewrapPlaintext: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var password by remember(qrUri) { mutableStateOf("") }
    var isDecrypting by remember(qrUri) { mutableStateOf(false) }
    var errorMessage by remember(qrUri) { mutableStateOf<String?>(null) }
    var decryptedPayload by remember(qrUri) { mutableStateOf<InspectedPayload?>(null) }
    var noAppFallback by remember(qrUri) { mutableStateOf(false) }

    LaunchedEffect(qrUri) {
        if (decryptedPayload == null) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore if focus isn't ready yet
            }
        }
    }

    fun submitDecrypt() {
        if (isDecrypting) return
        if (password.isEmpty()) {
            errorMessage = context.getString(R.string.err_empty_password)
            return
        }
        errorMessage = null
        isDecrypting = true
        val passwordChars = password.toCharArray()

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    QRCryptEngine.decrypt(
                        qrUri = qrUri,
                        password = passwordChars,
                        clearPasswordAfterUse = true
                    )
                }
            }
            isDecrypting = false
            result.onSuccess { plaintext ->
                password = ""
                val inspected = PayloadInspector.inspect(plaintext)
                if (inspected is InspectedPayload.ActionableUri &&
                    !previewUriBeforeOpening &&
                    inspected.scheme != QRCryptEngine.SCHEME
                ) {
                    val opened = AndroidActions.openUri(context, inspected.uri)
                    if (opened) {
                        onDismiss()
                    } else {
                        noAppFallback = true
                        decryptedPayload = inspected
                    }
                } else {
                    decryptedPayload = inspected
                }
            }.onFailure { err ->
                errorMessage = when (err) {
                    is QRCryptException.WrongPasswordOrCorrupted ->
                        context.getString(R.string.err_wrong_password)
                    is QRCryptException.UnsupportedVersion ->
                        context.getString(R.string.err_unsupported_version)
                    is QRCryptException.InvalidFormat ->
                        context.getString(R.string.err_invalid_qrcrypt)
                    else -> context.getString(R.string.err_wrong_password)
                }
            }
        }
    }

    val payload = decryptedPayload
    if (payload == null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.decrypt_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Text(
                text = stringResource(R.string.decrypt_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PasswordTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = stringResource(R.string.field_unlock_password),
                enabled = !isDecrypting,
                isError = errorMessage != null,
                imeAction = ImeAction.Done,
                onImeAction = { submitDecrypt() },
                modifier = Modifier.focusRequester(focusRequester)
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isDecrypting
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submitDecrypt() },
                    enabled = !isDecrypting && password.isNotEmpty()
                ) {
                    if (isDecrypting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_decrypting))
                    } else {
                        Text(stringResource(R.string.action_decrypt))
                    }
                }
            }
        }
    } else {
        DecryptedResultView(
            payload = payload,
            noAppFallback = noAppFallback,
            onNoAppFallback = { noAppFallback = true },
            onDismiss = onDismiss,
            onRewrapPlaintext = onRewrapPlaintext,
            modifier = modifier
        )
    }
}

@Composable
private fun DecryptedResultView(
    payload: InspectedPayload,
    noAppFallback: Boolean,
    onNoAppFallback: () -> Unit,
    onDismiss: () -> Unit,
    onRewrapPlaintext: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (payload) {
            is InspectedPayload.Wifi -> {
                var showWifiPassword by remember { mutableStateOf(false) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.decrypt_result_wifi),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LabeledInfoRow(
                            label = stringResource(R.string.wifi_label_ssid),
                            value = payload.wifi.ssid
                        )
                        LabeledInfoRow(
                            label = stringResource(R.string.wifi_label_security),
                            value = when (payload.wifi.security) {
                                WifiSecurity.WPA -> stringResource(R.string.wifi_security_wpa)
                                WifiSecurity.WEP -> stringResource(R.string.wifi_security_wep)
                                WifiSecurity.OPEN -> stringResource(R.string.wifi_security_open)
                            }
                        )
                        if (payload.wifi.hidden) {
                            LabeledInfoRow(
                                label = stringResource(R.string.wifi_label_hidden),
                                value = stringResource(R.string.yes)
                            )
                        }
                        if (payload.wifi.security != WifiSecurity.OPEN) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.wifi_label_password),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = if (showWifiPassword) {
                                                payload.wifi.password
                                            } else {
                                                "•".repeat(payload.wifi.password.length.coerceIn(8, 16))
                                            },
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                                IconButton(onClick = { showWifiPassword = !showWifiPassword }) {
                                    Icon(
                                        imageVector = if (showWifiPassword) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = stringResource(R.string.action_toggle_password)
                                    )
                                }
                            }
                        } else {
                            LabeledInfoRow(
                                label = stringResource(R.string.wifi_label_password),
                                value = stringResource(R.string.wifi_open_none)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        AndroidActions.promptSaveWifiNetwork(context, payload.wifi)
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_wifi_add_sent),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_connect_wifi))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (payload.wifi.password.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                SensitiveClipboard.copySensitive(context, "Wi-Fi", payload.wifi.password)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_copied_sensitive),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_copy_password))
                        }
                    }
                    OutlinedButton(
                        onClick = { AndroidActions.openWifiSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wi-Fi")
                    }
                }
            }

            is InspectedPayload.ActionableUri -> {
                val totp = payload.totpInfo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (totp != null) Icons.Outlined.Key else Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (totp != null) R.string.decrypt_result_totp else R.string.decrypt_result_uri
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                if (noAppFallback) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.msg_no_app_for_uri),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (totp != null) {
                            if (totp.issuer.isNotBlank()) {
                                LabeledInfoRow(stringResource(R.string.field_totp_issuer), totp.issuer)
                            }
                            if (totp.account.isNotBlank()) {
                                LabeledInfoRow(stringResource(R.string.field_totp_account), totp.account)
                            }
                            LabeledInfoRow(
                                label = stringResource(R.string.field_totp_algorithm),
                                value = "${totp.algorithm.name} · ${totp.digits}d · ${totp.period}s"
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = payload.uri,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }
                }

                if (!noAppFallback && payload.scheme != QRCryptEngine.SCHEME) {
                    Button(
                        onClick = {
                            val ok = AndroidActions.openUri(context, payload.uri)
                            if (ok) {
                                onDismiss()
                            } else {
                                onNoAppFallback()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            SensitiveClipboard.copySensitive(context, "QRCrypt", payload.uri)
                            Toast.makeText(
                                context,
                                context.getString(R.string.msg_copied_sensitive),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                    OutlinedButton(
                        onClick = {
                            AndroidActions.shareText(
                                context,
                                payload.uri,
                                context.getString(R.string.action_share)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share))
                    }
                }
            }

            is InspectedPayload.PlainText -> {
                var monospace by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.decrypt_result_text),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                FilterChip(
                    selected = monospace,
                    onClick = { monospace = !monospace },
                    label = { Text(stringResource(R.string.action_toggle_monospace)) }
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 260.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = payload.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
                            ),
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            SensitiveClipboard.copySensitive(context, "QRCrypt", payload.text)
                            Toast.makeText(
                                context,
                                context.getString(R.string.msg_copied_sensitive),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                    FilledTonalButton(
                        onClick = {
                            AndroidActions.shareText(
                                context,
                                payload.text,
                                context.getString(R.string.action_share)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share))
                    }
                }
            }
        }

        if (onRewrapPlaintext != null) {
            OutlinedButton(
                onClick = { onRewrapPlaintext(payload.raw) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_rewrap))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun LabeledInfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
