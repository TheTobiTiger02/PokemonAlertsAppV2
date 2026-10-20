package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PokebattlerAttackStrategy
import com.example.pokemonalertsv2.data.counters.PokebattlerFriendship
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.ui.components.PanelGutter
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.keepScrollInSheet
import com.example.pokemonalertsv2.ui.components.panelGutter
import com.example.pokemonalertsv2.ui.theme.Spacing

// The battle setup as one flat panel: blue section labels over labelled rows, each row opening
// an editor page inside the same panel. The raid counters designs and Settings › Raid counters
// share it, so a setting reads and edits the same way wherever it is shown.

/** Settings a design keeps outside the panel, so that each one is set in exactly one place. */
internal enum class SetupItem { WEATHER, LEVEL, SORT, OWNED }

/** What the panel can change beyond [RaidCounterOptions]: only an open raid has these. */
internal class LiveSetup(val state: RaidCountersUiState, val actions: RaidCountersActions)

private enum class SetupPage { LIST, MOVESET, LEVEL, FRIENDSHIP, STRATEGY, SORT, MOVES, MEGA }

/**
 * The panel itself. [live] is null in Settings, where only the saved defaults are edited.
 * With [scroll] false the caller scrolls the list (Settings is one long scrolling page).
 */
@Composable
internal fun CounterSetupPanel(
    options: RaidCounterOptions,
    onOptionsChanged: (RaidCounterOptions) -> Unit,
    modifier: Modifier = Modifier,
    live: LiveSetup? = null,
    title: String? = null,
    excluded: Set<SetupItem> = emptySet(),
    scroll: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    var page by rememberSaveable { mutableStateOf(SetupPage.LIST) }
    val back = { page = SetupPage.LIST }
    val pageModifier = if (scroll) {
        Modifier.keepScrollInSheet().verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(modifier = modifier.fillMaxWidth()) {
        PanelPages(page = page, root = SetupPage.LIST, onBack = back) { shown ->
            when (shown) {
                SetupPage.LIST -> Column(pageModifier.fillMaxWidth()) {
                    title?.let { PanelHeader(title = it) }
                    SetupList(options, onOptionsChanged, live, excluded) { page = it }
                    footer()
                }

                SetupPage.MOVESET -> live?.let { (state, actions) ->
                    Column(pageModifier) {
                        PanelHeader(title = "Boss moveset", onBack = back)
                        val entries = buildList {
                            add(null to "Average moveset")
                            state.bossMovesets.filterNot { it.isRandom }.forEach { add(it to it.displayName) }
                        }.distinctBy { it.first?.cacheToken() ?: "AVERAGE" }
                        ChoiceList(
                            entries = entries,
                            isSelected = { it?.cacheToken() == state.selectedBossMoveset?.cacheToken() },
                            onSelect = { actions.onBossMovesetChanged(it); back() }
                        )
                    }
                }

                SetupPage.LEVEL -> Column(pageModifier) {
                    PanelHeader(title = "Attacker level", onBack = back)
                    if (live?.state?.showingPersonal == true) {
                        PageNote("My Pokémon ranks each of yours at its own level; this level is for the Pokébattler list.")
                    }
                    LevelTiles(
                        selected = options.attackerLevel,
                        onSelect = { onOptionsChanged(options.copy(attackerLevel = it)); back() },
                        modifier = Modifier.panelGutter().padding(top = Spacing.sm, bottom = Spacing.lg)
                    )
                }

                SetupPage.FRIENDSHIP -> Column(pageModifier) {
                    PanelHeader(title = "Friendship", onBack = back)
                    ChoiceList(
                        entries = PokebattlerFriendship.entries.map { it to it.label },
                        isSelected = { it == options.friendship },
                        onSelect = { onOptionsChanged(options.copy(friendship = it)); back() }
                    )
                }

                SetupPage.STRATEGY -> Column(pageModifier) {
                    PanelHeader(title = "Attack strategy", onBack = back)
                    ChoiceList(
                        entries = PokebattlerAttackStrategy.entries.map { it to it.label },
                        isSelected = { it == options.attackStrategy },
                        onSelect = { onOptionsChanged(options.copy(attackStrategy = it)); back() }
                    )
                }

                SetupPage.SORT -> Column(pageModifier) {
                    PanelHeader(title = "Rank by", onBack = back)
                    ChoiceList(
                        entries = PokebattlerSort.entries.map { it to it.label },
                        isSelected = { it == options.sort },
                        onSelect = { onOptionsChanged(options.copy(sort = it)); back() }
                    )
                }

                SetupPage.MOVES -> live?.let { (state, actions) ->
                    Column(pageModifier) {
                        PanelHeader(title = "My Pokémon moves", onBack = back)
                        ChoiceList(
                            entries = PersonalMovesMode.entries.map { it to it.label },
                            isSelected = { it == state.personalMovesMode },
                            onSelect = { actions.onPersonalMovesModeChanged(it); back() }
                        )
                        PageNote(
                            if (state.personalMovesMode == PersonalMovesMode.CURRENT) {
                                "Only imports with a recorded fast move and at least one charged move are ranked."
                            } else {
                                "Known moves are kept; only missing components use the best legal move."
                            }
                        )
                    }
                }

                SetupPage.MEGA -> live?.let { (state, actions) ->
                    Column {
                        PanelHeader(title = "Active mega", onBack = back)
                        ActiveMegaSheet(
                            state = state,
                            showTitle = false,
                            onSelect = { actions.onActiveMegaChanged(it); back() }
                        )
                    }
                }
            }
        }
    }
}

private operator fun LiveSetup.component1() = state
private operator fun LiveSetup.component2() = actions

@Composable
private fun SetupList(
    options: RaidCounterOptions,
    onOptionsChanged: (RaidCounterOptions) -> Unit,
    live: LiveSetup?,
    excluded: Set<SetupItem>,
    open: (SetupPage) -> Unit
) {
    val defaults = RaidCounterOptions()
    val state = live?.state
    val actions = live?.actions

    if (SetupItem.WEATHER !in excluded) {
        PanelSectionLabel("Weather")
        WeatherTiles(
            selected = options.weather,
            onSelect = { onOptionsChanged(options.copy(weather = it)) },
            modifier = Modifier.panelGutter()
        )
        state?.let { weatherSourceNote(it) }?.let { PageNote(it) }
    }

    PanelSectionLabel("Battle")
    if (state != null) {
        PanelNavRow(
            title = "Boss moveset",
            summary = state.selectedBossMoveset?.displayName ?: "Average",
            active = state.selectedBossMoveset != null,
            onClick = { open(SetupPage.MOVESET) },
            modifier = Modifier.testTag("setup_row_moveset")
        ) { PanelIcon(R.drawable.ic_category) }
    }
    if (SetupItem.LEVEL !in excluded) {
        PanelNavRow(
            title = "Attacker level",
            summary = if (state?.showingPersonal == true) {
                "Level ${options.attackerLevel} · yours use their own"
            } else {
                "Level ${options.attackerLevel}"
            },
            active = options.attackerLevel != defaults.attackerLevel,
            onClick = { open(SetupPage.LEVEL) },
            modifier = Modifier.testTag("setup_row_level")
        ) { PanelIcon(R.drawable.ic_insights) }
    }
    PanelNavRow(
        title = "Friendship",
        summary = options.friendship.label,
        active = options.friendship != defaults.friendship,
        onClick = { open(SetupPage.FRIENDSHIP) },
        modifier = Modifier.testTag("setup_row_friendship")
    ) { PanelIcon(R.drawable.ic_group) }
    PanelNavRow(
        title = "Attack strategy",
        summary = options.attackStrategy.label,
        active = options.attackStrategy != defaults.attackStrategy,
        onClick = { open(SetupPage.STRATEGY) },
        modifier = Modifier.testTag("setup_row_strategy")
    ) { PanelIcon(R.drawable.ic_tune) }
    if (SetupItem.SORT !in excluded) {
        PanelNavRow(
            title = "Rank by",
            summary = options.sort.label,
            active = options.sort != defaults.sort,
            onClick = { open(SetupPage.SORT) },
            modifier = Modifier.testTag("setup_row_sort")
        ) { PanelIcon(R.drawable.ic_filter) }
    }
    PanelSwitchRow(
        title = "Party Power",
        checked = options.partyPower,
        onCheckedChange = { onOptionsChanged(options.copy(partyPower = it)) },
        icon = { PanelIcon(R.drawable.ic_timer) }
    )

    PanelSectionLabel("Include")
    PanelSwitchRow(
        title = "Megas",
        checked = options.includeMegas,
        onCheckedChange = { onOptionsChanged(options.copy(includeMegas = it)) },
        icon = { PanelIcon(R.drawable.ic_reward_mega_energy) }
    )
    PanelSwitchRow(
        title = "Shadows",
        checked = options.includeShadow,
        onCheckedChange = { onOptionsChanged(options.copy(includeShadow = it)) },
        icon = { PanelIcon(R.drawable.ic_visibility) }
    )
    PanelSwitchRow(
        title = "Legendaries",
        checked = options.includeLegendary,
        onCheckedChange = { onOptionsChanged(options.copy(includeLegendary = it)) },
        icon = { PanelIcon(R.drawable.ic_star_border) }
    )

    if (state == null || actions == null) return
    val showOwned = SetupItem.OWNED !in excluded && state.pokeGenieCount > 0
    if (!showOwned && !state.hasPersonalSource) return
    PanelSectionLabel("My Pokémon")
    if (showOwned) {
        PanelSwitchRow(
            title = "Owned only",
            subtitle = "Pokébattler list keeps only Pokémon you have",
            checked = state.ownedOnly,
            onCheckedChange = actions.onOwnedOnlyChanged,
            icon = { PanelIcon(R.drawable.ic_species) }
        )
    }
    if (state.hasPersonalSource) {
        val mega = state.activeMegaId?.let { id -> state.megaOptions.firstOrNull { it.pokemonId == id } }
        PanelNavRow(
            title = "Active mega",
            summary = mega?.displayName ?: "None",
            active = mega != null,
            onClick = { open(SetupPage.MEGA) },
            modifier = Modifier.testTag("setup_row_mega")
        ) { PanelIcon(R.drawable.ic_reward_mega_energy) }
        PanelNavRow(
            title = "Moves",
            summary = state.personalMovesMode.label,
            onClick = { open(SetupPage.MOVES) },
            modifier = Modifier.testTag("setup_row_moves")
        ) { PanelIcon(R.drawable.ic_category) }
    }
}

/** Where the raid's weather came from, when it did not come from the user. */
internal fun weatherSourceNote(state: RaidCountersUiState): String? = when {
    state.weatherFromAlert && state.weatherUnconfirmed -> "From the raid, not confirmed yet"
    state.weatherFromAlert -> "From the raid"
    state.weatherUnconfirmed -> "Not confirmed yet"
    else -> null
}

@Composable
private fun PageNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.panelGutter().padding(top = Spacing.sm)
    )
}

