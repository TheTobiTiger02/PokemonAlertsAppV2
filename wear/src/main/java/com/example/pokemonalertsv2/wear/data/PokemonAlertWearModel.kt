package com.example.pokemonalertsv2.wear.data

import kotlinx.serialization.Serializable

@Serializable
data class PokemonAlertWearModel(
    val id: Int? = null,
    val name: String,
    val description: String = "",
    val endTime: String = "",
    val type: List<String>? = null,
    val pokemon: String? = null,
    val iv: String? = null,
    val cp: Int? = null,
    val level: Double? = null,
    val isShiny: Boolean? = null,
    val isPerfect: Boolean = false,
    val isNundo: Boolean = false,
    val area: String? = null
) {
    val uniqueId: String get() = "${name.trim()}|${endTime.trim()}"
    
    val cleanPokemonName: String
        get() = pokemon ?: name.replace(Regex("^[^a-zA-Z]+"), "").trim()
}
