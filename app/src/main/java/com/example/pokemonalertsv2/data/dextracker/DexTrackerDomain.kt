package com.example.pokemonalertsv2.data.dextracker

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A Dex Tracker collection (`users/{uid}/collections/{id}`). */
data class DexTrackerCollection(val id: String, val title: String, val archived: Boolean, val createdAt: Double)

/** A named alt account that holds spares to trade over (`users/{uid}/accounts/{id}`). */
data class DexTrackerAccount(val id: String, val name: String, val order: Int)

/**
 * One entry of a collection: a Pokémon line and the copies of it. As Dex Tracker's `Entry`
 * (src/domain.ts): [owned] per evolution stage on the main account, [unassigned] copies on the
 * main account not yet given a stage, spares on other accounts ([extras] without an account,
 * [alts] per named alt account). Counts of zero are left out of the maps.
 */
data class DexTrackerEntry(
    val id: String,
    val pokemonId: Int,
    val name: String,
    val label: String = "",
    val target: Int,
    val targets: List<Int> = emptyList(),
    val owned: Map<String, Int> = emptyMap(),
    val unassigned: Int = 0,
    val extras: Int = 0,
    val alts: Map<String, Int> = emptyMap(),
    val order: Int = 0,
    val revision: Long = 0
)

data class DexTrackerProgress(
    val owned: Int,
    val missing: Int,
    val toCatch: Int,
    val tradable: Int,
    val complete: Boolean,
    val evolved: Int
)

data class DexTrackerTotals(
    val owned: Int = 0,
    val credited: Int = 0,
    val target: Int = 0,
    val extras: Int = 0,
    val tradable: Int = 0,
    val missing: Int = 0,
    val toCatch: Int = 0,
    val complete: Int = 0,
    val evolved: Int = 0,
    val evolutionTargets: Int = 0
)

enum class DexTrackerStage { OWNED, UNASSIGNED, TRADE, MISSING }

/** The web app's list filters; [ALT] with an account: what that alt can fill right now. */
enum class DexTrackerFilterKind { MISSING, CATCH, TRADES, EVOLUTIONS, COMPLETE, ALL, ALT }
data class DexTrackerFilter(val kind: DexTrackerFilterKind, val account: String? = null)

/** Where a stage's copy comes from when it is ticked. */
sealed interface DexTrackerSource {
    data object Unassigned : DexTrackerSource
    /** A spare traded over, from [account] (null: the spares without an account). */
    data class Spare(val account: String?) : DexTrackerSource
    data object NewCatch : DexTrackerSource
    /** A copy moved over from another owned stage. */
    data class Stage(val pokemonId: Int) : DexTrackerSource
}

/** The quick actions of the web app's list and entry sheet (domain.ts `EntryAction`). */
sealed interface DexTrackerAction {
    /** ±1 on the main account ([account] ignored) or on a spare pool. */
    data class Add(val main: Boolean, val delta: Int, val account: String? = null) : DexTrackerAction
    data class Trade(val count: Int, val account: String?) : DexTrackerAction
    data class Assign(val pokemonId: Int, val source: DexTrackerSource) : DexTrackerAction
    data class Remove(val pokemonId: Int) : DexTrackerAction
}

/** A quick edit as Firestore increments, so it saves offline and merges with other edits. */
data class DexTrackerPatch(val next: DexTrackerEntry, val increments: Map<String, Long>, val gained: Int)

/** One activity log event (`events/{id}`), as domain.ts `ActivityEvent`. */
data class DexTrackerEvent(
    val id: String,
    val at: Long,
    val entryId: String,
    val entryName: String,
    val pokemonId: Int,
    val kind: String,
    val count: Int,
    val change: Int,
    /** Set for trades and spares; its value is null for the spares without an account. */
    val account: DexTrackerPool? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("id", id); put("at", at); put("entryId", entryId); put("entryName", entryName)
        put("pokemonId", pokemonId); put("kind", kind); put("count", count); put("change", change)
        if (account != null) put("account", account.account)
    }
}

/** A spare pool: a named alt account, or [account] null for none. */
data class DexTrackerPool(val account: String?)

class DexTrackerException(message: String) : IllegalStateException(message)

/**
 * Dex Tracker's own rules (PokemonGoChecklist src/domain.ts), ported for the app's list and
 * quick actions. DexTrackerDomainTest checks it against cases worked out by domain.ts itself
 * (scripts/android-fixtures.mjs there).
 */
