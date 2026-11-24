package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import vn.uit.healthos.core.designsystem.glass.DampedDragAnimation
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.liquidThumb
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrackHeight = 6.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/**
 * iOS 26 slider, ported from Kyant's LiquidSlider: a thin capsule track with an accent fill and a white capsule thumb
 * that lifts into a liquid lens (magnifying the track with chromatic fringes) while pressed, with velocity squash and
 * stretch. Grab the thumb and drag, drag from anywhere on the track (the thumb jumps under the finger), or tap the
 * track to move there. [steps] > 0 snaps to that many intermediate stops, like the Material slider.
 *
 * Accessible like a Material slider: progress range semantics with a set-progress action, optional
 * [contentDescription] and [valueText] (the spoken state, for example "72 bpm"), arrow keys move by one step (or 5%
 * without steps), 48dp tall. Vertical scrolling still wins over a vertical swipe on the slider. Springs snap with
 * reduced motion.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    valueText: ((Float) -> String)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalGlassColors.current.base.luminance() < 0.5f
    val trackColor = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val fillColor = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val currentOnChange = rememberUpdatedState(onValueChange)
    val currentOnFinished = rememberUpdatedState(onValueChangeFinished)
    val scope = rememberCoroutineScope()
    val clamped = value.coerceIn(valueRange)
    val snap: (Float) -> Float = remember(valueRange, steps) { { snapToSteps(it, valueRange, steps) } }

    val animation = remember(scope, valueRange) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = clamped,
            valueRange = valueRange,
            visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
            initialScale = 1f,
            pressedScale = 1.5f,
            reducedMotion = { reduced.value },
            onDrag = { _, _ -> },
        )
    }
    // External changes (and values the caller refused) move the thumb to the real value.
    LaunchedEffect(animation, clamped) {
        if (animation.targetValue != clamped) animation.updateValue(clamped)
    }

    val trackLayer = rememberLayerBackdrop()
    val background = LocalGlassBackdrops.current?.background ?: emptyBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(
        background,
        rememberBackdrop(trackLayer) { drawTrack ->
            val progress = animation.pressProgress
            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawTrack() }
        },
    )
    val span = valueRange.endInclusive - valueRange.start
    // Width of the slider, read only while drawing the thumb.
    val widthPx = remember { mutableFloatStateOf(0f) }
    val keyStep = if (steps > 0) span / (steps + 1) else span / 20f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTokens.MinTouchTarget)
            .onSizeChanged { widthPx.floatValue = it.width.toFloat() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, valueRange, steps)
                if (valueText != null) stateDescription = valueText(clamped)
                if (enabled) {
                    setProgress { target ->
                        val next = snap(target.coerceIn(valueRange))
                        if (next != clamped) {
                            currentOnChange.value(next)
                            currentOnFinished.value?.invoke()
                        }
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(
                if (enabled) {
                    Modifier
                        .onKeyEvent { event ->
                            val direction = when (event.key) {
                                Key.DirectionRight -> if (isLtr) 1 else -1
                                Key.DirectionLeft -> if (isLtr) -1 else 1
                                Key.DirectionUp -> 1
                                Key.DirectionDown -> -1
                                else -> 0
                            }
                            if (direction == 0) return@onKeyEvent false
                            if (event.type == KeyEventType.KeyDown) {
                                val next = snap((clamped + direction * keyStep).coerceIn(valueRange))
                                if (next != clamped) currentOnChange.value(next)
                            } else if (event.type == KeyEventType.KeyUp) {
                                currentOnFinished.value?.invoke()
                            }
                            true
                        }
                        .focusable()
                        .pointerInput(animation, isLtr, valueRange, steps) {
                            val thumbWidth = ThumbWidth.toPx()
                            fun valueAt(x: Float): Float {
                                val usable = (size.width - thumbWidth).coerceAtLeast(1f)
                                val fromStart = if (isLtr) x else size.width - x
                                val fraction = ((fromStart - thumbWidth / 2f) / usable).coerceIn(0f, 1f)
                                return snap(valueRange.start + fraction * span)
                            }
                            fun thumbCenter(): Float {
                                val usable = size.width - thumbWidth
                                val fromStart = thumbWidth / 2f + usable * animation.progress
                                return if (isLtr) fromStart else size.width - fromStart
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                animation.press()
                                // Grabbing the thumb drags it relatively; elsewhere it jumps under the finger.
                                val grabbed = abs(down.position.x - thumbCenter()) <= thumbWidth / 2f
                                var dragging = false
                                var cancelled = false
                                var travelled = Offset.Zero
                                var dragValue = animation.targetValue
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.fastFirstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) {
                                        cancelled = true
                                        break
                                    }
                                    if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                                    val delta = change.positionChange()
                                    travelled += delta
                                    if (!dragging) {
                                        val slop = viewConfiguration.touchSlop
                                        if (abs(travelled.y) > slop && abs(travelled.y) > abs(travelled.x)) {
                                            // A vertical swipe belongs to the scrolling parent.
                                            cancelled = true
                                            break
                                        }
                                        if (abs(travelled.x) > slop) {
                                            dragging = true
                                            if (!grabbed) dragValue = valueAt(change.position.x)
                                        }
                                    }
                                    if (dragging) {
                                        change.consume()
                                        if (grabbed) {
                                            val usable = (size.width - thumbWidth).coerceAtLeast(1f)
                                            val dx = if (isLtr) delta.x else -delta.x
                                            dragValue = (dragValue + dx / usable * span).coerceIn(valueRange)
                                        } else {
                                            dragValue = valueAt(change.position.x)
                                        }
                                        val snapped = snap(dragValue)
                                        animation.updateValue(snapped)
                                        currentOnChange.value(snapped)
                                    }
                                }
                                animation.release()
                                if (!cancelled && !dragging) {
                                    // A tap on the track moves the thumb there.
                                    val tapped = valueAt(down.position.x)
                                    animation.animateToValue(tapped)
                                    currentOnChange.value(tapped)
                                }
                                if (!cancelled) currentOnFinished.value?.invoke()
                            }
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackLayer)
                .fillMaxWidth()
                .height(TrackHeight)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(trackColor, cornerRadius = radius)
                    val thumbWidth = ThumbWidth.toPx()
                    val filled = thumbWidth / 2f + (size.width - thumbWidth) * animation.progress
                    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - filled
                    drawRoundRect(fillColor, topLeft = Offset(left, 0f), size = Size(filled, size.height), cornerRadius = radius)
                },
        )
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (widthPx.floatValue - size.width).coerceAtLeast(0f) * animation.progress
                    translationX = if (isLtr) travel else -travel
                }
                .liquidThumb(animation, lensBackdrop, restColor = Color.White)
                .size(ThumbWidth, ThumbHeight),
        )
    }
}

/** Snaps [value] to the nearest of [steps] evenly spaced stops inside [range] (no snapping when steps is 0). */
internal fun snapToSteps(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val clamped = value.coerceIn(range)
    if (steps <= 0) return clamped
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val segments = steps + 1
    val index = ((clamped - range.start) / span * segments).roundToInt()
    return (range.start + index * span / segments).coerceIn(range)
}
