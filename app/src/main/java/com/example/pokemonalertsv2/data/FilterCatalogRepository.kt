package com.example.pokemonalertsv2.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FilterCatalogRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("filter_catalog_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun cached(): FilterCatalog? = preferences.getString(CACHE_KEY, null)?.let { raw ->
        runCatching { json.decodeFromString<FilterCatalog>(raw) }.getOrNull()
    }

    suspend fun refresh(): FilterCatalog {
        return runCatching { PokemonAlertsApi.catalogService.getFilterCatalog() }
            .onSuccess { catalog -> preferences.edit().putString(CACHE_KEY, json.encodeToString(catalog)).apply() }
            .getOrElse { error -> cached() ?: throw error }
    }

    suspend fun offlineCatalog(currentAlerts: List<PokemonAlert>): FilterCatalog {
        val cached = cached()
        val localSpecies = PokemonSpeciesRepository.getInstance(appContext).getAllSpeciesNames()
        return mergeFilterCatalog(cached, localSpecies, currentAlerts)
    }

    companion object {
        private const val CACHE_KEY = "catalog_v1"
        @Volatile private var instance: FilterCatalogRepository? = null
        fun getInstance(context: Context): FilterCatalogRepository {
            return instance ?: synchronized(this) {
                instance ?: FilterCatalogRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

/** Pure offline merge also keeps observed quest pairs available before a catalog refresh. */
internal fun mergeFilterCatalog(cached: FilterCatalog?, localSpecies: List<String>, currentAlerts: List<PokemonAlert>): FilterCatalog {
        val observedSpecies = currentAlerts.mapNotNull { it.pokemon?.trim()?.takeIf(String::isNotEmpty) }
        val observedRaidSpecies = currentAlerts
            .filter { FilterAlertType.RAID in it.filterAlertTypes() }
            .mapNotNull { it.filterSpecies().trim().takeIf(String::isNotEmpty) }
        val observedAreas = currentAlerts.mapNotNull { it.area?.trim()?.takeIf(String::isNotEmpty) }
        val observedRocket = currentAlerts.mapNotNull { it.gruntType?.trim()?.takeIf(String::isNotEmpty) }
        val observedQuests = currentAlerts.mapNotNull { alert ->
            if (FilterAlertType.QUEST !in alert.filterAlertTypes()) return@mapNotNull null
            val pair = QuestPairRule.of(alert.questTask, alert.questReward) ?: return@mapNotNull null
            FilterCatalogQuest(
                key = "${pair.taskKey}::${pair.rewardKey}",
                taskKey = pair.taskKey,
                rewardKey = pair.rewardKey,
                task = alert.questTask.orEmpty(),
                reward = alert.questReward.orEmpty(),
                active = true
            )
        }
        return (cached ?: FilterCatalog()).copy(
            areas = (cached?.areas.orEmpty() + observedAreas).distinctBy(::normalizeFilterToken),
            spawnSpecies = (cached?.spawnSpecies.orEmpty() + localSpecies + observedSpecies).distinctBy(::normalizeFilterToken),
            // Raid bosses are a small curated rotation from the backend, so the local Pokedex is
            // deliberately not folded in here the way it is for spawns.
            raidSpecies = (cached?.raidSpecies.orEmpty() + observedRaidSpecies).distinctBy(::normalizeFilterToken),
            raidTiers = (cached?.raidTiers.orEmpty() + RaidTier.entries.map(RaidTier::displayLabel)).distinct(),
            rocketTypes = (cached?.rocketTypes.orEmpty() + DEFAULT_ROCKET_FILTER_TYPES + observedRocket).distinctBy(::normalizeFilterToken),
            quests = (observedQuests + cached?.quests.orEmpty()).distinctBy { it.key }
        )
}