object DexTrackerDomain {
    fun ownedCount(entry: DexTrackerEntry): Int = entry.unassigned + entry.owned.values.sum()

    /** All spare copies on other accounts, named or not. */
    fun spares(entry: DexTrackerEntry): Int = entry.extras + entry.alts.values.sum()

    fun poolCount(entry: DexTrackerEntry, account: String?): Int = if (account == null) entry.extras else entry.alts[account] ?: 0

    /** Pools that hold a spare: no account first, then named alts. */
    fun sparePools(entry: DexTrackerEntry): List<String?> =
        (if (entry.extras > 0) listOf<String?>(null) else emptyList()) + entry.alts.filterValues { it > 0 }.keys

    /** Copies from one pool that would fill a missing spot. */
    fun usefulFrom(entry: DexTrackerEntry, account: String?): Int = minOf(progress(entry).missing, poolCount(entry, account))

    /** Copies that count towards the target; extra copies beyond it do not. */
    fun credited(entry: DexTrackerEntry): Int = minOf(ownedCount(entry), entry.target)

    fun progress(entry: DexTrackerEntry): DexTrackerProgress {
        val owned = ownedCount(entry)
        val missing = maxOf(0, entry.target - owned)
        val spare = spares(entry)
        return DexTrackerProgress(
            owned = owned,
            missing = missing,
            toCatch = maxOf(0, missing - spare),
            tradable = minOf(missing, spare),
            complete = missing == 0,
            evolved = entry.targets.count { (entry.owned[it.toString()] ?: 0) > 0 }
        )
    }

    /**
     * Each evolution stage: missing stages are covered first by unassigned copies on the main
     * account, then by spares that can be traded over; the rest still need catching.
     */
    fun stageStatus(entry: DexTrackerEntry): Map<Int, DexTrackerStage> {
        var unassigned = entry.unassigned
        var tradable = progress(entry).tradable
        return entry.targets.associateWith { id ->
            when {
                (entry.owned[id.toString()] ?: 0) > 0 -> DexTrackerStage.OWNED
                unassigned > 0 -> DexTrackerStage.UNASSIGNED.also { unassigned-- }
                tradable > 0 -> DexTrackerStage.TRADE.also { tradable-- }
                else -> DexTrackerStage.MISSING
            }
        }
    }

    fun totals(entries: List<DexTrackerEntry>): DexTrackerTotals = entries.fold(DexTrackerTotals()) { sum, entry ->
        val p = progress(entry)
        DexTrackerTotals(
            owned = sum.owned + p.owned,
            credited = sum.credited + minOf(p.owned, entry.target),
            target = sum.target + entry.target,
            extras = sum.extras + spares(entry),
            tradable = sum.tradable + p.tradable,
            missing = sum.missing + p.missing,
            toCatch = sum.toCatch + p.toCatch,
            complete = sum.complete + if (p.complete) 1 else 0,
            evolved = sum.evolved + p.evolved,
            evolutionTargets = sum.evolutionTargets + entry.targets.size
        )
    }

    fun matchesFilter(entry: DexTrackerEntry, filter: DexTrackerFilter): Boolean {
        val p = progress(entry)
        return when (filter.kind) {
            DexTrackerFilterKind.ALL -> true
            DexTrackerFilterKind.MISSING -> p.missing > 0
            DexTrackerFilterKind.CATCH -> p.toCatch > 0
            DexTrackerFilterKind.TRADES -> p.tradable > 0
            DexTrackerFilterKind.COMPLETE -> p.complete
            DexTrackerFilterKind.EVOLUTIONS -> p.evolved < entry.targets.size
            DexTrackerFilterKind.ALT -> usefulFrom(entry, filter.account) > 0
        }
    }

    /**
     * What a tap on a stage does, as the web list's toggleStage: an owned stage loses its copy;
     * a missing one takes an unassigned copy, else a spare (null when several pools hold one and
     * the user has to pick), else a new catch.
     */
    fun stageTap(entry: DexTrackerEntry, pokemonId: Int): DexTrackerAction? {
        if ((entry.owned[pokemonId.toString()] ?: 0) > 0) return DexTrackerAction.Remove(pokemonId)
        val source = when {
            entry.unassigned > 0 -> DexTrackerSource.Unassigned
            progress(entry).tradable > 0 -> sparePools(entry).singleOrNull()?.let { DexTrackerSource.Spare(it) } ?: return null
            else -> DexTrackerSource.NewCatch
        }
        return DexTrackerAction.Assign(pokemonId, source)
    }

