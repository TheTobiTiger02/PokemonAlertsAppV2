package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.ui.alerts.MapCameraSnapshot
import com.example.pokemonalertsv2.ui.alerts.MapGroupingEditor
import com.example.pokemonalertsv2.ui.alerts.MapWeatherCell
import com.example.pokemonalertsv2.ui.alerts.OfflineTilesSection
import com.example.pokemonalertsv2.ui.alerts.rememberMapClusteringSettings
import com.example.pokemonalertsv2.ui.theme.LocalAppDarkTheme
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.s2CellAt
import com.example.pokemonalertsv2.ui.components.PanelPages
import com.example.pokemonalertsv2.ui.components.PanelHeader
import com.example.pokemonalertsv2.ui.components.PanelSectionLabel
import com.example.pokemonalertsv2.ui.components.PanelNavRow
import com.example.pokemonalertsv2.ui.components.PanelSwitchRow
import com.example.pokemonalertsv2.ui.components.PanelFooterButton
import com.example.pokemonalertsv2.ui.components.PanelIcon
import com.example.pokemonalertsv2.ui.components.panelGutter
import androidx.compose.ui.text.style.TextOverflow

/** The Layers sheet's pages: the list, and the marker grouping editor. */
internal enum class LayersPage(val title: String) {
    ROOT("Map layers"),
    GROUPING("Marker grouping")
}

/**
 * How the map looks and behaves: its style, what it draws on top of the alerts, how markers group,
 * and what happens when you leave the app. Every switch applies at once.
 */
