package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntChipTextTest {

    @Test
    fun `a spawn in range reports its CP`() {
        val alert = PokemonAlert(name = "Larvitar", type = listOf("Spawn"), pokemon = "Larvitar", cp = 1234)

        assertEquals("CP 1234", huntInRangeChipText(alert))
    }

    @Test
    fun `a rocket in range reports the stop it is sitting on`() {
        val alert = PokemonAlert(
            name = "Dragon Grunt",
            type = listOf("Rocket"),
            gruntType = "Dragon",
            pokestop = "Alter Wasserturm"
        )

        assertEquals("Alter Wasserturm", huntInRangeChipText(alert))
    }

    @Test
    fun `a kecleon in range reports its stop`() {
        val alert = PokemonAlert(
            name = "Kecleon",
            type = listOf("Kecleon"),
            pokestop = "Rathaus"
        )

        assertEquals("Rathaus", huntInRangeChipText(alert))
    }

    @Test
    fun `a quest in range reports the reward, falling back to the stop`() {
        val withReward = PokemonAlert(
            name = "Spinda Quest",
            type = listOf("Quest"),
            questReward = "Spinda",
            pokestop = "Brunnen"
        )
        assertEquals("Spinda", huntInRangeChipText(withReward))

        val withoutReward = PokemonAlert(
            name = "Quest",
            type = listOf("Quest"),
            pokestop = "Brunnen"
        )
        assertEquals("Brunnen", huntInRangeChipText(withoutReward))
    }

    @Test
    fun `raids yield the slot to the raid live update`() {
        val alert = PokemonAlert(
            name = "Rayquaza",
            type = listOf("Raid"),
            pokemon = "Rayquaza",
            cp = 2191,
            gym = "Marktplatz"
        )

        assertNull(huntInRangeChipText(alert))
    }

    @Test
    fun `an alert with nothing worth showing falls back to the caller's wording`() {
        val alert = PokemonAlert(name = "Something", type = listOf("Spawn"), pokemon = "Ditto")

        assertNull(huntInRangeChipText(alert))
    }

    @Test
    fun `a long venue name is elided rather than cut mid-word`() {
        val alert = PokemonAlert(
            name = "Grunt",
            type = listOf("Rocket"),
            gruntType = "Dragon",
            pokestop = "Sehr Langer Pokestop Name Der Nicht Passt"
        )

        val chip = requireNotNull(huntInRangeChipText(alert))
        assertTrue(chip.length <= 18)
        assertTrue(chip.endsWith("…"))
    }

    @Test
    fun `a hundo spawn is still a spawn and reports CP`() {
        val alert = PokemonAlert(
            name = "Larvitar",
            type = listOf("Spawn", "Hundo"),
            pokemon = "Larvitar",
            ivAttack = 15,
            ivDefense = 15,
            ivStamina = 15,
            cp = 1234
        )

        assertEquals("CP 1234", huntInRangeChipText(alert))
    }
}
