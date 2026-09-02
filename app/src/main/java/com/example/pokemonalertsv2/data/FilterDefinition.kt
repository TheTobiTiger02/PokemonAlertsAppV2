package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/** An explicit selection. Empty sets never carry a second, hidden meaning. */
@Serializable
data class FilterSelection(
    val mode: FilterSelectionMode = FilterSelectionMode.ALL,
    val values: Set<String> = emptySet()
) {
    fun contains(value: String?): Boolean = when (mode) {
        FilterSelectionMode.ALL -> true
        FilterSelectionMode.NONE -> false
        FilterSelectionMode.ONLY -> value != null && normalizedValues.contains(normalizeFilterToken(value))
    }

    val normalizedValues: Set<String> by lazy { values.mapNotNull(::normalizeFilterTokenOrNull).toSet() }

    val selectedCount: Int get() = if (mode == FilterSelectionMode.ONLY) normalizedValues.size else 0

    companion object {
        val All = FilterSelection(FilterSelectionMode.ALL)
        val None = FilterSelection(FilterSelectionMode.NONE)

        fun only(values: Iterable<String>): FilterSelection {
            val normalized = values.mapNotNull(::normalizeFilterTokenOrNull).toSet()
            return if (normalized.isEmpty()) None else FilterSelection(FilterSelectionMode.ONLY, normalized)
        }

        /** Legacy species sets used empty for all and `_none_` for none. */
        fun fromLegacyAllowed(values: Set<String>): FilterSelection = when {
            values.isEmpty() -> All
            values.any { it.equals("_none_", ignoreCase = true) } -> None
            else -> only(values)
        }
    }
}

@Serializable
enum class FilterSelectionMode { ALL, NONE, ONLY }

@Serializable
enum class FilterAlertType(val label: String) {
    SPAWN("Spawns"),
    RAID("Raids"),
    QUEST("Quests"),
    ROCKET("Rocket"),
    KECLEON("Kecleon"),
    HUNDO("Hundos"),
    NUNDO("Nundos"),
    PVP("PvP"),
    RARE("Rare"),
    WEATHER("Weather"),
    OTHER("Other")
}

@Serializable
data class QuestPairRule(
    val taskKey: String,
    val rewardKey: String
) {
    companion object {
        fun of(task: String?, reward: String?): QuestPairRule? {
            val taskKey = normalizeFilterTokenOrNull(task) ?: return null
            val rewardKey = normalizeFilterTokenOrNull(reward) ?: return null
            return QuestPairRule(taskKey, rewardKey)
        }
    }
}

@Serializable
data class QuestFilterRules(
    val exactPairs: Set<QuestPairRule> = emptySet(),
    val facetEnabled: Boolean = false,
    val tasks: FilterSelection = FilterSelection.All,
    val rewards: FilterSelection = FilterSelection.All,
    val exactMode: FilterSelectionMode = if (exactPairs.isNotEmpty()) FilterSelectionMode.ONLY
        else if (facetEnabled) FilterSelectionMode.NONE else FilterSelectionMode.ALL
)

/**
 * Distance limits that are narrower (or wider) than the surface default.
 *
 * Resolution is most-specific-wins: species beats alert type beats [FilterDefinition.maxDistanceKm].
 * `0` means unlimited at every level, so an override can widen as well as narrow.
 */
@Serializable
data class DistanceOverrides(
    /** [FilterAlertType.name] -> km. */
    val perType: Map<String, Int> = emptyMap(),
    /** Normalized species/reward token -> km. */
    val perSpecies: Map<String, Int> = emptyMap()
) {
    val ruleCount: Int get() = perType.size + perSpecies.size

    fun withType(type: FilterAlertType, km: Int?): DistanceOverrides = copy(
        perType = if (km == null) perType - type.name else perType + (type.name to km.coerceIn(0, MAX_FILTER_DISTANCE_KM))
    )

    fun withSpecies(species: String, km: Int?): DistanceOverrides {
        val token = normalizeFilterTokenOrNull(species) ?: return this
        return copy(
            perSpecies = if (km == null) perSpecies - token else perSpecies + (token to km.coerceIn(0, MAX_FILTER_DISTANCE_KM))
        )
    }

    companion object {
        val None = DistanceOverrides()
    }
}

