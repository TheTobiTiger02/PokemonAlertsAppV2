package com.example.pokemonalertsv2.data.counters

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a candidate JWT into a stored, verified Pokebattler session.
 *
 * Verification is the point: the login flow scrapes candidate tokens out of a WebView
 * without knowing which one Pokebattler meant, so a candidate only becomes the stored
 * session once `GET /secure/user` accepts it and returns a user id. That also makes the
 * flow robust to Pokebattler changing how the token is delivered.
 */
class PokebattlerAuthRepository @VisibleForTesting internal constructor(
    private val service: PokebattlerService,
    private val auth: PokebattlerAuth
) {

    val account get() = auth.account
    fun authorizationHeader(): String? = auth.authorizationHeader()
    fun signOut() = auth.clear()

    /** Verifies [token] against the API and stores it on success. */
    suspend fun signIn(token: String): Result<PokebattlerAccount> = withContext(Dispatchers.IO) {
        fetchUser("Bearer: $token")
            .mapCatching { user ->
                val id = user.id?.takeIf { it.isNotBlank() }
                    ?: error("Pokébattler did not return a user id for this account.")
                PokebattlerAccount(id, user.displayName).also { auth.save(token, it) }
            }
    }

    /**
     * Re-checks the stored token, clearing it when the server no longer accepts it.
     *
     * @return true when a usable session remains.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        // Read the token once: every call re-runs Keystore decryption, so a failure
        // between the header check and the save must not turn into an NPE.
        val token = auth.token() ?: return@withContext false
        fetchUser("Bearer: $token")
            .fold(
                onSuccess = { user ->
                    val id = user.id?.takeIf { it.isNotBlank() } ?: return@fold false
                    auth.save(token, PokebattlerAccount(id, user.displayName))
                    true
                },
                onFailure = { throwable ->
                    // Only a rejection means signed out. A network blip must not silently
                    // unlink the account.
                    if (throwable.isUnauthorized()) {
                        auth.clear()
                        false
                    } else {
                        true
                    }
                }
            )
    }

    /**
     * Looks the account up on the base host, falling back to the sign-in host.
     *
     * Only a rejection stops the fallback: a 401 from the first host is a verdict on the
     * token, whereas a 404 or a transport error just means the account record lives
     * elsewhere.
     */
    private suspend fun fetchUser(header: String): Result<PokebattlerUserResponse> {
        val primary = runCatching { service.getCurrentUser(header) }
        if (primary.isSuccess || primary.exceptionOrNull()?.isUnauthorized() == true) return primary
        return runCatching { service.getCurrentUserAt(USER_HOST_ENDPOINT, header) }
            .recoverCatching { throw primary.exceptionOrNull() ?: it }
    }

    companion object {
        private const val USER_HOST_ENDPOINT =
            "https://" + PokebattlerLoginUrls.LOGIN_HOST + "/secure/user"

        @Volatile
        private var INSTANCE: PokebattlerAuthRepository? = null

        fun getInstance(context: Context): PokebattlerAuthRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PokebattlerAuthRepository(
                    service = PokebattlerApi.service(context),
                    auth = PokebattlerAuth.getInstance(context)
                ).also { INSTANCE = it }
            }
    }
}

private fun Throwable.isUnauthorized(): Boolean =
    (this as? retrofit2.HttpException)?.code() in setOf(401, 403)
