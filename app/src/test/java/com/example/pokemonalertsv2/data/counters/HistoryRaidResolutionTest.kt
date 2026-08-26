package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every raid the real alert feed has ever produced must resolve to a Pokébattler boss.
 *
 * The fixtures are real data, not invented cases: `history_raid_bosses.tsv` is the 257
 * distinct `(pokemon, form)` pairs across 1879 raid alerts from the live feed, and
 * `raid_boss_catalogue.tsv` / `species_rarity.tsv` are the full `/raids` and `/pokemon`
 * catalogues as the app cached them. Between them they cover the awkward shapes that
 * invented fixtures kept missing — costume Pikachu, parenthesised event forms, a shadow
 * *and* form-bearing Giratina, Kyurem Black/White, Zacian/Zamazenta Crowned, Genesect
 * Burn/Chill, Deoxys Attack/Defense/Speed, primals, 30+ megas and three regional families.
 *
 * Regenerate with the script in the repository notes if the feed starts producing a shape
 * that is not represented here.
 */
class HistoryRaidResolutionTest {

    private data class HistoryBoss(val count: Int, val pokemon: String, val form: String?)

    private fun resource(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("counters/$name")) {
            "missing test resource counters/$name"
        }.bufferedReader().readLines().filter { it.isNotBlank() }

    private val catalogue: List<RaidBossCatalogEntry> by lazy {
        resource("raid_boss_catalogue.tsv").map { line ->
            val parts = line.split("\t")
            RaidBossCatalogEntry(
                tier = parts[0],
                pokemonId = parts[1],
                displayName = parts.getOrNull(2).orEmpty(),
                cp = parts.getOrNull(3)?.toIntOrNull()
            )
        }
    }

