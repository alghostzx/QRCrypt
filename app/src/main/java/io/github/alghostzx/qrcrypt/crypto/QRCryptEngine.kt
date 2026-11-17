package io.github.alghostzx.qrcrypt.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Self-contained offline encryption engine for the `qrcrypt://` URI scheme.
 *
 * URI format:
 *   `qrcrypt://v1/<base64url-without-padding>`
 *
 * Binary payload layout (big-endian):
 *   [0]      Format version       (0x01)
 *   [1]      KDF ID               (0x01 = Argon2id)
 *   [2]      Cipher ID            (0x01 = AES-256-GCM)
 *   [3]      Flags                (0x00 = raw UTF-8, 0x01 = raw DEFLATE compressed UTF-8)
 *   [4..7]   Argon2 memory (KiB)  (uint32 BE)
 *   [8]      Argon2 iterations    (uint8)
 *   [9]      Argon2 parallelism   (uint8)
 *   [10..25] Salt                 (16 random bytes)
 *   [26..37] GCM Nonce / IV       (12 random bytes)
 *   [38..N]  AES-256-GCM ciphertext + 16-byte authentication tag
 *
 * The entire 38-byte header [0..37] is passed as AAD (Additional Authenticated Data)
 * to AES-256-GCM so any tampering with version, KDF parameters, flags, salt, or nonce
 * fails authentication.
 */
object QRCryptEngine {

    const val SCHEME = "qrcrypt"
    const val URI_PREFIX_V1 = "qrcrypt://v1/"

    const val FORMAT_VERSION_1: Byte = 0x01
    const val KDF_ARGON2ID: Byte = 0x01
    const val CIPHER_AES_256_GCM: Byte = 0x01

    const val FLAG_RAW: Byte = 0x00
    const val FLAG_DEFLATE: Byte = 0x01

    const val HEADER_SIZE = 38
    const val SALT_SIZE = 16
    const val NONCE_SIZE = 12
    const val KEY_SIZE = 32
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

    // Tuned for pure-JVM Argon2id: strong memory-hardness while staying snappy on mobile (~300-600ms).
    const val DEFAULT_MEMORY_KIB = 16_384 // 16 MiB
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 1

    // Safety bounds against maliciously crafted headers on decrypt.
    private const val MIN_MEMORY_KIB = 64
    private const val MAX_MEMORY_KIB = 262_144 // 256 MiB
    private const val MIN_ITERATIONS = 1
    private const val MAX_ITERATIONS = 64
    private const val MIN_PARALLELISM = 1
    private const val MAX_PARALLELISM = 8

    private const val COMPRESSION_THRESHOLD_BYTES = 140
    private const val MAX_DECOMPRESSED_BYTES = 65_536
    const val MAX_QR_URI_CHARS = 2_900

    private val secureRandom = SecureRandom()

    data class KdfParams(
        val memoryKiB: Int = DEFAULT_MEMORY_KIB,
        val iterations: Int = DEFAULT_ITERATIONS,
        val parallelism: Int = DEFAULT_PARALLELISM
    )

    fun isQRCryptUri(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw.trim().startsWith("$SCHEME://", ignoreCase = true)
    }

    /**
     * Encrypts [plaintext] using [password] and returns a `qrcrypt://v1/...` URI.
     * By default zeroes [password] in-place after key derivation.
     */
    fun encrypt(
        plaintext: String,
        password: CharArray,
        kdfParams: KdfParams = KdfParams(),
        clearPasswordAfterUse: Boolean = true
    ): String {
        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        try {
            return encryptBytes(
                plaintextBytes = plaintextBytes,
                password = password,
                kdfParams = kdfParams,
                clearPasswordAfterUse = clearPasswordAfterUse
            )
        } finally {
            Arrays.fill(plaintextBytes, 0.toByte())
        }
    }