/** One choice of a few: a radio row each, the whole row tappable. */
@Composable
private fun <T> ChoiceList(
    entries: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    entries.forEach { (value, label) ->
        val selected = isSelected(value)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(value) })
                .padding(horizontal = PanelGutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(Spacing.lg))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(Modifier.size(Spacing.lg))
}

/** Short tile names; the long labels are for lists. */
internal val PokebattlerWeather.tileLabel: String
    get() = when (this) {
        PokebattlerWeather.NONE -> "None"
        PokebattlerWeather.CLEAR -> "Sunny"
        PokebattlerWeather.RAINY -> "Rain"
        PokebattlerWeather.PARTLY_CLOUDY -> "Partly cloudy"
        PokebattlerWeather.OVERCAST -> "Cloudy"
        PokebattlerWeather.WINDY -> "Windy"
        PokebattlerWeather.SNOW -> "Snow"
        PokebattlerWeather.FOG -> "Fog"
    }

internal val PokebattlerWeather.icon: Int
    get() = when (this) {
        PokebattlerWeather.NONE -> R.drawable.ic_weather_none
        PokebattlerWeather.CLEAR -> R.drawable.ic_weather_clear
        PokebattlerWeather.RAINY -> R.drawable.ic_weather_rain
        PokebattlerWeather.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
        PokebattlerWeather.OVERCAST -> R.drawable.ic_weather_cloudy
        PokebattlerWeather.WINDY -> R.drawable.ic_weather_windy
        PokebattlerWeather.SNOW -> R.drawable.ic_weather_snow
        PokebattlerWeather.FOG -> R.drawable.ic_weather_fog
    }

