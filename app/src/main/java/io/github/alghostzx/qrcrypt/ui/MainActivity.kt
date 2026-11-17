package io.github.alghostzx.qrcrypt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.alghostzx.qrcrypt.BuildConfig
import io.github.alghostzx.qrcrypt.R
import io.github.alghostzx.qrcrypt.crypto.QRCryptEngine
import io.github.alghostzx.qrcrypt.crypto.QRCryptException
import io.github.alghostzx.qrcrypt.payload.InspectedPayload
import io.github.alghostzx.qrcrypt.payload.PayloadInspector
import io.github.alghostzx.qrcrypt.payload.TotpAlgorithm
import io.github.alghostzx.qrcrypt.payload.TotpPayload
import io.github.alghostzx.qrcrypt.payload.WifiPayload
import io.github.alghostzx.qrcrypt.payload.WifiSecurity
import io.github.alghostzx.qrcrypt.prefs.AppPreferences
import io.github.alghostzx.qrcrypt.qr.QrCodeDecoder
import io.github.alghostzx.qrcrypt.qr.QrCodeGenerator
import io.github.alghostzx.qrcrypt.ui.components.CameraScannerView
import io.github.alghostzx.qrcrypt.ui.components.DecryptContent
import io.github.alghostzx.qrcrypt.ui.components.PasswordTextField
import io.github.alghostzx.qrcrypt.ui.components.QrResultDialog
import io.github.alghostzx.qrcrypt.ui.theme.QRCryptTheme
import io.github.alghostzx.qrcrypt.util.AndroidActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab { CREATE, SCAN }
private enum class TemplateType { NOTE, TOTP, WIFI, URI }

