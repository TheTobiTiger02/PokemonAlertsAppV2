package com.example.pokemonalertsv2.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.pokemonalertsv2.data.database.GoDexEntryDao
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** A GoDex caught mark as it travels: the attempt count and error stay with the device that tried. */
@Serializable
data class GoDexMark(val caught: Boolean, val revision: Long, val timestamp: Long)

/**
 * GoDex caught marks not yet written back, one item per entry. A mark made on one device shows
 * on the others at once; whichever device writes it back deletes it, and the deletion clears
 * it everywhere.
 */
class GoDexSyncAdapter(private val dao: GoDexEntryDao) : SyncAdapter {
    override val name = DeviceSync.GODEX
    override val debounceMillis: Long = 300L
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> =
        dao.getPendingUpdates().associate { it.entryKey to json.encodeToString(GoDexMark.serializer(), GoDexMark(it.caught, it.revision, it.timestamp)) }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        deletes.forEach { dao.deletePendingUpdate(it) }
        val held = LinkedHashSet<String>()
        upserts.forEach { (entryKey, value) ->
            val mark = runCatching { json.decodeFromString(GoDexMark.serializer(), value) }.getOrNull()
            if (mark == null) held += entryKey else dao.applySyncedPending(entryKey, mark.caught, mark.revision, mark.timestamp)
        }
        return held
    }

    override val changes: Flow<Unit> = dao.observePendingUpdates()
        .map { rows -> rows.map { Triple(it.entryKey, it.caught, it.revision) } }
        .distinctUntilChanged()
        .map { }
}

/** One home screen widget's configuration, the raw values of `widget_filter_prefs`. */
@Serializable
data class WidgetConfigRow(
    val filters: Set<String>? = null,
    val priority: String? = null,
    val distance: String? = null,
    val area: String? = null,
    val assignment: String? = null,
    val followSet: Boolean? = null
)

/**
 * Each device's widget configurations, so a widget set up on one phone can be copied onto a
 * widget on another. Widget ids exist on one device only: a device writes only its own rows
 * (`<deviceId>/<appWidgetId>`) and keeps the others' rows as they came, never applying them to
 * its own widgets.
 */
class WidgetsSyncAdapter(context: Context, private val selfId: String) : SyncAdapter {
    override val name = DeviceSync.WIDGETS
    private val prefs: SharedPreferences = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    private val catalog: SharedPreferences = context.getSharedPreferences(CATALOG_PREFS, Context.MODE_PRIVATE)
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> {
        val items = LinkedHashMap<String, String>()
        // Others' rows exactly as received, so they read back unchanged and are never re-claimed.
        catalog.all.forEach { (key, value) -> if (!key.startsWith("$selfId/") && value is String) items[key] = value }
        ownRows(prefs).forEach { (id, row) -> items["$selfId/$id"] = json.encodeToString(WidgetConfigRow.serializer(), row) }
        return items
    }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        // Written before the engine records the sync, so the next snapshot reads it back.
        catalog.edit(commit = true) {
            deletes.filterNot { it.startsWith("$selfId/") }.forEach { remove(it) }
            upserts.filterKeys { !it.startsWith("$selfId/") }.forEach { (key, value) -> putString(key, value) }
        }
        return emptySet()
    }

    override val changes: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.map { ownRows(prefs) }.distinctUntilChanged().map { }

    companion object {
        const val WIDGET_PREFS = "widget_filter_prefs"
        private const val CATALOG_PREFS = "widget_sync_catalog"
        private const val FILTER_PREFIX = "widget_filters_"
        private const val PRIORITY_PREFIX = "widget_priority_"
        private const val DISTANCE_PREFIX = "widget_distance_"
        private const val AREA_PREFIX = "widget_area_"
        private const val ASSIGNMENT_PREFIX = "widget_filter_assignment_"
        private const val FOLLOW_SET_PREFIX = "widget_filter_follow_set_"
        private val PREFIXES = listOf(ASSIGNMENT_PREFIX, FOLLOW_SET_PREFIX, FILTER_PREFIX, PRIORITY_PREFIX, DISTANCE_PREFIX, AREA_PREFIX)

        internal fun ownRows(prefs: SharedPreferences): Map<Int, WidgetConfigRow> {
            val all = prefs.all
            val ids = all.keys.mapNotNullTo(sortedSetOf()) { key ->
                PREFIXES.firstOrNull { key.startsWith(it) }?.let { key.removePrefix(it).toIntOrNull() }
            }
            return ids.associateWith { id ->
                WidgetConfigRow(
                    filters = (all["$FILTER_PREFIX$id"] as? Set<*>)?.filterIsInstance<String>()?.toSortedSet(),
                    priority = all["$PRIORITY_PREFIX$id"] as? String,
                    distance = all["$DISTANCE_PREFIX$id"] as? String,
                    area = all["$AREA_PREFIX$id"] as? String,
                    assignment = all["$ASSIGNMENT_PREFIX$id"] as? String,
                    followSet = all["$FOLLOW_SET_PREFIX$id"] as? Boolean
                )
            }
        }

        /** Another device's widget, as offered by the widget set-up screen. */
        data class Offer(val deviceName: String, val appWidgetId: Int, val row: WidgetConfigRow)

        /** Widgets of the group's other devices, newest device names from the devices collection. */
        suspend fun offers(context: Context): List<Offer> {
            val app = context.applicationContext
            val self = DeviceSync.deviceId(app)
            val devices = DeviceSync.state(app).devices()
            val catalog = app.getSharedPreferences(CATALOG_PREFS, Context.MODE_PRIVATE)
            return catalog.all.mapNotNull { (key, value) ->
                val device = key.substringBefore('/')
                val id = key.substringAfter('/').toIntOrNull() ?: return@mapNotNull null
                if (device == self || value !is String) return@mapNotNull null
                val row = runCatching { SyncMerge.json.decodeFromString(WidgetConfigRow.serializer(), value) }.getOrNull() ?: return@mapNotNull null
                Offer(devices[device]?.name ?: device.take(6), id, row)
            }.sortedWith(compareBy({ it.deviceName }, { it.appWidgetId }))
        }

        fun clearCatalog(context: Context) {
            context.getSharedPreferences(CATALOG_PREFS, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }

        /** Copies [row] onto this device's widget [appWidgetId]. */
        fun copyInto(context: Context, appWidgetId: Int, row: WidgetConfigRow) {
            // Committed: the set-up screen is recreated right after and reads it back.
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit(commit = true) {
                fun put(prefix: String, value: String?) {
                    if (value == null) remove("$prefix$appWidgetId") else putString("$prefix$appWidgetId", value)
                }
                if (row.filters == null) remove("$FILTER_PREFIX$appWidgetId") else putStringSet("$FILTER_PREFIX$appWidgetId", row.filters)
                put(PRIORITY_PREFIX, row.priority)
                put(DISTANCE_PREFIX, row.distance)
                put(AREA_PREFIX, row.area)
                put(ASSIGNMENT_PREFIX, row.assignment)
                if (row.followSet == null) remove("$FOLLOW_SET_PREFIX$appWidgetId") else putBoolean("$FOLLOW_SET_PREFIX$appWidgetId", row.followSet)
            }
        }
    }
}
