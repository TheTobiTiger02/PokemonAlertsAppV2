package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A checklist besides the GoDex Hundo collection (which keeps its own tables and settings):
 * another goDex collection added by link, or a checklist made in the app.
 */
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    /** [SOURCE_GODEX] or [SOURCE_LOCAL]. */
    val source: String,
    val title: String,
    /** A [com.example.pokemonalertsv2.data.checklists.ChecklistKind] name. */
    val kind: String,
    /** The goDex collection link; null for a local checklist. */
    val url: String? = null,
    /** Alerts get a badge when the species is still needed here. */
    val useForAlerts: Boolean = false,
    /** Also badge an alert whose evolution is still needed here. */
    val includeEvolutions: Boolean = false,
    val sortOrder: Int = 0,
    val lastSyncMillis: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        const val SOURCE_GODEX = "GODEX"
        const val SOURCE_LOCAL = "LOCAL"
    }
}

/** One Pokémon of a [ChecklistEntity], in goDex's entry key format (`0026_alola-female`). */
@Entity(
    tableName = "checklist_entries",
    primaryKeys = ["checklistId", "entryKey"],
    indices = [Index("pokedexId")]
)
data class ChecklistEntryEntity(
    val checklistId: String,
    val entryKey: String,
    val pokedexId: Int,
    val formSlug: String?,
    val gender: String,
    val displayName: String,
    val needed: Boolean,
    val spriteUrl: String? = null,
    /** When the caught state last changed here; device sync orders changes by it. */
    val updatedAt: Long = 0
) {
    /** The shape the goDex matcher and editor work on. */
    fun asGoDexEntry() = GoDexEntryEntity(entryKey, pokedexId, formSlug, gender, displayName, needed, spriteUrl)
}