    /** Whether main −1 can be done here: the web app sends several owned stages to its copy editor. */
    fun canRemoveFromMain(entry: DexTrackerEntry): Boolean =
        entry.unassigned > 0 || entry.owned.count { it.value > 0 } == 1

    private fun fail(message: String): Nothing = throw DexTrackerException(message)

    private fun MutableMap<String, Int>.adjust(key: String, delta: Int) {
        val value = (this[key] ?: 0) + delta
        if (value == 0) remove(key) else this[key] = value
    }

    /** Removes [count] spares from a pool (a negative count adds them). */
    private fun takeSpares(entry: DexTrackerEntry, account: String?, count: Int): DexTrackerEntry {
        val left = poolCount(entry, account) - count
        if (left < 0) fail(if (account == null) "There are not enough spares without an account." else "That alt account does not have enough spares.")
        return if (account == null) entry.copy(extras = left)
        else entry.copy(alts = entry.alts.toMutableMap().apply { if (left == 0) remove(account) else this[account] = left })
    }

    fun apply(current: DexTrackerEntry, action: DexTrackerAction): DexTrackerEntry {
        var entry = current
        when (action) {
            is DexTrackerAction.Add -> {
                if (kotlin.math.abs(action.delta) != 1) fail("Invalid count change.")
                entry = when {
                    !action.main -> takeSpares(entry, action.account, -action.delta)
                    action.delta > 0 -> entry.copy(unassigned = entry.unassigned + 1)
                    entry.unassigned > 0 -> entry.copy(unassigned = entry.unassigned - 1)
                    else -> {
                        val remaining = entry.owned.filterValues { it > 0 }.keys
                        if (remaining.size != 1) fail("Choose which Pokémon to remove in the copy editor.")
                        entry.copy(owned = entry.owned.toMutableMap().apply { adjust(remaining.single(), -1) })
                    }
                }
            }
            is DexTrackerAction.Trade -> {
                if (action.count < 1) fail("There are not enough spares to trade.")
                entry = takeSpares(entry, action.account, action.count).let { it.copy(unassigned = it.unassigned + action.count) }
            }
            is DexTrackerAction.Assign -> {
                val key = action.pokemonId.toString()
                if (action.pokemonId !in entry.targets || (entry.owned[key] ?: 0) > 0) {
                    fail("This evolution is already owned or is outside this entry’s targets.")
                }
                entry = when (val source = action.source) {
                    DexTrackerSource.Unassigned -> {
                        if (entry.unassigned < 1) fail("There are no unassigned copies left.")
                        entry.copy(unassigned = entry.unassigned - 1)
                    }
                    is DexTrackerSource.Spare -> takeSpares(entry, source.account, 1)
                    DexTrackerSource.NewCatch -> entry
                    is DexTrackerSource.Stage -> {
                        val from = source.pokemonId.toString()
                        if (source.pokemonId == action.pokemonId || (entry.owned[from] ?: 0) < 1) fail("That source copy is no longer available.")
                        entry.copy(owned = entry.owned.toMutableMap().apply { adjust(from, -1) })
                    }
                }
                entry = entry.copy(owned = entry.owned.toMutableMap().apply { this[key] = 1 })
            }
            is DexTrackerAction.Remove -> {
                val key = action.pokemonId.toString()
                if ((entry.owned[key] ?: 0) < 1) fail("There is no copy of this Pokémon to remove.")
                entry = entry.copy(owned = entry.owned.toMutableMap().apply { adjust(key, -1) })
            }
        }
        return entry.copy(revision = entry.revision + 1)
    }

    fun actionPatch(entry: DexTrackerEntry, action: DexTrackerAction): DexTrackerPatch = patchBetween(entry, apply(entry, action))

    /**
     * Undoes a quick edit ([before] → [after]) on the entry as it is now, by reversing each count
     * change; changes made since on other counts stay.
     */
    fun revertPatch(current: DexTrackerEntry, before: DexTrackerEntry, after: DexTrackerEntry): DexTrackerPatch {
        fun reverse(target: Map<String, Int>, from: Map<String, Int>, to: Map<String, Int>): Map<String, Int> {
            val result = target.toMutableMap()
            for (key in from.keys + to.keys) {
                val value = (result[key] ?: 0) + (from[key] ?: 0) - (to[key] ?: 0)
                if (value < 0) fail("This can no longer be undone; the counts changed.")
                if (value == 0) result.remove(key) else result[key] = value
            }
            return result
        }
        val next = current.copy(
            unassigned = current.unassigned + before.unassigned - after.unassigned,
            extras = current.extras + before.extras - after.extras,
            owned = reverse(current.owned, before.owned, after.owned),
            alts = reverse(current.alts, before.alts, after.alts),
            revision = current.revision + 1
        )
        if (next.unassigned < 0 || next.extras < 0) fail("This can no longer be undone; the counts changed.")
        return patchBetween(current, next)
    }

