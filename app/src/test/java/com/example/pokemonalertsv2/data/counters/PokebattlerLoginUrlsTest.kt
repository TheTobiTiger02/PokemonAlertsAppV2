package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Login lives on `user.pokebattler.com`, not the `fight.` host the rest of the app uses.
 * The login page served by `fight.` advertises `/secure/login/google`, but that path 401s —
 * it is stale. These tests pin the entry point taken from Pokebattler's own web client.
 */
class PokebattlerLoginUrlsTest {

    @Test
    fun `login url targets the user host with an onsuccess redirect`() {
        val url = PokebattlerLoginUrls.loginUrl(PokebattlerLoginProvider.GOOGLE)
        assertEquals(
            "https://user.pokebattler.com/login/google" +
                "?onsuccess=https%3A%2F%2Fwww.pokebattler.com%2Fuser",
            url
        )
    }

    @Test
    fun `every provider has its own path`() {
        assertTrue(
            PokebattlerLoginUrls.loginUrl(PokebattlerLoginProvider.FACEBOOK)
                .startsWith("https://user.pokebattler.com/login/facebook")
        )
        assertTrue(
            PokebattlerLoginUrls.loginUrl(PokebattlerLoginProvider.PATREON)
                .startsWith("https://user.pokebattler.com/login/patreon")
        )
        assertTrue(
            PokebattlerLoginUrls.loginUrl(PokebattlerLoginProvider.DISCORD)
                .startsWith("https://user.pokebattler.com/login/discord")
        )
    }

    @Test
    fun `jwt shape screening rejects ordinary values`() {
        assertFalse(PokebattlerLoginUrls.looksLikeJwt(null))
        assertFalse(PokebattlerLoginUrls.looksLikeJwt(""))
        assertFalse(PokebattlerLoginUrls.looksLikeJwt("58010"))
        assertFalse(PokebattlerLoginUrls.looksLikeJwt("https://www.pokebattler.com/user"))
        // Right shape, too short to be a session token.
        assertFalse(PokebattlerLoginUrls.looksLikeJwt("a.b.c"))
        // Two segments only.
        assertFalse(PokebattlerLoginUrls.looksLikeJwt(HEADER + "." + PAYLOAD))
    }

    @Test
    fun `jwt shape screening accepts a real looking token`() {
        assertTrue(PokebattlerLoginUrls.looksLikeJwt(JWT))
    }

    @Test
    fun `candidates are pulled from any query parameter`() {
        // Pokebattler's own client passes the token as a bare query parameter
        // (`/link/google?currentJWT=...`), but the name on the success redirect is not
        // documented, so every parameter is a candidate.
        assertEquals(
            listOf(JWT),
            PokebattlerLoginUrls.jwtCandidates("https://www.pokebattler.com/user?currentJWT=$JWT")
        )
        assertEquals(
            listOf(JWT),
            PokebattlerLoginUrls.jwtCandidates("https://www.pokebattler.com/user?foo=bar&t=$JWT")
        )
    }

    @Test
    fun `candidates are pulled from the fragment`() {
        assertEquals(
            listOf(JWT),
            PokebattlerLoginUrls.jwtCandidates("https://www.pokebattler.com/user#jwt=$JWT")
        )
    }

    @Test
    fun `a url with no token yields nothing`() {
        assertTrue(
            PokebattlerLoginUrls.jwtCandidates("https://www.pokebattler.com/user").isEmpty()
        )
        assertTrue(PokebattlerLoginUrls.jwtCandidates("not a url at all").isEmpty())
    }

    private companion object {
        const val HEADER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        const val PAYLOAD = "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRvYmlhcyJ9"
        const val SIGNATURE = "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        const val JWT = "$HEADER.$PAYLOAD.$SIGNATURE"
    }
}
