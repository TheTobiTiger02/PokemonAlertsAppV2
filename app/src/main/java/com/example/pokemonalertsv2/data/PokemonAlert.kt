package com.example.pokemonalertsv2.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Represents a PvP ranking for a Pokemon.
 */
@Immutable
@Serializable
data class PvpRanking(
    val league: String? = null,
    val pokemon: String? = null,
    val rank: Int? = null,
    val cp: Int? = null,
    val level: Double? = null,
    val percentage: Double? = null
)

/**
 * Represents Pokemon moves (fast and charged attacks).
 */
@Immutable
@Serializable
data class PokemonMoves(
    val fast: String? = null,
    val charged: String? = null
) {
    /** Returns formatted moves string like "Fast Move / Charged Move" */
    fun formatted(): String? {
        val parts = listOfNotNull(fast, charged)
        return if (parts.isNotEmpty()) parts.joinToString(" / ") else null
    }
}

/**
 * Represents CP values at different levels for a 100% IV Pokemon.
 */
@Immutable
@Serializable
data class HundoCP(
    val level20: Int? = null,
    val level25: Int? = null
) {
    /** Returns formatted string like "L20: 2113 | L25: 2641" */
    fun formatted(): String? {
        val parts = mutableListOf<String>()
        level20?.let { parts.add("L20: $it") }
        level25?.let { parts.add("L25: $it") }
        return if (parts.isNotEmpty()) parts.joinToString(" | ") else null
    }
}

/**
 * Represents a Pokemon reward from a quest or encounter.
 */
@Immutable
@Serializable
data class PokemonReward(
    val rarity: String? = null,
    val pokemon: String? = null,
    val percentage: Int? = null
)

/**
 * Compact reference to an active alert invalidated by a weather change.
 */
@Immutable
@Serializable
data class AffectedAlert(
    val id: Int? = null,
    val name: String? = null,
    val pokemon: String? = null,
    val pokemonForm: String? = null,
    val cp: Int? = null,
    val type: List<String>? = null,
    val endTime: String? = null,
    val area: String? = null
)

internal fun AffectedAlert.matches(candidate: PokemonAlert): Boolean {
    id?.let { affectedId ->
        return candidate.id == affectedId
    }

    val affectedPokemon = pokemon.normalizedAlertIdentity() ?: return false
    val affectedCp = cp ?: return false
    val affectedEndTime = endTime.normalizedAlertIdentity() ?: return false
    val affectedArea = area.normalizedAlertIdentity() ?: return false
    val affectedTypes = type.normalizedAlertTypes().takeIf { it.isNotEmpty() } ?: return false

    return candidate.pokemon.normalizedAlertIdentity() == affectedPokemon &&
        candidate.pokemonForm.normalizedAlertIdentity() ==
            pokemonForm.normalizedAlertIdentity() &&
        candidate.cp == affectedCp &&
        candidate.endTime.normalizedAlertIdentity() == affectedEndTime &&
        candidate.area.normalizedAlertIdentity() == affectedArea &&
        candidate.type.normalizedAlertTypes() == affectedTypes
}

private fun String?.normalizedAlertIdentity(): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }

private fun List<String>?.normalizedAlertTypes(): Set<String> =
    orEmpty().mapNotNull { it.normalizedAlertIdentity() }.toSet()

/**
 * Represents a Pokemon Alert with all structured data from the backend API.
 */
