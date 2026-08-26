package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.SpeciesCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Poke Genie writes short form labels ("Sword", "Galar", "Baile") while Pokebattler spells
 * the id out in full (`ZACIAN_CROWNED_SWORD_FORM`). Every case below is checked twice: that
 * the expected id is offered ahead of the looser candidates, and that the id actually exists
 * in the real Pokebattler catalogue fixtures. The second half is the part that matters -- a
 * candidate list that agrees with a hand-written expectation but not with Pokebattler still
 * drops the Pokemon out of the counters.
 */
class PokebattlerFormLabelTest {

    private val catalogue: Set<String> = (
        fixture("species_rarity.tsv").map { it.substringBefore('\t') } +
            fixture("raid_boss_catalogue.tsv").mapNotNull { it.split('\t').getOrNull(1) }
        ).filter { it.isNotBlank() }.toSet()

    private fun fixture(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("counters/$name")) {
            "missing test resource counters/$name"
        }.bufferedReader().readLines().filter { it.isNotBlank() }

    /** Species, the label Poke Genie writes in its Form column, the id Pokebattler uses. */
    private val cases = listOf(
        // The bug this table was written for: Poke Genie abbreviates the crowned forms.
        Triple("Zacian", "Sword", "ZACIAN_CROWNED_SWORD_FORM"),
        Triple("Zamazenta", "Shield", "ZAMAZENTA_CROWNED_SHIELD_FORM"),
        Triple("Zacian", "Hero", "ZACIAN_HERO_FORM"),
        Triple("Zamazenta", "Hero", "ZAMAZENTA_HERO_FORM"),
        // ...but "Shield" and "Sword" must stay themselves everywhere else.
        Triple("Aegislash", "Shield", "AEGISLASH_SHIELD_FORM"),
        Triple("Aegislash", "Blade", "AEGISLASH_BLADE_FORM"),
        // Same class of bug, found by round-tripping the whole Pokebattler form vocabulary.
        Triple("Darmanitan", "Galar", "DARMANITAN_GALARIAN_STANDARD_FORM"),
        Triple("Darmanitan", "Zen", "DARMANITAN_ZEN_FORM"),
        Triple("Zygarde", "50%", "ZYGARDE_FIFTY_PERCENT_FORM"),
        Triple("Zygarde", "10%", "ZYGARDE_TEN_PERCENT_FORM"),
        Triple("Zygarde", "Complete", "ZYGARDE_COMPLETE_FORM"),
        Triple("Dudunsparce", "Two-Segment", "DUDUNSPARCE_TWO_FORM"),
        Triple("Dudunsparce", "Three-Segment", "DUDUNSPARCE_THREE_FORM"),
        Triple("Tauros", "Paldean Blaze", "TAUROS_PALDEA_BLAZE_FORM"),
        Triple("Tauros", "Paldean Aqua", "TAUROS_PALDEA_AQUA_FORM"),
        Triple("Tauros", "Paldean Combat", "TAUROS_PALDEA_COMBAT_FORM"),
        Triple("Oricorio", "Pom-Pom", "ORICORIO_POMPOM_FORM"),
        Triple("Wormadam", "Plant Cloak", "WORMADAM_PLANT_FORM"),
        // Cases that already worked, kept so the extra candidates cannot regress them.
        Triple("Kyurem", "Black", "KYUREM_BLACK_FORM"),
        Triple("Kyurem", "White", "KYUREM_WHITE_FORM"),
        Triple("Necrozma", "Dusk Mane", "NECROZMA_DUSK_MANE_FORM"),
        Triple("Necrozma", "Dawn Wings", "NECROZMA_DAWN_WINGS_FORM"),
        Triple("Landorus", "Therian", "LANDORUS_THERIAN_FORM"),
        Triple("Giratina", "Origin", "GIRATINA_ORIGIN_FORM"),
        Triple("Marowak", "Alola", "MAROWAK_ALOLA_FORM"),
        Triple("Slowpoke", "Galar", "SLOWPOKE_GALARIAN_FORM"),
        Triple("Qwilfish", "Hisui", "QWILFISH_HISUIAN_FORM"),
        Triple("Wooper", "Paldea", "WOOPER_PALDEA_FORM"),
        Triple("Oricorio", "Baile", "ORICORIO_BAILE_FORM"),
        Triple("Pumpkaboo", "Small", "PUMPKABOO_SMALL_FORM"),
        Triple("Pumpkaboo", "Super", "PUMPKABOO_SUPER_FORM"),
        Triple("Shellos", "East Sea", "SHELLOS_EAST_SEA_FORM"),
        Triple("Urshifu", "Rapid Strike", "URSHIFU_RAPID_STRIKE_FORM"),
        Triple("Calyrex", "Ice Rider", "CALYREX_ICE_RIDER_FORM"),
        Triple("Toxtricity", "Low Key", "TOXTRICITY_LOW_KEY_FORM"),
        Triple("Basculin", "White Striped", "BASCULIN_WHITE_STRIPED_FORM"),
        Triple("Rotom", "Wash", "ROTOM_WASH_FORM"),
        Triple("Charizard", "Mega Y", "CHARIZARD_MEGA_Y"),
        Triple("Kyogre", "Primal", "KYOGRE_PRIMAL")
    )

    @Test
    fun `every Poke Genie form label reaches its Pokebattler id`() {
        val misses = cases.filterNot { (species, form, expected) ->
            expected in PokebattlerNameNormalizer.candidateIds(species, form)
        }
        assertEquals(emptyList<Triple<String, String, String>>(), misses)
    }

    @Test
    fun `the expected id is preferred over the looser candidates`() {
        cases.forEach { (species, form, expected) ->
            val candidates = PokebattlerNameNormalizer.candidateIds(species, form)
            val chosen = candidates.first { it in catalogue }
            assertEquals("$species ($form)", expected, chosen)
        }
    }

    @Test
    fun `the catalogue resolver reaches the same id`() {
        // The static tables and the downloaded catalogue must not disagree: the resolver is
        // what carries a form Pokebattler ships after this table was last edited.
        val resolver = SpeciesCatalogue(catalogue)
        cases.forEach { (species, form, expected) ->
            val candidates = PokebattlerNameNormalizer.candidateIds(species, form)
            assertEquals("$species ($form)", expected, resolver.resolve(candidates))
        }
    }

    @Test
    fun `every expected id exists in the real catalogue`() {
        val unknown = cases.map { it.third }.filterNot { it in catalogue }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun `a base form label still resolves to the bare species`() {
        assertEquals(listOf("MEWTWO"), PokebattlerNameNormalizer.candidateIds("Mewtwo", "Normal"))
        assertEquals(listOf("NECROZMA"), PokebattlerNameNormalizer.candidateIds("Necrozma", "Normal"))
    }

    @Test
    fun `an unknown form still falls back to the species`() {
        val candidates = PokebattlerNameNormalizer.candidateIds("Pikachu", "Flying 5th Anniv")
        assertTrue(candidates.contains("PIKACHU"))
        assertEquals("PIKACHU", candidates.last())
    }

    @Test
    fun `shadow still outranks form`() {
        val candidates =
            PokebattlerNameNormalizer.candidateIds("Giratina", form = "Origin", shadow = true)
        assertEquals("GIRATINA_ORIGIN_SHADOW_FORM", candidates.first())
        assertTrue(candidates.indexOf("GIRATINA_SHADOW_FORM") < candidates.indexOf("GIRATINA_ORIGIN_FORM"))
    }
}
