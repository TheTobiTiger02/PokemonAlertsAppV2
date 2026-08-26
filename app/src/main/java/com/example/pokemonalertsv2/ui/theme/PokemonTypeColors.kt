package com.example.pokemonalertsv2.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * The eighteen type hues.
 *
 * Deliberately fixed rather than derived from the theme: a type colour is an identity, and
 * players read it faster than the word. They are used only as low-alpha fills behind
 * theme-coloured text, so they never have to carry contrast on their own — see
 * `typeContentColor` for the one place a type colour is drawn as a foreground.
 *
 * Values are the familiar franchise hues nudged darker and slightly desaturated so that a
 * 14% fill stays visible on the light background and a 22% fill does not glow on the dark
 * one. Pokebattler names types as `POKEMON_TYPE_FIRE`, so [typeColor] strips that prefix.
 */
private val TypeHues: Map<String, Color> = mapOf(
    "NORMAL" to Color(0xFF9099A1),
    "FIRE" to Color(0xFFE2694A),
    "WATER" to Color(0xFF4A8FE2),
    "ELECTRIC" to Color(0xFFD9A62E),
    "GRASS" to Color(0xFF5BA84F),
    "ICE" to Color(0xFF4FA9AE),
    "FIGHTING" to Color(0xFFC0492F),
    "POISON" to Color(0xFF9A54B0),
    "GROUND" to Color(0xFFB07348),
    "FLYING" to Color(0xFF7E8FD4),
    "PSYCHIC" to Color(0xFFDA5F87),
    "BUG" to Color(0xFF86A32B),
    "ROCK" to Color(0xFFA08A4E),
    "GHOST" to Color(0xFF6B5FA8),
    "DRAGON" to Color(0xFF5F5FC0),
    "DARK" to Color(0xFF6C5F5A),
    "STEEL" to Color(0xFF7E8C99),
    "FAIRY" to Color(0xFFD871A8)
)

/** Bare type name, e.g. `POKEMON_TYPE_FIRE` and `Fire` both become `FIRE`. */
internal fun normalizeTypeName(raw: String?): String? = raw
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.uppercase(Locale.ROOT)
    ?.removePrefix("POKEMON_TYPE_")
    ?.takeIf { it in TypeHues }

/** Display label for a type, e.g. `POKEMON_TYPE_FIRE` -> `Fire`. */
fun typeLabel(raw: String?): String? = normalizeTypeName(raw)
    ?.lowercase(Locale.ROOT)
    ?.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * The hue for a type, or null when the name is unknown.
 *
 * Null rather than a neutral default so callers can fall back to the theme's own colours
 * instead of painting an unrecognised type as if it were a real one.
 */
fun typeColor(raw: String?): Color? = normalizeTypeName(raw)?.let { TypeHues[it] }
