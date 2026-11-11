package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather

/**
 * The weather an alert reports, ready to render.
 *
 * Every surface (detail card, feed row, notification glance, map badge) goes through this so the
 * wording and the glyph never drift apart.
 */
@Immutable
internal data class CurrentWeatherDisplay(
    /** Feed text with the decoration stripped, e.g. "Partly Cloudy". */
    val label: String,
    /** Compact stand-in for surfaces with no room for words. */
    val glyph: String,
    /**
     * False only when the server explicitly said so. The weather is still used as reported —
     * unconfirmed means "last known", not "unknown" — but it is labelled everywhere it shows.
     */
    val confirmed: Boolean,
    val boosted: Boolean
) {
    /** Glyph plus words, for anything with a line to spare. */
    val labelWithGlyph: String get() = "$glyph $label"

    /** Glyph, words and the caveat, for single-line surfaces. */
    val compactLabel: String get() = if (confirmed) labelWithGlyph else "$labelWithGlyph?"
}

internal const val UNCONFIRMED_WEATHER_NOTE = "Not confirmed yet"

internal fun currentWeatherDisplay(alert: PokemonAlert): CurrentWeatherDisplay? =
    currentWeatherDisplay(
        weather = alert.currentWeather,
        confirmed = alert.currentWeatherConfirmed,
        boosted = alert.isWeatherBoosted == true
    )

/**
 * @param confirmed null means the server said nothing, which is treated as confirmed so older
 *   payloads do not sprout a caveat they never earned.
 */
internal fun currentWeatherDisplay(
    weather: String?,
    confirmed: Boolean?,
    boosted: Boolean = false
): CurrentWeatherDisplay? {
    val raw = weather?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // Reuses the Pokebattler mapper, which already strips the feed's emoji and diacritics.
    val known = PokebattlerWeather.fromAlertWeather(raw)
    return CurrentWeatherDisplay(
        label = known?.let(::weatherLabel) ?: raw.stripDecoration(),
        glyph = known.glyph(),
        confirmed = confirmed != false,
        boosted = boosted
    )
}

/** The game's own wording, which is shorter than Pokebattler's option labels. */
private fun weatherLabel(weather: PokebattlerWeather): String = when (weather) {
    PokebattlerWeather.NONE -> "No weather"
    PokebattlerWeather.CLEAR -> "Clear"
    PokebattlerWeather.RAINY -> "Rain"
    PokebattlerWeather.PARTLY_CLOUDY -> "Partly cloudy"
    PokebattlerWeather.OVERCAST -> "Cloudy"
    PokebattlerWeather.WINDY -> "Windy"
    PokebattlerWeather.SNOW -> "Snow"
    PokebattlerWeather.FOG -> "Fog"
}

private fun PokebattlerWeather?.glyph(): String = when (this) {
    PokebattlerWeather.CLEAR -> "☀️"
    PokebattlerWeather.RAINY -> "🌧️"
    PokebattlerWeather.PARTLY_CLOUDY -> "⛅"
    PokebattlerWeather.OVERCAST -> "☁️"
    PokebattlerWeather.WINDY -> "💨"
    PokebattlerWeather.SNOW -> "❄️"
    PokebattlerWeather.FOG -> "🌫️"
    // Unrecognised text still renders, with a neutral glyph rather than a guess.
    PokebattlerWeather.NONE, null -> "🌡️"
}

/** Drops the emoji the feed appends ("Partly Cloudy 🌤") without touching the words. */
private fun String.stripDecoration(): String =
    replace(Regex("""[^\p{L}\p{N}\s/'-]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifEmpty { this }
