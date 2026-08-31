package com.example.pokemonalertsv2.data.pokegenie

import com.example.pokemonalertsv2.data.counters.isMegaOrPrimalId
import com.example.pokemonalertsv2.data.counters.megaBaseSpeciesId
import java.util.Locale

/**
 * Adds the base-form rows Poké Genie leaves out of its export.
 *
 * Poké Genie merges a Pokémon's base form and its Mega Evolution into one entry, and the
 * CSV export contains only the mega: a box with a Mega Charizard Y gets no Charizard row,
 * so counter recommendations never offer the base form. One physical Pokémon cannot be
 * both at once, but it can be either — which one to bring is already the active-mega
 * picker's job — so the base form is synthesized at import.
 */
object MegaBaseExpander {

    /** The roster after expansion, plus how many rows [expand] added. */
    data class Result(val rows: List<PokeGenieRow>, val synthesizedBaseCount: Int)

    /**
     * A base Pokémon cannot exceed level 50, so a mega reported above that is showing the
     * Mega-Evolution level boost (Mega Level 4 adds +2) and the cap recovers the base
     * level. Mega Levels 1-3 add no levels, and the export carries no mega level to
     * correct by, so lower levels pass through unchanged.
     */
    private const val BASE_LEVEL_CAP = 50.0

    fun expand(rows: List<PokeGenieRow>): Result {
        val shadowFlags = rows.associateWith { it.shadowState == ShadowState.SHADOW }
        // The best base form the export already contains, shadow and non-shadow kept
        // separate, exactly as the counter matching keeps them. It is the *best* copy and
        // not merely "one exists": a box holding a level 50 Mega Pinsir and a level 28
        // plain Pinsir used to suppress the synthesized level 50 base entirely, and since
        // the ranking drops anything under MIN_PERSONAL_LEVEL that left no Pinsir at all.
        val scannedBases = HashMap<Pair<String, Boolean>, PokeGenieRow>()
        // One base row per species even if both Mega X and Mega Y were scanned; the
        // higher-level scan wins, since it reports the stronger base.
        val candidates = LinkedHashMap<Pair<String, Boolean>, PokeGenieRow>()

        rows.forEach { row ->
            val key = baseKeyOf(row) ?: return@forEach
            val shadow = shadowFlags.getValue(row)
            if (isMegaRow(row)) {
                val base = baseRowOf(row)
                candidates.merge(key to shadow, base) { current, challenger ->
                    if ((challenger.level ?: -1.0) > (current.level ?: -1.0)) challenger else current
                }
            } else {
                scannedBases.merge(key to shadow, row) { current, challenger ->
                    if (BEST_COPY.compare(challenger, current) > 0) challenger else current
                }
            }
        }

        val emitted = HashSet<Pair<String, Boolean>>()
        val output = ArrayList<PokeGenieRow>(rows.size + candidates.size)
        var added = 0
        rows.forEach { row ->
            output += row
            if (!isMegaRow(row)) return@forEach
            val key = baseKeyOf(row)?.let { it to shadowFlags.getValue(row) } ?: return@forEach
            if (key in emitted) return@forEach
            val base = candidates.getValue(key)
            val scanned = scannedBases[key]
            if (scanned != null && BEST_COPY.compare(base, scanned) <= 0) return@forEach
            output += base
            emitted.add(key)
            added++
        }
        return Result(output, added)
    }

    /**
     * Which of two copies of the same species is worth keeping: level, then IVs, then CP.
     *
     * Deliberately the same ordering `PokeGenieIndex.BEST_COPY` uses to pick the copy a
     * counter row represents, so the roster and the counter list never disagree about which
     * Pinsir is "yours". A synthesized base row carries no CP, which is why CP ranks last.
     */
    private val BEST_COPY: Comparator<PokeGenieRow> = compareBy(
        { it.level ?: Double.MIN_VALUE },
        { ivTotalOf(it) ?: -1 },
        { it.cp ?: -1 }
    )

    private fun ivTotalOf(row: PokeGenieRow): Int? {
        val atk = row.atkIv ?: return null
        val def = row.defIv ?: return null
        val sta = row.staIv ?: return null
        return atk + def + sta
    }

    /** True when the row's best Pokebattler id is a Mega Evolution or Primal Reversion. */
    private fun isMegaRow(row: PokeGenieRow): Boolean =
        PokeGenieMatcher.matchKeysFor(row).firstOrNull()?.isMegaOrPrimalId() == true

    /**
     * The bare species id every candidate list ends on, normalized to its base form —
     * `CHARIZARD_MEGA_X` and `CHARIZARD` both report `CHARIZARD`.
     */
    private fun baseKeyOf(row: PokeGenieRow): String? =
        PokeGenieMatcher.matchKeysFor(row).lastOrNull()?.megaBaseSpeciesId()?.takeIf { it.isNotEmpty() }

    private fun baseRowOf(mega: PokeGenieRow): PokeGenieRow {
        val baseLevel = mega.level?.coerceAtMost(BASE_LEVEL_CAP)
        return mega.copy(
            // No real scan stands behind this row.
            scanIndex = null,
            name = baseDisplayName(mega.name),
            form = null,
            // The mega's CP and HP are wrong for the base form and cannot be derived
            // without base stats; both are display weight only, so they are left empty.
            cp = null,
            hp = null,
            level = baseLevel,
            levelMin = baseLevel,
            levelMax = baseLevel
        )
    }

    /** `Mega Charizard X` -> `Charizard`; a plain name passes through untouched. */
    private fun baseDisplayName(name: String): String {
        var result = name.trim()
        var stripped = true
        while (stripped) {
            stripped = false
            for (prefix in PREFIXES) {
                if (result.length > prefix.length &&
                    result.startsWith(prefix, ignoreCase = true) &&
                    result[prefix.length].isWhitespace()
                ) {
                    result = result.substring(prefix.length).trim()
                    stripped = true
                }
            }
        }
        if (result.contains(' ')) {
            val last = result.substringAfterLast(' ')
            if (last.equals("X", ignoreCase = true) || last.equals("Y", ignoreCase = true)) {
                result = result.substringBeforeLast(' ').trim()
            }
        }
        return result.ifEmpty { name.trim() }
    }

    private val PREFIXES = listOf("mega", "primal")
}