    fun encryptBytes(
        plaintextBytes: ByteArray,
        password: CharArray,
        kdfParams: KdfParams = KdfParams(),
        clearPasswordAfterUse: Boolean = true
    ): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(kdfParams.memoryKiB in MIN_MEMORY_KIB..MAX_MEMORY_KIB) { "Invalid Argon2 memory" }
        require(kdfParams.iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid Argon2 iterations" }
        require(kdfParams.parallelism in MIN_PARALLELISM..MAX_PARALLELISM) { "Invalid Argon2 parallelism" }

        var flags = FLAG_RAW
        var payloadToEncrypt = plaintextBytes
        var compressedBuffer: ByteArray? = null

        if (plaintextBytes.size >= COMPRESSION_THRESHOLD_BYTES) {
            val candidate = deflateRaw(plaintextBytes)
            if (candidate.size + 8 < plaintextBytes.size) {
                flags = FLAG_DEFLATE
                compressedBuffer = candidate
                payloadToEncrypt = candidate
            } else {
                Arrays.fill(candidate, 0.toByte())
            }
        }

        val salt = ByteArray(SALT_SIZE).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(NONCE_SIZE).also { secureRandom.nextBytes(it) }

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            put(FORMAT_VERSION_1)
            put(KDF_ARGON2ID)
            put(CIPHER_AES_256_GCM)
            put(flags)
            putInt(kdfParams.memoryKiB)
            put(kdfParams.iterations.toByte())
            put(kdfParams.parallelism.toByte())
            put(salt)
            put(nonce)
        }.array()

