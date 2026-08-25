package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/**
 * Sprite URLs for a Pokebattler id.
 *
 * Uses the same UICONS set the alert feed already points at, so counters look like the rest
 * of the app. The set names variants `{dex}_e{n}` for temporary evolutions (megas),
 * `{dex}_a{n}` for alignment (shadow) and `{dex}_f{n}` for forms, but coverage is uneven, so
 * these are offered as a best-first list and the caller falls through on a load error. The
 * plain `{dex}.png` always exists and is always last.
 *
 * Form variants are deliberately not attempted: the numeric form index is not derivable from
 * a Pokebattler id.
 */
object PokemonSpriteUrls {

    private const val BASE =
        "https://raw.githubusercontent.com/ReuschelCGN/PkmnHomeMod/main/" +
            "UICONS_Full_Shiny_Sparkle_128/pokemon"

    /** Best-first URLs to try, or empty when the dex number is unknown. */
    fun candidates(dexNumber: Int?, pokemonId: String?): List<String> {
        val dex = dexNumber?.takeIf { it > 0 } ?: return emptyList()
        val id = pokemonId?.uppercase(Locale.ROOT).orEmpty()
        val plain = "$BASE/$dex.png"

        val variant = when {
            id.contains("_MEGA") -> "$BASE/${dex}_e1.png"
            id.endsWith("_SHADOW_FORM") || id.endsWith("_SHADOW") -> "$BASE/${dex}_a1.png"
            else -> null
        }
        return listOfNotNull(variant, plain).distinct()
    }
}
