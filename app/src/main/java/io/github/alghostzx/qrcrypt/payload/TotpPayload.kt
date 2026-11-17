package io.github.alghostzx.qrcrypt.payload

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class TotpAlgorithm {
    SHA1, SHA256, SHA512
}

data class TotpPayload(
    val issuer: String,
    val account: String,
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30
) {
    fun toOtpAuthUri(): String {
        val cleanSecret = normalizeSecret(secret)
        val cleanIssuer = issuer.trim()
        val cleanAccount = account.trim()

        val label = when {
            cleanIssuer.isNotEmpty() && cleanAccount.isNotEmpty() ->
                "${encodePathSegment(cleanIssuer)}:${encodePathSegment(cleanAccount)}"
            cleanIssuer.isNotEmpty() -> encodePathSegment(cleanIssuer)
            cleanAccount.isNotEmpty() -> encodePathSegment(cleanAccount)
            else -> "Account"
        }

        val queryParts = mutableListOf("secret=$cleanSecret")
        if (cleanIssuer.isNotEmpty()) {
            queryParts.add("issuer=${encodeQueryValue(cleanIssuer)}")
        }
        if (algorithm != TotpAlgorithm.SHA1) {
            queryParts.add("algorithm=${algorithm.name}")
        }
        if (digits != 6) {
            queryParts.add("digits=$digits")
        }
        if (period != 30) {
            queryParts.add("period=$period")
        }
        return "otpauth://totp/$label?${queryParts.joinToString("&")}"
    }

    companion object {
        private val BASE32_REGEX = Regex("^[A-Z2-7]+=*$")

        fun normalizeSecret(raw: String): String =
            raw.replace(Regex("[\\s-]+"), "").uppercase()

        fun isValidBase32Secret(raw: String): Boolean {
            val normalized = normalizeSecret(raw)
            return normalized.length >= 8 && BASE32_REGEX.matches(normalized)
        }

        fun parse(uri: String): TotpPayload? {
            val trimmed = uri.trim()
            if (!trimmed.startsWith("otpauth://", ignoreCase = true)) return null
            val withoutScheme = trimmed.substring(10)
            val slashIdx = withoutScheme.indexOf('/')
            if (slashIdx <= 0) return null
            val type = withoutScheme.substring(0, slashIdx).lowercase()
            if (type != "totp" && type != "hotp") return null

            val rest = withoutScheme.substring(slashIdx + 1)
            val qIdx = rest.indexOf('?')
            val rawLabel = if (qIdx >= 0) rest.substring(0, qIdx) else rest
            val rawQuery = if (qIdx >= 0) rest.substring(qIdx + 1) else ""

            val decodedLabel = decodeUrl(rawLabel)
            val colonIdx = decodedLabel.indexOf(':')
            val labelIssuer = if (colonIdx > 0) decodedLabel.substring(0, colonIdx).trim() else ""
            val labelAccount = if (colonIdx > 0) decodedLabel.substring(colonIdx + 1).trim() else decodedLabel.trim()

            val params = rawQuery.split('&')
                .mapNotNull { part ->
                    val eq = part.indexOf('=')
                    if (eq <= 0) null
                    else part.substring(0, eq).lowercase() to decodeUrl(part.substring(eq + 1))
                }.toMap()

            val secret = params["secret"].orEmpty()
            if (secret.isBlank()) return null

            val issuer = params["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer
            val algorithm = when (params["algorithm"]?.uppercase()) {
                "SHA256" -> TotpAlgorithm.SHA256
                "SHA512" -> TotpAlgorithm.SHA512
                else -> TotpAlgorithm.SHA1
            }
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30

            return TotpPayload(
                issuer = issuer,
                account = labelAccount,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                period = period
            )
        }

        private fun encodePathSegment(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        private fun encodeQueryValue(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        private fun decodeUrl(value: String): String = try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
