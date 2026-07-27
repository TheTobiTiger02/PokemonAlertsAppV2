package com.example.pokemonalertsv2.ui.settings

import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoDexCollectionContentTest {
    private val entries = listOf(
        entry("0025-none", 25, null, "none", "Pikachu", needed = false),
        entry("0026_alola-female", 26, "alola", "female", "Alolan Raichu", needed = true),
        entry("0503_hisui-none", 503, "hisui", "none", "Hisuian Samurott", needed = true)
    )

    @Test
    fun neededFilterShowsOnlyRawNeededEntriesByDefault() {
        val result = filterGoDexCollectionEntries(
            entries,
            GoDexCollectionFilter.NEEDED,
            query = ""
        )

        assertEquals(listOf("0026_alola-female", "0503_hisui-none"), result.map { it.entryKey })
    }

    @Test
    fun caughtAndAllFiltersPreserveExactEntryIdentity() {
        assertEquals(
            listOf("0025-none"),
            filterGoDexCollectionEntries(entries, GoDexCollectionFilter.CAUGHT, "").map { it.entryKey }
        )
        assertEquals(
            entries.map { it.entryKey },
            filterGoDexCollectionEntries(entries, GoDexCollectionFilter.ALL, "").map { it.entryKey }
        )
    }

    @Test
    fun searchMatchesFormGenderNumberAndExactKey() {
        assertEquals(
            listOf("0026_alola-female"),
            filterGoDexCollectionEntries(entries, GoDexCollectionFilter.ALL, "female").map { it.entryKey }
        )
        assertEquals(
            listOf("0026_alola-female"),
            filterGoDexCollectionEntries(entries, GoDexCollectionFilter.ALL, "alola").map { it.entryKey }
        )
        assertEquals(
            listOf("0503_hisui-none"),
            filterGoDexCollectionEntries(entries, GoDexCollectionFilter.ALL, "0503").map { it.entryKey }
        )
    }

    @Test
    fun compactGridVariantLabelsPreserveExactFormAndGenderIdentity() {
        assertEquals("alola \u2022 female", goDexEntryVariantLabel(entries[1]))
        assertEquals("hisui", goDexEntryVariantLabel(entries[2]))
        assertEquals("", goDexEntryVariantLabel(entries[0]))
    }

    @Test
    fun selectionRequiresAnExplicitSecondStepAndTogglesOff() {
        val pikachu = entries.first()
        val selected = toggleGoDexCollectionSelection(null, pikachu)

        assertEquals(GoDexCollectionSelection("0025-none", neededAtSelection = false), selected)
        assertNull(toggleGoDexCollectionSelection(selected, pikachu))
    }

    @Test
    fun selectionClearsWhenHiddenRemovedOrAuthoritativeStateChanges() {
        val selected = GoDexCollectionSelection("0026_alola-female", neededAtSelection = true)

        assertNull(resolveGoDexCollectionSelection(selected, entries, setOf("0025-none")))
        assertNull(
            resolveGoDexCollectionSelection(
                selected,
                entries.filterNot { it.entryKey == selected.entryKey },
                setOf(selected.entryKey)
            )
        )
        assertNull(
            resolveGoDexCollectionSelection(
                selected,
                entries.map {
                    if (it.entryKey == selected.entryKey) it.copy(needed = false) else it
                },
                setOf(selected.entryKey)
            )
        )
        assertEquals(
            "0026_alola-female",
            resolveGoDexCollectionSelection(selected, entries, setOf(selected.entryKey))?.entryKey
        )
    }

    @Test
    fun disconnectCopyWarnsBeforeDiscardingPendingChanges() {
        assertEquals(
            "This removes the cached checklist and discards 1 unsent change from this device. " +
                "GoDex itself will not be changed.",
            goDexDisconnectMessage(1)
        )
        assertEquals(
            "This removes the cached checklist and discards 3 unsent changes from this device. " +
                "GoDex itself will not be changed.",
            goDexDisconnectMessage(3)
        )
    }

    private fun entry(
        key: String,
        dex: Int,
        form: String?,
        gender: String,
        name: String,
        needed: Boolean
    ) = GoDexEntryEntity(key, dex, form, gender, name, needed)
}