@Immutable
@Serializable
data class PokemonAlert(
    @SerialName("id")
    val id: Int? = null,
    val name: String,
    val description: String = "",
    @SerialName("imageUrl")
    val imageUrl: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null,
    @SerialName("endTime")
    val endTime: String = "",
    val type: List<String>? = null,
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    
    // Pokemon identification
    val pokemon: String? = null,
    val pokemonForm: String? = null,
    val pokedexId: Int? = null,
    
    // IVs
    val iv: String? = null,
    val ivAttack: Int? = null,
    val ivDefense: Int? = null,
    val ivStamina: Int? = null,
    
    // Pokemon details
    val gender: String? = null,
    val isShiny: Boolean? = null,
    val cp: Int? = null,
    val level: Double? = null,
    
    // Weather
    val isWeatherBoosted: Boolean? = null,
    val currentWeather: String? = null,
    
    // Location details
    val pokemonLocation: String? = null,
    val gym: String? = null,
    val pokestop: String? = null,
    
    // Combat info
    val moves: PokemonMoves? = null,
    val hundoCP: HundoCP? = null,
    val pvpRankings: List<PvpRanking>? = null,
    
    // Team Rocket
    val gruntType: String? = null,
    
    // Quest info
    val questTask: String? = null,
    val questReward: String? = null,
    val requiresAR: Boolean? = null,
    val pokemonRewards: List<PokemonReward>? = null,
    
    // Weather change info
    val newCp: Int? = null,
    val newIv: String? = null,
    val weatherFrom: String? = null,
    val weatherTo: String? = null,
    val affectedAlerts: List<AffectedAlert> = emptyList(),
    
    // Species replacement info
    val oldSpecies: String? = null,
    val oldIv: String? = null,
    val oldCp: Int? = null,
    val newSpecies: String? = null,
    
    // Area
    val area: String? = null,
    
    // Timestamps
    val createdAt: String? = null,
    val invalidatedAt: String? = null,
    val invalidationReason: String? = null,
    val invalidatedByAlertId: Int? = null
) {
    val uniqueId: String get() = "${name.trim()}|${endTime.trim()}"
    
    /** Returns true if this is a weather-change alert */
    val isWeatherChange: Boolean get() = hasType("WeatherChange")
    
    /** Returns true if this is a species replacement alert (different species replaced the original) */
    val isSpeciesReplacement: Boolean get() = oldSpecies != null && newSpecies != null

    /** Returns true when the backend marks this alert as no longer active. */
    val isInvalidated: Boolean
        get() = invalidatedAt != null ||
            invalidationReason != null ||
            invalidatedByAlertId != null

    val googleMapsUri: Uri?
        get() = if (latitude != null && longitude != null) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        } else null
    
    /** Returns formatted IV string like "15/15/15" or the raw iv field */
    val formattedIv: String?
        get() = iv ?: if (ivAttack != null && ivDefense != null && ivStamina != null) {
            "$ivAttack/$ivDefense/$ivStamina"
        } else null
    
    /** Returns IV percentage (0-100) if all IVs are available */
    val ivPercentage: Int?
        get() = if (ivAttack != null && ivDefense != null && ivStamina != null) {
            ((ivAttack + ivDefense + ivStamina) * 100) / 45
        } else null
    
    /** Returns true if this is a perfect (100%) IV Pokemon */
    val isPerfect: Boolean
        get() = ivAttack == 15 && ivDefense == 15 && ivStamina == 15
    
    /** Returns true if this is a zero (0%) IV Pokemon */
    val isNundo: Boolean
        get() = ivAttack == 0 && ivDefense == 0 && ivStamina == 0
    
    /** Clean Pokemon name without emoji prefixes */
    val cleanPokemonName: String
        get() = pokemon ?: name.replace(Regex("^[^a-zA-Z]+"), "").trim()
    
    /** Location display text - prioritizes pokemonLocation, then gym, then pokestop */
    val locationDisplay: String?
        get() = pokemonLocation?.takeIf { it.isNotBlank() }
            ?: gym?.takeIf { it.isNotBlank() }
            ?: pokestop?.takeIf { it.isNotBlank() }
    
    /** Check if alert has a specific type (case-insensitive) */
    fun hasType(typeName: String): Boolean =
        type?.any { it.equals(typeName, ignoreCase = true) } == true
    
    /** Check if alert contains a type that matches partially (case-insensitive) */
    fun hasTypeContaining(substring: String): Boolean =
        type?.any { it.contains(substring, ignoreCase = true) } == true
    
    /** Returns the type list as a formatted string (e.g., "Hundo, PvP") */
    val typeDisplay: String?
        get() = type?.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
