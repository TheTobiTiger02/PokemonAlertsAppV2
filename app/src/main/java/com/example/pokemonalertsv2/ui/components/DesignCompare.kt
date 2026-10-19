package com.example.pokemonalertsv2.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.ui.theme.Spacing

/**
 * Screens with several designs built to compare on a device. Option 0 is always the design that
 * shipped before, so release builds keep it until a pick replaces the others.
 */
internal enum class DesignScreen(val options: List<String>) {
    FILTER_STUDIO(listOf("Current", "Tabs", "Overview", "Tiles")),
    RAID_COUNTERS(listOf("Current", "Chip row", "Collapsing", "Tabs")),
    CATCH_ROUTES(listOf("Current", "Rows", "Map + sheet", "Steps")),
    ALERT_DETAIL(listOf("Current", "One column", "Tabs", "Map-first")),
    SETTINGS_PAGES(listOf("Current", "Flat rows", "Status cards")),
    EVENTS(listOf("Current", "One sheet", "Tabs")),
    INSIGHTS(listOf("Current", "Tabs", "Summary cards"))
}

/**
 * The chosen option per screen, shared by every screen showing it and kept across restarts so a
 * comparison survives a relaunch. Only debug builds can change it.
 */
private object DesignOptions {
    private const val PREFS = "design_compare"
    private val chosen = mutableStateMapOf<DesignScreen, Int>()

    fun get(context: Context, screen: DesignScreen): Int =
        chosen.getOrPut(screen) {
            if (!BuildConfig.DEBUG) 0
            else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(screen.name, 0)
                .coerceIn(0, screen.options.lastIndex)
        }

    fun set(context: Context, screen: DesignScreen, option: Int) {
        chosen[screen] = option
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(screen.name, option).apply()
    }
}

/** Which of [screen]'s designs to draw: an index into [DesignScreen.options]. */
@Composable
internal fun rememberDesignOption(screen: DesignScreen): Int = DesignOptions.get(LocalContext.current, screen)

/**
 * A thin strip of the screen's designs, for picking one on a device. Debug builds only; it draws
 * nothing in release builds.
 */
@Composable
internal fun DesignCompareStrip(screen: DesignScreen, modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val current = rememberDesignOption(screen)
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("design_compare_${screen.name.lowercase()}"),
        contentPadding = PaddingValues(horizontal = PanelGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        itemsIndexed(screen.options) { index, label ->
            FilterChip(
                selected = index == current,
                onClick = { DesignOptions.set(context, screen, index) },
                // New designs are lettered A, B, C as they are referred to in conversation.
                label = { Text(if (index == 0) label else "${'A' + (index - 1)} · $label") },
                modifier = Modifier.testTag("design_option_${screen.name.lowercase()}_$index")
            )
        }
    }
}