/** The eight weathers as icon tiles, four across. */
@Composable
internal fun WeatherTiles(
    selected: PokebattlerWeather,
    onSelect: (PokebattlerWeather) -> Unit,
    modifier: Modifier = Modifier
) {
    ChoiceTiles(
        entries = PokebattlerWeather.entries,
        isSelected = { it == selected },
        onSelect = onSelect,
        label = { it.tileLabel },
        icon = { it.icon },
        // Four across on a 348dp phone, inside an inset editor too.
        minTileWidth = 64.dp,
        // Two even rows rather than five and three.
        maxColumns = 4,
        tag = { "weather_tile_${it.name.lowercase()}" },
        modifier = modifier
    )
}

/** Attacker levels as number tiles. */
@Composable
internal fun LevelTiles(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    ChoiceTiles(
        entries = RaidCounterOptions.ATTACKER_LEVELS,
        isSelected = { it == selected },
        onSelect = onSelect,
        label = { it.toString() },
        minTileWidth = 52.dp,
        tag = { "level_tile_$it" },
        modifier = modifier
    )
}

@Composable
internal fun <T> ChoiceTiles(
    entries: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    minTileWidth: Dp,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> Int)? = null,
    maxColumns: Int = Int.MAX_VALUE
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = Spacing.sm
        val columns = ((maxWidth + gap) / (minTileWidth + gap)).toInt().coerceIn(3, maxOf(3, maxColumns))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { entry ->
                        ChoiceTile(
                            label = label(entry),
                            icon = icon?.invoke(entry),
                            selected = isSelected(entry),
                            onClick = { onSelect(entry) },
                            modifier = Modifier.weight(1f).testTag(tag(entry))
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    label: String,
    icon: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(Spacing.xxs))
            }
            Text(
                text = label,
                style = if (icon != null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The panel's footer inside a sheet: keep these as defaults, then close. */
@Composable
internal fun ColumnScope.SetupSheetFooter(onSaveAsDefault: () -> Unit, onDone: () -> Unit) {
    TextButton(
        onClick = onSaveAsDefault,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.md)
    ) { Text("Save as default") }
    com.example.pokemonalertsv2.ui.components.PanelFooterButton(text = "Done", onClick = onDone)
    Spacer(Modifier.size(Spacing.lg))
}
