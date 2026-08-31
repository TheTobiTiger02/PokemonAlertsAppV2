package com.example.pokemonalertsv2.data.godex

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the GoDex session cookies with an AES/GCM key held in the Android Keystore,
 * mirroring how the Pokebattler JWT is stored.
 *
 * Cookies are a bearer credential for the user's godex.site session, so they must not
 * sit in the (cloud-backupable) DataStore in plaintext. Values that fail to decrypt are
 * returned as-is so cookies written by an older plaintext build keep working until the
 * next save re-encrypts them; likewise an encrypt failure falls back to plaintext rather
 * than breaking sign-in.
 */
internal object GoDexSessionCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "godex_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun encrypt(value: String): String {
        if (value.isEmpty()) return value
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            // iv || ciphertext, so no second storage key is needed.
            Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        }.getOrDefault(value)
    }

    fun decrypt(value: String): String {
        if (value.isEmpty()) return value
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            if (bytes.size <= GCM_IV_BYTES) return@runCatching value
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES)
            )
            String(
                cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES),
                Charsets.UTF_8
            )
        }.getOrDefault(value)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
