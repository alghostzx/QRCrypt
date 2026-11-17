package io.github.alghostzx.qrcrypt.crypto

sealed class QRCryptException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidFormat(message: String = "Invalid qrcrypt:// format", cause: Throwable? = null) :
        QRCryptException(message, cause)

    class UnsupportedVersion(message: String = "Unsupported QRCrypt version or parameters") :
        QRCryptException(message)

    class WrongPasswordOrCorrupted(
        message: String = "Authentication failed: wrong password or corrupted data",
        cause: Throwable? = null
    ) : QRCryptException(message, cause)

    class PayloadTooLarge(message: String = "Payload exceeds QR code capacity") :
        QRCryptException(message)
}