@Serializable
data class FilterDefinition(
    val alertTypes: FilterSelection = FilterSelection.All,
    val areas: FilterSelection = FilterSelection.All,
    val maxDistanceKm: Int = 0,
    val maxWalkingMinutes: Int = 0,
    val distanceOverrides: DistanceOverrides = DistanceOverrides.None,
    val spawnSpecies: FilterSelection = FilterSelection.All,
    val rareSpecies: FilterSelection = FilterSelection.All,
    val hundoSpecies: FilterSelection = FilterSelection.All,
    val nundoSpecies: FilterSelection = FilterSelection.All,
    val pvpSpecies: FilterSelection = FilterSelection.All,
    val raidSpecies: FilterSelection = FilterSelection.All,
    val raidTiers: FilterSelection = FilterSelection.All,
    val rocketTypes: FilterSelection = FilterSelection.All,
    val quests: QuestFilterRules = QuestFilterRules(),
    /** Retained when a legacy feed preset is promoted into the reusable library. */
    val feedSort: String? = null,
    val schemaVersion: Int = CURRENT_FILTER_SCHEMA_VERSION
) {
    val advancedRuleCount: Int
        get() = listOf(
            spawnSpecies, rareSpecies, hundoSpecies, nundoSpecies, pvpSpecies,
            raidSpecies, raidTiers, rocketTypes
        ).count { it.mode != FilterSelectionMode.ALL } +
            (if (quests.exactMode != FilterSelectionMode.ALL || quests.facetEnabled) 1 else 0) +
            distanceOverrides.ruleCount

    /** True when any distance limit is in play, so callers know whether to prefetch walking routes. */
    val usesDistanceRules: Boolean
        get() = maxDistanceKm > 0 || maxWalkingMinutes > 0 || distanceOverrides.ruleCount > 0
}

@Serializable
data class FilterProfile(
    val id: String,
    val name: String,
    val definition: FilterDefinition,
    val updatedAtMillis: Long = 0L
)

@Serializable
enum class FilterAssignmentMode { LOCAL, LINKED }

@Serializable
data class FilterAssignment(
    val mode: FilterAssignmentMode = FilterAssignmentMode.LOCAL,
    val profileId: String? = null,
    val definition: FilterDefinition = FilterDefinition()
) {
    fun resolve(document: FilterStateDocument): FilterDefinition =
        if (mode == FilterAssignmentMode.LINKED) {
            document.profiles.firstOrNull { it.id == profileId }?.definition ?: definition
        } else {
            definition
        }

    companion object {
        fun local(definition: FilterDefinition = FilterDefinition()) =
            FilterAssignment(definition = definition)

        fun linked(profile: FilterProfile) = FilterAssignment(
            mode = FilterAssignmentMode.LINKED,
            profileId = profile.id,
            definition = profile.definition
        )
    }
}

@Serializable
data class FilterStateDocument(
    val schemaVersion: Int = CURRENT_FILTER_SCHEMA_VERSION,
    val profiles: List<FilterProfile> = emptyList(),
    val feed: FilterAssignment = FilterAssignment.local(),
    val map: FilterAssignment = FilterAssignment.local(),
    val notifications: FilterAssignment = FilterAssignment.local()
) {
    fun assignment(surface: FilterSurface): FilterAssignment = when (surface) {
        FilterSurface.FEED -> feed
        FilterSurface.MAP -> map
        FilterSurface.NOTIFICATIONS -> notifications
    }

    fun withAssignment(surface: FilterSurface, assignment: FilterAssignment): FilterStateDocument = when (surface) {
        FilterSurface.FEED -> copy(feed = assignment)
        FilterSurface.MAP -> copy(map = assignment)
        FilterSurface.NOTIFICATIONS -> copy(notifications = assignment)
    }

    fun consumersOf(profileId: String): Set<FilterSurface> = FilterSurface.entries
        .filterTo(linkedSetOf()) { assignment(it).mode == FilterAssignmentMode.LINKED && assignment(it).profileId == profileId }

    /** Copies rules into linked consumers before removal, so deleting never resets behavior. */
    fun deleteProfilePreservingConsumers(profileId: String): FilterStateDocument {
        val profile = profiles.firstOrNull { it.id == profileId } ?: return this
        var result = this
        consumersOf(profileId).forEach { surface ->
            result = result.withAssignment(surface, FilterAssignment.local(profile.definition))
        }
        return result.copy(profiles = result.profiles.filterNot { it.id == profileId })
    }
}

