package io.github.alghostzx.qrcrypt.payload

enum class WifiSecurity(val qrCodeToken: String) {
    WPA("WPA"),
    WEP("WEP"),
    OPEN("nopass");

    companion object {
        fun fromToken(token: String?): WifiSecurity {
            return when (token?.trim()?.uppercase()) {
                "WEP" -> WEP
                "NOPASS", "NONE", "" -> OPEN
                else -> WPA // WPA, WPA2, WPA3, SAE
            }
        }
    }
}

data class WifiPayload(
    val ssid: String,
    val password: String,
    val security: WifiSecurity,
    val hidden: Boolean = false
) {
    fun toQrString(): String {
        val sb = StringBuilder("WIFI:")
        sb.append("T:").append(security.qrCodeToken).append(';')
        sb.append("S:").append(escape(ssid)).append(';')
        if (security != WifiSecurity.OPEN && password.isNotEmpty()) {
            sb.append("P:").append(escape(password)).append(';')
        }
        if (hidden) {
            sb.append("H:true;")
        }
        sb.append(';')
        return sb.toString()
    }

    companion object {
        fun isWifiPayload(raw: String): Boolean =
            raw.trim().startsWith("WIFI:", ignoreCase = true)

        fun parse(raw: String): WifiPayload? {
            val trimmed = raw.trim()
            if (!isWifiPayload(trimmed)) return null

            val body = trimmed.substring(5)
            val fields = parseFields(body)
            val ssid = fields["S"] ?: return null
            val rawSecurity = fields["T"]
            val password = fields["P"].orEmpty()
            val security = if (rawSecurity != null) {
                WifiSecurity.fromToken(rawSecurity)
            } else if (password.isEmpty()) {
                WifiSecurity.OPEN
            } else {
                WifiSecurity.WPA
            }
            val hidden = fields["H"]?.equals("true", ignoreCase = true) == true
            return WifiPayload(
                ssid = ssid,
                password = password,
                security = security,
                hidden = hidden
            )
        }

        internal fun escape(value: String): String {
            val out = StringBuilder(value.length + 4)
            for (ch in value) {
                if (ch == '\\' || ch == ';' || ch == ',' || ch == '"' || ch == ':') {
                    out.append('\\')
                }
                out.append(ch)
            }
            return out.toString()
        }

        private fun parseFields(body: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var i = 0
            val len = body.length

            while (i < len) {
                if (body[i] == ';') {
                    i++
                    continue
                }
                val colonIdx = body.indexOf(':', startIndex = i)
                if (colonIdx <= i) break

                val key = body.substring(i, colonIdx).trim().uppercase()
                i = colonIdx + 1

                val value = StringBuilder()
                while (i < len) {
                    val c = body[i]
                    if (c == '\\' && i + 1 < len) {
                        value.append(body[i + 1])
                        i += 2
                    } else if (c == ';') {
                        i++
                        break
                    } else {
                        value.append(c)
                        i++
                    }
                }
                result[key] = value.toString()
            }
            return result
        }
    }
}
