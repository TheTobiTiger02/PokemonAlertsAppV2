package com.example.pokemonalertsv2.data.counters

import java.text.Normalizer
import java.util.Locale

/**
 * Turns a display name plus an optional form into candidate Pokebattler `pokemonId`s.
 *
 * Pokebattler is not consistent about form suffixes — `KYUREM_BLACK_FORM` and
 * `HOUNDOOM_SHADOW_FORM` carry `_FORM`, megas are `CHARIZARD_MEGA_X`. Rather than try to
 * be exactly right, this emits an ordered best-first list of plausible ids and lets the
 * downloaded `/raids` catalogue arbitrate (see [resolveBoss]). A wrong guess therefore
 * degrades into a looser match instead of a broken request.
 */
object PokebattlerNameNormalizer {

    /** Underscore-insensitive lookup key, for near-miss matching. */
    fun looseKey(pokemonId: String): String =
        pokemonId.uppercase(Locale.ROOT).replace("_", "")

    /**
     * @param displayName e.g. "Mega Charizard X", "Alolan Marowak", "Kyurem"
     * @param form the alert's separate form field, e.g. "Black", "Alola", "Therian"
     */
    /**
     * @param megaVariant "X" or "Y" when the caller already knows it, e.g. from a Poke Genie
     *   form column reading "Mega Y".
     */
    fun candidateIds(
        displayName: String?,
        form: String? = null,
        shadow: Boolean = false,
        mega: Boolean = false,
        primal: Boolean = false,
        megaVariant: String? = null
    ): List<String> {
        val raw = displayName?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        val parsed = stripModifiers(raw)
        val base = coreNormalize(parsed.name)
        if (base.isEmpty()) return emptyList()

        val isMega = mega || parsed.mega
        val isPrimal = primal || parsed.primal
        val isShadow = shadow || parsed.shadow
        val variant = megaVariant?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: parsed.megaVariant

        val suffixes = LinkedHashSet<String>()
        variant?.let { suffixes += "MEGA_$it" }
        if (isMega && variant == null) suffixes += "MEGA"
        if (isPrimal) suffixes += "PRIMAL"
        parsed.regional?.let { suffixes += it }
        formSuffix(form)?.let { suffixes += it }
        if (isShadow) suffixes += "SHADOW"

        val candidates = LinkedHashSet<String>()
        suffixes.forEach { suffix ->
            candidates += "${base}_${suffix}_FORM"
            candidates += "${base}_$suffix"
        }
        // Combined regional/form + shadow, which the catalogue does carry for shadow raids.
        if (isShadow) {
            suffixes.filter { it != "SHADOW" }.forEach { suffix ->
                candidates += "${base}_${suffix}_SHADOW_FORM"
                candidates += "${base}_${suffix}_SHADOW"
            }
        }
        // A mega or primal is a different Pokémon to Pokebattler, with its own id and its own
        // stats, so the bare species must NOT be offered as a fallback: it would resolve a
        // "Mega Charizard X" raid to plain Charizard, and would report a Mega Y Mewtwo in the
        // user's box as though it were an ordinary Mewtwo.
        if (!isMega && !isPrimal) candidates += base
        return candidates.toList()
    }

    private data class Parsed(
        val name: String,
        val mega: Boolean = false,
        val megaVariant: String? = null,
        val primal: Boolean = false,
        val shadow: Boolean = false,
        val regional: String? = null
    )

