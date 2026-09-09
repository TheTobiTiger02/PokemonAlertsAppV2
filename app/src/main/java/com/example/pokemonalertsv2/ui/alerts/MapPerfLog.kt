package com.example.pokemonalertsv2.ui.alerts

import android.util.Log
import com.example.pokemonalertsv2.BuildConfig

/**
 * Timing for the marker pipeline, on debug builds only.
 *
 * The map draws into its own SurfaceView, so `dumpsys gfxinfo` sees the Compose chrome and not
 * the map at all, and SurfaceFlinger only reports a steady 60 fps while a pan is in flight. What
 * neither can show is the work that happens when the camera settles - the cull, the clustering,
 * the icon rendering and the symbol update - which is where the map actually stalls. These logs
 * measure that directly, so before/after numbers come from the phone rather than a guess.
 *
 * Read them with `adb logcat -s MapPerf`.
 */
internal object MapPerfLog {
    const val TAG = "MapPerf"

    /**
     * JVM unit tests run this code with an unmocked `android.util.Log`, which throws rather than
     * returning a default. Probing once is enough to keep the marker pipeline testable off-device
     * without the tests having to know that it logs at all.
     */
    private val androidRuntimeAvailable: Boolean by lazy {
        runCatching { Log.isLoggable(TAG, Log.DEBUG) }.isSuccess
    }

    val enabled: Boolean get() = BuildConfig.DEBUG && androidRuntimeAvailable

    // System.nanoTime rather than SystemClock: same monotonic guarantee, and it exists off-device.
    inline fun <T> timed(stage: String, detail: () -> String = { "" }, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        val result = block()
        val millis = (System.nanoTime() - start) / 1_000_000.0
        event(stage, "%.1fms %s".format(millis, detail()))
        return result
    }

    fun event(stage: String, detail: String) {
        if (enabled) Log.d(TAG, "$stage $detail")
    }
}