private data class GeneratedQrState(
    val bitmap: Bitmap,
    val visibleLabel: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val prefs = AppPreferences(this)

        setContent {
            QRCryptTheme {
                QRCryptMainScreen(prefs = prefs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRCryptMainScreen(prefs: AppPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(MainTab.CREATE) }
    var showSettings by remember { mutableStateOf(false) }
    var previewUriSetting by remember { mutableStateOf(prefs.previewUriBeforeOpening) }

    var generatedQr by remember { mutableStateOf<GeneratedQrState?>(null) }
    var inlineDecryptUri by remember { mutableStateOf<String?>(null) }
    var scannedRawPayload by remember { mutableStateOf<String?>(null) }

    fun generateEncryptedQr(
        plaintext: String,
        password: String,
        visibleLabel: String,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onDone: () -> Unit
    ) {
        onStart()
        val passwordChars = password.toCharArray()
        val cleanLabel = visibleLabel.trim().takeIf { it.isNotEmpty() }

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val uri = QRCryptEngine.encrypt(
                        plaintext = plaintext,
                        password = passwordChars,
                        clearPasswordAfterUse = true
                    )
                    val bitmap = QrCodeGenerator.generateBitmap(uri, cleanLabel)
                    GeneratedQrState(bitmap = bitmap, visibleLabel = cleanLabel)
                }
            }
            onDone()
            result.onSuccess { qrState ->
                generatedQr = qrState
            }.onFailure { err ->
                val msg = when (err) {
                    is QRCryptException.PayloadTooLarge ->
                        context.getString(R.string.err_payload_too_large)
                    else -> err.message ?: context.getString(R.string.err_invalid_qrcrypt)
                }
                onError(msg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeTab == MainTab.CREATE,
                    onClick = { activeTab = MainTab.CREATE },
                    icon = { Icon(Icons.Outlined.AddBox, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_create)) }
                )
                NavigationBarItem(
                    selected = activeTab == MainTab.SCAN,
                    onClick = { activeTab = MainTab.SCAN },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_scan)) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when (activeTab) {
                MainTab.CREATE -> CreateTabContent(
                    onEncryptRequest = ::generateEncryptedQr
                )
                MainTab.SCAN -> ScanAndWrapTabContent(
                    scannedRaw = scannedRawPayload,
                    onScannedRawChange = { scannedRawPayload = it },
                    onDecryptExisting = { uri -> inlineDecryptUri = uri },
                    onEncryptRequest = ::generateEncryptedQr
                )
            }
        }
    }

    generatedQr?.let { qrState ->
        QrResultDialog(
            bitmap = qrState.bitmap,
            visibleLabel = qrState.visibleLabel,
            onDismiss = {
                generatedQr = null
            }
        )
    }

    inlineDecryptUri?.let { uri ->
        Dialog(
            onDismissRequest = { inlineDecryptUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                DecryptContent(
                    qrUri = uri,
                    previewUriBeforeOpening = previewUriSetting,
                    onDismiss = { inlineDecryptUri = null },
                    onRewrapPlaintext = { innerPlaintext ->
                        inlineDecryptUri = null
                        scannedRawPayload = innerPlaintext
                        activeTab = MainTab.SCAN
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            previewUriBeforeOpening = previewUriSetting,
            onPreviewUriChanged = { enabled ->
                previewUriSetting = enabled
                prefs.previewUriBeforeOpening = enabled
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun CreateTabContent(
    onEncryptRequest: (
        plaintext: String,
        password: String,
        visibleLabel: String,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onDone: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current

    var selectedTemplate by remember { mutableStateOf(TemplateType.NOTE) }

    // Note template state
    var noteText by remember { mutableStateOf("") }

    // TOTP template state
    var totpIssuer by remember { mutableStateOf("") }
    var totpAccount by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var totpShowAdvanced by remember { mutableStateOf(false) }
    var totpAlgorithm by remember { mutableStateOf(TotpAlgorithm.SHA1) }
    var totpDigits by remember { mutableIntStateOf(6) }
    var totpPeriod by remember { mutableIntStateOf(30) }

    // Wi-Fi template state
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf(WifiSecurity.WPA) }
    var wifiHidden by remember { mutableStateOf(false) }

    // URI template state
    var uriText by remember { mutableStateOf("") }

    // Shared encryption state
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visibleLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isEncrypting by remember { mutableStateOf(false) }

    fun validateAndEncrypt() {
        if (isEncrypting) return
        errorMessage = null

        val plaintext: String = when (selectedTemplate) {
            TemplateType.NOTE -> {
                if (noteText.isBlank()) {
                    errorMessage = context.getString(R.string.err_empty_content)
                    return
                }
                noteText
            }
            TemplateType.TOTP -> {
                if (totpSecret.isBlank()) {
                    errorMessage = context.getString(R.string.err_empty_totp_secret)
                    return
                }
                if (!TotpPayload.isValidBase32Secret(totpSecret)) {
                    errorMessage = context.getString(R.string.err_invalid_totp_secret)
                    return
                }
                TotpPayload(
                    issuer = totpIssuer,
                    account = totpAccount,
                    secret = totpSecret,
                    algorithm = totpAlgorithm,
                    digits = totpDigits,
                    period = totpPeriod
                ).toOtpAuthUri()
            }
            TemplateType.WIFI -> {
                if (wifiSsid.isBlank()) {
                    errorMessage = context.getString(R.string.err_empty_ssid)
                    return
                }
                WifiPayload(
                    ssid = wifiSsid.trim(),
                    password = if (wifiSecurity == WifiSecurity.OPEN) "" else wifiPassword,
                    security = wifiSecurity,
                    hidden = wifiHidden
                ).toQrString()
            }
            TemplateType.URI -> {
                val trimmed = uriText.trim()
                if (!PayloadInspector.isValidUriInput(trimmed)) {
                    errorMessage = context.getString(R.string.err_invalid_uri)
                    return
                }
                trimmed
            }
        }

        if (password.isEmpty()) {
            errorMessage = context.getString(R.string.err_empty_password)
            return
        }
        if (password != confirmPassword) {
            errorMessage = context.getString(R.string.err_password_mismatch)
            return
        }

        val pwdSnapshot = password
        onEncryptRequest(
            plaintext,
            pwdSnapshot,
            visibleLabel,
            { err -> errorMessage = err },
            { isEncrypting = true },
            {
                isEncrypting = false
                password = ""
                confirmPassword = ""
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Template chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTemplate == TemplateType.NOTE,
                onClick = { selectedTemplate = TemplateType.NOTE; errorMessage = null },
                label = { Text(stringResource(R.string.template_note)) },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = selectedTemplate == TemplateType.TOTP,
                onClick = { selectedTemplate = TemplateType.TOTP; errorMessage = null },
                label = { Text(stringResource(R.string.template_totp)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = selectedTemplate == TemplateType.WIFI,
                onClick = { selectedTemplate = TemplateType.WIFI; errorMessage = null },
                label = { Text(stringResource(R.string.template_wifi)) },
                leadingIcon = { Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = selectedTemplate == TemplateType.URI,
                onClick = { selectedTemplate = TemplateType.URI; errorMessage = null },
                label = { Text(stringResource(R.string.template_uri)) },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Template fields
        when (selectedTemplate) {
            TemplateType.NOTE -> {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it; errorMessage = null },
                    label = { Text(stringResource(R.string.field_note_label)) },
                    placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TemplateType.TOTP -> {
                OutlinedTextField(
                    value = totpIssuer,
                    onValueChange = { totpIssuer = it; errorMessage = null },
                    label = { Text(stringResource(R.string.field_totp_issuer)) },
                    placeholder = { Text(stringResource(R.string.field_totp_issuer_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = totpAccount,
                    onValueChange = { totpAccount = it; errorMessage = null },
                    label = { Text(stringResource(R.string.field_totp_account)) },
                    placeholder = { Text(stringResource(R.string.field_totp_account_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordTextField(
                    value = totpSecret,
                    onValueChange = { totpSecret = it; errorMessage = null },
                    label = stringResource(R.string.field_totp_secret),
                    imeAction = ImeAction.Next
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { totpShowAdvanced = !totpShowAdvanced }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.field_totp_advanced),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Icon(
                                imageVector = if (totpShowAdvanced) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = totpShowAdvanced) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.field_totp_algorithm),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TotpAlgorithm.entries.forEach { alg ->
                                        FilterChip(
                                            selected = totpAlgorithm == alg,
                                            onClick = { totpAlgorithm = alg },
                                            label = { Text(alg.name) }
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.field_totp_digits),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(6, 8).forEach { d ->
                                        FilterChip(
                                            selected = totpDigits == d,
                                            onClick = { totpDigits = d },
                                            label = { Text("$d") }
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.field_totp_period),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(30, 60).forEach { p ->
                                        FilterChip(
                                            selected = totpPeriod == p,
                                            onClick = { totpPeriod = p },
                                            label = { Text("${p}s") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TemplateType.WIFI -> {
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it; errorMessage = null },
                    label = { Text(stringResource(R.string.field_wifi_ssid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.field_wifi_security),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = wifiSecurity == WifiSecurity.WPA,
                        onClick = { wifiSecurity = WifiSecurity.WPA },
                        label = { Text(stringResource(R.string.wifi_security_wpa)) }
                    )
                    FilterChip(
                        selected = wifiSecurity == WifiSecurity.WEP,
                        onClick = { wifiSecurity = WifiSecurity.WEP },
                        label = { Text(stringResource(R.string.wifi_security_wep)) }
                    )
                    FilterChip(
                        selected = wifiSecurity == WifiSecurity.OPEN,
                        onClick = { wifiSecurity = WifiSecurity.OPEN },
                        label = { Text(stringResource(R.string.wifi_security_open)) }
                    )
                }

                if (wifiSecurity != WifiSecurity.OPEN) {
                    PasswordTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it; errorMessage = null },
                        label = stringResource(R.string.field_wifi_password),
                        imeAction = ImeAction.Next
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { wifiHidden = !wifiHidden }
                ) {
                    Checkbox(
                        checked = wifiHidden,
                        onCheckedChange = { wifiHidden = it }
                    )
                    Text(stringResource(R.string.field_wifi_hidden))
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { AndroidActions.openWifiSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_open_wifi_settings))
                        }
                    }
                }
            }

            TemplateType.URI -> {
                OutlinedTextField(
                    value = uriText,
                    onValueChange = { uriText = it; errorMessage = null },
                    label = { Text(stringResource(R.string.field_uri_label)) },
                    placeholder = { Text(stringResource(R.string.field_uri_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HorizontalDivider()

        EncryptionFormSection(
            password = password,
            onPasswordChange = { password = it; errorMessage = null },
            confirmPassword = confirmPassword,
            onConfirmPasswordChange = { confirmPassword = it; errorMessage = null },
            visibleLabel = visibleLabel,
            onVisibleLabelChange = { visibleLabel = it },
            errorMessage = errorMessage,
            isEncrypting = isEncrypting,
            onSubmit = { validateAndEncrypt() }
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScanAndWrapTabContent(
    scannedRaw: String?,
    onScannedRawChange: (String?) -> Unit,
    onDecryptExisting: (String) -> Unit,
    onEncryptRequest: (
        plaintext: String,
        password: String,
        visibleLabel: String,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onDone: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cameraActive by remember { mutableStateOf(false) }
    var permissionDeniedMsg by remember { mutableStateOf(false) }

    var password by remember(scannedRaw) { mutableStateOf("") }
    var confirmPassword by remember(scannedRaw) { mutableStateOf("") }
    var visibleLabel by remember(scannedRaw) { mutableStateOf("") }
    var errorMessage by remember(scannedRaw) { mutableStateOf<String?>(null) }
    var isEncrypting by remember(scannedRaw) { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMsg = false
            cameraActive = true
        } else {
            permissionDeniedMsg = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching { QrCodeDecoder.decodeFromUri(context, uri) }
            }
            decoded.onSuccess { text ->
                if (text != null) {
                    cameraActive = false
                    onScannedRawChange(text)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_no_qr_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_image_read_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (cameraActive) {
            CameraScannerView(
                onQrDetected = { raw ->
                    cameraActive = false
                    onScannedRawChange(raw)
                },
                onCancel = { cameraActive = false }
            )
        } else if (scannedRaw == null) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.scan_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (permissionDeniedMsg) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_denied),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val hasCamera = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCamera) {
                        permissionDeniedMsg = false
                        cameraActive = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.action_scan_camera))
            }

            FilledTonalButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.action_pick_gallery))
            }

            Text(
                text = stringResource(R.string.camera_permission_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (QRCryptEngine.isQRCryptUri(scannedRaw)) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.scanned_already_encrypted_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.scanned_already_encrypted_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onDecryptExisting(scannedRaw) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_decrypt_now))
                    }
                    OutlinedButton(
                        onClick = { onScannedRawChange(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_scan_another))
                    }
                }
            }
        } else {
            val inspected = remember(scannedRaw) { PayloadInspector.inspect(scannedRaw) }
            val detectedLabel = when (inspected) {
                is InspectedPayload.Wifi -> stringResource(R.string.type_wifi)
                is InspectedPayload.ActionableUri -> if (inspected.totpInfo != null) {
                    stringResource(R.string.type_totp)
                } else {
                    stringResource(R.string.type_uri)
                }
                is InspectedPayload.PlainText -> stringResource(R.string.type_text)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.scanned_wrap_title),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { onScannedRawChange(null) }) {
                    Text(stringResource(R.string.action_scan_another))
                }
            }

            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.scanned_payload_badge, detectedLabel)) },
                leadingIcon = {
                    Icon(
                        imageVector = when (inspected) {
                            is InspectedPayload.Wifi -> Icons.Outlined.Wifi
                            is InspectedPayload.ActionableUri ->
                                if (inspected.totpInfo != null) Icons.Outlined.Key else Icons.Outlined.Link
                            is InspectedPayload.PlainText -> Icons.Outlined.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                val summary = when (inspected) {
                    is InspectedPayload.Wifi -> "SSID: ${inspected.wifi.ssid} (${inspected.wifi.security.name})"
                    is InspectedPayload.ActionableUri -> {
                        val totp = inspected.totpInfo
                        if (totp != null) {
                            listOf(totp.issuer, totp.account)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "otpauth://totp/…" }
                        } else {
                            inspected.uri.take(120)
                        }
                    }
                    is InspectedPayload.PlainText -> inspected.text.take(120)
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(14.dp)
                )
            }

            HorizontalDivider()

            EncryptionFormSection(
                password = password,
                onPasswordChange = { password = it; errorMessage = null },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it; errorMessage = null },
                visibleLabel = visibleLabel,
                onVisibleLabelChange = { visibleLabel = it },
                errorMessage = errorMessage,
                isEncrypting = isEncrypting,
                onSubmit = {
                    if (isEncrypting) return@EncryptionFormSection
                    errorMessage = null
                    if (password.isEmpty()) {
                        errorMessage = context.getString(R.string.err_empty_password)
                        return@EncryptionFormSection
                    }
                    if (password != confirmPassword) {
                        errorMessage = context.getString(R.string.err_password_mismatch)
                        return@EncryptionFormSection
                    }
                    val pwdSnapshot = password
                    onEncryptRequest(
                        scannedRaw,
                        pwdSnapshot,
                        visibleLabel,
                        { err -> errorMessage = err },
                        { isEncrypting = true },
                        {
                            isEncrypting = false
                            password = ""
                            confirmPassword = ""
                        }
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EncryptionFormSection(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    visibleLabel: String,
    onVisibleLabelChange: (String) -> Unit,
    errorMessage: String?,
    isEncrypting: Boolean,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PasswordTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.field_password),
            enabled = !isEncrypting,
            imeAction = ImeAction.Next
        )

        PasswordTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = stringResource(R.string.field_password_confirm),
            enabled = !isEncrypting,
            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
            imeAction = ImeAction.Next
        )

        OutlinedTextField(
            value = visibleLabel,
            onValueChange = onVisibleLabelChange,
            label = { Text(stringResource(R.string.field_visible_label)) },
            supportingText = { Text(stringResource(R.string.field_visible_label_hint)) },
            singleLine = true,
            enabled = !isEncrypting,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !isEncrypting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isEncrypting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.action_encrypting))
            } else {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_encrypt_generate))
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    previewUriBeforeOpening: Boolean,
    onPreviewUriChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPreviewUriChanged(!previewUriBeforeOpening) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.pref_preview_uri_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.pref_preview_uri_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = previewUriBeforeOpening,
                        onCheckedChange = onPreviewUriChanged
                    )
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.about_crypto),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
