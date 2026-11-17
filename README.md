# QRCrypt

**QRCrypt** is a 100% offline, self-contained Android utility ("compile and forget") that adds a transparent encryption layer to QR Codes using the custom `qrcrypt://` URI scheme.

## Features

- **Native Camera Decryption (`qrcrypt://` Intent Handler):** Scan any `qrcrypt://` QR code using your phone's native camera (e.g., Samsung Camera) and tap the link to open a floating password dialog.
  - **URIs (`otpauth://`, `https://`, `mailto:`, `geo:`, etc.):** Delegates directly to Android (`ACTION_VIEW`) to open Bitwarden, Aegis, browser, etc. (with an optional setting to preview URIs before opening).
  - **Wi-Fi (`WIFI:...`):** Displays SSID, security type, and password cleanly, with one-tap native Android Wi-Fi saving (`ACTION_WIFI_ADD_NETWORKS`), sensitive password copy, and system Wi-Fi settings shortcut.
  - **Plain Text / Recovery Codes:** Displays text (with monospace toggle) and buttons to copy (marked sensitive on Android clipboard) or share.
- **Template Generator:**
  - **Text / Note**
  - **2FA (TOTP):** Service, Account, Base32 Secret + collapsible advanced options (SHA1/SHA256/SHA512, digits, period).
  - **Wi-Fi:** SSID, Password, Security (WPA/WPA2/WPA3, WEP, Open), Hidden flag, plus a shortcut to system Wi-Fi settings.
  - **Link / URI**
- **Scan & Wrap Existing QR Codes:**
  - Scan an existing QR code via **Camera** (CameraX + ZXing, 100% offline) or pick an image from the **Gallery** (Android Photo Picker, no storage permissions required).
  - If the scanned QR is already `qrcrypt://`, QRCrypt offers to decrypt it directly in-app (and optionally re-wrap it with a new password).
- **Export:** High-resolution PNG with an optional visible label printed below the QR code, ready to **Save to Gallery** (`Pictures/QRCrypt`) or **Share**.

## Security & Privacy

- **Zero Network Access:** The APK requests **no `INTERNET` permission** (verified automatically in CI via `aapt2 dump permissions`).
- **Screen & Memory Protection:** `FLAG_SECURE` is enabled globally to block screenshots and recents previews; password char arrays, UTF-8 byte buffers, and derived keys are zeroed after use.
- **Self-Contained Modern Cryptography (`qrcrypt://v1/<base64url>`):**

| Offset | Length | Field | Value |
| --- | --- | --- | --- |
| `0` | 1 | Format Version | `0x01` |
| `1` | 1 | KDF ID | `0x01` (Argon2id v1.3) |
| `2` | 1 | Cipher ID | `0x01` (AES-256-GCM) |
| `3` | 1 | Flags | `0x00` (raw UTF-8) / `0x01` (raw DEFLATE) |
| `4..7` | 4 | Argon2 Memory (KiB) | `uint32` big-endian (default `16384` = 16 MiB) |
| `8` | 1 | Argon2 Iterations (`t`) | `uint8` (default `3`) |
| `9` | 1 | Argon2 Parallelism (`p`) | `uint8` (default `1`) |
| `10..25` | 16 | Random Salt | `SecureRandom` |
| `26..37` | 12 | Random GCM Nonce | `SecureRandom` |
| `38..N` | var | Ciphertext + 16-byte Tag | AES-256-GCM with bytes `0..37` as AAD |

## Reproducible Headless Build (Docker)

No Android Studio, JDK, or Android SDK installation is required on your host machine — only Docker.

```bash
# Run unit tests
./build.sh test

# Compile signed release APK -> dist/app-release.apk
./build.sh release

# Verify APK signature and zero INTERNET permission
./build.sh verify dist/app-release.apk
```

### Release Signing Setup

Local signing reads `keystore/keystore.properties` (excluded from Git via `.gitignore`):

```properties
storeFile=keystore/qrcrypt-release.jks
storePassword=...
keyAlias=qrcrypt
keyPassword=...
```

In GitHub Actions (`.github/workflows/build.yml`), configure repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Pushing a tag like `v1.0.0` automatically builds, verifies, signs, and publishes a GitHub Release with `QRCrypt-v1.0.0.apk` and `SHA256SUMS.txt`.