    private fun stripModifiers(input: String): Parsed {
        var working = input.trim()
        var mega = false
        var primal = false
        var shadow = false
        var regional: String? = null
        var megaVariant: String? = null

        var matched = true
        while (matched) {
            matched = false
            for ((prefix, apply) in PREFIXES) {
                if (working.length > prefix.length &&
                    working.startsWith(prefix, ignoreCase = true) &&
                    working[prefix.length].isWhitespace()
                ) {
                    working = working.substring(prefix.length).trim()
                    when (apply) {
                        "MEGA" -> mega = true
                        "PRIMAL" -> primal = true
                        "SHADOW" -> shadow = true
                        else -> regional = apply
                    }
                    matched = true
                    break
                }
            }
        }

        // Some feeds put the form in front of the species ("Therian Landorus") rather than
        // in the separate form field. Only strip a leading word we already know as a form.
        val leading = working.substringBefore(' ', "")
        if (leading.isNotEmpty() && working.contains(' ')) {
            val alias = FORM_SUFFIX_ALIASES[leading.lowercase(Locale.ROOT)]
            if (alias != null && regional == null) {
                regional = alias
                working = working.substringAfter(' ').trim()
            }
        }

        // "Mega Charizard X" / "Mega Charizard Y" -> variant suffix on the id.
        if (mega) {
            val last = working.substringAfterLast(' ', "")
            if (last.equals("X", ignoreCase = true) || last.equals("Y", ignoreCase = true)) {
                megaVariant = last.uppercase(Locale.ROOT)
                working = working.substringBeforeLast(' ').trim()
            }
        }
        return Parsed(working, mega, megaVariant, primal, shadow, regional)
    }

    /**
     * Species name to Pokebattler's id spelling.
     *
     * Handles Farfetch'd, Nidoran gender symbols, Mr. Mime, Ho-Oh, Type: Null,
     * Porygon-Z, Jangmo-o and accented names like Flabebe.
     */
    private fun coreNormalize(name: String): String {
        var value = name.trim()
        value = value.replace("\u2640", " FEMALE").replace("\u2642", " MALE")
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
        value = value.uppercase(Locale.ROOT)
        value = value.replace(Regex("""['.:,]"""), "")
        value = value.replace(Regex("""[^A-Z0-9]+"""), "_")
        value = value.replace(Regex("""_+"""), "_")
        return value.trim('_')
    }

    private fun formSuffix(form: String?): String? {
        val key = form?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        FORM_SUFFIX_ALIASES[key]?.let { return it }
        if (key in BASE_FORM_LABELS) return null
        return coreNormalize(key).takeIf { it.isNotEmpty() }
    }

    private val PREFIXES: List<Pair<String, String>> = listOf(
        "Mega" to "MEGA",
        "Primal" to "PRIMAL",
        "Shadow" to "SHADOW",
        "Alolan" to "ALOLA",
        "Alola" to "ALOLA",
        "Galarian" to "GALARIAN",
        "Galar" to "GALARIAN",
        "Hisuian" to "HISUIAN",
        "Hisui" to "HISUIAN",
        "Paldean" to "PALDEA",
        "Paldea" to "PALDEA"
    )

    private val BASE_FORM_LABELS = setOf(
        "normal", "normal form", "natural", "natural form",
        "default", "default form", "base", "base form", ""
    )

    /** Mirrors the spirit of `GoDexMatcher.FORM_ALIASES`, mapped onto Pokebattler spellings. */
    private val FORM_SUFFIX_ALIASES: Map<String, String> = mapOf(
        "alola" to "ALOLA", "alolan" to "ALOLA",
        "galar" to "GALARIAN", "galarian" to "GALARIAN",
        "hisui" to "HISUIAN", "hisuian" to "HISUIAN",
        "paldea" to "PALDEA", "paldean" to "PALDEA",
        "therian" to "THERIAN", "incarnate" to "INCARNATE",
        "origin" to "ORIGIN", "altered" to "ALTERED",
        "black" to "BLACK", "white" to "WHITE",
        "attack" to "ATTACK", "defense" to "DEFENSE", "speed" to "SPEED",
        "sunny" to "SUNNY", "rainy" to "RAINY", "snowy" to "SNOWY",
        "dawn wings" to "DAWN_WINGS", "dusk mane" to "DUSK_MANE",
        "ultra" to "ULTRA", "crowned sword" to "CROWNED_SWORD",
        "crowned shield" to "CROWNED_SHIELD", "eternamax" to "ETERNAMAX"
    )
}
