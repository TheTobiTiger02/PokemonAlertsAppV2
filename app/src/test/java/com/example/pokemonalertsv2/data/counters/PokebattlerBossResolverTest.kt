package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.RaidTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokebattlerNameNormalizerTest {

    private fun first(name: String, form: String? = null) =
        PokebattlerNameNormalizer.candidateIds(name, form).first()

    @Test
    fun `normalizes awkward species names`() {
        assertEquals("FARFETCHD", first("Farfetch'd"))
        assertEquals("MR_MIME", first("Mr. Mime"))
        assertEquals("HO_OH", first("Ho-Oh"))
        assertEquals("TYPE_NULL", first("Type: Null"))
        assertEquals("PORYGON_Z", first("Porygon-Z"))
        assertEquals("JANGMO_O", first("Jangmo-o"))
        assertEquals("FLABEBE", first("Flab\u00e9b\u00e9"))
    }

    @Test
    fun `maps gender symbols`() {
        assertEquals("NIDORAN_FEMALE", first("Nidoran\u2640"))
        assertEquals("NIDORAN_MALE", first("Nidoran\u2642"))
    }

    @Test
    fun `emits both underscore form and bare form spellings`() {
        val ids = PokebattlerNameNormalizer.candidateIds("Kyurem", "Black")
        assertTrue(ids.toString(), ids.contains("KYUREM_BLACK_FORM"))
        assertTrue(ids.toString(), ids.contains("KYUREM_BLACK"))
        assertTrue(ids.toString(), ids.contains("KYUREM"))
        assertTrue("form spelling must be tried first", ids.indexOf("KYUREM_BLACK_FORM") < ids.indexOf("KYUREM"))
    }

    @Test
    fun `handles mega x and y variants`() {
        val ids = PokebattlerNameNormalizer.candidateIds("Mega Charizard X")
        assertTrue(ids.toString(), ids.contains("CHARIZARD_MEGA_X"))
        assertTrue(PokebattlerNameNormalizer.candidateIds("Mega Gengar").contains("GENGAR_MEGA"))
    }

    @Test
    fun `a mega or primal never falls back to the plain species`() {
        // Pokebattler treats these as separate Pokemon with their own stats, so matching a
        // Mega Charizard onto plain CHARIZARD would give the wrong counters entirely, and
        // would report a Mega Y Mewtwo in a Poke Genie box as an ordinary Mewtwo.
        assertFalse(PokebattlerNameNormalizer.candidateIds("Mega Charizard X").contains("CHARIZARD"))
        assertFalse(PokebattlerNameNormalizer.candidateIds("Mega Gengar").contains("GENGAR"))
        assertFalse(PokebattlerNameNormalizer.candidateIds("Primal Groudon").contains("GROUDON"))
        // Shadow still falls back, because ownership matching filters on the shadow flag
        // and boss resolution degrades more usefully than it fails.
        assertTrue(PokebattlerNameNormalizer.candidateIds("Shadow Houndoom").contains("HOUNDOOM"))
    }

    @Test
    fun `accepts an explicit mega variant`() {
        val ids = PokebattlerNameNormalizer.candidateIds("Mewtwo", mega = true, megaVariant = "Y")
        assertTrue(ids.toString(), ids.contains("MEWTWO_MEGA_Y"))
        assertFalse(ids.toString(), ids.contains("MEWTWO"))
    }

    @Test
    fun `handles regional prefixes shadow and primal`() {
        assertTrue(PokebattlerNameNormalizer.candidateIds("Alolan Marowak").contains("MAROWAK_ALOLA"))
        assertTrue(PokebattlerNameNormalizer.candidateIds("Galarian Darmanitan").contains("DARMANITAN_GALARIAN"))
        assertTrue(PokebattlerNameNormalizer.candidateIds("Primal Groudon").contains("GROUDON_PRIMAL"))
        assertTrue(PokebattlerNameNormalizer.candidateIds("Shadow Houndoom").contains("HOUNDOOM_SHADOW_FORM"))
    }

    @Test
    fun `ignores placeholder form labels`() {
        assertEquals(listOf("PIKACHU"), PokebattlerNameNormalizer.candidateIds("Pikachu", "Normal"))
    }

    @Test
    fun `accepts the Forme spelling`() {
        assertTrue(
            PokebattlerNameNormalizer.candidateIds("Giratina Altered Forme")
                .contains("GIRATINA_ALTERED_FORM")
        )
    }

    @Test
    fun `reads a form written after the species name`() {
        val ids = PokebattlerNameNormalizer.candidateIds("Shadow Giratina Altered Forme")
        // Shadow outranks form: Pokebattler has one shadow Giratina, not one per form.
        assertTrue(ids.toString(), ids.contains("GIRATINA_SHADOW_FORM"))
        assertTrue(
            "shadow must be offered before the non-shadow form id",
            ids.indexOf("GIRATINA_SHADOW_FORM") < ids.indexOf("GIRATINA_ALTERED_FORM")
        )
    }

    @Test
    fun `handles a two word trailing form`() {
        assertTrue(
            PokebattlerNameNormalizer.candidateIds("Necrozma Dawn Wings")
                .contains("NECROZMA_DAWN_WINGS_FORM")
        )
    }

    @Test
    fun `a single word name is never emptied`() {
        assertEquals(listOf("LUNALA"), PokebattlerNameNormalizer.candidateIds("Lunala"))
    }

    @Test
    fun `loose key ignores underscores`() {
        assertEquals("KYUREMBLACKFORM", PokebattlerNameNormalizer.looseKey("KYUREM_BLACK_FORM"))
    }
}