        var derivedKey: ByteArray? = null
        try {
            derivedKey = deriveKeyArgon2id(
                password = password,
                salt = salt,
                memoryKiB = kdfParams.memoryKiB,
                iterations = kdfParams.iterations,
                parallelism = kdfParams.parallelism
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(derivedKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            val ciphertextWithTag = cipher.doFinal(payloadToEncrypt)

            val packet = ByteArray(HEADER_SIZE + ciphertextWithTag.size)
            System.arraycopy(header, 0, packet, 0, HEADER_SIZE)
            System.arraycopy(ciphertextWithTag, 0, packet, HEADER_SIZE, ciphertextWithTag.size)

            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(packet)
            val uri = URI_PREFIX_V1 + encoded
            if (uri.length > MAX_QR_URI_CHARS) {
                throw QRCryptException.PayloadTooLarge(
                    "Encrypted URI is ${uri.length} chars (max $MAX_QR_URI_CHARS)"
                )
            }
            return uri
        } finally {
            derivedKey?.let { Arrays.fill(it, 0.toByte()) }
            compressedBuffer?.let { Arrays.fill(it, 0.toByte()) }
            if (clearPasswordAfterUse) {
                Arrays.fill(password, '\u0000')
            }
        }
    }

    /**
     * Decrypts a `qrcrypt://v1/...` URI using [password] and returns the UTF-8 plaintext.
     * By default zeroes [password] in-place after key derivation.
     */
    fun decrypt(
        qrUri: String,
        password: CharArray,
        clearPasswordAfterUse: Boolean = true
    ): String {
        val bytes = decryptToBytes(qrUri, password, clearPasswordAfterUse)
        try {
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    fun decryptToBytes(
        qrUri: String,
        password: CharArray,
        clearPasswordAfterUse: Boolean = true
    ): ByteArray {
        val trimmed = qrUri.trim()
        val body = extractV1Body(trimmed)

        val packet = try {
            Base64.getUrlDecoder().decode(body)
        } catch (e: IllegalArgumentException) {
            if (clearPasswordAfterUse) Arrays.fill(password, '\u0000')
            throw QRCryptException.InvalidFormat("Invalid Base64URL encoding", e)
        }

        if (packet.size < HEADER_SIZE + GCM_TAG_BYTES) {
            if (clearPasswordAfterUse) Arrays.fill(password, '\u0000')
            throw QRCryptException.InvalidFormat("Payload truncated")
        }

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get()
        val kdfId = buffer.get()
        val cipherId = buffer.get()
        val flags = buffer.get()
        val memoryKiB = buffer.int
        val iterations = buffer.get().toInt() and 0xFF
        val parallelism = buffer.get().toInt() and 0xFF

        if (version != FORMAT_VERSION_1 || kdfId != KDF_ARGON2ID || cipherId != CIPHER_AES_256_GCM) {
            if (clearPasswordAfterUse) Arrays.fill(password, '\u0000')
            throw QRCryptException.UnsupportedVersion(
                "Unsupported version=$version kdf=$kdfId cipher=$cipherId"
            )
        }

        if (flags != FLAG_RAW && flags != FLAG_DEFLATE) {
            if (clearPasswordAfterUse) Arrays.fill(password, '\u0000')
            throw QRCryptException.UnsupportedVersion("Unsupported flags=$flags")
        }

        if (memoryKiB !in MIN_MEMORY_KIB..MAX_MEMORY_KIB ||
            iterations !in MIN_ITERATIONS..MAX_ITERATIONS ||
            parallelism !in MIN_PARALLELISM..MAX_PARALLELISM
        ) {
            if (clearPasswordAfterUse) Arrays.fill(password, '\u0000')
            throw QRCryptException.UnsupportedVersion(
                "Argon2 parameters out of safe bounds: m=$memoryKiB t=$iterations p=$parallelism"
            )
        }

        val salt = ByteArray(SALT_SIZE)
        buffer.get(salt)
        val nonce = ByteArray(NONCE_SIZE)
        buffer.get(nonce)

        val header = Arrays.copyOfRange(packet, 0, HEADER_SIZE)
        val ciphertextWithTag = Arrays.copyOfRange(packet, HEADER_SIZE, packet.size)

        var derivedKey: ByteArray? = null
        var decryptedRaw: ByteArray? = null
        try {
            derivedKey = deriveKeyArgon2id(
                password = password,
                salt = salt,
                memoryKiB = memoryKiB,
                iterations = iterations,
                parallelism = parallelism
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(derivedKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            decryptedRaw = cipher.doFinal(ciphertextWithTag)

            return if (flags == FLAG_DEFLATE) {
                val inflated = inflateRaw(decryptedRaw)
                Arrays.fill(decryptedRaw, 0.toByte())
                inflated
            } else {
                decryptedRaw
            }
        } catch (e: AEADBadTagException) {
            decryptedRaw?.let { Arrays.fill(it, 0.toByte()) }
            throw QRCryptException.WrongPasswordOrCorrupted(cause = e)
        } catch (e: GeneralSecurityException) {
            decryptedRaw?.let { Arrays.fill(it, 0.toByte()) }
            throw QRCryptException.WrongPasswordOrCorrupted(cause = e)
        } finally {
            derivedKey?.let { Arrays.fill(it, 0.toByte()) }
            if (clearPasswordAfterUse) {
                Arrays.fill(password, '\u0000')
            }
        }
    }

    private fun extractV1Body(trimmedUri: String): String {
        val schemePrefix = "$SCHEME://"
        if (!trimmedUri.startsWith(schemePrefix, ignoreCase = true)) {
            throw QRCryptException.InvalidFormat("URI must start with $schemePrefix")
        }
        val afterScheme = trimmedUri.substring(schemePrefix.length)
        val slashIndex = afterScheme.indexOf('/')
        if (slashIndex <= 0 || slashIndex == afterScheme.lastIndex) {
            throw QRCryptException.InvalidFormat("Missing version segment")
        }
        val versionSegment = afterScheme.substring(0, slashIndex)
        if (versionSegment != "v1") {
            throw QRCryptException.UnsupportedVersion("Unsupported URI version segment: $versionSegment")
        }
        val body = afterScheme.substring(slashIndex + 1).trim()
        if (body.isEmpty()) {
            throw QRCryptException.InvalidFormat("Empty payload")
        }
        return body
    }

    private fun deriveKeyArgon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        val passwordBytes = charsToUtf8Bytes(password)
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(params)
            val key = ByteArray(KEY_SIZE)
            generator.generateBytes(passwordBytes, key)
            return key
        } finally {
            Arrays.fill(passwordBytes, 0.toByte())
        }
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0.toByte())
        }
        return bytes
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val output = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        try {
            deflater.setInput(input)
            deflater.finish()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count == 0 && deflater.needsInput()) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
            Arrays.fill(buffer, 0.toByte())
        }
    }

    private fun inflateRaw(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            inflater.setInput(input)
            var total = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                total += count
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw QRCryptException.InvalidFormat("Decompressed payload exceeds safety limit")
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } catch (e: Exception) {
            if (e is QRCryptException) throw e
            throw QRCryptException.InvalidFormat("Corrupted compressed payload", e)
        } finally {
            inflater.end()
            Arrays.fill(buffer, 0.toByte())
        }
    }
}
