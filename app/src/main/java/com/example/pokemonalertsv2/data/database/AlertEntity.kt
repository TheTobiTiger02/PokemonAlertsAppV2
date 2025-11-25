package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pokemonalertsv2.data.PokemonAlert

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val uniqueId: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val longitude: Double,
    val latitude: Double,
    val endTime: String,
    val type: String?,
    val thumbnailUrl: String?,
    val createdAt: Long = System.currentTimeMillis()
)

fun AlertEntity.toDomain(): PokemonAlert {
    return PokemonAlert(
        name = name,
        description = description,
        imageUrl = imageUrl,
        longitude = longitude,
        latitude = latitude,
        endTime = endTime,
        type = type,
        thumbnailUrl = thumbnailUrl
    )
}

fun PokemonAlert.toEntity(): AlertEntity {
    return AlertEntity(
        uniqueId = uniqueId,
        name = name,
        description = description,
        imageUrl = imageUrl,
        longitude = longitude,
        latitude = latitude,
        endTime = endTime,
        type = type,
        thumbnailUrl = thumbnailUrl
    )
}
