package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.PvpRanking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val uniqueId: String,
    val id: Int? = null,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val longitude: Double,
    val latitude: Double,
    val endTime: String,
    val type: String?,
    val thumbnailUrl: String?,
    val createdAt: Long = System.currentTimeMillis(),
    
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
    val movesFast: String? = null,
    val movesCharged: String? = null,
    val hundoCPL20: Int? = null,
    val hundoCPL25: Int? = null,
    val pvpRankingsJson: String? = null,
    
    // Team Rocket
    val gruntType: String? = null,
    val pokemonRewardsJson: String? = null,
    
    // Quest info
    val questTask: String? = null,
    val questReward: String? = null,
    val requiresAR: Boolean? = null,
    
    // Weather change
    val newCp: Int? = null,
    val newIv: String? = null,
    
    // Species replacement
    val oldSpecies: String? = null,
    val oldIv: String? = null,
    val oldCp: Int? = null,
    val newSpecies: String? = null,
    
    // Area
    val area: String? = null,
    
    // Timestamps
    val alertCreatedAt: String? = null
)

fun AlertEntity.toDomain(): PokemonAlert {
    return PokemonAlert(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        longitude = longitude,
        latitude = latitude,
        endTime = endTime,
        type = type?.let {
            try {
                Json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        },
        thumbnailUrl = thumbnailUrl,
        pokemon = pokemon,
        pokemonForm = pokemonForm,
        pokedexId = pokedexId,
        iv = iv,
        ivAttack = ivAttack,
        ivDefense = ivDefense,
        ivStamina = ivStamina,
        gender = gender,
        isShiny = isShiny,
        cp = cp,
        level = level,
        isWeatherBoosted = isWeatherBoosted,
        currentWeather = currentWeather,
        pokemonLocation = pokemonLocation,
        gym = gym,
        pokestop = pokestop,
        moves = if (movesFast != null || movesCharged != null) {
            PokemonMoves(fast = movesFast, charged = movesCharged)
        } else null,
        hundoCP = if (hundoCPL20 != null || hundoCPL25 != null) {
            HundoCP(level20 = hundoCPL20, level25 = hundoCPL25)
        } else null,
        pvpRankings = pvpRankingsJson?.let {
            try {
                Json.decodeFromString<List<PvpRanking>>(it)
            } catch (e: Exception) {
                null
            }
        },
        gruntType = gruntType,
        pokemonRewards = pokemonRewardsJson?.let {
            try {
                Json.decodeFromString<List<PokemonReward>>(it)
            } catch (e: Exception) {
                null
            }
        },
        questTask = questTask,
        questReward = questReward,
        requiresAR = requiresAR,
        newCp = newCp,
        newIv = newIv,
        oldSpecies = oldSpecies,
        oldIv = oldIv,
        oldCp = oldCp,
        newSpecies = newSpecies,
        area = area,
        createdAt = alertCreatedAt
    )
}

fun PokemonAlert.toEntity(): AlertEntity {
    return AlertEntity(
        uniqueId = uniqueId,
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        longitude = longitude ?: 0.0,
        latitude = latitude ?: 0.0,
        endTime = endTime,
        type = type?.let { 
            if (it.isNotEmpty()) Json.encodeToString(it) else null 
        },
        thumbnailUrl = thumbnailUrl,
        pokemon = pokemon,
        pokemonForm = pokemonForm,
        pokedexId = pokedexId,
        iv = iv,
        ivAttack = ivAttack,
        ivDefense = ivDefense,
        ivStamina = ivStamina,
        gender = gender,
        isShiny = isShiny,
        cp = cp,
        level = level,
        isWeatherBoosted = isWeatherBoosted,
        currentWeather = currentWeather,
        pokemonLocation = pokemonLocation,
        gym = gym,
        pokestop = pokestop,
        movesFast = moves?.fast,
        movesCharged = moves?.charged,
        hundoCPL20 = hundoCP?.level20,
        hundoCPL25 = hundoCP?.level25,
        pvpRankingsJson = pvpRankings?.let { 
            if (it.isNotEmpty()) Json.encodeToString(it) else null 
        },
        gruntType = gruntType,
        pokemonRewardsJson = pokemonRewards?.let {
            if (it.isNotEmpty()) Json.encodeToString(it) else null
        },
        questTask = questTask,
        questReward = questReward,
        requiresAR = requiresAR,
        newCp = newCp,
        newIv = newIv,
        oldSpecies = oldSpecies,
        oldIv = oldIv,
        oldCp = oldCp,
        newSpecies = newSpecies,
        area = area,
        alertCreatedAt = createdAt
    )
}
