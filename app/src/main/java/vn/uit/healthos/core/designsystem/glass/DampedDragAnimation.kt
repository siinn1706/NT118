package vn.uit.healthos.core.designsystem.glass

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drag-driven value with the feel of iOS 26 liquid selectors, ported from Kyant's catalog: a stiff spring follows the
 * finger, the velocity of that spring drives squash-and-stretch, and a press spring lifts the element ([pressProgress],
 * [scaleX], [scaleY]) on touch down and settles it once the value has arrived. Everything is Animatable state, so read
 * it in layer or draw lambdas. With [reducedMotion] every spring snaps and velocity stays at zero.
 */
@Stable
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val reducedMotion: () -> Boolean = { false },
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    private val onDragStopped: DampedDragAnimation.() -> Unit = {},
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocitySpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring(1f, 1000f, 0.001f)
    private val scaleXSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value

    /** Normalised velocity of the value spring (range units per second divided by the range). */
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            if (reducedMotion()) {
                pressProgressAnimation.snapTo(1f)
                scaleXAnimation.snapTo(pressedScale)
                scaleYAnimation.snapTo(pressedScale)
            } else {
                launch { pressProgressAnimation.animateTo(1f, pressSpec) }
                launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
                launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
            }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            if (reducedMotion()) {
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            } else {
                launch { pressProgressAnimation.animateTo(0f, pressSpec) }
                launch { scaleXAnimation.animateTo(initialScale, scaleXSpec) }
                launch { scaleYAnimation.animateTo(initialScale, scaleYSpec) }
            }
        }
    }

    /** Follows the finger: moves the target and lets the value spring chase it, tracking velocity. */
    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            if (reducedMotion()) {
                valueAnimation.snapTo(target)
            } else {
                valueAnimation.animateTo(target, valueSpec) { updateVelocity() }
            }
        }
    }

    /** Programmatic move (tap, external selection): lifts, travels and settles like a short drag. */
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                val target = value.coerceIn(valueRange)
                if (reducedMotion()) {
                    valueAnimation.snapTo(target)
                    velocityAnimation.snapTo(0f)
                    return@mutate
                }
                press()
                launch { valueAnimation.animateTo(target, valueSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }

    /** Jumps without any motion (initial sync, or reduced motion). */
    fun snapToValue(value: Float) {
        animationScope.launch { valueAnimation.snapTo(value.coerceIn(valueRange)) }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val target = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(target, velocitySpec) }
    }
}
