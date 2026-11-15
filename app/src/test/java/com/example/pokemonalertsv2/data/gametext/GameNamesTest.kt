package com.example.pokemonalertsv2.data.gametext

import com.example.pokemonalertsv2.data.AlertTranslation
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.MoveIds
import com.example.pokemonalertsv2.ui.alerts.questAlertPresentation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GameNamesTest {
    private val german = GameTextDictionary(
        lang = "de",
        names = mapOf(
            "pokemon" to mapOf("6" to "Glurak", "122" to "Pantimos"),
            "move" to mapOf("14" to "Hyperstrahl", "200" to "Feuerwirbel"),
            "grunt" to mapOf("10" to "Unlicht - Rüpel (Weiblich)")
        ),
        byEnglish = mapOf(
            "pokemon" to mapOf("charizard" to "Glurak", "mrmime" to "Pantimos"),
            "move" to mapOf("hyperbeam" to "Hyperstrahl", "firespin" to "Feuerwirbel"),
            "type" to mapOf("fire" to "Feuer"),
            "form" to mapOf("alola" to "Alola"),
            "item" to mapOf("greatball" to "Superball"),
            "reward" to mapOf("stardust" to "Sternenstaub")
        )
    )
    private var savedLocale: Locale = Locale.getDefault()

    @Before fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        GameNames.override = german
    }

    @After fun tearDown() {
        GameNames.override = null
        Locale.setDefault(savedLocale)
    }

    @Test fun idsWinThenEnglishNamesThenTheOriginal() {
        assertEquals("Glurak", GameNames.pokemon("Charizard"))
        assertEquals("Glurak", GameNames.pokemon(null, dexId = 6))
        assertEquals("Pantimos", GameNames.pokemon("Mr. Mime"))
        assertEquals("Hyperstrahl", GameNames.move("Something else", moveId = 14))
        assertEquals("Unknownmon", GameNames.pokemon("Unknownmon"))
        assertEquals(null, GameNames.move(null))
    }

    @Test fun formsAndPrefixesKeepTheirOtherWords() {
        assertEquals("Mega Glurak X", GameNames.pokemon("Mega Charizard X", dexId = 6))
        assertEquals("Shadow Glurak", GameNames.pokemon("Shadow Charizard"))
    }

    @Test fun rewardsTranslateNamesAndKeepAmounts() {
        assertEquals("Superball ×5", GameNames.reward("Great Ball ×5"))
        assertEquals("Glurak", GameNames.reward("Charizard"))
        assertEquals("500 Sternenstaub", GameNames.reward("500 Stardust"))
        assertEquals("Avatar Clothing", GameNames.reward("Avatar Clothing"))
    }

    @Test fun gruntsFallBackToTypes() {
        assertEquals("Unlicht - Rüpel (Weiblich)", GameNames.grunt("Dark", gruntId = 10))
        assertEquals("Feuer", GameNames.grunt("Fire"))
    }

    @Test fun displayHelpersNeverChangeTheAlert() {
        val alert = PokemonAlert(
            name = "Charizard raid", pokemon = "Charizard", pokedexId = 6, type = listOf("Quest"),
            moves = PokemonMoves("Fire Spin", "Hyper Beam"), moveIds = MoveIds(200, 14),
            questTask = "Catch 5 Pokémon", questReward = "500 Stardust",
            i18n = mapOf("de" to AlertTranslation("Fange 5 Pokémon", "500 Sternenstaub"))
        )
        assertEquals("Glurak", alert.displayPokemon)
        assertEquals("Feuerwirbel / Hyperstrahl", alert.displayMoves)
        assertEquals("Fange 5 Pokémon", questAlertPresentation(alert)?.task)
        assertEquals("500 Sternenstaub", questAlertPresentation(alert)?.reward)
        assertEquals("Charizard", alert.pokemon)
    }

    @Test fun englishShowsEverythingUnchanged() {
        Locale.setDefault(Locale.ENGLISH)
        GameNames.override = null
        assertEquals("Charizard", GameNames.pokemon("Charizard"))
        val alert = PokemonAlert(name = "q", type = listOf("Quest"), questTask = "Catch 5 Pokémon",
            i18n = mapOf("de" to AlertTranslation("Fange 5 Pokémon")))
        assertEquals("Catch 5 Pokémon", questAlertPresentation(alert)?.task)
    }
}
