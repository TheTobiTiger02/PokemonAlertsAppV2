package com.example.pokemonalertsv2.data.godex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexImporterTest {
    private val importer = GoDexImporter()

    @Test
    fun validatesAndNormalizesOnlyPublicHttpsGoDexUrls() {
        assertEquals(
            "https://godex.site/public-collection/ABC123",
            GoDexImporter.validateUrl(" https://godex.site/public-collection/ABC123/?ignored=1 ")
        )
        assertThrows(IllegalArgumentException::class.java) {
            GoDexImporter.validateUrl("http://godex.site/public-collection/ABC123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoDexImporter.validateUrl("https://example.com/public-collection/ABC123")
        }
    }

    @Test
    fun parsesRepresentativeRegionAndCaughtState() {
        val entries = importer.parseEntries(
            """
            <div wire:key="0025-none"><img dusk="pokemon-sprite" alt="Pikachu"></div>
            <div wire:key="0026_alola-female"><img dusk="pokemon-sprite" alt="Raichu (Alola)"></div>
            """.trimIndent(),
            caughtKeys = setOf("0025-none")
        )

        assertEquals(2, entries.size)
        assertFalse(entries.single { it.entryKey == "0025-none" }.needed)
        val alola = entries.single { it.entryKey == "0026_alola-female" }
        assertTrue(alola.needed)
        assertEquals(26, alola.pokedexId)
        assertEquals("alola", alola.formSlug)
        assertEquals("female", alola.gender)
    }

    @Test
    fun parsesInitialPageOnlyWhenAllElevenRegionsArePresent() {
        val regions = (1..11).joinToString("\n") { index ->
            "<div wire:snapshot='snapshot-$index' x-init=\"__lazyLoad('mount-$index')\"></div>"
        }
        val page = importer.parseInitialPage(
            """
            <html><head><script src="/livewire/livewire.min.js" data-csrf="csrf-value" data-update-uri="/livewire/update"></script></head><body>
              <img src="/images/collections/hundo.png" alt="My Hundo checklist">
              <span wire:snapshot='{&quot;data&quot;:{&quot;totalPokemons&quot;:11,&quot;caughtPokemons&quot;:0}}'></span>
              $regions
            </body></html>
            """.trimIndent(),
            "https://godex.site/public-collection/ABC123"
        )

        assertEquals("csrf-value", page.csrfToken)
        assertEquals("My Hundo checklist", page.collectionTitle)
        assertEquals(11, page.lazyComponents.size)
        assertEquals("mount-1", page.lazyComponents.first().mountParam)
        assertEquals(11, page.expectedTotalCount)
        assertEquals(0, page.expectedCollectedCount)
    }

    @Test
    fun incompleteInitialPageFailsValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            importer.parseInitialPage(
                """
                <meta name="csrf-token" content="csrf-value">
                <img src="/images/collections/hundo.png" alt="Hundos">
                <div wire:snapshot="snapshot" x-init="__lazyLoad('mount')"></div>
                """.trimIndent(),
                "https://godex.site/public-collection/ABC123"
            )
        }
    }

    @Test
    fun stillAcceptsLegacyCsrfMetaTag() {
        val regions = (1..11).joinToString("\n") { index ->
            "<div wire:snapshot='snapshot-$index' x-init=\"${'$'}wire.__lazyLoad('mount-$index')\"></div>"
        }
        val page = importer.parseInitialPage(
            """
            <meta name="csrf-token" content="legacy-token">
            <img src="/images/collections/normal-hundo.svg" alt="Hundos">
            <span wire:snapshot='{&quot;data&quot;:{&quot;totalPokemons&quot;:11,&quot;caughtPokemons&quot;:0}}'></span>
            $regions
            """.trimIndent(),
            "https://godex.site/public-collection/ABC123"
        )

        assertEquals("legacy-token", page.csrfToken)
        assertEquals(11, page.lazyComponents.size)
    }

    @Test
    fun parsesAllPumpkabooAndGourgeistSizeKeys() {
        val entries = importer.parseEntries(
            """
            <div wire:key="0710a_small-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Small)"></div>
            <div wire:key="0710b_average-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Average)"></div>
            <div wire:key="0710c_large-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Large)"></div>
            <div wire:key="0710d_super-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Super)"></div>
            <div wire:key="0711a_small-none"><img dusk="pokemon-sprite" alt="Gourgeist (Small)"></div>
            <div wire:key="0711b_average-none"><img dusk="pokemon-sprite" alt="Gourgeist (Average)"></div>
            <div wire:key="0711c_large-none"><img dusk="pokemon-sprite" alt="Gourgeist (Large)"></div>
            <div wire:key="0711d_super-none"><img dusk="pokemon-sprite" alt="Gourgeist (Super)"></div>
            """.trimIndent(),
            caughtKeys = setOf(
                "0710b_average-none",
                "0710c_large-none",
                "0710d_super-none"
            )
        )

        assertEquals(8, entries.size)
        assertEquals(setOf(710, 711), entries.map { it.pokedexId }.toSet())
        assertEquals(setOf("small", "average", "large", "super"), entries.map { it.formSlug }.toSet())
        assertEquals(3, entries.count { !it.needed })
        assertEquals("0710b_average-none", entries.single { it.displayName == "Pumpkaboo (Average)" }.entryKey)
    }

    @Test
    fun unrecognizedPokemonKeyFailsInsteadOfBeingSkipped() {
        assertThrows(IllegalArgumentException::class.java) {
            importer.parseEntries(
                "<div wire:key='unexpected-key'><img dusk='pokemon-sprite' alt='Unknown'></div>",
                emptySet()
            )
        }
    }

    @Test
    fun validationRejectsCountMismatchesAndMissingCaughtKeys() {
        val entries = importer.parseEntries(
            """
            <div wire:key="0710a_small-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Small)"></div>
            <div wire:key="0710b_average-none"><img dusk="pokemon-sprite" alt="Pumpkaboo (Average)"></div>
            """.trimIndent(),
            caughtKeys = setOf("0710b_average-none")
        )

        assertThrows(IllegalArgumentException::class.java) {
            importer.validateEntries(entries, setOf("0710b_average-none"), 3, 1, "test region")
        }
        assertThrows(IllegalArgumentException::class.java) {
            importer.validateEntries(entries, setOf("0710b_average-none"), 2, 2, "test region")
        }
        assertThrows(IllegalArgumentException::class.java) {
            importer.validateEntries(entries, setOf("0710c_large-none"), 2, 1, "test region")
        }
    }

    @Test
    fun malformedOrChangedRegionMarkupFailsInsteadOfClearingCache() {
        assertThrows(IllegalArgumentException::class.java) {
            importer.parseEntries("<div data-key='0025-none'></div>", emptySet())
        }
    }
}
