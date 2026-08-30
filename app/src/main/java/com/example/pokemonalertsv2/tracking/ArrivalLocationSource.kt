package com.example.pokemonalertsv2.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

internal interface ArrivalLocationSource {
    fun start(
        onLocation: (Location) -> Unit,
        onAvailabilityChanged: (Boolean) -> Unit
    ): Boolean

    fun stop()
}

internal fun interface ArrivalLocationSourceFactory {
    fun create(context: Context): ArrivalLocationSource
}

internal object DefaultArrivalLocationSourceFactory : ArrivalLocationSourceFactory {
    override fun create(context: Context): ArrivalLocationSource =
        FusedArrivalLocationSource(context.applicationContext)
}

private class FusedArrivalLocationSource(context: Context) : ArrivalLocationSource {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(
        onLocation: (Location) -> Unit,
        onAvailabilityChanged: (Boolean) -> Unit
    ): Boolean {
        if (callback != null) return true
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(onLocation)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                onAvailabilityChanged(availability.isLocationAvailable)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(5f)
            // Deliver the best available fix instead of withholding updates while the
            // fused provider waits for an accuracy threshold that can be unreachable indoors.
            .setWaitForAccurateLocation(false)
            .build()
        return runCatching {
            fusedClient.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
            callback = newCallback
        }.isSuccess
    }

    override fun stop() {
        callback?.let(fusedClient::removeLocationUpdates)
        callback = null
    }
}
