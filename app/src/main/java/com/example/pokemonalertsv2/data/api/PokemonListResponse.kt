package com.example.pokemonalertsv2.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PokemonListResponse(
    @SerialName("results") val results: List<PokemonResult>
)

@Serializable
data class PokemonResult(
    @SerialName("name") val name: String,
    @SerialName("url") val url: String
) {
    val id: Int
        get() = url.trimEnd('/').substringAfterLast('/').toIntOrNull() ?: 0
}