class PokebattlerBossResolverTest {

    private val catalogue = listOf(
        RaidBossCatalogEntry("RAID_LEVEL_5", "MEWTWO", "Mewtwo", 54148),
        RaidBossCatalogEntry("RAID_LEVEL_5_LEGACY", "MEWTWO", "Mewtwo", 54148),
        RaidBossCatalogEntry("RAID_LEVEL_1", "IMPIDIMP", "Impidimp", 0),
        RaidBossCatalogEntry("RAID_LEVEL_ELITE_LEGACY", "REGIGIGAS", "Regigigas", 30000),
        RaidBossCatalogEntry("RAID_LEVEL_5_FUTURE", "KYUREM_BLACK_FORM", "Kyurem (Black)", 40000),
        RaidBossCatalogEntry("RAID_LEVEL_MEGA", "CHARIZARD_MEGA_Y", "Mega Charizard Y", 50000),
        RaidBossCatalogEntry("RAID_LEVEL_3", "ALAKAZAM_SHADOW_FORM", "Shadow Alakazam", 20000)
    )

    private fun resolve(name: String, tier: RaidTier?, form: String? = null) =
        resolveBossFromCatalogue(PokebattlerNameNormalizer.candidateIds(name, form), tier, catalogue, name)

    @Test
    fun `matches inside the claimed tier`() {
        val r = resolve("Mewtwo", RaidTier.TIER_5) as BossResolution.Resolved
        assertEquals("MEWTWO", r.pokemonId)
        assertEquals("RAID_LEVEL_5", r.raidLevel)
        assertEquals(54148, r.bossCp)
    }

    @Test
    fun `the alert tier wins over the catalogue tier`() {
        // The catalogue only lists Regigigas as a legacy Elite boss, but the alert says it
        // is running as a 5-star right now, and the alert describes the live raid.
        val r = resolve("Regigigas", RaidTier.TIER_5) as BossResolution.Resolved
        assertEquals("RAID_LEVEL_5", r.raidLevel)
    }

    @Test
    fun `substitutes a real tier rather than querying the unset bucket`() {
        val onlyUnset = listOf(RaidBossCatalogEntry("RAID_LEVEL_UNSET", "DITTO", "Ditto", 0))
        // RAID_LEVEL_UNSET is the "every Pokemon" bucket, not a raid tier, so it must never
        // be queried. Refusing outright used to lose 34 of 252 real alerts, so an inferred
        // tier is substituted instead.
        val r = resolveBossFromCatalogue(listOf("DITTO"), null, onlyUnset, "Ditto") as BossResolution.Resolved
        assertEquals("RAID_LEVEL_3", r.raidLevel)
        assertTrue(r.raidLevel != "RAID_LEVEL_UNSET")
        // A tier on the alert still wins.
        val ok = resolveBossFromCatalogue(listOf("DITTO"), RaidTier.TIER_1, onlyUnset, "Ditto") as BossResolution.Resolved
        assertEquals("RAID_LEVEL_1", ok.raidLevel)
    }

    @Test
    fun `uses rarity to infer the tier of an unset legendary`() {
        // Solgaleo is only in the unset bucket, and the feed sends no tier token.
        val onlyUnset = listOf(RaidBossCatalogEntry("RAID_LEVEL_UNSET", "SOLGALEO", "Solgaleo", 0))
        val r = resolveBossFromCatalogue(listOf("SOLGALEO"), null, onlyUnset, "Solgaleo") {
            "POKEMON_RARITY_LEGENDARY"
        } as BossResolution.Resolved
        assertEquals("RAID_LEVEL_5", r.raidLevel)
    }

    @Test
    fun `a shadow legendary falls back to the shadow tier`() {
        val onlyUnset = listOf(
            RaidBossCatalogEntry("RAID_LEVEL_UNSET", "PALKIA_SHADOW_FORM", "Shadow Palkia", 0)
        )
        val r = resolveBossFromCatalogue(
            listOf("PALKIA_SHADOW_FORM"), null, onlyUnset, "Shadow Palkia"
        ) { "POKEMON_RARITY_LEGENDARY" } as BossResolution.Resolved
        assertEquals("RAID_LEVEL_5_SHADOW", r.raidLevel)
    }

