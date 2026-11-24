package vn.uit.healthos.core.designsystem.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.core.designsystem.theme.VbMotion

/** Sizes shared by every Liquid Glass surface. Corners are continuous (squircle) like iOS. */
object GlassTokens {
    val CardRadius = 26.dp
    val ControlRadius = 18.dp
    val TabBarHeight = 64.dp
    val TabBarMargin = 12.dp
    val NavButtonSize = 44.dp
    val Hairline = 0.75.dp

    val CardShape = RoundedRectangle(CardRadius)
    val ControlShape = RoundedRectangle(ControlRadius)
}

/**
 * How a glass surface bends and frosts what is behind it.
 * [Chrome] is the navigation layer (tab bar, toolbars, floating buttons): strong lens refraction, light frost.
 * [Material] is for content cards: heavier frost, subtle edge refraction, so text stays legible.
 * [Control] is for buttons and small controls: crisp lens, almost no frost.
 */
@Immutable
data class GlassStyle(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val highlightAlpha: Float,
    val shadowRadius: Dp,
    val kind: Kind,
) {
    enum class Kind { Chrome, Material, Control }

    companion object {
        val Chrome = GlassStyle(
            blur = 4.dp, lensHeight = 22.dp, lensAmount = 30.dp,
            highlightAlpha = 1f, shadowRadius = 28.dp, kind = Kind.Chrome,
        )
        val Material = GlassStyle(
            blur = 20.dp, lensHeight = 8.dp, lensAmount = 12.dp,
            highlightAlpha = 0.85f, shadowRadius = 18.dp, kind = Kind.Material,
        )
        val Control = GlassStyle(
            blur = 2.dp, lensHeight = 12.dp, lensAmount = 22.dp,
            highlightAlpha = 1f, shadowRadius = 12.dp, kind = Kind.Control,
        )
    }
}

/** Fills, rim and ambient colours for glass in the current light/dark appearance. */
@Immutable
data class GlassColors(
    val chromeFill: Color,
    val materialFill: Color,
    val controlFill: Color,
    /** Used when the device cannot blur (Android 9/10) or no backdrop is available: nearly opaque. */
    val solidFill: Color,
    val rim: Color,
    val shadow: Color,
    val base: Color,
    val aurora: List<Color>,
) {
    fun fillFor(kind: GlassStyle.Kind): Color = when (kind) {
        GlassStyle.Kind.Chrome -> chromeFill
        GlassStyle.Kind.Material -> materialFill
        GlassStyle.Kind.Control -> controlFill
    }
}

val LightGlassColors = GlassColors(
    chromeFill = Color.White.copy(alpha = 0.46f),
    materialFill = Color.White.copy(alpha = 0.62f),
    controlFill = Color.White.copy(alpha = 0.30f),
    solidFill = Color.White.copy(alpha = 0.9f),
    rim = Color.Black.copy(alpha = 0.06f),
    shadow = Color.Black.copy(alpha = 0.10f),
    base = Color(0xFFF2F2F7),
    aurora = listOf(
        Color(0xFF5EEAD4).copy(alpha = 0.55f),
        Color(0xFF7DD3FC).copy(alpha = 0.45f),
        Color(0xFFFDA4AF).copy(alpha = 0.38f),
        Color(0xFFFDE68A).copy(alpha = 0.42f),
    ),
)

val DarkGlassColors = GlassColors(
    chromeFill = Color(0xFF1C1C1E).copy(alpha = 0.50f),
    materialFill = Color(0xFF1C1C1E).copy(alpha = 0.64f),
    controlFill = Color.White.copy(alpha = 0.10f),
    solidFill = Color(0xFF1C1C1E).copy(alpha = 0.94f),
    rim = Color.White.copy(alpha = 0.12f),
    shadow = Color.Black.copy(alpha = 0.45f),
    base = Color(0xFF000000),
    aurora = listOf(
        Color(0xFF0F766E).copy(alpha = 0.60f),
        Color(0xFF3730A3).copy(alpha = 0.45f),
        Color(0xFF9F1239).copy(alpha = 0.34f),
        Color(0xFF155E75).copy(alpha = 0.50f),
    ),
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }

/**
 * Layers that glass can sample. [background] holds only the ambient backdrop, so content cards (which live inside the
 * screen layer) can refract it without sampling themselves. [chrome] also includes the screens, for surfaces that
 * float above them such as the tab bar.
 */
@Immutable
class GlassBackdrops(val background: Backdrop, val chrome: Backdrop)

val LocalGlassBackdrops = staticCompositionLocalOf<GlassBackdrops?> { null }

/**
 * Space a tab screen must leave at the bottom of its scrolling content so the last item can scroll clear of the
 * floating tab bar (bar height, margins and the system navigation bar). Zero on screens without the tab bar.
 */
val LocalBottomChromeInset = compositionLocalOf { 0.dp }

/** Real blur needs RenderEffect (Android 12+); refraction needs RuntimeShader (Android 13+). */
private val canBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Draws a Liquid Glass surface in [shape]: the [backdrop] is saturated, frosted and refracted at the edges, then a
 * translucent fill, an optional [tint], a specular rim and a soft shadow are layered on top. Without a backdrop, or on
 * devices that cannot blur, it falls back to an almost opaque fill with a hairline rim so text keeps its contrast.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    style: GlassStyle = GlassStyle.Material,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.background,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val colors = LocalGlassColors.current
    if (backdrop == null || !canBlur) {
        val fill = colors.solidFill
        return this
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .background(fill, shape)
            .then(if (tint.isSpecified) Modifier.background(tint, shape) else Modifier)
            .border(GlassTokens.Hairline, colors.rim, shape)
    }
    val fill = colors.fillFor(style.kind)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(style.blur.toPx())
            lens(style.lensHeight.toPx(), style.lensAmount.toPx())
        },
        highlight = { Highlight(width = GlassTokens.Hairline, alpha = style.highlightAlpha) },
        shadow = if (style.shadowRadius > 0.dp) {
            { Shadow(radius = style.shadowRadius, color = colors.shadow) }
        } else {
            null
        },
        layerBlock = layerBlock,
        onDrawSurface = {
            drawRect(fill)
            if (tint.isSpecified) drawRect(tint)
        },
    )
}

/** Scale that follows the press state of [interactionSource]: glass "gives" under the finger like iOS controls. */
@Composable
fun rememberPressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = VbMotion.press(),
        label = "glassPress",
    )
}

/**
 * Root of the glass world: paints the ambient backdrop once, records it as a layer and exposes it through
 * [LocalGlassBackdrops]. Screens draw on top with a transparent background.
 */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val background = rememberLayerBackdrop()
    val backdrops = remember(background) { GlassBackdrops(background = background, chrome = background) }
    Box(modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize().layerBackdrop(background))
        CompositionLocalProvider(LocalGlassBackdrops provides backdrops) {
            content()
        }
    }
}

/**
 * Static aurora of soft brand-coloured light behind every screen. It gives glass something to refract; it is kept
 * still on purpose so that frosted cards do not have to re-render every frame.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    Canvas(modifier) {
        drawRect(colors.base)
        val w = size.width
        val h = size.height
        val blobs = listOf(
            Offset(w * 0.05f, h * 0.04f) to w * 0.85f,
            Offset(w * 1.0f, h * 0.34f) to w * 0.75f,
            Offset(w * 0.0f, h * 0.70f) to w * 0.72f,
            Offset(w * 0.95f, h * 1.0f) to w * 0.80f,
        )
        blobs.forEachIndexed { index, (center, radius) ->
            val color = colors.aurora[index % colors.aurora.size]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
