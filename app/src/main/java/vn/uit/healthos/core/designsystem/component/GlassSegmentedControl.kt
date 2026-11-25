package vn.uit.healthos.core.designsystem.component

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.DampedDragAnimation
import vn.uit.healthos.core.designsystem.glass.GlassRendering
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.drawFaded
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.glassRendering
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.liquidThumb
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion

private val ThumbInset = 3.dp

/**
 * iOS 26 segmented control: a capsule track with a sliding thumb behind the selected label. Tap a segment to select it,
 * or press and drag the thumb: it lifts into a liquid lens (magnifying the labels with chromatic fringes), follows the
 * finger with velocity squash and stretch and settles on the nearest segment. The selected label is semibold.
 *
 * Accessible as a group of selectable tabs (one per option, selected state announced), 48dp tall, RTL aware; springs
 * snap with reduced motion. On the ambient background the track is Control glass, inside a glass card it is an inset.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.isNotEmpty()) { "GlassSegmentedControl needs at least one option" }
    val count = options.size
    val scheme = MaterialTheme.colorScheme
    val colors = LocalGlassColors.current
    val dark = colors.base.luminance() < 0.5f
    val thumbColor = if (dark) Color(0xFF636366) else Color.White
    val labelColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val ltr = rememberUpdatedState(isLtr)
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val currentSelected = rememberUpdatedState(selectedIndex)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()

    val animation = remember(scope, count) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.12f,
            reducedMotion = { reduced.value },
            onDragStopped = {
                val target = targetValue.fastRoundToInt().coerceIn(0, count - 1)
                animateToValue(target.toFloat())
                if (target != currentSelected.value) currentOnSelect.value(target)
            },
            onDrag = { size, dragAmount ->
                val delta = dragAmount.x / size.width.coerceAtLeast(1) * if (ltr.value) 1f else -1f
                updateValue((targetValue + delta).fastCoerceIn(0f, (count - 1).toFloat()))
            },
        )
    }
    LaunchedEffect(animation, selectedIndex) {
        if (animation.targetValue.fastRoundToInt() != selectedIndex) animation.animateToValue(selectedIndex.toFloat())
    }

    val trackLayer = rememberLayerBackdrop()
    val labelsLayer = rememberLayerBackdrop()
    val parentBackdrop = LocalGlassBackdrops.current?.background
    val background = parentBackdrop ?: emptyBackdrop()
    // Opaque surfaces stay opaque: no see-through glass under a pressed thumb.
    val solid = glassRendering(parentBackdrop) == GlassRendering.Solid
    val fadePaint = remember { Paint() }
    // At rest the thumb shows only the selected label on its solid fill; pressed, the glass behind fades in.
    val thumbBackdrop = rememberCombinedBackdrop(
        rememberBackdrop(background) { draw ->
            drawFaded(if (solid) 0f else animation.pressProgress, fadePaint, draw)
        },
        rememberBackdrop(trackLayer) { draw ->
            drawFaded(if (solid) 0f else animation.pressProgress, fadePaint, draw)
        },
        labelsLayer,
    )
    val trackSurface = if (LocalInsideGlass.current) {
        Modifier.glassInset(Capsule())
    } else {
        Modifier.liquidGlass(Capsule(), GlassStyle.Control)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTokens.MinTouchTarget),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(trackLayer)
                .then(trackSurface),
        )
        // Visible labels: semantics and taps.
        Row(
            Modifier
                .matchParentSize()
                .padding(ThumbInset)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                Segment(
                    label = option,
                    color = labelColor,
                    bold = false,
                    selected = index == selectedIndex,
                    onClick = if (enabled) {
                        { onSelect(index) }
                    } else {
                        null
                    },
                )
            }
        }
        // Copy of the labels in the selected style, recorded for the thumb to draw and magnify.
        Row(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(labelsLayer)
                .padding(ThumbInset),
        ) {
            options.forEach { option ->
                Segment(label = option, color = labelColor, bold = true, selected = false, onClick = null)
            }
        }
        Box(
            Modifier
                .padding(ThumbInset)
                .graphicsLayer {
                    val travel = animation.value * size.width
                    translationX = if (isLtr) travel else -travel
                }
                .then(if (enabled) animation.modifier else Modifier)
                // No rest blur: at rest the thumb draws only the selected label, which must stay sharp.
                .liquidThumb(animation, thumbBackdrop, restColor = thumbColor, restBehind = true, restBlur = 0.dp)
                .fillMaxHeight()
                .fillMaxWidth(1f / count),
        )
    }
}

@Composable
private fun RowScope.Segment(
    label: String,
    color: Color,
    bold: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Shrinks to fit instead of truncating ("Hệ th...", "Mọi n...") at large font sizes.
        val base = MaterialTheme.typography.labelLarge
        BasicText(
            text = label,
            style = base.copy(
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = base.fontSize, stepSize = 0.5.sp),
        )
    }
}
