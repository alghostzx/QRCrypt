package io.github.alghostzx.qrcrypt.payload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadParsingTest {

    @Test
    fun wifiRoundTripWithSpecialCharacters() {
        val original = WifiPayload(
            ssid = """Casa:5G;Guest\1,"Hi"""",
            password = """p@ss;w:rd\123,4""",
            security = WifiSecurity.WPA,
            hidden = true
        )
        val qr = original.toQrString()
        assertTrue(qr.startsWith("WIFI:"))
        assertTrue(qr.endsWith(";;"))

        val parsed = WifiPayload.parse(qr)
        assertEquals(original, parsed)
    }

    @Test
    fun wifiParsesOpenAndSaeNetworks() {
        val open = WifiPayload.parse("WIFI:T:nopass;S:CoffeeShop;;")
        assertEquals(WifiPayload("CoffeeShop", "", WifiSecurity.OPEN, false), open)

        val wpa3 = WifiPayload.parse("WIFI:T:SAE;S:HomeMesh;P:secret123;H:false;;")
        assertEquals(WifiPayload("HomeMesh", "secret123", WifiSecurity.WPA, false), wpa3)
    }

    @Test
    fun totpBuildAndParseStandardAndAdvanced() {
        assertTrue(TotpPayload.isValidBase32Secret("jbsw y3dp-ehpk 3pxp"))
        assertFalse(TotpPayload.isValidBase32Secret("INVALID1890!"))

        val simple = TotpPayload(
            issuer = "GitHub",
            account = "alice@example.com",
            secret = "jbsw y3dp ehpk 3pxp"
        )
        val simpleUri = simple.toOtpAuthUri()
        assertEquals(
            "otpauth://totp/GitHub:alice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
            simpleUri
        )

        val advanced = TotpPayload(
            issuer = "My Bank",
            account = "user 1",
            secret = "JBSWY3DPEHPK3PXP",
            algorithm = TotpAlgorithm.SHA256,
            digits = 8,
            period = 60
        )
        val advUri = advanced.toOtpAuthUri()
        val parsed = TotpPayload.parse(advUri)
        assertNotNull(parsed)
        assertEquals("My Bank", parsed!!.issuer)
        assertEquals("user 1", parsed.account)
        assertEquals("JBSWY3DPEHPK3PXP", parsed.secret)
        assertEquals(TotpAlgorithm.SHA256, parsed.algorithm)
        assertEquals(8, parsed.digits)
        assertEquals(60, parsed.period)
    }

    @Test
    fun payloadInspectorClassifiesAllTypesAccurately() {
        val wifi = PayloadInspector.inspect("WIFI:T:WPA;S:MyNet;P:pass123;;")
        assertTrue(wifi is InspectedPayload.Wifi)

        val otp = PayloadInspector.inspect(
            "otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        )
        assertTrue(otp is InspectedPayload.ActionableUri)
        assertEquals("otpauth", (otp as InspectedPayload.ActionableUri).scheme)
        assertNotNull(otp.totpInfo)

        val https = PayloadInspector.inspect("https://example.com/path?a=1")
        assertTrue(https is InspectedPayload.ActionableUri)
        assertEquals("https", (https as InspectedPayload.ActionableUri).scheme)

        val mailto = PayloadInspector.inspect("mailto:alice@example.com")
        assertTrue(mailto is InspectedPayload.ActionableUri)

        val geo = PayloadInspector.inspect("geo:-23.5505,-46.6333")
        assertTrue(geo is InspectedPayload.ActionableUri)

        // Plain notes with colons or newlines must NOT be misclassified as URIs
        val noteWithColon = PayloadInspector.inspect("Recovery: ABCD-1234\nBackup: EFGH-5678")
        assertTrue(noteWithColon is InspectedPayload.PlainText)

        val kvSecret = PayloadInspector.inspect("admin:supersecretpassword")
        assertTrue(kvSecret is InspectedPayload.PlainText)
    }
}
