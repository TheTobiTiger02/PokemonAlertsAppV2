package com.example.pokemonalertsv2.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Shared app-wide location source for background surfaces.
 *
 * Callers get a fresh fix when possible, otherwise a recent in-memory fix, then
 * FusedLocationProvider's last-known location. This keeps widgets and
 * notifications from making conflicting distance decisions during the same
 * alert refresh.
 */
object CachedLocationProvider {
    private const val STALE_LOCATION_MS = 60 * 1000L

    @Volatile
    private var cachedLocation: Location? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    suspend fun get(
        context: Context,
        timeoutMs: Long = 4000,
        highAccuracy: Boolean = false
    ): Location? {
        val now = System.currentTimeMillis()
        cachedLocation
            ?.takeIf { now - cachedAtMillis < STALE_LOCATION_MS }
            ?.let { return it }

        val appContext = context.applicationContext
        val freshLocation = LocationUtils.getCurrentLocationOrNull(
            context = appContext,
            timeoutMs = timeoutMs,
            highAccuracy = highAccuracy
        )
        if (freshLocation != null) {
            updateCache(freshLocation)
            return freshLocation
        }

        val lastKnown = getLastKnownLocation(appContext)
        if (lastKnown != null) {
            updateCache(lastKnown)
            return lastKnown
        }

        return null
    }

    suspend fun getCachedOrLastKnown(context: Context): Location? {
        val now = System.currentTimeMillis()
        cachedLocation
            ?.takeIf { now - cachedAtMillis < STALE_LOCATION_MS }
            ?.let { return it }

        val lastKnown = getLastKnownLocation(context.applicationContext)
        if (lastKnown != null) {
            updateCache(lastKnown)
            return lastKnown
        }

        return null
    }

    private fun updateCache(location: Location) {
        cachedLocation = location
        cachedAtMillis = System.currentTimeMillis()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                suspendCancellableCoroutine<Location?> { cont ->
                    fused.lastLocation
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                }
            }.getOrNull()
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
