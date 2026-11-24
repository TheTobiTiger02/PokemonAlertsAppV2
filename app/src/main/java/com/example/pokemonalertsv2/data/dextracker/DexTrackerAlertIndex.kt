package com.example.pokemonalertsv2.data.dextracker

import com.example.pokemonalertsv2.data.database.GoDexEntryEntity

/**
 * A Dex Tracker collection as the alert badges read checklists: one entry per national dex
 * number, needed while a stage of that species still has to be caught. A stage an unassigned
 * copy or a spare can fill is not needed. An entry without stages is needed while it has copies
 * to catch. One species in several entries (labels, forms) is needed when any of them needs it.
 */
object DexTrackerAlertIndex {
    fun entries(collection: List<DexTrackerEntry>, catalog: DexTrackerCatalog): List<GoDexEntryEntity> {
        val needed = LinkedHashMap<Int, Boolean>()
        fun mark(pokemonId: Int, need: Boolean) {
            val dex = catalog.speciesOf(pokemonId)
            needed[dex] = (needed[dex] ?: false) || need
        }
        collection.forEach { entry ->
            if (entry.targets.isEmpty()) {
                mark(entry.pokemonId, DexTrackerDomain.progress(entry).toCatch > 0)
            } else {
                DexTrackerDomain.stageStatus(entry).forEach { (stage, status) -> mark(stage, status == DexTrackerStage.MISSING) }
            }
        }
        return needed.map { (dex, need) ->
            GoDexEntryEntity(
                entryKey = "%04d-none".format(dex),
                pokedexId = dex,
                formSlug = null,
                gender = "none",
                displayName = catalog.byId[dex]?.name ?: "#$dex",
                needed = need
            )
        }
    }
}
