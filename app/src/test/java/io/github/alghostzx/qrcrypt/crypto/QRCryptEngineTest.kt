package io.github.alghostzx.qrcrypt.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class QRCryptEngineTest {

    // Fast Argon2id parameters for unit tests; default production params are also tested explicitly.
    private val testKdf = QRCryptEngine.KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)

    @Test
    fun roundTripShortAndUnicodeTextWithDefaultParams() {
        val secret = "otpauth://totp/GitHub:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub 🔐"
        val encPwd = "s3nha-fort3-ção!".toCharArray()
        val decPwd = "s3nha-fort3-ção!".toCharArray()

        val uri = QRCryptEngine.encrypt(secret, encPwd)
        assertTrue(uri.startsWith(QRCryptEngine.URI_PREFIX_V1))
        assertTrue(QRCryptEngine.isQRCryptUri(uri))
        // URI must be URL-safe (no spaces, %, +, /, =)
        val body = uri.removePrefix(QRCryptEngine.URI_PREFIX_V1)
        assertTrue(body.matches(Regex("^[A-Za-z0-9_-]+$")))

        // Password char array must be zeroed after encryption
        assertArrayEquals(CharArray(encPwd.size) { '\u0000' }, encPwd)

        val decrypted = QRCryptEngine.decrypt(uri, decPwd)
        assertEquals(secret, decrypted)
        // Password char array must be zeroed after decryption
        assertArrayEquals(CharArray(decPwd.size) { '\u0000' }, decPwd)
    }

    @Test
    fun roundTripLongCompressiblePayloadUsesDeflateFlag() {
        val longNote = buildString {
            repeat(20) { idx ->
                append("RECOVERY-CODE-BLOCK-00").append(idx).append(": AAAA-BBBB-CCCC-DDDD\n")
            }
        }
        val uri = QRCryptEngine.encrypt(longNote, "correct-horse".toCharArray(), kdfParams = testKdf)
        val rawPacket = Base64.getUrlDecoder().decode(uri.removePrefix(QRCryptEngine.URI_PREFIX_V1))
        assertEquals(QRCryptEngine.FLAG_DEFLATE, rawPacket[3])

        val decrypted = QRCryptEngine.decrypt(uri, "correct-horse".toCharArray())
        assertEquals(longNote, decrypted)
    }

    @Test
    fun encryptProducesUniqueSaltAndNonceEachTime() {
        val uri1 = QRCryptEngine.encrypt("same-secret", "same-pass".toCharArray(), kdfParams = testKdf)
        val uri2 = QRCryptEngine.encrypt("same-secret", "same-pass".toCharArray(), kdfParams = testKdf)
        assertNotEquals(uri1, uri2)
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val uri = QRCryptEngine.encrypt("top secret", "right-pass".toCharArray(), kdfParams = testKdf)
        try {
            QRCryptEngine.decrypt(uri, "wrong-pass".toCharArray())
            fail("Expected WrongPasswordOrCorrupted")
        } catch (_: QRCryptException.WrongPasswordOrCorrupted) {
            // Expected
        }
    }

    @Test
    fun tamperingWithHeaderFailsAadVerification() {
        val uri = QRCryptEngine.encrypt("top secret", "right-pass".toCharArray(), kdfParams = testKdf)
        val packet = Base64.getUrlDecoder().decode(uri.removePrefix(QRCryptEngine.URI_PREFIX_V1))
        // Flip a bit in the salt (part of authenticated header AAD)
        packet[12] = (packet[12].toInt() xor 0x01).toByte()
        val tampered = QRCryptEngine.URI_PREFIX_V1 +
            Base64.getUrlEncoder().withoutPadding().encodeToString(packet)

        try {
            QRCryptEngine.decrypt(tampered, "right-pass".toCharArray())
            fail("Expected WrongPasswordOrCorrupted")
        } catch (_: QRCryptException.WrongPasswordOrCorrupted) {
            // Expected
        }
    }

    @Test
    fun tamperingWithCiphertextFailsAuthentication() {
        val uri = QRCryptEngine.encrypt("top secret", "right-pass".toCharArray(), kdfParams = testKdf)
        val packet = Base64.getUrlDecoder().decode(uri.removePrefix(QRCryptEngine.URI_PREFIX_V1))
        packet[packet.lastIndex] = (packet[packet.lastIndex].toInt() xor 0x80).toByte()
        val tampered = QRCryptEngine.URI_PREFIX_V1 +
            Base64.getUrlEncoder().withoutPadding().encodeToString(packet)

        try {
            QRCryptEngine.decrypt(tampered, "right-pass".toCharArray())
            fail("Expected WrongPasswordOrCorrupted")
        } catch (_: QRCryptException.WrongPasswordOrCorrupted) {
            // Expected
        }
    }

    @Test
    fun invalidAndUnsupportedUrisThrowTypedExceptions() {
        assertFalse(QRCryptEngine.isQRCryptUri("https://example.com"))
        assertTrue(QRCryptEngine.isQRCryptUri("QRCRYPT://v1/abc"))

        try {
            QRCryptEngine.decrypt("https://example.com", "pass".toCharArray())
            fail("Expected InvalidFormat")
        } catch (_: QRCryptException.InvalidFormat) {
            // Expected
        }

        try {
            QRCryptEngine.decrypt("qrcrypt://v2/AAAA", "pass".toCharArray())
            fail("Expected UnsupportedVersion")
        } catch (_: QRCryptException.UnsupportedVersion) {
            // Expected
        }

        try {
            QRCryptEngine.decrypt("qrcrypt://v1/not-long-enough", "pass".toCharArray())
            fail("Expected InvalidFormat")
        } catch (_: QRCryptException.InvalidFormat) {
            // Expected
        }
    }
}
