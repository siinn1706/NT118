package vn.uit.healthos.core.designsystem.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Direction of the light that draws the specular rim on Chrome and Control glass, in degrees (the convention of
 * Kyant's HighlightStyle.Default). [angle] is snapshot state: read it only inside draw lambdas so a tilt redraws
 * the rim and nothing else.
 */
@Stable
interface GlassLight {
    val angle: Float
}

/** Light fixed at the classic top-left 45 degrees: used without a GlassRoot and while reduced motion is on. */
object FixedGlassLight : GlassLight {
    override val angle: Float = DEFAULT_LIGHT_ANGLE
}

/** The light of the nearest [GlassRoot]; null outside one (glass then uses [FixedGlassLight]). */
val LocalGlassLight = staticCompositionLocalOf<GlassLight?> { null }

internal const val DEFAULT_LIGHT_ANGLE = 45f

/**
 * Light that follows gravity like iOS 26 specular highlights: the accelerometer is sampled at about 30 Hz, low-pass
 * filtered, and only listened to while the screen is resumed. With [enabled] false (reduced motion, or a device
 * without an accelerometer) the light stays at 45 degrees and no sensor is registered.
 */
@Composable
fun rememberGlassLight(enabled: Boolean): GlassLight {
    val context = LocalContext.current
    val light = remember(context) { SensorGlassLight(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(light, lifecycleOwner, enabled) {
        if (!enabled) {
            light.reset()
            return@DisposableEffect onDispose {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> light.start()
                Lifecycle.Event.ON_PAUSE -> light.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) light.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            light.stop()
        }
    }
    return light
}

private class SensorGlassLight(context: Context) : GlassLight, SensorEventListener {

    override var angle: Float by mutableFloatStateOf(DEFAULT_LIGHT_ANGLE)
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val filter = GravityFilter()
    private var listening = false

    fun start() {
        if (listening || sensorManager == null || accelerometer == null) return
        listening = sensorManager.registerListener(this, accelerometer, SAMPLING_PERIOD_US)
    }

    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    fun reset() {
        stop()
        filter.reset()
        angle = DEFAULT_LIGHT_ANGLE
    }

    override fun onSensorChanged(event: SensorEvent) {
        val next = filter.update(event.values[0], event.values[1]) ?: return
        if (angleDistance(next, angle) >= MIN_ANGLE_STEP) angle = next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SAMPLING_PERIOD_US = 33_333
        const val MIN_ANGLE_STEP = 0.75f
    }
}

/**
 * Low-pass filter over the gravity vector projected on the screen plane. Filtering the vector (not the angle) avoids
 * the jump at +/-180 degrees. Returns null while the phone lies flat and the projection carries no direction.
 */
internal class GravityFilter(private val smoothing: Float = 0.2f) {
    private var x = 0f
    private var y = 0f
    private var primed = false

    fun update(rawX: Float, rawY: Float): Float? {
        if (!primed) {
            x = rawX
            y = rawY
            primed = true
        } else {
            x += (rawX - x) * smoothing
            y += (rawY - y) * smoothing
        }
        if (abs(x) < FLAT_THRESHOLD && abs(y) < FLAT_THRESHOLD) return null
        return (atan2(y, x) * (180f / PI.toFloat()))
    }

    fun reset() {
        primed = false
        x = 0f
        y = 0f
    }

    private companion object {
        /** m/s^2 on both screen axes; below this the phone is nearly flat on a table. */
        const val FLAT_THRESHOLD = 0.8f
    }
}

/** Smallest absolute difference between two angles in degrees, accounting for wrap-around. */
internal fun angleDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}
