package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "godex_entries",
    indices = [Index("pokedexId")]
)
data class GoDexEntryEntity(
    @PrimaryKey val entryKey: String,
    val pokedexId: Int,
    val formSlug: String?,
    val gender: String,
    val displayName: String,
    val needed: Boolean
)
