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
    /**
     * The alert types this checklist badges, as comma-separated
     * [com.example.pokemonalertsv2.data.filters.FilterAlertType] names; null means its kind's
     * defaults ([com.example.pokemonalertsv2.data.checklists.ChecklistMatcher.defaultAlertTypes]).
     */
    val alertTypes: String? = null,
    val sortOrder: Int = 0,
    val lastSyncMillis: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        const val SOURCE_GODEX = "GODEX"
        const val SOURCE_LOCAL = "LOCAL"
        /**
         * A Dex Tracker collection's settings in the app (kind, badges); its Pokémon stay in Dex
         * Tracker. Id `dextracker-{collection id}`.
         */
        const val SOURCE_DEXTRACKER = "DEXTRACKER"
    }
}

/**
 * A collection of the signed-in goDex account (`/collection/{id}`), read and marked with the
 * session, as opposed to one added by its share link (`/public-collection/...`), which is
 * read-only.
 */
val ChecklistEntity.isGoDexAccount: Boolean
    get() = source == ChecklistEntity.SOURCE_GODEX &&
        com.example.pokemonalertsv2.data.godex.GoDexAccountCollections.collectionIdOf(url) != null

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

/**
 * A mark on a goDex collection of the signed-in account, waiting to be written back to goDex
 * ([com.example.pokemonalertsv2.work.GoDexWriteWorker]); as `godex_pending_updates` for the
 * Hundo collection.
 */
@Entity(tableName = "checklist_pending", primaryKeys = ["checklistId", "entryKey"])
data class ChecklistPendingEntity(
    val checklistId: String,
    val entryKey: String,
    val caught: Boolean,
    val revision: Long,
    val timestamp: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null
) {
    fun asGoDexPending() = GoDexPendingUpdateEntity(entryKey, caught, revision, timestamp, attemptCount, lastError)
}
