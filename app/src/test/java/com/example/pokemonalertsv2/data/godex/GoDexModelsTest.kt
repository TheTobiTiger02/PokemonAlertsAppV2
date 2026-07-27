package com.example.pokemonalertsv2.data.godex

import org.junit.Assert.assertEquals
import org.junit.Test

class GoDexModelsTest {
    @Test
    fun debugStatusLabelsDescribeFormChangeAndCombinedResults() {
        val evolution = GoDexEvolutionTarget("0002-none", 2, "Evolution", 1)
        val formChange = GoDexFormChangeTarget("0001_alt-none", 1, "Alternate", 1)

        assertEquals(
            "Collected • Form change needed: Alternate",
            debugEntry(GoDexMatchResult(GoDexMatchStatus.FORM_CHANGE_NEEDED, formChangeTargets = listOf(formChange))).statusLabel
        )
        assertEquals(
            "Collected • Evolution needed: Evolution • Form change needed: Alternate",
            debugEntry(
                GoDexMatchResult(
                    GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED,
                    evolutionTargets = listOf(evolution),
                    formChangeTargets = listOf(formChange)
                )
            ).statusLabel
        )
    }

    @Test
    fun anonymousPublicCollectionCookiesDoNotImplyAuthentication() {
        assertEquals(
            GoDexSessionState.NONE,
            resolveGoDexSessionState(
                storedName = null,
                cookies = "PHPSESSID=anonymous",
                writeBackUrl = ""
            )
        )
        assertEquals(
            GoDexSessionState.AUTHENTICATED,
            resolveGoDexSessionState(
                storedName = null,
                cookies = "PHPSESSID=authenticated",
                writeBackUrl = "https://godex.site/collection/abc"
            )
        )
        assertEquals(
            GoDexSessionState.REAUTH_REQUIRED,
            resolveGoDexSessionState(
                storedName = GoDexSessionState.REAUTH_REQUIRED.name,
                cookies = "PHPSESSID=expired",
                writeBackUrl = "https://godex.site/collection/abc"
            )
        )
    }

    private fun debugEntry(result: GoDexMatchResult) = GoDexDebugEntry(
        entryKey = "0001-none",
        pokedexId = 1,
        displayName = "Source",
        formSlug = null,
        gender = "none",
        result = result
    )
}
