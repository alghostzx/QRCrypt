package io.github.alghostzx.qrcrypt.payload

sealed class InspectedPayload {
    abstract val raw: String

    data class Wifi(
        val wifi: WifiPayload,
        override val raw: String
    ) : InspectedPayload()

    data class ActionableUri(
        val uri: String,
        val scheme: String,
        val totpInfo: TotpPayload? = null
    ) : InspectedPayload() {
        override val raw: String get() = uri
    }

    data class PlainText(
        val text: String
    ) : InspectedPayload() {
        override val raw: String get() = text
    }
}

object PayloadInspector {

    private val SCHEME_WITH_AUTHORITY_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://\\S+$")
    private val KNOWN_OPAQUE_SCHEMES = setOf(
        "mailto", "geo", "tel", "sms", "smsto", "bitcoin", "lightning", "ethereum", "magnet", "upi"
    )
    private val OPAQUE_URI_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):\\S+$")

    fun inspect(decryptedText: String): InspectedPayload {
        val trimmed = decryptedText.trim()

        if (WifiPayload.isWifiPayload(trimmed)) {
            val parsed = WifiPayload.parse(trimmed)
            if (parsed != null) {
                return InspectedPayload.Wifi(wifi = parsed, raw = trimmed)
            }
        }

        if (!trimmed.contains('\n') && !trimmed.contains('\r') && !trimmed.contains(' ')) {
            val authorityMatch = SCHEME_WITH_AUTHORITY_REGEX.matchEntire(trimmed)
            if (authorityMatch != null) {
                val scheme = authorityMatch.groupValues[1].lowercase()
                val totp = if (scheme == "otpauth") TotpPayload.parse(trimmed) else null
                return InspectedPayload.ActionableUri(
                    uri = trimmed,
                    scheme = scheme,
                    totpInfo = totp
                )
            }

            val opaqueMatch = OPAQUE_URI_REGEX.matchEntire(trimmed)
            if (opaqueMatch != null) {
                val scheme = opaqueMatch.groupValues[1].lowercase()
                if (scheme in KNOWN_OPAQUE_SCHEMES) {
                    return InspectedPayload.ActionableUri(uri = trimmed, scheme = scheme)
                }
            }
        }

        return InspectedPayload.PlainText(decryptedText)
    }

    fun isValidUriInput(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.contains('\n') || trimmed.contains(' ')) return false
        return SCHEME_WITH_AUTHORITY_REGEX.matches(trimmed) ||
            (OPAQUE_URI_REGEX.matchEntire(trimmed)?.groupValues?.get(1)?.lowercase() in KNOWN_OPAQUE_SCHEMES)
    }
}
