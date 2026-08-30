package com.example.pokemonalertsv2.ui.alerts

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.CurrentWeatherRepository
import com.example.pokemonalertsv2.ui.motion.appCollapseOut
import com.example.pokemonalertsv2.ui.motion.appExpandIn

/**
 * The area's current weather, as a corner badge like the game's own.
 *
 * Only shown when the user is standing inside a scanned area — weather elsewhere is not the
 * app's to report. Collapses to the bare glyph and expands to the full label when tapped.
 */
@Composable
internal fun MapWeatherBadge(userLocation: Location?) {
    val context = LocalContext.current
    val repository = remember(context) {
        CurrentWeatherRepository.getInstance(context.applicationContext)
    }
    val area = remember(userLocation?.latitude, userLocation?.longitude) {
        userLocation?.let { areaAtLocation(it.latitude, it.longitude) }
    }
    var display by remember { mutableStateOf<CurrentWeatherDisplay?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(area) {
        // The repository caches, so re-entering the area does not mean another request.
        display = repository.weatherFor(area)
            ?.let { currentWeatherDisplay(it.currentWeather, it.confirmed) }
    }

    val weather = display?.takeIf { area != null }
    AnimatedVisibility(
        visible = weather != null,
        enter = appExpandIn(),
        exit = appCollapseOut()
    ) {
        // Held across the exit animation so the badge does not blank out mid-collapse.
        val shown = remember(weather) { weather } ?: return@AnimatedVisibility
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier
                // Unconfirmed weather reads as provisional before the label is even opened.
                .alpha(if (shown.confirmed) 1f else 0.65f)
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = shown.glyph, style = MaterialTheme.typography.titleMedium)
                if (expanded) {
                    Text(
                        text = if (shown.confirmed) {
                            shown.label
                        } else {
                            "${shown.label} · $UNCONFIRMED_WEATHER_NOTE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
