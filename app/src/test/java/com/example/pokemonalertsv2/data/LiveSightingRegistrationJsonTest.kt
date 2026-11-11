package com.example.pokemonalertsv2.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The body the server's register() reads: installId only when known; an empty set is just the id, which the server reads as "nothing". */
class LiveSightingRegistrationJsonTest {
    // Same settings as PokemonAlertsApi: defaults and nulls are left out of the body.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun includesTheInstallIdAndEmptySets() {
        assertEquals(
            """{"installId":"abc-123-def"}""",
            json.encodeToString(LiveSightingRegistration(installId = "abc-123-def"))
        )
    }

    @Test
    fun leavesTheInstallIdOutWhenThereIsNone() {
        assertEquals(
            """{"all":true,"species":[4]}""",
            json.encodeToString(LiveSightingRegistration(all = true, species = listOf(4)))
        )
    }
}
