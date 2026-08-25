package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/**
 * Sprite URLs for a Pokebattler id.
 *
 * Uses the same UICONS set the alert feed already points at, so counters look like the rest
 * of the app. Filenames carry variants as `_f{n}` for a form, `_e{n}` for a temporary
 * evolution (mega) and `_a1` for shadow, in that order, e.g. `487_f90_a1.png` for Shadow
 * Giratina Altered.
 *
 * Coverage is uneven, so these are returned best-first and the caller falls through on a
 * load error. The plain `{dex}.png` always exists and is always last.
 */
object PokemonSpriteUrls {

    private const val BASE =
        "https://raw.githubusercontent.com/ReuschelCGN/PkmnHomeMod/main/" +
            "UICONS_Full_Shiny_Sparkle_128/pokemon"

    /** Best-first URLs to try, or empty when the dex number is unknown. */
    fun candidates(
        dexNumber: Int?,
        pokemonId: String?,
        formId: Int? = null,
        megaEvoId: Int? = null
    ): List<String> {
        val dex = dexNumber?.takeIf { it > 0 } ?: return emptyList()
        val id = pokemonId?.uppercase(Locale.ROOT).orEmpty()
        val shadow = id.endsWith("_SHADOW_FORM") || id.endsWith("_SHADOW")
        val mega = id.contains("_MEGA")

        // Most specific first, so a miss degrades one step at a time rather than straight to
        // the base sprite.
        val variants = buildList {
            val evo = megaEvoId?.takeIf { it > 0 }
            val form = formId?.takeIf { it > 0 }
            if (mega && evo != null) {
                if (shadow) add("_e${evo}_a1")
                add("_e$evo")
            }
            if (mega && evo == null) {
                if (shadow) add("_e1_a1")
                add("_e1")
            }
            if (form != null) {
                if (shadow) add("_f${form}_a1")
                add("_f$form")
            }
            if (shadow) add("_a1")
            add("")
        }
        return variants.distinct().map { suffix -> "$BASE/$dex$suffix.png" }
    }
}
