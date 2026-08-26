package com.example.pokemonalertsv2.data.counters

import java.net.URLEncoder

/** Which identity provider Pokebattler should hand the sign-in to. */
enum class PokebattlerLoginProvider(val slug: String, val label: String) {
    GOOGLE("google", "Google"),
    FACEBOOK("facebook", "Facebook"),
    PATREON("patreon", "Patreon"),
    DISCORD("discord", "Discord")
}

/**
 * Sign-in URLs for Pokebattler.
 *
 * Note the host: login lives on `user.pokebattler.com`, not the `fight.` host the rest of
 * the app talks to. The login page served at `fight.pokebattler.com/secure/index.html`
 * advertises `/secure/login/google`, but that path returns 401 — it is stale. The live entry
 * point, taken from Pokebattler's own web client, is
 * `https://user.pokebattler.com/login/{provider}?onsuccess={url}`, which 302s to the provider
 * with `redirect_uri=https://user.pokebattler.com/login/{provider}`.
 *
 * [ONSUCCESS_URL] is a real Pokebattler page rather than a custom scheme, because the server
 * will not redirect to an arbitrary host. The session JWT is recovered from the resulting
 * page instead — see `PokebattlerLoginActivity`.
 *
 * Deliberately free of `android.net.Uri` so the URL and token rules stay unit-testable.
 */
object PokebattlerLoginUrls {

    const val LOGIN_HOST = "user.pokebattler.com"
    const val WEB_HOST = "www.pokebattler.com"

    /** Where Pokebattler sends the browser once the provider has authenticated. */
    const val ONSUCCESS_URL = "https://www.pokebattler.com/user"

    fun loginUrl(provider: PokebattlerLoginProvider): String =
        "https://$LOGIN_HOST/login/${provider.slug}" +
            "?onsuccess=" + URLEncoder.encode(ONSUCCESS_URL, "UTF-8")

    fun logoutUrl(): String = "https://$LOGIN_HOST/logout"

    /**
     * Pulls anything JWT-shaped out of a redirect URL.
     *
     * Pokebattler passes the session token around as a bare query parameter (their own
     * client builds `/link/google?currentJWT=<jwt>`), but the parameter name on the success
     * redirect is not documented, so every parameter and the fragment are checked against
     * the JWT shape. Candidates are always verified against the API before being stored, so
     * a false positive here costs one rejected request.
     */
    fun jwtCandidates(url: String): List<String> {
        val afterScheme = url.substringAfter("://", url)
        val query = afterScheme.substringAfter('?', "").substringBefore('#')
        val fragment = afterScheme.substringAfter('#', "")
        return (query + '&' + fragment)
            .split('&', ';')
            .map { it.substringAfter('=', it).trim() }
            .filter(::looksLikeJwt)
            .distinct()
    }

    /** Three base64url segments separated by dots — enough to screen candidates. */
    fun looksLikeJwt(value: String?): Boolean {
        if (value.isNullOrBlank() || value.length < 40) return false
        val parts = value.split('.')
        if (parts.size != 3) return false
        return parts.all { part ->
            part.length >= 8 &&
                part.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }
        }
    }
}
