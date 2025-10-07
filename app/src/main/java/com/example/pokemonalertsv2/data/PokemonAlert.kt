package com.example.pokemonalertsv2.data

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PokemonAlert(
    val name: String,
    val description: String = "",
    @SerialName("imageUrl")
    val imageUrl: String? = null,
    val longitude: Double,
    val latitude: Double,
    @SerialName("endTime")
    val endTime: String = "",
    val type: String? = null,
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null
) {
    val uniqueId: String get() = "${name.trim()}|${endTime.trim()}"

    val googleMapsUri: Uri
        get() = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
}
