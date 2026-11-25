package vn.uit.healthos.core.designsystem.glass

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** Which edge of a scrolling area a [ScrollEdgeEffect] softens. */
enum class ScrollEdge { Top, Bottom }

/**
 * iOS 26 scroll edge effect: where content scrolls under floating chrome, a soft wash of the base colour fades the
 * content out towards the edge so titles and glyphs on the chrome stay legible. With a glass [backdrop] above it the
 * wash is lighter, since the glass already frosts the content. Size it with [modifier] (full width, the chrome height plus a fade). Place it
 * above the content and below the chrome; it is decorative and invisible to accessibility services.
 *
 * [visibility] (0..1) is read only while drawing; drive it from scroll state to show the effect once content is
 * actually underneath.
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
        // A gradient wash of the base colour, without sampling the backdrop. The live blur of the content under the
        // chrome re-rendered a full-width offscreen strip on every scroll frame (about 10 ms per frame on a Galaxy
        // S21); the glass chrome above already frosts what is behind it, so a lighter wash is enough there.
        val underGlass = rendering != GlassRendering.Solid && backdrop != null
        val wash = if (underGlass) {
            towardsContent(
                0f to colors.base.copy(alpha = 0.7f),
                0.55f to colors.base.copy(alpha = 0.35f),
                1f to colors.base.copy(alpha = 0f),
            )
        } else {
            // Plain-text titles sit around the middle of the band: keep the wash nearly opaque there so scrolled content
            // never reads through the title, then fade quickly.
            towardsContent(
                0f to colors.base.copy(alpha = 0.97f),
                0.65f to colors.base.copy(alpha = 0.9f),
                1f to colors.base.copy(alpha = 0f),
            )
        }
        Modifier.drawBehind { drawRect(wash) }
    }
    Spacer(
        modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = visibility().coerceIn(0f, 1f) }
            .then(effect),
    )
}

