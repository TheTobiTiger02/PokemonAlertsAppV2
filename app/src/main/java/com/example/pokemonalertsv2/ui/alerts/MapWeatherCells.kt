@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.pokemonalertsv2.data.CurrentWeatherRepository
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.Spacing
import com.example.pokemonalertsv2.util.S2CellRef
import com.example.pokemonalertsv2.util.S2LatLng
import com.example.pokemonalertsv2.util.boundary
import com.example.pokemonalertsv2.util.centre
import com.example.pokemonalertsv2.util.s2CellAt
import kotlinx.coroutines.delay

/**
 * One scanned area's weather, as the S2 cell the game reads it from.
 *
 * The backend serves weather per *area name*, not per coordinate, so there is exactly one of
 * these per scanned area — the level-10 cell containing that area's centre. That is deliberately
 * not a grid: every other cell on screen would be blank, because there is no weather to put in
 * it. Drawing the one cell the data actually covers is the honest version.
 */
@Immutable
internal data class MapWeatherCell(
    val area: String,
    val display: CurrentWeatherDisplay,
    val cell: S2CellRef,
    val centre: S2LatLng,
    val boundary: List<S2LatLng>
)

/**
 * Below this the cell is a handful of pixels wide and the glyph is meaningless clutter.
 * A level-10 cell spans ~10km, which is a few hundred pixels at zoom 9.
 */
internal const val WEATHER_CELL_MIN_ZOOM = 9.0

/** Weather changes on the hour; the repository caches for five minutes, so poll to match. */
private const val WEATHER_POLL_INTERVAL_MILLIS = 5 * 60 * 1000L

/**
 * Resolves and keeps fresh one weather cell per scanned area.
 *
 * The corner badge this replaced fetched once per *area name change*, which for anyone not
 * driving between towns meant once per session — it could sit an hour stale straight through a
 * weather change. This polls while the map is on screen.
 */
@Composable
internal fun rememberMapWeatherCells(
    alerts: List<PokemonAlert>,
    enabled: Boolean
): List<MapWeatherCell> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) {
        CurrentWeatherRepository.getInstance(context.applicationContext)
    }

    val areaCentres = rememberAreaCentres(alerts)
    var cells by remember { mutableStateOf<List<MapWeatherCell>>(emptyList()) }

    // Keyed on the names, not the coordinates: a centre derived from alert positions wobbles as
    // alerts come and go, and re-fetching weather because a spawn expired would be absurd.
    val areaKey = remember(areaCentres) { areaCentres.keys.sorted().joinToString(",") }

    LaunchedEffect(enabled, areaKey) {
        if (!enabled || areaCentres.isEmpty()) {
            cells = emptyList()
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                cells = areaCentres.mapNotNull { (area, centre) ->
                    val weather = repository.weatherFor(area) ?: return@mapNotNull null
                    val display = currentWeatherDisplay(weather.currentWeather, weather.confirmed)
                        ?: return@mapNotNull null
                    val cell = s2CellAt(centre.first, centre.second)
                    MapWeatherCell(
                        area = area,
                        display = display,
                        cell = cell,
                        centre = cell.centre(),
                        boundary = cell.boundary()
                    )
                }
                delay(WEATHER_POLL_INTERVAL_MILLIS)
            }
        }
    }

    return if (enabled) cells else emptyList()
}

/**
 * Where each scanned area sits.
 *
 * [AREA_CENTRES] is the source of truth and is stable, which matters because the cell must not
 * flip as alerts churn. An area the backend starts reporting that is not in that map still gets
 * a cell, from the median of its own alerts' coordinates — median rather than mean so one alert
 * with a bad fix cannot drag the cell into the next one over.
 */
@Composable
private fun rememberAreaCentres(alerts: List<PokemonAlert>): Map<String, Pair<Double, Double>> {
    val unknownAreas = remember(alerts) {
        alerts.mapNotNullTo(sortedSetOf()) { alert ->
            alert.area?.trim()?.takeIf { it.isNotEmpty() && it !in AREA_CENTRES }
        }
    }
    return remember(unknownAreas) {
        if (unknownAreas.isEmpty()) return@remember AREA_CENTRES
        val derived = unknownAreas.mapNotNull { area ->
            val coordinates = alerts
                .filter { it.area?.trim() == area }
                .mapNotNull { it.mapCoordinatesOrNull() }
                .takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val latitudes = coordinates.map { it.latitude }.sorted()
            val longitudes = coordinates.map { it.longitude }.sorted()
            area to (latitudes[latitudes.size / 2] to longitudes[longitudes.size / 2])
        }
        AREA_CENTRES + derived
    }
}

/**
 * What one weather cell is showing, and the fastest way to stop showing it.
 *
 * The glyph used to be inert - a Google info bubble at best, and nothing at all on the
 * OpenStreetMap layer, where it is drawn as a style layer rather than an annotation. Tapping it
 * now explains the cell it belongs to and offers the off-switch that otherwise lives three
 * levels into the map panel.
 */
@Composable
internal fun MapWeatherSheet(
    cell: MapWeatherCell,
    onDismiss: () -> Unit,
    onHideWeather: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = cell.area,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(text = cell.display.glyph, style = MaterialTheme.typography.headlineMedium)
                Text(text = cell.display.label, style = MaterialTheme.typography.titleMedium)
            }
            if (!cell.display.confirmed) {
                Text(
                    text = UNCONFIRMED_WEATHER_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "The game reads weather per cell. The outline is the cell covering this area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onHideWeather) { Text("Hide weather") }
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
