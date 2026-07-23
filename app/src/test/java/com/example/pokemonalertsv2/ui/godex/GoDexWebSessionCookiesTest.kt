package com.example.pokemonalertsv2.ui.godex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoDexWebSessionCookiesTest {
    @Test
    fun sessionCookieNamesSelectOnlyGoDexAuthenticationCookies() {
        val names = GoDexWebSessionCookies.sessionCookieNames(
            "PHPSESSID=php; XSRF-TOKEN=csrf; godex_session=godex; " +
                "laravel_session=laravel; locale=en; GOOGLE_PROVIDER=keep"
        )

        assertEquals(
            setOf("PHPSESSID", "XSRF-TOKEN", "godex_session", "laravel_session"),
            names
        )
        assertFalse("GOOGLE_PROVIDER" in names)
        assertFalse("locale" in names)
    }

    @Test
    fun sessionCookieNamesAreCaseInsensitiveAndIgnoreMalformedValues() {
        val names = GoDexWebSessionCookies.sessionCookieNames(
            "phpsessid=value; xsrf-token=value; malformed; =missing-name"
        )

        assertEquals(setOf("PHPSESSID", "XSRF-TOKEN"), names)
    }
}
