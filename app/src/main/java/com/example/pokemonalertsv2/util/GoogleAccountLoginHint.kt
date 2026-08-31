package com.example.pokemonalertsv2.util

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Steers the login WebView toward the Google account the user picked on the device.
 *
 * Pokébattler and GoDex both hand the sign-in off to accounts.google.com inside a WebView,
 * where the device's saved accounts are not offered and everything has to be typed by hand.
 * The OAuth endpoints accept the standard `login_hint` parameter, which pre-selects or
 * pre-fills that account — so the system account chooser's result is folded into the
 * sign-in URL here and only the password is left to type, on Google's own page.
 *
 * Plain string handling on purpose: this runs in JVM unit tests without Android stubs.
 */
object GoogleAccountLoginHint {

    private const val HOST = "accounts.google.com"
    private const val PARAMETER = "login_hint"

    /**
     * [url] with [email] applied as `login_hint`, or null when it must not be touched.
     *
     * Only OAuth entry points are rewritten (`/o/oauth2/…`, `/ServiceLogin`); the later
     * steps of the flow — password entry, 2FA challenges — live on other paths and would
     * break if reloaded. A URL that already carries the same hint also returns null, which
     * is what keeps a rewrite from re-triggering itself when the WebView starts loading the
     * rewritten URL.
     */
    fun apply(url: String, email: String?): String? {
        if (email.isNullOrBlank()) return null
        val hint = URLEncoder.encode(email, "UTF-8")

        val hashIndex = url.indexOf('#')
        val base = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""

        val schemeEnd = base.indexOf("://")
        if (schemeEnd <= 0) return null
        val authorityStart = schemeEnd + 3
        val pathStart = base.indexOf('/', authorityStart).let { if (it == -1) base.length else it }
        val host = base.substring(authorityStart, pathStart).substringBefore(':')
        if (!host.equals(HOST, ignoreCase = true)) return null

        val queryStart = base.indexOf('?', pathStart)
        val path = base.substring(pathStart, if (queryStart == -1) base.length else queryStart)
        if (!isEntryPath(path)) return null

        val params = (if (queryStart == -1) "" else base.substring(queryStart + 1))
            .split('&')
            .filter { it.isNotEmpty() }
            .toMutableList()

        val existing = params.indexOfFirst { parameterName(it) == PARAMETER }
        val rewritten = when {
            existing == -1 -> {
                params.add("$PARAMETER=$hint")
                join(base, queryStart, params)
            }
            parameterValue(params[existing]) == email -> return null
            else -> {
                params[existing] = "$PARAMETER=$hint"
                join(base, queryStart, params)
            }
        }
        return rewritten + fragment
    }

    private fun isEntryPath(path: String): Boolean =
        path.contains("/o/oauth2", ignoreCase = true) || path.equals("/ServiceLogin", ignoreCase = true)

    private fun parameterName(parameter: String): String = decode(parameter.substringBefore('='))

    private fun parameterValue(parameter: String): String =
        decode(parameter.substringAfter('=', ""))

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun join(base: String, queryStart: Int, params: List<String>): String {
        val query = params.joinToString("&")
        return if (queryStart == -1) "$base?$query" else base.substring(0, queryStart + 1) + query
    }
}
