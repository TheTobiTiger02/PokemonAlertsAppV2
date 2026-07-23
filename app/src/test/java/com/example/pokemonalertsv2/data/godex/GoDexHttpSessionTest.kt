package com.example.pokemonalertsv2.data.godex

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexHttpSessionTest {
    @Test
    fun responseCookiesReplacePersistedValues() {
        val session = GoDexHttpSession("PHPSESSID=old; locale=en")
        val request = Request.Builder().url("https://godex.site/collection/test").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Set-Cookie", "PHPSESSID=refreshed; Path=/; HttpOnly")
            .body("ok".toResponseBody())
            .build()

        session.consume(response)

        assertEquals("PHPSESSID=refreshed; locale=en", session.cookieHeader())
    }

    @Test
    fun authenticationCodesRequireReauthentication() {
        val session = GoDexHttpSession("PHPSESSID=old")
        val request = Request.Builder().url("https://godex.site/collection/test").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(419)
            .message("Page Expired")
            .body("expired".toResponseBody())
            .build()

        val result = session.consume(response)

        assertTrue(result.requiresReauthentication)
    }
}
