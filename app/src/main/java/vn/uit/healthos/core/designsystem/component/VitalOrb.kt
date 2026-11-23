package vn.uit.healthos.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.rememberReducedMotion
import vn.uit.healthos.core.designsystem.theme.vitalColorFor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ORB_POINTS = 8
private const val TWO_PI = (2 * PI).toFloat()

/**
 * Soft, breathing blob whose colour follows a 0..1 health [score] (teal = good, amber = watch, coral = alert).
 * Breathing speed follows [breathsPerMinute]; motion stops when the system disables animations.
 */
@Composable
fun VitalOrb(
    score: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    breathsPerMinute: Float = 6f,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val vitalColors = LocalVitalColors.current
    val color by animateColorAsState(
        targetValue = vitalColorFor(score, vitalColors),
        animationSpec = tween(durationMillis = 600),
        label = "orbColor",
    )
    val motion = if (rememberReducedMotion()) 0f else 1f
    val breathMs = (60_000f / breathsPerMinute.coerceIn(2f, 30f) / 2f).toInt()
    val transition = rememberInfiniteTransition(label = "orb")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "phase",
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radiusMax = this.size.minDimension / 2f
            val scale = 1f - 0.06f * motion * (1f - breath)
            val base = radiusMax * 0.8f * scale
            val wobble = 0.07f * motion

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = radiusMax,
                ),
                radius = radiusMax,
            )

            val points = List(ORB_POINTS) { i ->
                val angle = TWO_PI * i / ORB_POINTS
                val r = base * (1f + wobble * (0.6f * sin(phase * 2f + i * 1.7f) + 0.4f * sin(phase * 3f - i * 0.9f)))
                Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
            }
            drawPath(
                path = smoothClosedPath(points),
                brush = Brush.radialGradient(
                    colors = listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.25f)),
                    center = Offset(center.x - base * 0.3f, center.y - base * 0.35f),
                    radius = base * 1.7f,
                ),
            )
        }
        content()
    }
}

/** Closed Catmull-Rom spline through [points], converted to cubic Bézier segments. */
private fun smoothClosedPath(points: List<Offset>): Path {
    val n = points.size
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            val c1 = p1 + (p2 - p0) / 6f
            val c2 = p2 - (p3 - p1) / 6f
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        close()
    }
}
