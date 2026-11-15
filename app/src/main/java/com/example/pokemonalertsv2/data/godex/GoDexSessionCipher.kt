package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.util.KeystoreCipher

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
    private val cipher = KeystoreCipher("godex_session_key")

    fun encrypt(value: String): String {
        if (value.isEmpty()) return value
        return runCatching { cipher.encrypt(value) }.getOrDefault(value)
    }

    fun decrypt(value: String): String {
        if (value.isEmpty()) return value
        return runCatching { cipher.decrypt(value) ?: value }.getOrDefault(value)
    }
}
