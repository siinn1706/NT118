package vn.uit.healthos.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.floor

/**
 * Determinate linear progress: a quiet inset capsule track with an accent capsule fill. [progress] (0..1) is read
 * while drawing, so animating it does not recompose. Exposes progress to accessibility services.
 */
@Composable
fun GlassProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    height: Dp = 6.dp,
) {
    val fill = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    val track = LocalGlassColors.current.insetFill
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        drawCapsule(track, 0f, size.width)
        val width = size.width * progress().coerceIn(0f, 1f)
        if (width > 0f) drawCapsule(fill, 0f, width.coerceAtLeast(size.height))
    }
}

/** Indeterminate linear progress: an accent segment sweeping along the track (a gentle pulse with reduced motion). */
@Composable
fun GlassProgressBar(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    height: Dp = 6.dp,
) {
    val fill = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    val track = LocalGlassColors.current.insetFill
    val reducedMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "glassProgress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion) 2_400 else 1_300, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        drawCapsule(track, 0f, size.width)
        if (reducedMotion) {
            val pulse = 0.35f + 0.4f * (1f - abs(phase * 2f - 1f))
            drawCapsule(fill.copy(alpha = fill.alpha * pulse), 0f, size.width)
        } else {
            val segment = size.width * 0.35f
            val start = -segment + (size.width + segment) * phase
            val left = start.coerceAtLeast(0f)
            val right = (start + segment).coerceAtMost(size.width)
            if (right > left) drawCapsule(fill, left, right - left)
        }
    }
}

private fun DrawScope.drawCapsule(color: Color, start: Float, width: Float) {
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start
    drawRoundRect(
        color = color,
        topLeft = Offset(x, 0f),
        size = Size(width, size.height),
        cornerRadius = CornerRadius(size.height / 2f),
    )
}

/**
 * iOS-style activity indicator: eight rounded spokes fading around the circle, stepping like UIActivityIndicatorView.
 * Uses [color] or the current content colour. Pass [contentDescription] when nothing else announces the loading state.
 */
@Composable
fun GlassSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val spokeColor = when {
        color.isSpecified -> color
        LocalContentColor.current.isSpecified -> LocalContentColor.current
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val transition = rememberInfiniteTransition(label = "glassSpinner")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPOKES.toFloat(),
        animationSpec = infiniteRepeatable(tween(SPOKES * 100, easing = LinearEasing)),
        label = "step",
    )
    Canvas(
        modifier
            .size(maxOf(size, 8.dp))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        val head = floor(step).toInt()
        val radius = this.size.minDimension / 2f
        val stroke = radius * 0.22f
        for (i in 0 until SPOKES) {
            // The spoke at the head is brightest; the trail fades behind it.
            val age = (head - i + SPOKES) % SPOKES
            val alpha = 1f - age * (0.75f / SPOKES)
            rotate(degrees = i * (360f / SPOKES)) {
                drawLine(
                    color = spokeColor.copy(alpha = spokeColor.alpha * alpha),
                    start = Offset(center.x, center.y - radius * 0.45f),
                    end = Offset(center.x, center.y - radius + stroke / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private const val SPOKES = 8

