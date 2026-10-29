package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.normalizeFilterToken
import com.example.pokemonalertsv2.data.speciesArt
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing

/** What Tappables give, split the way the tab lists it: loot first, then the Pokémon. */
internal data class TappableRewardSections(val loot: List<String>, val pokemon: List<String>) {
    val all: List<String> get() = loot + pokemon
}

/**
 * The backend's Tappable reward catalogue, plus anything already picked that has since dropped out
 * of it, so a selection can always be undone. A name is a Pokémon when the game's species list or
 * the local Pokédex knows it; anything else is loot.
 */
internal fun tappableRewardSections(
    catalog: FilterCatalog,
    selection: FilterSelection,
    artwork: Map<String, String>
): TappableRewardSections {
    // Selections are stored normalized ("pidgey"); the species list gives them their name back.
    val speciesNames = catalog.spawnSpecies.associateBy(::normalizeFilterToken)
    val names = LinkedHashMap<String, String>()
    catalog.tappableRewards.forEach { names.putIfAbsent(normalizeFilterToken(it), it) }
    if (selection.mode == FilterSelectionMode.ONLY) {
        selection.values.forEach { value -> normalizeFilterToken(value).let { names.putIfAbsent(it, speciesNames[it] ?: value) } }
    }
    val species = speciesNames.keys
    val (pokemon, loot) = names.entries.partition { (key, name) ->
        key in species || speciesArt(name, artwork).dex != null
    }
    return TappableRewardSections(loot = loot.map { it.value }, pokemon = pokemon.map { it.value })
}

/** A reward's icon: the backend's, else the species sprite. */
internal fun tappableRewardIcon(name: String, catalog: FilterCatalog, artwork: Map<String, String>): String? {
    val key = normalizeFilterToken(name)
    return catalog.tappableRewardIcons.entries.firstOrNull { normalizeFilterToken(it.key) == key }?.value
        ?: speciesArt(name, artwork).url
}

/** Tappables by what they give, never the whole Pokédex: only the rewards seen on Tappables lately. */
@Composable
internal fun TappablesTab(state: FiltersState) {
    val selection = state.definition.tappableSpecies
    val sections = remember(state.catalog, selection, state.artwork) {
        tappableRewardSections(state.catalog, selection, state.artwork)
    }
    val accent = Color(AlertCategory.TAPPABLE.accentArgb)
    val look = { name: String ->
        TokenLook(label = name, badge = accent, glyph = name.take(1).uppercase(), imageUrl = tappableRewardIcon(name, state.catalog, state.artwork))
    }
    val update = { next: FilterSelection -> state.onDefinitionChange(state.definition.copy(tappableSpecies = next)) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("map_tappables_list")) {
        Row(modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { update(FilterSelection.All) }) { Text("All") }
            TextButton(onClick = { update(FilterSelection.None) }) { Text("None") }
        }
        if (sections.all.isEmpty()) {
            EditorNote("These load with the next refresh.")
            return@Column
        }
        listOf("Loot" to sections.loot, "Pokémon" to sections.pokemon).forEach { (title, tokens) ->
            if (tokens.isEmpty()) return@forEach
            PanelSectionLabel(title)
            TokenTileGrid(
                tokens = tokens,
                selection = selection,
                onSelectionChange = update,
                perRow = 4,
                look = look,
                universe = sections.all,
                modifier = Modifier.panelGutter().padding(bottom = Spacing.lg)
            )
        }
    }
}