@Serializable
enum class FilterSurface(val label: String) {
    FEED("Feed"), MAP("Map"), NOTIFICATIONS("Notifications")
}

/** 2 added [FilterDefinition.distanceOverrides]; v1 documents still decode because the field is defaulted. */
const val CURRENT_FILTER_SCHEMA_VERSION = 2
const val MAX_FILTER_PROFILE_NAME = 40
const val MAX_FILTER_DISTANCE_KM = 50

object FilterStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: FilterStateDocument): String = json.encodeToString(FilterStateDocument.serializer(), document)

    fun decode(raw: String?): FilterStateDocument? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(FilterStateDocument.serializer(), raw) }.getOrNull()
            ?.takeIf { it.schemaVersion >= 1 }
    }

    fun encodeAssignment(assignment: FilterAssignment): String =
        json.encodeToString(FilterAssignment.serializer(), assignment)

    fun decodeAssignment(raw: String?): FilterAssignment? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(FilterAssignment.serializer(), raw) }.getOrNull()
    }
}

object BuiltInFilterProfiles {
    val everything = FilterProfile("builtin-everything", "Everything", FilterDefinition())
    val highValue = FilterProfile(
        "builtin-high-value",
        "High value",
        FilterDefinition(
            alertTypes = FilterSelection.only(
                listOf(FilterAlertType.HUNDO.name, FilterAlertType.NUNDO.name, FilterAlertType.PVP.name, FilterAlertType.RARE.name)
            )
        )
    )
    val quietEssentials = FilterProfile(
        "builtin-quiet-essentials",
        "Quiet essentials",
        FilterDefinition(alertTypes = FilterSelection.only(listOf(FilterAlertType.HUNDO.name, FilterAlertType.RAID.name)))
    )
    val all: List<FilterProfile> = listOf(everything, highValue, quietEssentials)
}

data class FilterMatchContext(
    val effectiveDistanceMeters: Float? = null,
    val walkingDurationSeconds: Long? = null
)

object AlertFilterMatcher {
    fun matches(
        alert: PokemonAlert,
        definition: FilterDefinition,
        context: FilterMatchContext = FilterMatchContext()
    ): Boolean {
        if (!definition.areas.contains(alert.area)) return false
        if (definition.maxWalkingMinutes > 0) {
            val walkingSeconds = context.walkingDurationSeconds
            if (walkingSeconds != null && walkingSeconds > definition.maxWalkingMinutes * 60L) return false
        }

        // The distance limit is resolved per matched type: one alert can be several types at once
        // (a 100% spawn is both SPAWN and HUNDO) and each may carry a different override.
        return alert.filterAlertTypes().any { type ->
            definition.alertTypes.contains(type.name) &&
                matchesAdvanced(type, alert, definition) &&
                withinDistance(type, alert, definition, context)
        }
    }

    /**
     * Most specific wins: a species override beats a type override beats the surface default.
     * Returns km, where 0 means unlimited.
     */
    internal fun distanceLimitKmFor(
        type: FilterAlertType,
        alert: PokemonAlert,
        definition: FilterDefinition
    ): Int {
        val overrides = definition.distanceOverrides
        if (overrides.ruleCount > 0) {
            val token = normalizeFilterTokenOrNull(type.matchTokenFor(alert))
            if (token != null) overrides.perSpecies[token]?.let { return it }
            overrides.perType[type.name]?.let { return it }
        }
        return definition.maxDistanceKm
    }

    /** A missing or non-finite distance never hides an alert, matching the walking-time rule. */
    private fun withinDistance(
        type: FilterAlertType,
        alert: PokemonAlert,
        definition: FilterDefinition,
        context: FilterMatchContext
    ): Boolean {
        val limitKm = distanceLimitKmFor(type, alert, definition)
        if (limitKm <= 0) return true
        val distance = context.effectiveDistanceMeters ?: return true
        if (!distance.isFinite()) return true
        return distance <= limitKm * 1000f
    }

