package com.example.pokemonalertsv2.util

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationUtils {
    /**
     * Attempts to actively fetch a current location fix using FusedLocationProviderClient.
     * Returns null on timeout, lack of permission, or failure. Does NOT fall back to last-known
     * to respect the caller's requirement for a fresh reading.
     */
    suspend fun getCurrentLocationOrNull(
        context: Context,
        timeoutMs: Long = 5000,
        highAccuracy: Boolean = false
    ): Location? {
        // Quick permission presence check; actual calls also protected with try/catch
        if (!hasLocationPermission(context)) return null
        val fused = runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull() ?: return null
        val cts = CancellationTokenSource()
        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val task = fused.getCurrentLocation(priority, cts.token)
                task.addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                task.addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val pm = context.packageManager
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == android.content.pm.PackageManager.PERMISSION_GRANTED || coarse == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // If app is backgrounded on Android 10+, background permission may be needed for fresh fixes.
            return true
        }
        return false
    }
}