    @Test
    fun `resolves a shadow boss whose form is written into the name`() {
        // The single most common raid in the real feed, and the one that used to fail.
        // Pokebattler models ONE shadow Giratina, so shadow must beat the form: picking
        // GIRATINA_ALTERED_FORM would silently return a non-shadow boss.
        val cat = listOf(
            RaidBossCatalogEntry("RAID_LEVEL_UNSET", "GIRATINA", "Giratina", 0),
            RaidBossCatalogEntry("RAID_LEVEL_5", "GIRATINA_ALTERED_FORM", "Giratina (Altered)", 0),
            RaidBossCatalogEntry("RAID_LEVEL_5", "GIRATINA_ORIGIN_FORM", "Giratina (Origin)", 0),
            RaidBossCatalogEntry("RAID_LEVEL_5", "GIRATINA_SHADOW_FORM", "Shadow Giratina", 0)
        )
        val ids = PokebattlerNameNormalizer.candidateIds("Shadow Giratina Altered Forme")
        val r = resolveBossFromCatalogue(ids, null, cat, "Shadow Giratina Altered Forme")
            as BossResolution.Resolved
        assertEquals("GIRATINA_SHADOW_FORM", r.raidLevel.let { r.pokemonId })
        assertEquals("RAID_LEVEL_5", r.raidLevel)
    }

    @Test
    fun `prefers a real tier over unset when the alert has none`() {
        val mixed = listOf(
            RaidBossCatalogEntry("RAID_LEVEL_UNSET", "LANDORUS_THERIAN_FORM", "Landorus", 0),
            RaidBossCatalogEntry("RAID_LEVEL_5_LEGACY", "LANDORUS_THERIAN_FORM", "Landorus", 40000)
        )
        val r = resolveBossFromCatalogue(listOf("LANDORUS_THERIAN_FORM"), null, mixed, "Landorus") as BossResolution.Resolved
        assertEquals("RAID_LEVEL_5", r.raidLevel)
    }

    @Test
    fun `falls back to a fuller catalogue spelling of a form`() {
        val cat = listOf(
            // The bare species is also in the catalogue and must NOT win: the more specific
            // candidate gets to try a prefix match before the looser one is considered.
            RaidBossCatalogEntry("RAID_LEVEL_UNSET", "DARMANITAN", "Darmanitan", 0),
            RaidBossCatalogEntry("RAID_LEVEL_3", "DARMANITAN_GALARIAN_STANDARD_FORM", "Galarian Darmanitan", 20000),
            RaidBossCatalogEntry("RAID_LEVEL_3", "DARMANITAN_GALARIAN_ZEN_FORM", "Galarian Darmanitan (Zen)", 22000)
        )
        val ids = PokebattlerNameNormalizer.candidateIds("Galarian Darmanitan")
        val r = resolveBossFromCatalogue(ids, RaidTier.TIER_3, cat, "Galarian Darmanitan") as BossResolution.Resolved
        assertEquals("DARMANITAN_GALARIAN_STANDARD_FORM", r.pokemonId)
    }

    @Test
    fun `resolves when the alert carries no tier at all`() {
        val r = resolve("Impidimp", null) as BossResolution.Resolved
        assertEquals("RAID_LEVEL_1", r.raidLevel)
    }

    @Test
    fun `reads a form written in front of the species name`() {
        val ids = PokebattlerNameNormalizer.candidateIds("Therian Landorus")
        assertTrue(ids.toString(), ids.contains("LANDORUS_THERIAN_FORM"))
    }

    @Test
    fun `strips future and legacy suffixes from the queried tier`() {
        val r = resolve("Kyurem", RaidTier.TIER_5, "Black") as BossResolution.Resolved
        assertEquals("KYUREM_BLACK_FORM", r.pokemonId)
        assertEquals("RAID_LEVEL_5", r.raidLevel)
    }

    @Test
    fun `resolves mega and shadow forms`() {
        val mega = resolve("Mega Charizard Y", RaidTier.MEGA) as BossResolution.Resolved
        assertEquals("CHARIZARD_MEGA_Y", mega.pokemonId)
        val shadow = resolve("Shadow Alakazam", RaidTier.TIER_3) as BossResolution.Resolved
        assertEquals("ALAKAZAM_SHADOW_FORM", shadow.pokemonId)
    }

    @Test
    fun `reports unresolved rather than guessing`() {
        assertTrue(resolve("Definitely Not A Pokemon", RaidTier.TIER_5) is BossResolution.Unresolved)
        assertTrue(resolveBossFromCatalogue(emptyList(), RaidTier.TIER_5, catalogue, "") is BossResolution.Unresolved)
        assertTrue(
            "empty catalogue must not resolve",
            resolveBossFromCatalogue(listOf("MEWTWO"), RaidTier.TIER_5, emptyList(), "Mewtwo") is BossResolution.Unresolved
        )
    }
}