    /** Increments that turn [entry] into [next] (the revision included). */
    fun patchBetween(entry: DexTrackerEntry, next: DexTrackerEntry): DexTrackerPatch {
        val increments = LinkedHashMap<String, Long>()
        fun add(field: String, delta: Int) { if (delta != 0) increments[field] = delta.toLong() }
        add("unassigned", next.unassigned - entry.unassigned)
        add("extras", next.extras - entry.extras)
        (entry.alts.keys + next.alts.keys).forEach { add("alts.$it", (next.alts[it] ?: 0) - (entry.alts[it] ?: 0)) }
        (entry.owned.keys + next.owned.keys).forEach { add("owned.$it", (next.owned[it] ?: 0) - (entry.owned[it] ?: 0)) }
        add("revision", 1)
        return DexTrackerPatch(next, increments, credited(next) - credited(entry))
    }

    /** How an entry changed in one save, for the activity log; null when no count changed. */
    fun describeChange(prev: DexTrackerEntry, next: DexTrackerEntry, at: Long, id: String): DexTrackerEvent? {
        fun diff(a: Map<String, Int>, b: Map<String, Int>) =
            (a.keys + b.keys).map { it to (b[it] ?: 0) - (a[it] ?: 0) }.filter { it.second != 0 }
        val owned = diff(prev.owned, next.owned)
        val pools: List<Pair<String?, Int>> =
            (if (next.extras != prev.extras) listOf<Pair<String?, Int>>(null to next.extras - prev.extras) else emptyList()) + diff(prev.alts, next.alts)
        val unassigned = next.unassigned - prev.unassigned
        if (owned.isEmpty() && pools.isEmpty() && unassigned == 0) return null
        val ups = owned.filter { it.second > 0 }
        val downs = owned.filter { it.second < 0 }
        val spareUp = pools.filter { it.second > 0 }
        val spareDown = pools.filter { it.second < 0 }
        val change = credited(next) - credited(prev)
        fun event(kind: String, count: Int, pokemonId: Int = next.pokemonId, account: DexTrackerPool? = null) =
            DexTrackerEvent(id, at, next.id, next.name, pokemonId, kind, count, change, account)
        if (ups.size == 1 && ups[0].second == 1) {
            val stage = ups[0].first.toInt()
            if (downs.isEmpty() && unassigned == 0 && pools.isEmpty()) return event("caught", 1, stage)
            if (downs.isEmpty() && unassigned == 0 && spareDown.size == 1 && spareUp.isEmpty() && spareDown[0].second == -1) {
                return event("traded", 1, stage, DexTrackerPool(spareDown[0].first))
            }
            if (pools.isEmpty() && ((unassigned == -1 && downs.isEmpty()) || (unassigned == 0 && downs.size == 1 && downs[0].second == -1))) {
                return event("assigned", 1, stage)
            }
        }
        if (ups.isEmpty() && downs.size == 1 && unassigned == 0 && pools.isEmpty()) return event("removed", downs[0].second, downs[0].first.toInt())
        if (owned.isEmpty() && pools.isEmpty()) return event(if (unassigned > 0) "caught" else "removed", unassigned)
        if (owned.isEmpty() && spareDown.size == 1 && spareUp.isEmpty() && unassigned == -spareDown[0].second) {
            return event("traded", unassigned, next.pokemonId, DexTrackerPool(spareDown[0].first))
        }
        if (owned.isEmpty() && unassigned == 0 && pools.size == 1) return event("spare", pools[0].second, next.pokemonId, DexTrackerPool(pools[0].first))
        if (owned.isEmpty() && unassigned == 0 && spareUp.size == 1 && spareDown.size == 1 && spareUp[0].second == -spareDown[0].second) {
            return event("moved", spareUp[0].second, next.pokemonId, DexTrackerPool(spareUp[0].first))
        }
        return event("edited", ownedCount(next) - ownedCount(prev))
    }

    /** Local calendar day as YYYY-MM-DD (domain.ts dayKey). */
    fun dayKey(date: LocalDate = LocalDate.now()): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
}
