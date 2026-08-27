package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the parenthesis expansion used by MonGo Creative Search. */
class PokemonGoSearchTest {

    private fun counter(
        displayName: String,
        pokemonId: String,
        cp: Int?,
        quick: String?,
        charge: String?,
        shadow: Boolean = displayName.startsWith("Shadow"),
        rankedFast: String = "RANKED_FAST",
        rankedCharged: String = "RANKED_CHARGED"
    ) = PersonalCounter(
        owned = OwnedPokemon(
            displayName = displayName,
            form = null,
            level = 40.0,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            cp = cp,
            quickMove = quick,
            chargeMove = charge,
            shadow = shadow,
            lucky = false,
            matchKeys = listOf(pokemonId)
        ),
        pokemonId = pokemonId,
        displayName = displayName,
        fastMove = syntheticFastMove(rankedFast),
        chargedMove = syntheticChargedMove(rankedCharged),
        movesetAssumed = false,
        dps = 10.0,
        tdo = 100.0,
        rating = 50.0,
        estimatedAttackers = 6.0
    )

    private fun slot(vararg copies: PersonalCounter) =
        PersonalTeamSlot(copies.first(), copies.size, copies.toList())

    private val shadowChandelure = counter(
        "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 3555, "Fire Spin", "Overheat"
    )
    private val shadowReshiram = counter(
        "Shadow Reshiram", "RESHIRAM_SHADOW_FORM", 4499, "Fire Fang", "Fusion Flare"
    )
    private val regularChandelure = counter(
        "Chandelure", "CHANDELURE", 3268, "Hex", "Shadow Ball", shadow = false
    )
    private val websiteTeam = listOf(
        slot(shadowChandelure),
        slot(shadowReshiram),
        slot(regularChandelure)
    )
    private val dex = mapOf(
        "CHANDELURE_SHADOW_FORM" to 609,
        "RESHIRAM_SHADOW_FORM" to 643,
        "CHANDELURE" to 609
    )

    @Test
    fun `the supplied named example exactly matches the website`() {
        val query = PokemonGoSearch.expandTerms(
            listOf(
                listOf("Chandelure", "shadow", "cp3555", "@Fire Spin", "@Overheat"),
                listOf("Reshiram", "shadow", "cp4499", "@Fire Fang", "@Fusion Flare"),
                listOf("Chandelure", "cp3268", "@Hex", "@Shadow Ball")
            )
        )

        assertEquals(2_696, query.length)
        assertEquals(100, query.clauseCount())
        assertEquals(
            "dc1131f75173cc90b7c18cb368aeab9a08429aa58480239500d48575f97a9a01",
            query.sha256()
        )
    }

    @Test
    fun `the app's numbered team exactly matches the website`() {
        val query = PokemonGoSearch.teamQuery(websiteTeam, dex)

        assertEquals(2_316, query.length)
        assertEquals(100, query.clauseCount())
        assertEquals(
            "45b87c0087b6da81dcd75b9247bfe1f75f3b8614e2ffd07ac4962309475b1a66",
            query.sha256()
        )
    }

    @Test
    fun `website ordering and duplicate behavior are preserved`() {
        assertEquals(
            "B,A&B,a&b,A&b,a",
            PokemonGoSearch.expandTerms(listOf(listOf("A", "a"), listOf("B", "b")))
        )
        assertEquals(
            "A&A,x&x,A&x",
            PokemonGoSearch.expandTerms(listOf(listOf("A", "x"), listOf("A", "x")))
        )
    }

    @Test
    fun `a single shadow Pokemon remains a normal conjunction`() {
        assertEquals(
            "609&shadow&CP3555&@Fire Spin&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(shadowChandelure)), dex)
        )
    }

    @Test
    fun `regular Pokemon do not receive a negative shadow predicate`() {
        assertEquals(
            "609&CP3268&@Hex&@Shadow Ball",
            PokemonGoSearch.teamQuery(listOf(slot(regularChandelure)), dex)
        )
    }

    @Test
    fun `moves come from the owned copy and never from the ranked moveset`() {
        val query = PokemonGoSearch.teamQuery(websiteTeam, dex)
        assertFalse(query.contains("RANKED_FAST"))
        assertFalse(query.contains("RANKED_CHARGED"))
        assertTrue(query.contains("@Fire Spin"))
        assertTrue(query.contains("@Fusion Flare"))
    }

    @Test
    fun `missing data weakens only the affected Pokemon term`() {
        val missingCp = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", null, "Fire Fang", "Overheat"
        )
        val missingFast = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 4499, null, "Overheat"
        )

        assertEquals(
            "609&shadow&@Fire Fang&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(missingCp)), dex)
        )
        assertEquals(
            "609&shadow&CP4499&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(missingFast)), dex)
        )
    }

    @Test
    fun `zero CP and blank moves are treated as missing data`() {
        val sparse = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 0, " ", "Overheat"
        )
        assertEquals(
            "609&shadow&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(sparse)), dex)
        )
    }

    @Test
    fun `an unknown dex number falls back to the stripped species name`() {
        val query = PokemonGoSearch.teamQuery(
            listOf(
                slot(counter("Kyurem (Black)", "KYUREM_BLACK_FORM", 4000, "Dragon Breath", "Fusion Bolt"))
            ),
            emptyMap()
        )
        assertEquals("kyurem&CP4000&@Dragon Breath&@Fusion Bolt", query)
    }

    @Test
    fun `the species fallback strips mega shadow and form decorations`() {
        assertEquals("charizard", PokemonGoSearch.searchName("Mega Charizard Y"))
        assertEquals("gengar", PokemonGoSearch.searchName("Mega Gengar"))
        assertEquals("kyogre", PokemonGoSearch.searchName("Primal Kyogre"))
        assertEquals("kyurem", PokemonGoSearch.searchName("Kyurem (Black)"))
        assertEquals("machamp", PokemonGoSearch.searchName("Shadow Machamp"))
        assertEquals("machamp", PokemonGoSearch.searchName("Machamp"))
    }

    @Test
    fun `the species-only fallback remains compact and deduplicated`() {
        assertEquals("609,643", PokemonGoSearch.speciesQuery(websiteTeam, dex))
    }

    @Test
    fun `six complete members produce the bounded worst case`() {
        val terms = (1..6).map { index ->
            listOf("$index", "shadow", "CP$index", "@Fast $index", "@Charged $index")
        }
        val query = PokemonGoSearch.expandTerms(terms)

        assertEquals(15_625, query.clauseCount())
        assertTrue(query.isNotEmpty())
    }

    @Test
    fun `an empty team produces no stray separators`() {
        assertEquals("", PokemonGoSearch.teamQuery(emptyList(), dex))
        assertEquals("", PokemonGoSearch.speciesQuery(emptyList(), dex))
        assertEquals("", PokemonGoSearch.expandTerms(emptyList()))
    }

    private fun String.clauseCount(): Int = if (isEmpty()) 0 else count { it == '&' } + 1

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
