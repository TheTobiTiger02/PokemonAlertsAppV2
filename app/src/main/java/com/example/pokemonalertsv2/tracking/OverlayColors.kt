package com.example.pokemonalertsv2.tracking

import android.content.Context
import android.content.res.Configuration
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.first

/**
 * The floating windows' chrome colours: the bar, the ⋯ panel, the strips and the round
 * buttons. Plain ARGB ints because the windows are Views without a Compose theme to read.
 *
 * The map tiles are not covered: the OSM raster style has no dark variant, so the pins keep
 * their own light palette to stay readable on the tiles they sit on.
 */
internal data class OverlayColors(
    val primary: Int,
    val onPrimary: Int,
    val onSurface: Int,
    val muted: Int,
    val bar: Int,
    /** The round buttons over the map and the strips; slightly see-through on purpose. */
    val control: Int,
    val panel: Int,
    val outline: Int,
    val divider: Int,
    /** The pill under the bubble. */
    val label: Int,
    val onLabel: Int,
    val bubble: Int
) {
    companion object {
        val Light = OverlayColors(
            primary = 0xFF0057D9.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            onSurface = 0xFF16181D.toInt(),
            muted = 0xFF5B6472.toInt(),
            bar = 0xFFF2F4F8.toInt(),
            control = 0xF2FFFFFF.toInt(),
            panel = 0xFFFFFFFF.toInt(),
            outline = 0x22000000,
            divider = 0x14000000,
            label = 0xE616181D.toInt(),
            onLabel = 0xFFFFFFFF.toInt(),
            bubble = 0xFFFFFFFF.toInt()
        )

        val Dark = OverlayColors(
            primary = 0xFF8AB4FF.toInt(),
            onPrimary = 0xFF0B1A33.toInt(),
            onSurface = 0xFFE6E8EE.toInt(),
            muted = 0xFFA3AAB8.toInt(),
            bar = 0xFF1E2128.toInt(),
            control = 0xF2262A33.toInt(),
            panel = 0xFF262A33.toInt(),
            outline = 0x33FFFFFF,
            divider = 0x1FFFFFFF,
            label = 0xE6F2F4F8.toInt(),
            onLabel = 0xFF16181D.toInt(),
            bubble = 0xFF262A33.toInt()
        )

        fun forDark(dark: Boolean): OverlayColors = if (dark) Dark else Light
    }
}

/** Whether the stored theme, applied to the system's current night mode, is dark. */
internal fun overlayIsDark(storedThemeMode: Int, systemDark: Boolean): Boolean =
    AppThemeMode.fromStored(storedThemeMode).resolveDark(systemDark)

internal fun systemIsDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

/** The colours the windows should wear right now; the same rule the widgets use. */
internal suspend fun resolveOverlayColors(context: Context): OverlayColors {
    val stored = AlertPreferences(context.alertPreferencesDataStore).themeMode.first()
    return OverlayColors.forDark(overlayIsDark(stored, systemIsDark(context)))
}