    private fun matchesAdvanced(type: FilterAlertType, alert: PokemonAlert, definition: FilterDefinition): Boolean = when (type) {
        FilterAlertType.SPAWN -> definition.spawnSpecies.contains(alert.filterSpecies())
        FilterAlertType.RARE -> definition.rareSpecies.contains(alert.filterSpecies())
        FilterAlertType.HUNDO -> definition.hundoSpecies.contains(alert.filterSpecies())
        FilterAlertType.NUNDO -> definition.nundoSpecies.contains(alert.filterSpecies())
        FilterAlertType.PVP -> definition.pvpSpecies.contains(alert.filterSpecies())
        FilterAlertType.RAID -> definition.raidSpecies.contains(alert.filterSpecies()) &&
            definition.raidTiers.contains(RaidTierParser.parse(alert)?.displayLabel)
        FilterAlertType.ROCKET -> definition.rocketTypes.contains(alert.gruntType)
        FilterAlertType.QUEST -> matchesQuest(alert, definition.quests)
        FilterAlertType.KECLEON, FilterAlertType.WEATHER, FilterAlertType.OTHER -> true
    }

    private fun matchesQuest(alert: PokemonAlert, rules: QuestFilterRules): Boolean {
        val pair = QuestPairRule.of(alert.questTask, alert.questReward)
        val exactMatch = when (rules.exactMode) {
            FilterSelectionMode.ALL -> true
            FilterSelectionMode.NONE -> false
            FilterSelectionMode.ONLY -> pair != null && pair in rules.exactPairs
        }
        val facetMatch = rules.facetEnabled &&
            rules.tasks.contains(alert.questTask) &&
            rules.rewards.contains(alert.questReward)
        return exactMatch || facetMatch
    }
}

fun PokemonAlert.filterAlertTypes(): Set<FilterAlertType> {
    val mapped = alertCategories().mapTo(linkedSetOf()) { category ->
        when (category) {
            AlertCategory.SPAWN -> FilterAlertType.SPAWN
            AlertCategory.RAID -> FilterAlertType.RAID
            AlertCategory.QUEST -> FilterAlertType.QUEST
            AlertCategory.ROCKET -> FilterAlertType.ROCKET
            AlertCategory.KECLEON -> FilterAlertType.KECLEON
            AlertCategory.HUNDO -> FilterAlertType.HUNDO
            AlertCategory.NUNDO -> FilterAlertType.NUNDO
            AlertCategory.PVP -> FilterAlertType.PVP
            AlertCategory.RARE -> FilterAlertType.RARE
            AlertCategory.WEATHER -> FilterAlertType.WEATHER
            AlertCategory.GENERIC -> FilterAlertType.OTHER
        }
    }
    if (mapped.isEmpty()) mapped += FilterAlertType.OTHER
    return mapped
}

/**
 * The value a species-scoped rule keys on for this type, mirroring what [AlertFilterMatcher]
 * already matches against: quests key on their reward, Rocket on the grunt type.
 */
fun FilterAlertType.matchTokenFor(alert: PokemonAlert): String? = when (this) {
    FilterAlertType.QUEST -> alert.questReward
    FilterAlertType.ROCKET -> alert.gruntType
    else -> alert.filterSpecies()
}

fun PokemonAlert.filterSpecies(): String = pokemon
    ?.takeIf { it.isNotBlank() }
    ?: newSpecies?.takeIf { it.isNotBlank() }
    ?: cleanPokemonName

fun normalizeFilterToken(value: String): String = normalizeFilterTokenOrNull(value).orEmpty()

// Precompiled once: the filter matcher normalizes a handful of strings per alert, so with
// 1000+ live alerts these patterns were being compiled tens of thousands of times per
// filter pass and dominated the main thread.
private val COMBINING_MARKS = Regex("""\p{M}+""")
private val NON_TOKEN_CHARS = Regex("""[^a-z0-9]+""")
private val WHITESPACE_RUNS = Regex("""\s+""")

/** Tokens that are already in their normalized spelling need no Normalizer or regex passes. */
private val PLAIN_TOKEN = Regex("""[a-z0-9]+(?: [a-z0-9]+)*""")

fun normalizeFilterTokenOrNull(value: String?): String? {
    val raw = value?.takeIf(String::isNotBlank) ?: return null
    if (PLAIN_TOKEN.matches(raw)) return raw
    return raw
        .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace("'", "")
        .replace("’", "")
        .replace(NON_TOKEN_CHARS, " ")
        .trim()
        .replace(WHITESPACE_RUNS, " ")
        .takeIf(String::isNotEmpty)
}
