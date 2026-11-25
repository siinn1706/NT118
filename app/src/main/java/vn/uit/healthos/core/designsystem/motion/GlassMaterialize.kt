package vn.uit.healthos.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.Backdrop
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.glassDensity
import vn.uit.healthos.core.designsystem.glass.glassSurface
import vn.uit.healthos.core.designsystem.glass.liquidGlass

/**
 * A [liquidGlass] surface that appears and disappears the way Liquid Glass does: instead of fading by alpha, its lens
 * refraction, frost, saturation, specular rim, fill and shadow ramp with [progress] (0 = absent, 1 = present). The
 * glass stays fully dense until the last quarter of the way out ([glassDensity]) and then dissolves into what is
 * behind it. On devices or settings without backdrop sampling the solid fallback fades along the same curve.
 *
 * [progress] is only read inside effect, draw and layer lambdas, so animating it (for example with
 * GlassMorphController.glassProgress) redraws the surface without recomposing. Pass a remembered or stable lambda.
 * Content drawn after this modifier is clipped to [shape] and is not faded: fade labels separately if needed.
 */
@Composable
fun Modifier.glassMaterialize(
    progress: () -> Float,
    shape: Shape,
    style: GlassStyle = GlassStyle.Chrome,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.background,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    tintMode: GlassTintMode = GlassTintMode.Wash,
): Modifier = glassSurface(
    shape = shape,
    style = style,
    tint = tint,
    backdrop = backdrop,
    layerBlock = layerBlock,
    tintMode = tintMode,
    exportedBackdrop = null,
    materialize = progress,
)
