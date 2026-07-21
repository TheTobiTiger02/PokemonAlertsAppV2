package com.example.pokemonalertsv2.ui.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.Priority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class MapUserPose(
    val location: Location,
    val headingDegrees: Float?,
    val headingFromSensor: Boolean
)

internal enum class MapTrackingStatus {
    INACTIVE,
    SEARCHING,
    ACTIVE,
    DEGRADED
}

internal data class MapTrackingInteractionState(
    val trackingRequested: Boolean = false,
    val cameraFollowEnabled: Boolean = false
) {
    fun onGpsTapped(): MapTrackingInteractionState = copy(
        trackingRequested = true,
        cameraFollowEnabled = true
    )

    fun onUserCameraGesture(): MapTrackingInteractionState =
        if (trackingRequested) copy(cameraFollowEnabled = false) else this

    fun onShowAllAlerts(): MapTrackingInteractionState = copy(cameraFollowEnabled = false)
}

internal object MapHeadingMath {
    fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    fun smooth(previous: Float?, next: Float, weight: Float = 0.18f): Float {
        if (previous == null) return normalize(next)
        val clampedWeight = weight.coerceIn(0f, 1f)
        val previousRadians = Math.toRadians(previous.toDouble())
        val nextRadians = Math.toRadians(next.toDouble())
        val x = (1f - clampedWeight) * cos(previousRadians) + clampedWeight * cos(nextRadians)
        val y = (1f - clampedWeight) * sin(previousRadians) + clampedWeight * sin(nextRadians)
        return normalize(Math.toDegrees(atan2(y, x)).toFloat())
    }

    fun movementBearing(
        hasBearing: Boolean,
        hasSpeed: Boolean,
        speedMetersPerSecond: Float,
        bearingDegrees: Float,
        minimumSpeedMetersPerSecond: Float = 0.7f
    ): Float? = bearingDegrees
        .takeIf { hasBearing && hasSpeed && speedMetersPerSecond >= minimumSpeedMetersPerSecond }
        ?.let(::normalize)

    fun movementBearing(location: Location, minimumSpeedMetersPerSecond: Float = 0.7f): Float? =
        movementBearing(
            hasBearing = location.hasBearing(),
            hasSpeed = location.hasSpeed(),
            speedMetersPerSecond = location.speed,
            bearingDegrees = location.bearing,
            minimumSpeedMetersPerSecond = minimumSpeedMetersPerSecond
        )
}

internal fun shouldAcceptLiveLocation(
    currentElapsedRealtimeNanos: Long?,
    candidateElapsedRealtimeNanos: Long,
    candidateAgeMillis: Long,
    latitude: Double,
    longitude: Double
): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        candidateAgeMillis <= 30_000L &&
        (currentElapsedRealtimeNanos == null || candidateElapsedRealtimeNanos >= currentElapsedRealtimeNanos)

internal fun sensorAxesForDisplayRotation(rotation: Int): Pair<Int, Int> = when (rotation) {
    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
}

/** Foreground-only location and device-heading source used by the map screen. */
internal interface MapPoseTracker {
    fun start()
    fun stop()
}

internal typealias MapPoseTrackerFactory = (
    context: Context,
    onPose: (MapUserPose) -> Unit,
    onStatus: (MapTrackingStatus) -> Unit
) -> MapPoseTracker

internal val DefaultMapPoseTrackerFactory: MapPoseTrackerFactory = { context, onPose, onStatus ->
    MapLocationTracker(context, onPose, onStatus)
}

internal class MapLocationTracker(
    context: Context,
    private val onPose: (MapUserPose) -> Unit,
    private val onStatus: (MapTrackingStatus) -> Unit
) : MapPoseTracker, SensorEventListener {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationMatrix = FloatArray(9)
    private val adjustedRotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var started = false
    private var latestLocation: Location? = null
    private var latestElapsedRealtimeNanos: Long? = null
    private var smoothedHeading: Float? = null
    private var sensorHeading: Float? = null
    private var sensorReliable = true

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::acceptLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable && latestLocation != null) {
                onStatus(MapTrackingStatus.DEGRADED)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (started || !hasLocationPermission()) return
        started = true
        onStatus(if (latestLocation == null) MapTrackingStatus.SEARCHING else MapTrackingStatus.ACTIVE)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(1_000L)
            .setWaitForAccurateLocation(true)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .build()
        runCatching {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }.onFailure {
            onStatus(MapTrackingStatus.DEGRADED)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        fusedClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        onStatus(MapTrackingStatus.INACTIVE)
    }

    @Suppress("DEPRECATION")
    override fun onSensorChanged(event: SensorEvent) {
        if (!started || !sensorReliable || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val (xAxis, yAxis) = sensorAxesForDisplayRotation(windowManager.defaultDisplay.rotation)
        SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, adjustedRotationMatrix)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)
        val magneticHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val declination = latestLocation?.let { location ->
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            ).declination
        } ?: 0f
        smoothedHeading = MapHeadingMath.smooth(smoothedHeading, magneticHeading + declination)
        sensorHeading = smoothedHeading
        emitPose()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        sensorReliable = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        if (!sensorReliable) {
            sensorHeading = null
            smoothedHeading = null
            emitPose()
        }
    }

    private fun acceptLocation(location: Location) {
        val ageMillis = if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        if (!shouldAcceptLiveLocation(
                currentElapsedRealtimeNanos = latestElapsedRealtimeNanos,
                candidateElapsedRealtimeNanos = location.elapsedRealtimeNanos,
                candidateAgeMillis = ageMillis,
                latitude = location.latitude,
                longitude = location.longitude
            )
        ) return

        latestLocation = Location(location)
        latestElapsedRealtimeNanos = location.elapsedRealtimeNanos
        onStatus(MapTrackingStatus.ACTIVE)
        emitPose()
    }

    private fun emitPose() {
        val location = latestLocation ?: return
        val heading = sensorHeading ?: MapHeadingMath.movementBearing(location)
        onPose(
            MapUserPose(
                location = Location(location),
                headingDegrees = heading,
                headingFromSensor = sensorHeading != null
            )
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
