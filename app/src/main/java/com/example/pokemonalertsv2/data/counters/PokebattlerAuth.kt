package com.example.pokemonalertsv2.data.counters

import android.content.Context
import com.example.pokemonalertsv2.util.KeystoreCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A linked Pokebattler account. */
data class PokebattlerAccount(
    /** Pokebattler's internal user id — the `attackers/users/{id}` path segment. */
    val userId: String,
    val displayName: String?
)

/**
 * Stores the Pokebattler session JWT.
 *
 * The token is a bearer credential for the user's account, so it is encrypted with an
 * AES/GCM key held in the Android Keystore and never written anywhere in plaintext — not
 * to the shared DataStore that holds ordinary settings, and not to logs. The user id and
 * display name are not secret and sit next to it unencrypted.
 */
class PokebattlerAuth private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cipher = KeystoreCipher(KEY_ALIAS)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<PokebattlerAccount?> = _account.asStateFlow()

    val isSignedIn: Boolean get() = _account.value != null

    /** The raw JWT, or null when signed out. */
    fun token(): String? = runCatching { decrypt(prefs.getString(KEY_TOKEN, null)) }.getOrNull()

    /**
     * The `X-Authorization` value, or null when signed out.
     *
     * Pokebattler really does want `Bearer:` with a colon — their own web client sends
     * `"Bearer: " + jwt`, and the plain `Bearer <token>` spelling is rejected.
     */
    fun authorizationHeader(): String? = token()?.let { "Bearer: $it" }

    fun save(token: String, account: PokebattlerAccount) {
        prefs.edit()
            .putString(KEY_TOKEN, encrypt(token))
            .putString(KEY_USER_ID, account.userId)
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun readAccount(): PokebattlerAccount? {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        // A token that can no longer be decrypted (the Keystore key was invalidated by a
        // lock-screen change, say) is the same as being signed out.
        if (token().isNullOrBlank()) return null
        return PokebattlerAccount(userId, prefs.getString(KEY_DISPLAY_NAME, null))
    }

    private fun encrypt(value: String): String = cipher.encrypt(value)

    private fun decrypt(stored: String?): String? =
        if (stored.isNullOrBlank()) null else cipher.decrypt(stored)

    companion object {
        private const val PREFS_NAME = "pokebattler_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ALIAS = "pokebattler_auth_key"

        @Volatile
        private var INSTANCE: PokebattlerAuth? = null

        fun getInstance(context: Context): PokebattlerAuth =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PokebattlerAuth(context).also { INSTANCE = it }
            }
    }
}
