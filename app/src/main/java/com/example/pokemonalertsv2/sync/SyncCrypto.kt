package com.example.pokemonalertsv2.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One sync group: the shared 32-byte secret and everything derived from it.
 *
 * [encKey] seals every blob. For a group linked to an account the server also keeps the
 * secret, sealed, to hand it to the account's devices (DEVICE_SYNC.md in the backend repo).
 */
class SyncGroup(val secret: ByteArray) {
    init {
        require(secret.size == SyncCrypto.SECRET_BYTES) { "A sync secret is ${SyncCrypto.SECRET_BYTES} bytes" }
    }

    val groupId: String = SyncCrypto.hkdf(secret, "group-id", 16).toHex()
    val authToken: String = SyncCrypto.base64Url(SyncCrypto.hkdf(secret, "auth", 32))
    internal val encKey: ByteArray = SyncCrypto.hkdf(secret, "enc", 32)

    /** The FCM topic the server pings after each write. */
    val topic: String get() = "devsync-$groupId"

    /** How the secret is stored on this device (SyncStateStore). */
    internal val pairingCode: String get() = SyncCrypto.encodePairingCode(secret)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

object SyncCrypto {
    const val SECRET_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val CODE_PREFIX = "P1"
    private const val CHECKSUM_CHARS = 4

    private val random = SecureRandom()

    fun newGroup(): SyncGroup = SyncGroup(ByteArray(SECRET_BYTES).also(random::nextBytes))

    /** HKDF-SHA256 (RFC 5869) with an empty salt. */
    fun hkdf(secret: ByteArray, info: String, length: Int): ByteArray {
        val prk = hmac(ByteArray(32), secret)
        val output = ByteArrayOutputStream()
        var block = ByteArray(0)
        var counter = 1
        while (output.size() < length) {
            block = hmac(prk, block + info.toByteArray(Charsets.UTF_8) + byteArrayOf(counter.toByte()))
            output.write(block)
            counter++
        }
        return output.toByteArray().copyOf(length)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** gzip, then AES-256-GCM. Output is `nonce || ciphertext+tag`; [aad] binds it to its group and collection. */
    fun seal(plain: ByteArray, key: ByteArray, aad: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return nonce + cipher.doFinal(gzip(plain))
    }

    /** Throws when the key, the AAD or the bytes are wrong: GCM authenticates all three. */
    fun open(sealed: ByteArray, key: ByteArray, aad: String): ByteArray {
        require(sealed.size > NONCE_BYTES) { "Sealed blob too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return gunzip(cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES))
    }

    fun aad(groupId: String, collection: String): String = "$groupId|$collection"

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    /**
     * `P1` + base64url(secret) + 4 checksum characters. The prefix versions the format; the
     * checksum catches a mistyped or truncated code before it is sent anywhere.
     */
    fun encodePairingCode(secret: ByteArray): String {
        val body = CODE_PREFIX + base64Url(secret)
        return body + checksum(body)
    }

    /** The secret in [input], a stored code ([encodePairingCode]). Null when it is not a valid code. */
    fun decodePairingCode(input: String): ByteArray? {
        val code = extractCode(input) ?: return null
        if (!code.startsWith(CODE_PREFIX) || code.length <= CODE_PREFIX.length + CHECKSUM_CHARS) return null
        val body = code.dropLast(CHECKSUM_CHARS)
        if (checksum(body) != code.takeLast(CHECKSUM_CHARS)) return null
        val secret = runCatching { Base64.getUrlDecoder().decode(body.removePrefix(CODE_PREFIX)) }.getOrNull() ?: return null
        return secret.takeIf { it.size == SECRET_BYTES }
    }

    private fun extractCode(input: String): String? {
        val trimmed = input.trim()
        val fromLink = if (trimmed.contains("code=")) {
            trimmed.substringAfter("code=").substringBefore('&').substringBefore('#')
        } else {
            trimmed
        }
        return fromLink.filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }

    private fun checksum(body: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))).take(CHECKSUM_CHARS)

    /** Short stable fingerprint of an item value, kept in the shadow instead of the value itself. */
    fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).copyOf(12).toHex()

}
