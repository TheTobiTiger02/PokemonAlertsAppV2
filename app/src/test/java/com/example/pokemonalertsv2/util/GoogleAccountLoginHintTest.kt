package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAccountLoginHintTest {

    private val oauthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&redirect_uri=https%3A%2F%2Fpokebattler.com"

    @Test
    fun `appends the hint to an oauth url without one`() {
        assertEquals(
            "$oauthUrl&login_hint=trainer%40gmail.com",
            GoogleAccountLoginHint.apply(oauthUrl, "trainer@gmail.com")
        )
    }

    @Test
    fun `replaces a different existing hint`() {
        val url = "https://accounts.google.com/o/oauth2/auth?login_hint=old%40gmail.com&client_id=abc"
        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?login_hint=trainer%40gmail.com&client_id=abc",
            GoogleAccountLoginHint.apply(url, "trainer@gmail.com")
        )
    }

    @Test
    fun `a url already carrying the same hint is left alone`() {
        // The rewrite must not re-trigger itself when the WebView starts loading the
        // rewritten URL.
        val url = "$oauthUrl&login_hint=trainer%40gmail.com"
        assertNull(GoogleAccountLoginHint.apply(url, "trainer@gmail.com"))
    }

    @Test
    fun `the legacy ServiceLogin path is rewritten too`() {
        val url = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgodex.site"
        assertEquals(
            "$url&login_hint=trainer%40gmail.com",
            GoogleAccountLoginHint.apply(url, "trainer@gmail.com")
        )
    }

    @Test
    fun `later steps of the sign-in flow are never touched`() {
        assertNull(
            GoogleAccountLoginHint.apply(
                "https://accounts.google.com/v3/signin/challenge/totp?login_hint=old%40gmail.com",
                "trainer@gmail.com"
            )
        )
    }

    @Test
    fun `non-google hosts are left alone`() {
        assertNull(GoogleAccountLoginHint.apply("https://godex.site/login", "trainer@gmail.com"))
        assertNull(GoogleAccountLoginHint.apply("https://user.pokebattler.com/login/google", "trainer@gmail.com"))
    }

    @Test
    fun `a blank or missing email changes nothing`() {
        assertNull(GoogleAccountLoginHint.apply(oauthUrl, null))
        assertNull(GoogleAccountLoginHint.apply(oauthUrl, "  "))
    }

    @Test
    fun `the fragment survives a rewrite`() {
        val url = "https://accounts.google.com/o/oauth2/auth?client_id=abc#fragment"
        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?client_id=abc&login_hint=trainer%40gmail.com#fragment",
            GoogleAccountLoginHint.apply(url, "trainer@gmail.com")
        )
    }

    @Test
    fun `a query-less url gets one`() {
        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?login_hint=trainer%40gmail.com",
            GoogleAccountLoginHint.apply("https://accounts.google.com/o/oauth2/auth", "trainer@gmail.com")
        )
    }
}
