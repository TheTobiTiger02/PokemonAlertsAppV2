package com.example.pokemonalertsv2.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A named combination of the feed's filter controls.
 *
 * The feed already has area, distance, type and sort; setting all four for a situation you
 * return to often ("near home, T5 raids only") is several taps every time. A preset stores
 * the state the screen already holds and puts it back in one.
 *
 * Enum values are stored by name rather than ordinal, so reordering [AlertFilter] or
 * [SortPreference] cannot silently repoint every saved preset at a different filter.
 */
@Immutable
@Serializable
data class FilterPreset(
    val name: String,
    val filter: String = "ALL",
    val sort: String = "POSTED_TIME",
    val area: String = "All",
    val maxDistance: Int = 0,
    /** [com.example.pokemonalertsv2.ui.alerts.AlertCategory] names. Empty = no type narrowing. */
    val categories: Set<String> = emptySet()
)

object FilterPresets {

    const val MAX_PRESETS = 8
    const val MAX_NAME_LENGTH = 24

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(presets: List<FilterPreset>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FilterPreset.serializer()), presets)

    /** A corrupt or hand-edited value yields no presets rather than crashing the feed. */
    fun decode(raw: String?): List<FilterPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(FilterPreset.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

    /**
     * Adds [preset], replacing any existing one with the same name.
     *
     * Replacing rather than appending means saving over a preset does what the name
     * implies; the cap then drops the oldest so the chip row cannot grow without bound.
     */
    fun upsert(existing: List<FilterPreset>, preset: FilterPreset): List<FilterPreset> {
        val normalized = preset.copy(name = normalizeName(preset.name))
        if (normalized.name.isEmpty()) return existing
        val without = existing.filterNot { it.name.equals(normalized.name, ignoreCase = true) }
        return (without + normalized).takeLast(MAX_PRESETS)
    }

    fun remove(existing: List<FilterPreset>, name: String): List<FilterPreset> =
        existing.filterNot { it.name.equals(name.trim(), ignoreCase = true) }

    fun normalizeName(name: String): String = name.trim().take(MAX_NAME_LENGTH)

    /** Falls back to the defaults when a stored name no longer matches an enum constant. */
    fun resolveFilter(preset: FilterPreset): String = preset.filter

    fun describe(preset: FilterPreset): String {
        val parts = buildList {
            if (preset.categories.isNotEmpty()) {
                add(
                    preset.categories.joinToString(" + ") { category ->
                        category.lowercase().replaceFirstChar { it.uppercase() }
                    }
                )
            } else {
                add(preset.filter.lowercase().replaceFirstChar { it.uppercase() })
            }
            if (preset.area != "All") add(preset.area)
            if (preset.maxDistance > 0) add("${preset.maxDistance} km")
        }
        return parts.joinToString(" • ")
    }
}
