package com.example.pokemonalertsv2.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Reads what a watch recorded during a hunt or catch route from Health Connect: no app on the
 * watch, just the phone's health store. A Galaxy Watch syncs to Samsung Health, which shares
 * heart rate, steps and calories with Health Connect once that is switched on in Samsung Health.
 *
 * Only read permissions are asked for, and only while the app is open (no background access).
 */
class HealthDataReader(private val context: Context) {

    enum class Availability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_UPDATE
        else -> Availability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient? =
        if (availability() == Availability.AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun grantedPermissions(): Set<String> =
        client()?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrNull() }.orEmpty()

    /** Heart rate is what makes the section worth showing; steps and calories are extras. */
    suspend fun isConnected(): Boolean = grantedPermissions().any { it in PERMISSIONS }

    /**
     * What was recorded between [startMillis] and [endMillis], or null when nothing was (the
     * watch may not have synced yet), when Health Connect is missing or nothing was granted.
     */
    suspend fun read(startMillis: Long, endMillis: Long, nowMillis: Long = System.currentTimeMillis()): WatchStats? {
        val client = client() ?: return null
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrNull().orEmpty()
        if (granted.none { it in PERMISSIONS }) return null
        if (endMillis <= startMillis) return null
        val window = TimeRangeFilter.between(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis))
        return runCatching {
            val heartRates = mutableListOf<Pair<Long, Int>>()
            var source: String? = null
            if (HEART_RATE in granted) {
                var pageToken: String? = null
                do {
                    val response = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, window, pageToken = pageToken))
                    response.records.forEach { record ->
                        if (source == null) source = sourceName(record.metadata.device?.model, record.metadata.dataOrigin.packageName)
                        record.samples.forEach { heartRates += it.time.toEpochMilli() to it.beatsPerMinute.toInt() }
                    }
                    pageToken = response.pageToken
                } while (!pageToken.isNullOrEmpty() && heartRates.size < MAX_HEART_RATE_SAMPLES)
            }
            val metrics = buildSet {
                if (STEPS in granted) add(StepsRecord.COUNT_TOTAL)
                if (ACTIVE_CALORIES in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                if (TOTAL_CALORIES in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }
            val totals = if (metrics.isEmpty()) null else client.aggregate(AggregateRequest(metrics, window))
            val steps = totals?.get(StepsRecord.COUNT_TOTAL) ?: 0L
            // Active calories are what the walk burned; total includes the resting rate.
            val calories = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
                ?: totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories
                ?: 0.0
            WatchStats.fromSamples(startMillis, endMillis, heartRates, steps, calories, source, nowMillis)
        }.onFailure { Log.w(TAG, "Could not read Health Connect", it) }.getOrNull()
    }

    /** "Galaxy Watch6" style when the record names its device, else the app that wrote it. */
    private fun sourceName(model: String?, packageName: String): String? {
        model?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }

    /** Opens Health Connect's own settings, where access can be changed or revoked. */
    fun manageIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }

    /** The Play Store page for Health Connect, for phones before Android 14 that lack it. */
    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending")

    companion object {
        private const val TAG = "HealthDataReader"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val MAX_HEART_RATE_SAMPLES = 20_000

        val HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
        val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val TOTAL_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val PERMISSIONS: Set<String> = setOf(HEART_RATE, STEPS, ACTIVE_CALORIES, TOTAL_CALORIES)

        fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        private val SHOW_KEY = booleanPreferencesKey("health_connect_show_watch_stats")

        /** Whether summaries show (and read) watch data; on unless the trainer turned it off. */
        fun showWatchStats(context: Context): Flow<Boolean> =
            context.alertPreferencesDataStore.data.map { it[SHOW_KEY] ?: true }

        suspend fun setShowWatchStats(context: Context, show: Boolean) {
            context.alertPreferencesDataStore.edit { it[SHOW_KEY] = show }
        }
    }
}
