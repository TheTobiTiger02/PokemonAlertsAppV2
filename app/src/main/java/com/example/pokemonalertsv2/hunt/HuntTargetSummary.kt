package com.example.pokemonalertsv2.hunt

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.buildAlertGlanceMetadata
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils

/**
 * What to call the alert you are currently walking to, and what to say about it.
 *
 * One place, because the same answer has to appear in the map banner, the hunt
 * panel, the notification title and the undo button — and a trainer comparing
 * two of those must not have to work out whether they mean the same alert.
 */

/**
 * The target's short name.
 *
 * Rocket and quest names carry the PokeStop — "Ground Grunt @ Haus der
 * Gartenfreunde" — which is most of a line for none of the meaning.
 */
internal fun huntTargetTitle(alert: PokemonAlert): String =
    alert.pokemon?.takeIf { it.isNotBlank() }
        ?: alert.name.substringBefore(" @ ").trim().takeIf { it.isNotBlank() }
        ?: "that one"

/**
 * CP, weather, how far, how long to walk, how long it lasts.
 *
 * Composed entirely from the formatters the cards and the notification already
 * use, so the banner cannot drift into its own dialect. [distanceMeters] is a
 * straight line; the walking figures are the standard estimate off it, marked
 * `~` by [WalkingRouteUtils.buildRouteDisplayInfo] exactly as they are elsewhere.
 */
internal fun huntTargetDetail(
    alert: PokemonAlert,
    distanceMeters: Float?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val route = WalkingRouteUtils.buildRouteDisplayInfo(
        straightLineDistanceMeters = distanceMeters,
        routeInfo = null,
        fallbackToEstimate = true
    )
    val glance = buildAlertGlanceMetadata(
        alert = alert,
        distanceText = route.distanceText,
        walkingText = route.walkingText,
        // The banner already sits inside a hunt named after the category.
        includeCategory = false
    )
    val remaining = TimeUtils.parseEndTimeToMillis(alert.endTime)
        ?.minus(nowMillis)
        ?.takeIf { it > 0L }
        ?.let { "${TimeUtils.formatDurationShort(it)} left" }

    return listOfNotNull(glance.takeIf { it.isNotBlank() }, remaining).joinToString(" • ")
}

/** Straight-line metres to the target, or null without a fix or a position to walk to. */
internal fun huntTargetDistanceMeters(from: Location?, alert: PokemonAlert): Float? {
    val origin = from ?: return null
    val latitude = alert.latitude ?: return null
    val longitude = alert.longitude ?: return null
    return WalkingRouteUtils.straightLineDistanceMeters(
        originLatitude = origin.latitude,
        originLongitude = origin.longitude,
        destinationLatitude = latitude,
        destinationLongitude = longitude
    )
}