    private val rarity: Map<String, String?> by lazy {
        resource("species_rarity.tsv").associate { line ->
            val parts = line.split("\t")
            parts[0].uppercase() to parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private val history: List<HistoryBoss> by lazy {
        resource("history_raid_bosses.tsv").map { line ->
            val parts = line.split("\t")
            HistoryBoss(
                count = parts[0].toInt(),
                pokemon = parts[1],
                form = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun rarityFor(id: String): String? =
        rarity[id.uppercase()] ?: rarity[PokebattlerNameNormalizer.baseSpeciesId(id)]

    /** Mirrors `RaidCountersRepository.resolveAgainstCatalogue`, species fallback included. */
    private fun resolve(boss: HistoryBoss): BossResolution {
        val candidates = PokebattlerNameNormalizer.candidateIds(boss.pokemon, boss.form)
        val fromCatalogue = resolveBossFromCatalogue(
            candidates = candidates,
            // Raid alerts arrive as type ["Raid"] with no tier token, so the tier always
            // comes from the catalogue or the rarity fallback.
            parsedTier = null,
            catalogue = catalogue,
            attemptedName = boss.pokemon,
            rarityFor = ::rarityFor
        )
        if (fromCatalogue is BossResolution.Resolved) return fromCatalogue
        // The /raids catalogue omits a few real bosses (HONEDGE); the repository falls back
        // to the species catalogue with an inferred tier.
        val speciesHit = candidates.firstOrNull { rarity.containsKey(it.uppercase()) }
            ?: return fromCatalogue
        return BossResolution.Resolved(
            pokemonId = speciesHit,
            raidLevel = fallbackRaidLevel(speciesHit, rarityFor(speciesHit)),
            displayName = boss.pokemon,
            bossCp = null
        )
    }

    private fun resolved(pokemon: String, form: String? = null): BossResolution.Resolved {
        val result = resolve(HistoryBoss(1, pokemon, form))
        assertTrue("$pokemon / $form did not resolve: $result", result is BossResolution.Resolved)
        return result as BossResolution.Resolved
    }

    @Test
    fun `the fixtures are the real feed, not a sample`() {
        assertEquals(257, history.size)
        assertEquals(1879, history.sumOf { it.count })
        assertEquals(3303, catalogue.size)
    }

    @Test
    fun `every raid boss the feed has produced resolves`() {
        val unresolved = history.filter { resolve(it) !is BossResolution.Resolved }
        assertTrue(
            "unresolved: " + unresolved.joinToString { "${it.pokemon}/${it.form}" },
            unresolved.isEmpty()
        )
    }

    @Test
    fun `every resolved boss gets a queryable tier, never the unset bucket`() {
        history.forEach { boss ->
            val tier = (resolve(boss) as BossResolution.Resolved).raidLevel
            assertTrue("${boss.pokemon}: $tier", tier.isNotBlank())
            assertEquals("${boss.pokemon} must not query the every-Pokemon bucket",
                false, tier == "RAID_LEVEL_UNSET")
            assertEquals("${boss.pokemon}: tier must be normalized", tier, normalizeTier(tier))
        }
    }

    @Test
    fun `shadow outranks form, because Pokebattler models one shadow Giratina`() {
        // GIRATINA_ALTERED_FORM exists and is not shadow, so emitting the form first would
        // confidently resolve this to the wrong boss.
        assertEquals("GIRATINA_SHADOW_FORM", resolved("Shadow Giratina Altered Forme").pokemonId)
        assertEquals("GIRATINA_ALTERED_FORM", resolved("Giratina", "Altered Forme").pokemonId)
        assertEquals("GIRATINA_ORIGIN_FORM", resolved("Giratina", "Origin Forme").pokemonId)
    }

    @Test
    fun `primals and megas keep their own identity`() {
        assertEquals("KYOGRE_PRIMAL", resolved("Primal Kyogre").pokemonId)
        assertEquals("GROUDON_PRIMAL", resolved("Primal Groudon").pokemonId)
        assertEquals("RAID_LEVEL_MEGA_5", resolved("Primal Kyogre").raidLevel)
        // A mega must never fall back to the bare species.
        listOf("Mega Garchomp", "Mega Swampert", "Mega Gengar", "Mega Tyranitar")
            .forEach { assertTrue(it, resolved(it).pokemonId.contains("MEGA")) }
    }

    @Test
    fun `named forms resolve to their own boss`() {
        // The feed writes the form as "Black Kyurem", repeating the species. Left alone
        // that falls through to the bare KYUREM -- a different boss with different typing.
        assertEquals("KYUREM_BLACK_FORM", resolved("Kyurem", "Black Kyurem").pokemonId)
        assertEquals("KYUREM_WHITE_FORM", resolved("Kyurem", "White Kyurem").pokemonId)
        assertEquals("KYUREM", resolved("Kyurem").pokemonId)
        assertEquals("NECROZMA_DUSK_MANE_FORM", resolved("Necrozma", "Dusk Mane").pokemonId)
        assertEquals("NECROZMA_DAWN_WINGS_FORM", resolved("Necrozma", "Dawn Wings").pokemonId)
        assertEquals("ZACIAN_CROWNED_SWORD_FORM", resolved("Zacian", "Crowned Sword").pokemonId)
        assertEquals("ZAMAZENTA_CROWNED_SHIELD_FORM", resolved("Zamazenta", "Crowned Shield").pokemonId)
        assertEquals("DEOXYS_ATTACK_FORM", resolved("Deoxys", "Attack").pokemonId)
        assertEquals("DEOXYS_DEFENSE_FORM", resolved("Deoxys", "Defense").pokemonId)
        assertEquals("DEOXYS_SPEED_FORM", resolved("Deoxys", "Speed").pokemonId)
    }

    @Test
    fun `regional forms resolve to the regional boss`() {
        assertEquals("MAROWAK_ALOLA_FORM", resolved("Marowak", "Alola").pokemonId)
        assertEquals("SAMUROTT_HISUIAN_FORM", resolved("Samurott", "Hisuian").pokemonId)
        assertTrue(resolved("Mr. Mime", "Galarian").pokemonId.startsWith("MR_MIME_GALARIAN"))
        assertTrue(resolved("Stunfisk", "Galarian").pokemonId.startsWith("STUNFISK_GALARIAN"))
    }

    @Test
    fun `costume and event forms resolve to a Pokemon of the right species and tier`() {
        // Pokebattler does model most costumes as their own id, so prefer that; what must
        // never happen is landing on a different species, or on the wrong tier -- tier 1
        // versus tier 3 is a fourfold difference in boss HP.
        listOf(
            "Gotour 2026 C 02", "Wcs 2022", "Gofest 2025 Monocle Yellow", "Gotour 2024 A"
        ).forEach { form ->
            val hit = resolved("Pikachu", form)
            assertTrue("Pikachu / $form -> ${hit.pokemonId}", hit.pokemonId.startsWith("PIKACHU"))
            // Costumed Pikachu only ever appear as tier-1 raids.
            assertEquals("Pikachu / $form", "RAID_LEVEL_1", hit.raidLevel)
        }
        assertEquals("SUDOWOODO_WINTER_2025_FORM", resolved("Sudowoodo", "Winter 2025").pokemonId)
        assertEquals("PSYDUCK_SWIM_2025_FORM", resolved("Psyduck", "Swim 2025").pokemonId)
        // A costume with no id of its own falls back to the plain species rather than failing.
        assertEquals("NIDORINO", resolved("Nidorino", "(Pokemon Day)").pokemonId)
        assertEquals("GRIMER", resolved("Grimer", "(Anniversary 2024)").pokemonId)
    }

    @Test
    fun `placeholder form labels are ignored`() {
        assertEquals("FURFROU", resolved("Furfrou", "Natural").pokemonId)
        assertEquals("TORNADUS_INCARNATE_FORM", resolved("Tornadus", "Incarnate").pokemonId)
        // The feed writes both spellings for the same thing.
        assertEquals(
            resolved("Tornadus", "Incarnate").pokemonId,
            resolved("Tornadus", "Incarnate Forme").pokemonId
        )
    }

    @Test
    fun `shadow bosses resolve to a shadow id and a shadow tier`() {
        listOf("Shadow Heatran", "Shadow Cresselia", "Shadow Dialga", "Shadow Palkia")
            .forEach { name ->
                val hit = resolved(name)
                assertTrue(name, hit.pokemonId.contains("SHADOW"))
                assertTrue("$name: ${hit.raidLevel}", hit.raidLevel.endsWith("_SHADOW"))
            }
    }

    @Test
    fun `Honedge resolves through the species catalogue`() {
        // HONEDGE is in /pokemon but has no /raids row at all, which used to cost every
        // Honedge raid its counters.
        assertTrue(catalogue.none { it.pokemonId.equals("HONEDGE", ignoreCase = true) })
        val hit = resolved("Honedge")
        assertEquals("HONEDGE", hit.pokemonId)
        assertEquals("RAID_LEVEL_3", hit.raidLevel)
    }
}
