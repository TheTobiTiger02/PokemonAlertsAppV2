package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon_species")
data class PokemonSpeciesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val imageUrl: String
)
