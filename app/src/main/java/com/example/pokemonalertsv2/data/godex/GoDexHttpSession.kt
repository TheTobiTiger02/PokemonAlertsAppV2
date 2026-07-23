package com.example.pokemonalertsv2.data.godex

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

internal data class GoDexHttpResponse(
    val code: Int,
    val body: String,
    val finalUrl: String,
    val redirectedToLogin: Boolean
) {
    val isSuccessful: Boolean get() = code in 200..299
    val requiresReauthentication: Boolean
        get() = code == 401 || code == 403 || code == 419 || redirectedToLogin
}

/**
 * Request-scoped cookie state. It starts with the WebView cookie header and
 * merges every Set-Cookie response so callers can persist the refreshed values.
 */
internal class GoDexHttpSession(initialCookieHeader: String) {
    private val cookies = ConcurrentHashMap<String, String>()

    init {
        initialCookieHeader.split(';')
            .map(String::trim)
            .filter { it.contains('=') }
            .forEach { cookie ->
                val name = cookie.substringBefore('=').trim()
                val value = cookie.substringAfter('=', "").trim()
                if (name.isNotBlank()) cookies[name] = value
            }
    }

    fun decorate(request: Request): Request = request.newBuilder()
        .apply {
            val header = cookieHeader()
            if (header.isNotBlank()) header("Cookie", header)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K)")
        }
        .build()

    fun consume(response: Response): GoDexHttpResponse {
        mergeSetCookies(response.request.url, response.headers)
        val finalUrl = response.request.url.toString()
        val redirectedToLogin = response.priorResponse != null &&
            response.request.url.encodedPath.contains("/login", ignoreCase = true)
        return GoDexHttpResponse(
            code = response.code,
            body = response.body?.string().orEmpty(),
            finalUrl = finalUrl,
            redirectedToLogin = redirectedToLogin
        )
    }

    fun cookieHeader(): String = cookies.entries
        .sortedBy { it.key }
        .joinToString("; ") { (name, value) -> "$name=$value" }

    private fun mergeSetCookies(url: HttpUrl, headers: Headers) {
        okhttp3.Cookie.parseAll(url, headers).forEach { cookie ->
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                cookies.remove(cookie.name)
            } else {
                cookies[cookie.name] = cookie.value
            }
        }
    }
}
