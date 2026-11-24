package vn.uit.healthos.core.designsystem.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Light that blooms under the finger on interactive glass, ported from Kyant's catalog: a radial glow added with
 * BlendMode.Plus at the touch point plus a faint wash over the whole surface (a flat Plus wash below Android 13),
 * driven by a press spring. The touch offset also feeds [pressLayerBlock], the elastic press physics of iOS 26
 * buttons. Reads of [pressProgress] and [offset] happen only in draw and layer lambdas, so pressing never recomposes.
 *
 * Attach [modifier] after the glass modifier (so the glow is clipped to the glass) and [gestureModifier] where the
 * touches arrive. The gesture handler never consumes events, so clicks, toggles and scrolling still work.
 */
@Stable
class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val reducedMotion: () -> Boolean = { false },
    /** Glow strength: 1 for controls, lower for large content cards. */
    private val intensity: Float = 1f,
    /** Where the glow is centred, given the surface size and the current finger position. */
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressSpec = spring(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    /** 0 at rest, 1 while the finger is down (springs between). */
    val pressProgress: Float get() = pressProgressAnimation.value

    /** Finger travel since touch down; springs back to zero on release. */
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            val shader = glowShader
            if (shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress * intensity), blendMode = BlendMode.Plus)
                val center = position(size, positionAnimation.value)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("color", Color.White.copy(alpha = 0.15f * progress * intensity))
                shader.setFloatUniform("radius", size.minDimension * 1.5f)
                shader.setFloatUniform(
                    "position",
                    center.x.fastCoerceIn(0f, size.width),
                    center.y.fastCoerceIn(0f, size.height),
                )
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(alpha = 0.25f * progress * intensity), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            onDragStart = { down -> press(down.position) },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            if (!reducedMotion()) animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    /** Lights the surface at [at] (also used for keyboard presses, with the centre as position). */
    fun press(at: Offset) {
        startPosition = at
        animationScope.launch {
            launch {
                if (reducedMotion()) pressProgressAnimation.snapTo(1f)
                else pressProgressAnimation.animateTo(1f, pressProgressSpec)
            }
            launch { positionAnimation.snapTo(at) }
        }
    }

    fun release() {
        animationScope.launch {
            launch {
                if (reducedMotion()) pressProgressAnimation.snapTo(0f)
                else pressProgressAnimation.animateTo(0f, pressProgressSpec)
            }
            launch {
                if (reducedMotion()) positionAnimation.snapTo(startPosition)
                else positionAnimation.animateTo(startPosition, positionSpec)
            }
        }
    }

    /**
     * Graphics layer block for the iOS 26 press: the surface swells by [bulge] (relative to its height), follows the
     * finger with a rubber-band offset and stretches along the drag direction. Pass it as the glass layerBlock so the
     * sampled backdrop is inverse-transformed and stays put while the glass moves.
     */
    fun pressLayerBlock(bulge: Dp = 4.dp): GraphicsLayerScope.() -> Unit = {
        val progress = pressProgress
        val width = size.width
        val height = size.height
        if (height > 0f && width > 0f) {
            val scale = lerp(1f, 1f + bulge.toPx() / height, progress)
            val maxOffset = size.minDimension
            val drag = offset
            translationX = maxOffset * tanh(0.05f * drag.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * drag.y / maxOffset)
            val maxDragScale = bulge.toPx() / height
            val angle = atan2(drag.y, drag.x)
            scaleX = scale + maxDragScale * abs(cos(angle) * drag.x / size.maxDimension) *
                (width / height).fastCoerceAtMost(1f)
            scaleY = scale + maxDragScale * abs(sin(angle) * drag.y / size.maxDimension) *
                (height / width).fastCoerceAtMost(1f)
        }
    }

    private companion object {
        /** One shader for every highlight: uniforms are copied when a draw is recorded, so sharing is safe. */
        val glowShader: RuntimeShader? by lazy {
            if (isRuntimeShaderSupported()) {
                RuntimeShader(
                    """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}""",
                )
            } else {
                null
            }
        }
    }
}

/** An [InteractiveHighlight] bound to this composition, honouring [LocalReducedMotion]. */
@Composable
fun rememberInteractiveHighlight(
    intensity: Float = 1f,
    position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
): InteractiveHighlight {
    val scope = rememberCoroutineScope()
    val reduced: State<Boolean> = rememberUpdatedState(LocalReducedMotion.current)
    return remember(scope, intensity) {
        InteractiveHighlight(scope, reducedMotion = { reduced.value }, intensity = intensity, position = position)
    }
}

/**
 * Reports a gesture from the very first touch (unlike detectDragGestures, which waits for touch slop) without
 * consuming anything, so it can sit next to clickable, toggleable and scrollable modifiers.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = trackDrag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        // Someone else (a scroll container) took the gesture over.
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed } ?: return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
