package vn.uit.healthos.core.designsystem.glass

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/** Which edge of a scrolling area a [ScrollEdgeEffect] softens. */
enum class ScrollEdge { Top, Bottom }

/**
 * iOS 26 scroll edge effect: where content scrolls under floating chrome, a band of [backdrop] (the content layer the
 * chrome floats over) is blurred and faded towards the content, with a soft wash of the base colour so titles and
 * glyphs on the chrome stay legible. Size it with [modifier] (full width, the chrome height plus a fade). Place it
 * above the content and below the chrome; it is decorative and invisible to accessibility services.
 *
 * [visibility] (0..1) is read only while drawing; drive it from scroll state to show the effect once content is
 * actually underneath. Without blur support it falls back to the gradient wash alone.
 */
@Composable
fun ScrollEdgeEffect(
    edge: ScrollEdge,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.chrome,
    visibility: () -> Float = { 1f },
) {
    val colors = LocalGlassColors.current
    val rendering = glassRendering(backdrop)
    val effect = remember(edge, colors, rendering, backdrop) {
        // Opaque at the edge, transparent towards the content. Stops are listed top to bottom.
        fun towardsContent(vararg stops: Pair<Float, Color>): Brush = Brush.verticalGradient(
            *if (edge == ScrollEdge.Top) stops else stops.map { (f, c) -> (1f - f) to c }.reversed().toTypedArray(),
        )
        val mask = towardsContent(0f to Color.Black, 1f to Color.Transparent)
        if (rendering == GlassRendering.Solid || backdrop == null) {
            val wash = towardsContent(
                0f to colors.base.copy(alpha = 0.94f),
                0.55f to colors.base.copy(alpha = 0.6f),
                1f to colors.base.copy(alpha = 0f),
            )
            Modifier.drawBehind { drawRect(wash) }
        } else {
            val wash = towardsContent(0f to colors.base.copy(alpha = 0.55f), 1f to colors.base.copy(alpha = 0f))
            Modifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur(EdgeBlur.toPx())
                },
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                    // The node composites offscreen, so DstIn fades the blurred copy out towards the content.
                    drawRect(mask, blendMode = BlendMode.DstIn)
                },
                onDrawSurface = { drawRect(wash) },
            )
        }
    }
    Spacer(
        modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = visibility().coerceIn(0f, 1f) }
            .then(effect),
    )
}

private val EdgeBlur = 12.dp
