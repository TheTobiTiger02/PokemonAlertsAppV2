package com.example.pokemonalertsv2.hunt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Device Y points towards the top edge, independent of display rotation. */
internal class HuntUpsideDownState {
    var blackedOut = false
        private set
    private var enteredAt: Long? = null

    fun update(verticalGravity: Float, nowMillis: Long): Boolean {
        if (!verticalGravity.isFinite() || verticalGravity > -3f) reset()
        else if (!blackedOut) {
            if (verticalGravity <= -7f) {
                val since = enteredAt ?: nowMillis.also { enteredAt = it }
                if (nowMillis - since >= 500) blackedOut = true
            } else enteredAt = null
        }
        return blackedOut
    }

    fun reset() { blackedOut = false; enteredAt = null }
}

/** Owned by the existing Hunt service; it never changes location or route cadence. */
internal class HuntBatterySaver(private val context: Context, private val onEscape: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    private val orientation = HuntUpsideDownState()
    private var running = false
    private var blackout: View? = null
    private var filteredY: Float? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            orientation.reset()
            hide()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == running) return
        if (!enabled) { close(); return }
        val problem = unavailableReason(context)
        if (problem != null) { fail(problem); return }
        val sensor = sensor(context) ?: return
        ContextCompat.registerReceiver(context, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        running = true
        if (!sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            fail("Battery Saver could not start the motion sensor.")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        if (!Settings.canDrawOverlays(context)) { fail("Allow display over other apps to use Battery Saver."); return }
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        val locked = context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        if (!interactive || locked) { orientation.reset(); hide(); return }
        val y = event.values[1]
        val gravityY = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            ((filteredY ?: y) * 0.8f + y * 0.2f).also { filteredY = it }
        } else y
        if (orientation.update(gravityY, event.timestamp / 1_000_000)) show() else hide()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun show() {
        if (blackout != null) return
        val view = object : View(context) {
            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
            }
            override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_DISMISS || action == AccessibilityNodeInfo.ACTION_CLICK) {
                    escape()
                    return true
                }
                return super.performAccessibilityAction(action, arguments)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Battery Saver. Turn the phone upright to restore the screen. Long press or activate Dismiss to turn Battery Saver off."
            setOnLongClickListener { escape(); true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = 0.01f
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            setTitle("Hunt Battery Saver")
        }
        runCatching { windows.addView(view, params); blackout = view }
            .onFailure { fail("Battery Saver could not cover the screen. Check display-over-apps permission.") }
    }

    fun restoreOverlayOrder() {
        if (running && orientation.blackedOut) { hide(); show() }
    }

    private fun escape() { close(); onEscape() }
    private fun fail(message: String) {
        escape()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    private fun hide() {
        blackout?.let { runCatching { windows.removeViewImmediate(it) } }
        blackout = null
    }
    fun close() {
        if (running) {
            sensors.unregisterListener(this)
            context.unregisterReceiver(screenReceiver)
        }
        running = false
        filteredY = null
        orientation.reset()
        hide()
    }

    companion object {
        private fun sensor(context: Context): Sensor? {
            val manager = context.getSystemService(SensorManager::class.java)
            return manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        fun unavailableReason(context: Context): String? = when {
            sensor(context) == null -> "Battery Saver needs a gravity sensor or accelerometer."
            !Settings.canDrawOverlays(context) -> "Allow display over other apps to use Battery Saver, including over Pokémon GO."
            else -> null
        }
    }
}
