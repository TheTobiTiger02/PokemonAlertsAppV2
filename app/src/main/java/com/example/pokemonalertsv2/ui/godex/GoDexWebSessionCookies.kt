package com.example.pokemonalertsv2.ui.godex

import android.webkit.CookieManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Keeps OAuth-provider cookies separate from GoDex session cookies.
 *
 * Normal login does not call either clearing method. GoDex-only clearing is
 * reserved for explicit GoDex sign-out, while clearing everything is reserved
 * for the explicit "Use another account" action.
 */
object GoDexWebSessionCookies {
    const val GODEX_ORIGIN = "https://godex.site"
    const val GODEX_LOGIN_URL = "$GODEX_ORIGIN/login"

    private val knownSessionCookieNames = linkedSetOf(
        "PHPSESSID",
        "XSRF-TOKEN",
        "godex_session",
        "laravel_session"
    )

    internal fun sessionCookieNames(cookieHeader: String?): Set<String> {
        val knownByLowercase = knownSessionCookieNames.associateBy(String::lowercase)
        return cookieHeader.orEmpty()
            .split(';')
            .map(String::trim)
            .filter { it.contains('=') }
            .map { it.substringBefore('=').trim() }
            .mapNotNull { knownByLowercase[it.lowercase()] }
            .toSet()
    }

    suspend fun clearGoDexSessionCookies(
        cookieManager: CookieManager = CookieManager.getInstance()
    ) {
        val names = knownSessionCookieNames + sessionCookieNames(
            cookieManager.getCookie(GODEX_ORIGIN)
        )
        suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(names.size * 2)
            val onCookieExpired: (Boolean) -> Unit = {
                if (remaining.decrementAndGet() == 0) {
                    cookieManager.flush()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            names.forEach { name ->
                val expiration =
                    "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure"
                cookieManager.setCookie(GODEX_ORIGIN, expiration, onCookieExpired)
                cookieManager.setCookie(
                    GODEX_ORIGIN,
                    "$expiration; Domain=godex.site",
                    onCookieExpired
                )
            }
        }
    }

    fun clearAllWebViewCookies(
        cookieManager: CookieManager = CookieManager.getInstance(),
        onComplete: () -> Unit
    ) {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onComplete()
        }
    }
}
