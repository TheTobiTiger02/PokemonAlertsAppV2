package com.example.pokemonalertsv2.data.pokegenie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokeGenieMatcherTest {

    private fun row(
        name: String,
        form: String? = null,
        level: Double? = 40.0,
        atk: Int? = 15,
        def: Int? = 15,
        sta: Int? = 15,
        cp: Int? = 3000,
        shadow: ShadowState = ShadowState.NORMAL
    ) = PokeGenieRow(
        name = name,
        form = form,
        level = level,
        atkIv = atk,
        defIv = def,
        staIv = sta,
        cp = cp,
        shadowState = shadow
    )

    private fun index(vararg rows: PokeGenieRow) =
        PokeGenieMatcher.index(rows.map { PokeGenieMatcher.toOwned(it) })

    @Test
    fun `matches a plain species`() {
        val owned = index(row("Machamp")).bestOwned("MACHAMP")
        assertEquals("Machamp", owned?.displayName)
    }

    @Test
    fun `shadow and normal copies never match each other`() {
        val box = index(
            row("Mewtwo", form = "Normal", cp = 4724),
            row("Mewtwo", form = "Normal", cp = 4709, atk = 14, shadow = ShadowState.SHADOW)
        )
        assertEquals(4724, box.bestOwned("MEWTWO")?.cp)
        assertEquals(4709, box.bestOwned("MEWTWO_SHADOW_FORM")?.cp)
        assertTrue(box.bestOwned("MEWTWO_SHADOW_FORM")!!.shadow)
    }

    @Test
    fun `owning only a mega does not count as owning the plain species`() {
        // Regression: a "Mega Y" Mewtwo used to fall back onto the bare MEWTWO id and be
        // reported as an ordinary Mewtwo, with the mega's inflated CP.
        val box = index(row("Mewtwo", form = "Mega Y", cp = 7267))
        assertEquals(7267, box.bestOwned("MEWTWO_MEGA_Y")?.cp)
        assertNull(box.bestOwned("MEWTWO"))
    }

    @Test
    fun `reads mega and primal forms into dedicated ids`() {
        assertTrue(PokeGenieMatcher.matchKeysFor(row("Charizard", "Mega X")).contains("CHARIZARD_MEGA_X"))
        assertTrue(PokeGenieMatcher.matchKeysFor(row("Gengar", "Mega")).contains("GENGAR_MEGA"))
        assertTrue(PokeGenieMatcher.matchKeysFor(row("Kyogre", "Primal")).contains("KYOGRE_PRIMAL"))
    }

    @Test
    fun `treats the Normal form label as no form`() {
        assertEquals(listOf("MEWTWO"), PokeGenieMatcher.matchKeysFor(row("Mewtwo", "Normal")))
    }

    @Test
    fun `matches regional forms`() {
        val box = index(row("Marowak", form = "Alola"))
        assertEquals("Marowak", box.bestOwned("MAROWAK_ALOLA_FORM")?.displayName)
    }

    @Test
    fun `matches the crowned forms Poke Genie abbreviates`() {
        // Regression: Poke Genie writes Zacian's Crowned Sword as just "Sword", so the box
        // only offered ZACIAN_SWORD_FORM and then fell through to the bare ZACIAN. The copy
        // never matched the ZACIAN_CROWNED_SWORD_FORM counter and vanished from My Pokemon.
        val box = index(
            row("Zacian", form = "Sword", cp = 5629),
            row("Zamazenta", form = "Shield", cp = 4717)
        )
        assertEquals(5629, box.bestOwned("ZACIAN_CROWNED_SWORD_FORM")?.cp)
        assertEquals(4717, box.bestOwned("ZAMAZENTA_CROWNED_SHIELD_FORM")?.cp)
    }

    @Test
    fun `the crowned alias does not leak onto other species`() {
        // AEGISLASH_SHIELD_FORM and AEGISLASH_BLADE_FORM are real ids of their own.
        val keys = PokeGenieMatcher.matchKeysFor(row("Aegislash", "Shield"))
        assertEquals("AEGISLASH_SHIELD_FORM", keys.first())
        assertFalse(keys.any { it.contains("CROWNED") })
    }

    private val catalogue = SpeciesCatalogue(
        listOf(
            "ZACIAN", "ZACIAN_HERO_FORM", "ZACIAN_CROWNED_SWORD_FORM",
            "HOUNDOOM", "HOUNDOOM_SHADOW_FORM",
            "PIKACHU", "PIKACHU_FLYING_01_FORM",
            "MEWTWO"
        )
    )

    @Test
    fun `a separately modelled form stops answering for the bare species`() {
        // Owning a Crowned Sword Zacian is not owning a Hero Zacian, exactly as owning a
        // Mega Gengar is not owning a Gengar.
        val keys = PokeGenieMatcher.matchKeysFor(row("Zacian", "Sword"), catalogue)
        assertEquals("ZACIAN_CROWNED_SWORD_FORM", keys.first())
        assertFalse(keys.toString(), keys.contains("ZACIAN"))
    }

    @Test
    fun `a cosmetic form keeps its species key`() {
        // Pokebattler has no id for this costume, so dropping PIKACHU would lose the copy
        // altogether -- worse than the loose match it would replace.
        val keys = PokeGenieMatcher.matchKeysFor(row("Pikachu", "Kariyushi"), catalogue)
        assertTrue(keys.toString(), keys.contains("PIKACHU"))
    }

    @Test
    fun `a shadow copy keeps its species key`() {
        // Shadow and non-shadow are already separated by the flag, and shadow ids are
        // patchier than form ids, so nothing is trimmed here.
        val keys = PokeGenieMatcher.matchKeysFor(
            row("Houndoom", shadow = ShadowState.SHADOW),
            catalogue
        )
        assertEquals("HOUNDOOM_SHADOW_FORM", keys.first())
        assertTrue(keys.toString(), keys.contains("HOUNDOOM"))
    }

    @Test
    fun `an unsynced catalogue changes nothing`() {
        val row = row("Zacian", "Sword")
        assertEquals(
            PokeGenieMatcher.matchKeysFor(row),
            PokeGenieMatcher.matchKeysFor(row, SpeciesCatalogue(emptyList()))
        )
    }

    @Test
    fun `picks the best copy by level then IVs then CP`() {
        val box = index(
            row("Tyranitar", level = 40.0, atk = 15, def = 15, sta = 15, cp = 4000),
            row("Tyranitar", level = 50.0, atk = 10, def = 10, sta = 10, cp = 4200),
            row("Tyranitar", level = 50.0, atk = 15, def = 15, sta = 15, cp = 4500)
        )
        val best = box.bestOwned("TYRANITAR")!!
        assertEquals(50.0, best.level!!, 1e-9)
        assertEquals(45, best.ivTotal)
        assertEquals(4500, best.cp)
    }

    @Test
    fun `copies with unknown IVs rank below appraised ones`() {
        val box = index(
            row("Machamp", atk = null, def = null, sta = null, cp = 3400),
            row("Machamp", atk = 10, def = 10, sta = 10, cp = 3000)
        )
        assertEquals(30, box.bestOwned("MACHAMP")?.ivTotal)
    }

    @Test
    fun `returns null for a species that is not in the box`() {
        assertNull(index(row("Machamp")).bestOwned("RAYQUAZA"))
    }

    @Test
    fun `handles awkward species names`() {
        val box = index(row("Ho-Oh"), row("Mr. Mime"), row("Jangmo-o"))
        assertEquals("Ho-Oh", box.bestOwned("HO_OH")?.displayName)
        assertEquals("Mr. Mime", box.bestOwned("MR_MIME")?.displayName)
        assertEquals("Jangmo-o", box.bestOwned("JANGMO_O")?.displayName)
    }
}