@Composable
internal fun MapLayersSheet(
    userLocation: android.location.Location?,
    weatherCells: List<MapWeatherCell>,
    mapCentre: MapCameraSnapshot,
    mapStyle: MapStylePreference,
    onMapStyleChanged: (MapStylePreference) -> Unit,
    showTimeLabels: Boolean,
    onToggleTimeLabels: () -> Unit,
    showWeatherCells: Boolean,
    onToggleWeatherCells: () -> Unit,
    showHuntPath: Boolean,
    onToggleHuntPath: () -> Unit,
    showSpawnRadius: Boolean,
    onToggleSpawnRadius: () -> Unit,
    spacialRendEnabled: Boolean,
    onToggleSpacialRend: () -> Unit,
    showSpawnpoints: Boolean = false,
    onToggleSpawnpoints: () -> Unit = {},
    showDismissed: Boolean,
    onToggleDismissed: () -> Unit,
    autoEnterPictureInPicture: Boolean,
    onToggleAutoEnterPictureInPicture: (() -> Unit)?,
    onDismiss: () -> Unit,
    useSidePanel: Boolean,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(LayersPage.ROOT) }
    val weather = remember(userLocation?.latitude, userLocation?.longitude, weatherCells) {
        localWeatherLabel(userLocation, weatherCells)
    }

    MapPanelContainer(
        useSidePanel = useSidePanel,
        testTag = "map_layers_sheet",
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            PanelPages(page = page, root = LayersPage.ROOT, onBack = { page = LayersPage.ROOT }) { shown ->
                when (shown) {
                    LayersPage.ROOT -> Column {
                        PanelHeader(title = shown.title, subtitle = weather)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().testTag("map_panel_list"),
                            contentPadding = PaddingValues(bottom = Spacing.md)
                        ) {
                            item("style_label") { PanelSectionLabel("Map style") }
                            item("style") {
                                Row(
                                    modifier = Modifier.panelGutter(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    MapStylePreference.entries.forEach { style ->
                                        MapStyleTile(
                                            style = style,
                                            selected = style == mapStyle,
                                            onClick = { onMapStyleChanged(style) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            if (mapStyle == MapStylePreference.OPENSTREETMAP) {
                                item("offline") {
                                    OfflineTilesSection(
                                        centre = mapCentre,
                                        modifier = Modifier.panelGutter().padding(top = Spacing.md)
                                    )
                                }
                            }

                            item("show_label") { PanelSectionLabel("Show on map") }
                            item("countdowns") {
                                PanelSwitchRow(
                                    title = "Countdown labels",
                                    subtitle = "Time left under every marker",
                                    checked = showTimeLabels,
                                    onCheckedChange = { onToggleTimeLabels() },
                                    icon = { PanelIcon(R.drawable.ic_timer) }
                                )
                            }
                            item("weather") {
                                PanelSwitchRow(
                                    title = "Weather cells",
                                    subtitle = "The game's weather over each area",
                                    checked = showWeatherCells,
                                    onCheckedChange = { onToggleWeatherCells() },
                                    icon = { PanelIcon(R.drawable.ic_cloud) }
                                )
                            }
                            item("hunt_path") {
                                PanelSwitchRow(
                                    title = "Hunt path",
                                    subtitle = "The walk to your hunt's targets",
                                    checked = showHuntPath,
                                    onCheckedChange = { onToggleHuntPath() },
                                    icon = { PanelIcon(R.drawable.ic_route) }
                                )
                            }
                            item("radius") {
                                PanelSwitchRow(
                                    title = "Spawn radius",
                                    subtitle = "The 40 m circle a spawn can be in",
                                    checked = showSpawnRadius,
                                    onCheckedChange = { onToggleSpawnRadius() },
                                    icon = { PanelIcon(R.drawable.ic_radius) }
                                )
                            }
                            item("spawnpoints") {
                                PanelSwitchRow(
                                    title = "Spawnpoints",
                                    subtitle = if (mapStyle == MapStylePreference.OPENSTREETMAP) {
                                        "Where spawns appear, coloured by their hourly timer"
                                    } else {
                                        "Shown on the OpenStreetMap style"
                                    },
                                    checked = showSpawnpoints,
                                    onCheckedChange = { onToggleSpawnpoints() },
                                    modifier = Modifier.testTag("map_show_spawnpoints"),
                                    icon = { PanelIcon(R.drawable.ic_species) }
                                )
                            }
                            // Spacial Rend only widens the circle, so it is offered only while
                            // the circle is drawn, nested under it.
                            item("spacial_rend") {
                                AnimatedVisibility(visible = showSpawnRadius) {
                                    PanelSwitchRow(
                                        title = "Spacial Rend",
                                        subtitle = "Widen the circle to 80 m",
                                        checked = spacialRendEnabled,
                                        onCheckedChange = { onToggleSpacialRend() },
                                        modifier = Modifier.testTag("map_spacial_rend")
                                    )
                                }
                            }
                            item("dismissed") {
                                PanelSwitchRow(
                                    title = "Dismissed alerts",
                                    subtitle = "Keep them on the map after you dismiss them",
                                    checked = showDismissed,
                                    onCheckedChange = { onToggleDismissed() },
                                    modifier = Modifier.testTag("map_show_dismissed"),
                                    icon = { PanelIcon(R.drawable.ic_visibility) }
                                )
                            }

                            item("markers_label") { PanelSectionLabel("Markers") }
                            item("grouping") {
                                val clustering = rememberMapClusteringSettings()
                                PanelNavRow(
                                    title = LayersPage.GROUPING.title,
                                    summary = clustering.label,
                                    onClick = { page = LayersPage.GROUPING },
                                    modifier = Modifier.testTag("map_grouping"),
                                    icon = { PanelIcon(R.drawable.ic_group) }
                                )
                            }

                            if (onToggleAutoEnterPictureInPicture != null) {
                                item("leave_label") { PanelSectionLabel("When you leave the app") }
                                item("auto_pip") {
                                    PanelSwitchRow(
                                        title = "Floating map automatically",
                                        subtitle = "Shrink the map into a window over other apps",
                                        checked = autoEnterPictureInPicture,
                                        onCheckedChange = { onToggleAutoEnterPictureInPicture() },
                                        modifier = Modifier.testTag("map_auto_pip"),
                                        icon = { PanelIcon(R.drawable.ic_pip) }
                                    )
                                }
                            }
                        }
                    }
                    LayersPage.GROUPING -> Column {
                        PanelHeader(title = shown.title, onBack = { page = LayersPage.ROOT })
                        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.md)) {
                            item("editor") { MapGroupingEditor(modifier = Modifier.panelGutter()) }
                        }
                    }
                }
            }
        }
        PanelFooterButton(text = "Done", onClick = onDismiss)
    }
}

/** "Darmstadt · 🌧 Rain?" for the weather cell the user is standing in, or null. */
private fun localWeatherLabel(location: android.location.Location?, cells: List<MapWeatherCell>): String? {
    // Matched by S2 cell, the same data the map draws, rather than by an area-name table that
    // the backend's area names have outgrown.
    location ?: return null
    val cell = s2CellAt(location.latitude, location.longitude)
    val here = cells.firstOrNull { it.cell == cell } ?: return null
    return "${here.area} · ${here.display.compactLabel}"
}

private fun MapStylePreference.label(): String = when (this) {
    MapStylePreference.GOOGLE_STANDARD -> "Standard"
    MapStylePreference.GOOGLE_SATELLITE -> "Satellite"
    MapStylePreference.OPENSTREETMAP -> "OpenStreetMap"
}

/** A style choice drawn as a tiny map of itself, so it is picked by look rather than by name. */
@Composable
private fun MapStyleTile(
    style: MapStylePreference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
        ) {
            Box {
                MapStylePreview(style, Modifier.fillMaxWidth().height(72.dp))
                if (selected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(20.dp),
                        shape = CircleShape,
                        color = scheme.primary,
                        contentColor = scheme.onPrimary
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        }
        Text(
            text = style.label(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Two streets and a block of park, in each style's own colours. */
@Composable
private fun MapStylePreview(style: MapStylePreference, modifier: Modifier) {
    val dark = LocalAppDarkTheme.current
    val (ground, road, park) = when (style) {
        MapStylePreference.GOOGLE_STANDARD -> if (dark) {
            Triple(Color(0xFF242F3E), Color(0xFF38414E), Color(0xFF263C3F))
        } else {
            Triple(Color(0xFFF1F3F4), Color(0xFFFFFFFF), Color(0xFFC8E6C9))
        }
        MapStylePreference.GOOGLE_SATELLITE -> Triple(Color(0xFF4E5B3A), Color(0xFFB8B09A), Color(0xFF2F4A2A))
        MapStylePreference.OPENSTREETMAP -> Triple(Color(0xFFF2EFE9), Color(0xFFFFFFFF), Color(0xFFCDEBB0))
    }
    val accentRoad = if (style == MapStylePreference.OPENSTREETMAP) Color(0xFFF9D99A) else road
    Canvas(modifier = modifier) {
        drawRect(ground)
        drawRect(park, topLeft = Offset(size.width * 0.58f, size.height * 0.08f), size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.38f))
        val stroke = size.height * 0.11f
        drawLine(road, Offset(0f, size.height * 0.68f), Offset(size.width, size.height * 0.52f), strokeWidth = stroke)
        drawLine(accentRoad, Offset(size.width * 0.32f, 0f), Offset(size.width * 0.44f, size.height), strokeWidth = stroke)
    }
}
