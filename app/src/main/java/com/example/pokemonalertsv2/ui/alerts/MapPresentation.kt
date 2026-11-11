@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils

internal fun mapCountdownRefreshKey(
    showTimeLabels: Boolean,
    nowMillis: Long,
    refreshIntervalMillis: Long = if (showTimeLabels) 1_000L else 30_000L
): Long = nowMillis / refreshIntervalMillis.coerceAtLeast(1L)

/**
 * [coarsenBeyondWindow] is for labels drawn into marker bitmaps, where a ticking seconds figure
 * costs a rasterization per marker per second. Single chips pass false and keep their seconds.
 */
internal fun mapCountdownLabel(
    endTime: String?,
    nowMillis: Long,
    minutePrecision: Boolean = false,
    coarsenBeyondWindow: Boolean = false
): String {
    val remaining = (TimeUtils.parseEndTimeToMillis(endTime) ?: Long.MAX_VALUE) - nowMillis
    if (remaining <= 0L) return "Expired"
    // The last minute always counts seconds: "1m" there says nothing about whether it is worth running.
    if (remaining <= 59_000L) return TimeUtils.formatDurationShort(remaining)
    // A ticking seconds figure earns its cost near the despawn and nowhere else. Beyond the
    // window, minute precision holds the label - and therefore the marker's icon cache key -
    // steady for a minute at a time, so a screen of pins no longer re-rasterizes every second
    // to advance digits nobody is watching.
    if (minutePrecision || (coarsenBeyondWindow && remaining > MAP_SECOND_PRECISION_WINDOW_MS)) {
        // Rounds up like the seconds countdown does; the label only changes once a minute,
        // which is what lets a crowded map skip per-second marker bitmap rebuilds.
        val minutes = ((remaining + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return if (minutes >= 60) {
            String.format(java.util.Locale.ROOT, "%dh %02dm", minutes / 60, minutes % 60)
        } else {
            "${minutes}m"
        }
    }
    return TimeUtils.formatDurationShort(remaining)
}

/**
 * Above this marker count, marker countdowns drop to minute precision to stay smooth.
 * Every second-precision marker re-renders its bitmap once a second, so the ceiling is
 * deliberately conservative: a dense Darmstadt rocket field crosses it easily.
 */
internal const val ADAPTIVE_COUNTDOWN_PRECISION_THRESHOLD = 48

/**
 * How close to despawn a marker must be to show a seconds countdown.
 *
 * The count threshold above is all-or-nothing: one marker too many and every countdown on the
 * map coarsens. This bounds the cost by remaining time instead, so the pins that are actually
 * about to expire keep their seconds however busy the map is.
 */
internal const val MAP_SECOND_PRECISION_WINDOW_MS = 15 * 60 * 1000L

enum class MapPresentationMode {
    FULL,
    COMPACT_PICTURE_IN_PICTURE,

    /**
     * A hunt's own window. Shares the compact chrome with
     * [COMPACT_PICTURE_IN_PICTURE] but browses the hunt's targets instead of the
     * map's filter, and trades the follow toggle for "Got it" — the existing map
     * PiP is deliberately left exactly as it was.
     */
    COMPACT_HUNT_PICTURE_IN_PICTURE
}

internal fun mapAlertsForPresentation(
    filteredAlerts: List<PokemonAlert>,
    trackedAlert: PokemonAlert?,
    compactPictureInPicture: Boolean,
    nowMillis: Long
): List<PokemonAlert> {
    if (!compactPictureInPicture || trackedAlert == null) return filteredAlerts
    val trackedIsActiveAndMappable = trackedAlert.mapCoordinatesOrNull() != null &&
        !trackedAlert.isInvalidated &&
        (TimeUtils.parseEndTimeToMillis(trackedAlert.endTime) ?: Long.MAX_VALUE) > nowMillis
    if (!trackedIsActiveAndMappable || filteredAlerts.any { it.uniqueId == trackedAlert.uniqueId }) {
        return filteredAlerts
    }
    return filteredAlerts + trackedAlert
}

/**
 * The alerts the floating window keeps out of clustering, so each is drawn as its own marker:
 * the destination being walked to, and the one the browse cursor is on. Emphasis only lands on
 * individual markers, so without this, stepping onto an alert that shares a spot with its
 * neighbours would leave the window showing a numbered bubble and nothing highlighted.
 *
 * Both are matched against [renderedAlerts]: an id that has expired or been filtered away
 * reserves nothing.
 *
 * A running hunt gets the same treatment on the full map, but for the tracked
 * alert only: there, the browse cursor is the ordinary tap-to-open selection, and
 * emphasizing it would fire on every tap.
 */
internal fun mapPipProtectedAlertIds(
    compactPictureInPicture: Boolean,
    trackedAlertId: String?,
    browsedAlertId: String?,
    renderedAlerts: List<PokemonAlert>,
    huntActive: Boolean = false
): Set<String> {
    if (!compactPictureInPicture && !huntActive) return emptySet()
    val available = renderedAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
    return setOfNotNull(
        trackedAlertId?.takeIf { it in available },
        browsedAlertId?.takeIf { compactPictureInPicture && it in available }
    )
}

internal fun mapPipEmphasizedAlertIds(
    compactPictureInPicture: Boolean,
    trackedAlertId: String?,
    browsedAlertId: String?,
    huntActive: Boolean = false
): Set<String> = when {
    compactPictureInPicture -> setOfNotNull(trackedAlertId, browsedAlertId)
    huntActive -> setOfNotNull(trackedAlertId)
    else -> emptySet()
}

internal fun initialMapPipBrowsedAlertId(
    selectedAlertId: String?,
    trackedAlertId: String?,
    renderedAlerts: List<PokemonAlert>
): String? {
    val availableIds = renderedAlerts.mapTo(mutableSetOf(), PokemonAlert::uniqueId)
    return selectedAlertId?.takeIf { it in availableIds }
        ?: trackedAlertId?.takeIf { it in availableIds }
}

internal fun normalizeMapPictureInPictureZoom(zoom: Double?): Double =
    zoom?.takeIf(Double::isFinite)?.coerceIn(3.0, 20.0) ?: USER_LOCATION_ZOOM.toDouble()

internal fun mapPictureInPictureZoom(
    mapSource: MapDisplaySource,
    googleMapZoom: Double,
    retainedZoom: Double
): Double = normalizeMapPictureInPictureZoom(
    if (mapSource == MapDisplaySource.GOOGLE) googleMapZoom else retainedZoom
)
