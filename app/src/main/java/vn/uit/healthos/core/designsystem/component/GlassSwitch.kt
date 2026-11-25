package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.DampedDragAnimation
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.liquidThumb
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp
private val ThumbPadding = 2.dp

/**
 * iOS 26 switch: an accent track and a white capsule thumb that becomes a liquid lens (magnifying the track with
 * chromatic fringes) while pressed or dragged, with velocity squash and stretch. Tap anywhere to toggle, or drag the
 * thumb across. Exposes switch semantics (role, on/off state, click action), works with a keyboard (Space/Enter),
 * has a 48dp touch target and snaps instead of springing with reduced motion. [onCheckedChange] null makes it
 * read-only and invisible to accessibility services: it is then only a picture of the state, for a row that owns the
 * toggle and announces it (GlassListRow with `toggled`, or a `Modifier.toggleable` row).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalGlassColors.current.base.luminance() < 0.5f
    val onColor = scheme.primary
    val offColor = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val interactive = enabled && onCheckedChange != null

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { (TrackWidth - ThumbWidth - ThumbPadding * 2).toPx() }
    val scope = rememberCoroutineScope()
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val currentChecked = rememberUpdatedState(checked)
    val currentOnChange = rememberUpdatedState(onCheckedChange)
    val ltr = rememberUpdatedState(isLtr)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(if (checked) 1f else 0f) }

    val animation = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            reducedMotion = { reduced.value },
            onDragStopped = {
                val target = if (didDrag) targetValue >= 0.5f else !currentChecked.value
                didDrag = false
                fraction = if (target) 1f else 0f
                animateToValue(fraction)
                if (target != currentChecked.value) currentOnChange.value?.invoke(target)
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth * if (ltr.value) 1f else -1f
                fraction = (fraction + delta).coerceIn(0f, 1f)
                updateValue(fraction)
            },
        )
    }
    // External changes (and a refused change) move the thumb back to the real state.
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (fraction != target || animation.targetValue != target) {
            fraction = target
            animation.animateToValue(target)
        }
    }

    val trackLayer = rememberLayerBackdrop()
    val background = LocalGlassBackdrops.current?.background ?: emptyBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(
        background,
        rememberBackdrop(trackLayer) { drawTrack ->
            val progress = animation.pressProgress
            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
        },
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(TrackWidth, TrackHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .then(
                if (onCheckedChange == null) {
                    // A separate focus stop here would split the row in two for TalkBack, without the state on the row.
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Switch
                        toggleableState = ToggleableState(checked)
                        if (enabled) {
                            onClick { onCheckedChange(!checked); true }
                        } else {
                            disabled()
                        }
                    }
                },
            )
            .then(
                if (interactive) {
                    Modifier
                        .onKeyEvent { event ->
                            val toggleKey = event.key == Key.Spacebar || event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                            if (toggleKey && event.type == KeyEventType.KeyUp) {
                                onCheckedChange?.invoke(!checked)
                                true
                            } else {
                                false
                            }
                        }
                        .focusable()
                        .then(animation.modifier)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackLayer)
                .clip(Capsule())
                .drawBehind { drawRect(lerp(offColor, onColor, animation.value)) }
                .size(TrackWidth, TrackHeight),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = ThumbPadding.toPx()
                    translationX = if (isLtr) {
                        lerp(padding, padding + dragWidth, animation.value)
                    } else {
                        lerp(-padding, -(padding + dragWidth), animation.value)
                    }
                }
                .liquidThumb(animation, lensBackdrop, restColor = Color.White, lensHeight = 5.dp, lensAmount = 10.dp, velocityDivisor = 50f)
                .size(ThumbWidth, ThumbHeight),
        )
    }
}
