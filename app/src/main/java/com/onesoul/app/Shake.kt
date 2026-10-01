package com.onesoul.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * True while the phone is being shaken top↔bottom (along its long axis): the acceleration keeps
 * swinging hard in alternating directions. Goes false within ~0.3s of the shaking stopping.
 * Only listens while [enabled].
 */
@Composable
fun rememberShaking(enabled: Boolean): State<Boolean> {
    val context = LocalContext.current
    val isOn by rememberUpdatedState(enabled)
    val shaking = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var gravityY = 0f
        var lastSign = 0
        var swings = 0
        var lastSwing = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = e.timestamp / 1_000_000
                // Without a linear-acceleration sensor, strip gravity with a low-pass filter.
                val y = if (linear != null) e.values[1] else {
                    gravityY = 0.9f * gravityY + 0.1f * e.values[1]; e.values[1] - gravityY
                }
                if (!isOn) { swings = 0; shaking.value = false; return }
                if (now - lastSwing > 300) swings = 0
                if (abs(y) > 11f) {
                    val sign = if (y > 0) 1 else -1
                    if (sign != lastSign) { swings++; lastSign = sign; lastSwing = now }
                }
                // Shaking = at least two swings and the latest one only just now.
                shaking.value = swings >= 2 && now - lastSwing <= 300
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    return shaking
}
