package io.github.angelomarzocchi.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Where the room's light is, as the device turns: [DefaultKeyLight] with the phone still, carried round
 * the rim as it tilts — the wrist turning, the light sliding along every edge. Read it while drawing,
 * in [GlassSurface]'s `lighting`: `lighting = { keyLight = roomLight() }`.
 *
 * It answers the phone *moving*, not the angle it is held at: held still at any angle, the light drifts
 * back to its rest within about a second. The sensor is only listened to while [enabled] and the screen
 * is resumed; without a rotation sensor, or in a preview, the light stays where it rests.
 */
@Composable
public fun rememberTiltKeyLight(enabled: Boolean = true): () -> Offset {
    val tilt = remember { TiltState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val active = enabled && !LocalInspectionMode.current

    DisposableEffect(active, lifecycleOwner) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!active || sensors == null || sensor == null) {
            tilt.x = 0f
            tilt.y = 0f
            return@DisposableEffect onDispose { }
        }
        val filter = TiltFilter()
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                filter.update(
                    pitchDegrees = Math.toDegrees(angles[1].toDouble()).toFloat(),
                    rollDegrees = Math.toDegrees(angles[2].toDouble()).toFloat(),
                )
                // Only a change the eye could see is written: every write redraws the glass.
                if (kotlin.math.abs(filter.rotationX - tilt.x) > DeadBand) tilt.x = filter.rotationX
                if (kotlin.math.abs(filter.rotationY - tilt.y) > DeadBand) tilt.y = filter.rotationY
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun start() = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        fun stop() {
            sensors.unregisterListener(listener)
            filter.reset()
            tilt.x = 0f
            tilt.y = 0f
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    return remember(tilt) { { keyLightFor(tilt.x, tilt.y) } }
}

/** The key light for a tilt of [rotationX] and [rotationY] degrees: up to 0.8 and 0.7 off its rest. */
internal fun keyLightFor(rotationX: Float, rotationY: Float): Offset = Offset(
    DefaultKeyLight.x + rotationY / TiltFilter.LimitDegrees * 0.8f,
    DefaultKeyLight.y - rotationX / TiltFilter.LimitDegrees * 0.7f,
)

private class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/**
 * The device's turn, without Android in it: feed it pitch and roll in degrees at every sensor sample,
 * read back a tilt within ±[limit] degrees. The rest pose drifts after the device (about a second to
 * catch up at 50 samples a second), so a phone held still at any angle reads as flat.
 */
internal class TiltFilter(
    private val alpha: Float = 0.1f,
    private val restAlpha: Float = 0.02f,
    private val limit: Float = LimitDegrees,
) {
    var rotationX = 0f
        private set
    var rotationY = 0f
        private set

    private var restPitch = Float.NaN
    private var restRoll = Float.NaN

    fun update(pitchDegrees: Float, rollDegrees: Float) {
        // A virtual sensor can hand back NaN: one such sample would stick in the filter for good.
        if (!pitchDegrees.isFinite() || !rollDegrees.isFinite()) return
        if (restPitch.isNaN()) {
            restPitch = pitchDegrees
            restRoll = rollDegrees
        }
        restPitch += wrap(pitchDegrees - restPitch) * restAlpha
        restRoll += wrap(rollDegrees - restRoll) * restAlpha
        val targetX = (-wrap(pitchDegrees - restPitch)).coerceIn(-limit, limit)
        val targetY = wrap(rollDegrees - restRoll).coerceIn(-limit, limit)
        rotationX += (targetX - rotationX) * alpha
        rotationY += (targetY - rotationY) * alpha
    }

    fun reset() {
        restPitch = Float.NaN
        restRoll = Float.NaN
        rotationX = 0f
        rotationY = 0f
    }

    /** The shortest way round, so a roll crossing ±180° is a small step and not a full turn. */
    private fun wrap(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    companion object {
        const val LimitDegrees = 6f
    }
}

private const val DeadBand = 0.02f
