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

    /** `PALKIA_SHADOW_FORM` -> `PALKIA`. Shadow forms have no game master entry of their own. */
    fun baseSpeciesId(pokemonId: String): String = pokemonId.uppercase(Locale.ROOT)
        .removeSuffix("_SHADOW_FORM")
        .removeSuffix("_SHADOW")

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
        suffixes += formSuffixes(form, base)
        if (isShadow) suffixes += "SHADOW"

        val candidates = LinkedHashSet<String>()
        val formSuffixes = suffixes.filter { it != "SHADOW" }

        // Shadow-ness outranks form-ness. Pokebattler models exactly ONE shadow Giratina
        // (GIRATINA_SHADOW_FORM), not a shadow per form, and GIRATINA_ALTERED_FORM does
        // exist and is NOT shadow -- so emitting the form first would confidently resolve a
        // Shadow Giratina to the wrong boss. Shadow is a 1.2x attack multiplier; a form is
        // usually a stat reshuffle, so shadow is the one to keep when only one can be had.
        if (isShadow) {
            formSuffixes.forEach { suffix ->
                candidates += "${base}_${suffix}_SHADOW_FORM"
                candidates += "${base}_${suffix}_SHADOW"
            }
            candidates += "${base}_SHADOW_FORM"
            candidates += "${base}_SHADOW"
        }

        formSuffixes.forEach { suffix ->
            candidates += "${base}_${suffix}_FORM"
            candidates += "${base}_$suffix"
        }
        // A mega or primal of a form still needs the plain mega/primal id as a fallback.
        if (isMega && variant == null) candidates += "${base}_MEGA"
        if (isPrimal) candidates += "${base}_PRIMAL"
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

        // "Shadow Giratina Altered Forme" glues the form onto the name with no form field.
        // Peel a trailing "Form"/"Forme" word, then a trailing one- or two-word form we
        // already recognise, so the base species is left on its own. Never empty the name.
        if (regional == null) {
            var words = working.split(" ").filter { it.isNotEmpty() }
            if (words.size > 1 && words.last().lowercase(Locale.ROOT) in FORM_WORDS) {
                words = words.dropLast(1)
            }
            if (words.size > 2) {
                val pair = words.takeLast(2).joinToString(" ").lowercase(Locale.ROOT)
                FORM_SUFFIX_ALIASES[pair]?.let {
                    regional = it
                    words = words.dropLast(2)
                }
            }
            if (regional == null && words.size > 1) {
                FORM_SUFFIX_ALIASES[words.last().lowercase(Locale.ROOT)]?.let {
                    regional = it
                    words = words.dropLast(1)
                }
            }
            if (words.isNotEmpty()) working = words.joinToString(" ")
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
        // The alert feed writes "Altered Forme"; Pokebattler writes "_FORM".
        value = value.replace(Regex("""(^|_)FORME(?=_|$)"""), "$1FORM")
        return value.trim('_')
    }

    /**
     * Ordered, best-first form suffixes for [form].
     *
     * One Poke Genie label can legitimately map to more than one Pokebattler spelling --
     * "Sword" is CROWNED_SWORD on Zacian, "Paldean Blaze" is PALDEA_BLAZE, "Pom-Pom" is
     * POMPOM. Emitting several suffixes lets the candidate list arbitrate rather than
     * requiring this function to be exactly right, and a wrong guess simply never matches.
     *
     * @param base the already-normalized species id, so a form that repeats the species
     *   name can be reduced to the part that actually names the form, and so a
     *   species-scoped alias can be applied.
     */
    private fun formSuffixes(form: String?, base: String): List<String> {
        val trimmed = form?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val key = trimmed.lowercase(Locale.ROOT)

        val suffixes = LinkedHashSet<String>()
        SPECIES_FORM_ALIASES[base to key]?.let { suffixes += it }
        FORM_SUFFIX_ALIASES[key]?.let { suffixes += it }
        if (suffixes.isEmpty() && key in BASE_FORM_LABELS) return emptyList()

        // The feed writes the form as "Black Kyurem", not "Black". Left alone that becomes
        // KYUREM_BLACK_KYUREM_FORM, which does not exist, and the candidate list then falls
        // through to the bare KYUREM -- a different boss with different typing. Drop the
        // species word and the remainder ("Black") hits the alias table and KYUREM_BLACK_FORM.
        val withoutSpecies = dropSpeciesWords(trimmed, base)
        if (withoutSpecies != null) {
            val stripped = withoutSpecies.lowercase(Locale.ROOT)
            FORM_SUFFIX_ALIASES[stripped]?.let { suffixes += it }
            if (stripped !in BASE_FORM_LABELS) suffixes += spellings(withoutSpecies)
        }
        suffixes += spellings(trimmed)
        return suffixes.toList()
    }

    /**
     * Plausible Pokebattler spellings of one unrecognized form label, best-first.
     *
     * Pokebattler is not consistent about how it writes a multi-word form, so rather than
     * enumerate every label this normalizes the pieces and offers each spelling.
     */
    private fun spellings(form: String): List<String> {
        val words = coreNormalize(form).split("_").filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()

        // "Paldean Blaze" -> PALDEA_BLAZE. A regional is spelled one way on its own and
        // another inside a longer form, so the alias has to be applied word by word.
        val regionalized = words.map { word ->
            val lower = word.lowercase(Locale.ROOT)
            if (lower in REGIONAL_WORDS) FORM_SUFFIX_ALIASES.getValue(lower) else word
        }
        out += regionalized.joinToString("_")
        out += words.joinToString("_")

        // "Two-Segment" -> TWO, "Plant Cloak" -> PLANT. Only drops words that carry no
        // meaning of their own, and only while something is left. GASTRODON_EAST_SEA_FORM
        // is why "sea" is not on the list.
        val meaningful = regionalized.filter { it.lowercase(Locale.ROOT) !in FORM_NOISE_WORDS }
        if (meaningful.isNotEmpty()) out += meaningful.joinToString("_")

        // "Pom-Pom" -> POMPOM. Pokebattler glues some multi-word forms together, and the
        // exact species lookup in PersonalCounterEngine has no loose-key fallback.
        if (words.size > 1) out += words.joinToString("")
        return out.toList()
    }

    /** Removes words of [base] from [form], or null when nothing would be left. */
    private fun dropSpeciesWords(form: String, base: String): String? {
        val speciesWords = base.split("_").filter { it.isNotEmpty() }.toSet()
        if (speciesWords.isEmpty()) return null
        val kept = form.split(" ")
            .filter { it.isNotEmpty() }
            .filter { coreNormalize(it) !in speciesWords }
        if (kept.isEmpty() || kept.size == form.split(" ").filter { it.isNotEmpty() }.size) {
            return null
        }
        return kept.joinToString(" ")
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

    /** Words the feed appends that only mark "this is a form", carrying no meaning. */
    private val FORM_WORDS = setOf("form", "forme")

    private val BASE_FORM_LABELS = setOf(
        "normal", "normal form", "natural", "natural form",
        "default", "default form", "base", "base form", ""
    )

    /** Form words that only mark a variant group and are absent from the Pokebattler id. */
    private val FORM_NOISE_WORDS = setOf("form", "forme", "cloak", "style", "size", "mode", "segment")

    /** Alias keys that name a region, and so also apply to a single word inside a form. */
    private val REGIONAL_WORDS =
        setOf("alola", "alolan", "galar", "galarian", "hisui", "hisuian", "paldea", "paldean")

    /**
     * Forms whose label only becomes unambiguous once the species is known.
     *
     * Poke Genie writes short labels: Zacian's Crowned Sword is just "Sword". A global
     * "sword"/"shield" alias would be wrong for everyone else -- AEGISLASH_SHIELD_FORM and
     * AEGISLASH_BLADE_FORM are real ids and must keep resolving to themselves.
     */
    private val SPECIES_FORM_ALIASES: Map<Pair<String, String>, String> = mapOf(
        ("ZACIAN" to "sword") to "CROWNED_SWORD",
        ("ZACIAN" to "crowned") to "CROWNED_SWORD",
        ("ZAMAZENTA" to "shield") to "CROWNED_SHIELD",
        ("ZAMAZENTA" to "crowned") to "CROWNED_SHIELD",
        // There is no bare GALARIAN Darmanitan; its default Galarian form is Standard.
        ("DARMANITAN" to "galar") to "GALARIAN_STANDARD",
        ("DARMANITAN" to "galarian") to "GALARIAN_STANDARD",
        // Pokebattler spells the percentages out, and coreNormalize would drop the sign.
        ("ZYGARDE" to "50%") to "FIFTY_PERCENT",
        ("ZYGARDE" to "50") to "FIFTY_PERCENT",
        ("ZYGARDE" to "10%") to "TEN_PERCENT",
        ("ZYGARDE" to "10") to "TEN_PERCENT"
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
