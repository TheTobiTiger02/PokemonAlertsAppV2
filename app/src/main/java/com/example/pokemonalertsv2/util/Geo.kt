package com.example.pokemonalertsv2.util

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Great-circle distance in metres between two points, on a sphere of the mean Earth radius.
 *
 * The app's one distance formula. It is pure, so the rules built on it run in JVM unit tests;
 * `Location.distanceBetween` needs a device and measures on the WGS84 ellipsoid, which differs
 * from this by under half a percent, far below anything a walking distance shows.
 */
fun haversineMeters(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
    val deltaLat = Math.toRadians(otherLatitude - latitude)
    val deltaLon = Math.toRadians(otherLongitude - longitude)
    val a = sin(deltaLat / 2).pow(2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(otherLatitude)) * sin(deltaLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}

/**
 * "850 m" up close, "2.4 km" once kilometres read better, in the user's number format.
 * The one distance format for cards, notifications, the journey pill and summaries.
 */
fun formatMeters(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
