package vn.uit.healthos.core.designsystem.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import vn.uit.healthos.core.designsystem.theme.LocalReducedTransparency
import vn.uit.healthos.core.designsystem.theme.VbMotion

/** Sizes shared by every Liquid Glass surface. Corners are continuous (squircle) like iOS. */
object GlassTokens {
    val CardRadius = 26.dp
    val ControlRadius = 18.dp
    val TabBarHeight = 64.dp
    val TabBarMargin = 12.dp
    val NavButtonSize = 44.dp
    val Hairline = 0.75.dp

    /** Smallest touch target for any interactive glass control (Material and WCAG 2.5.8 guidance). */
    val MinTouchTarget = 48.dp

    val CardShape = RoundedRectangle(CardRadius)
    val ControlShape = RoundedRectangle(ControlRadius)
}

/**
 * Radius that keeps a nested shape concentric with its container: the outer radius minus the inset between the two
 * edges, never below [min]. A 26dp card with 16dp padding gives 10dp for the controls inside it.
 */
fun concentricRadius(outer: Dp, inset: Dp, min: Dp = 4.dp): Dp {
    val radius = outer - inset
    return if (radius < min) min else radius
}

/**
 * Corner radius of the glass container the current content sits in, minus that container's padding, so nested
 * controls can stay concentric (see [concentricRadius]). [Dp.Unspecified] outside a container.
 */
val LocalContainerRadius = compositionLocalOf { Dp.Unspecified }

/**
 * True inside a glass surface (card, dialog panel, banner). Controls read it to draw as [glassInset] instead of
 * sampling the backdrop again, which would punch a hole of raw aurora through the frosted parent.
 */
val LocalInsideGlass = compositionLocalOf { false }

/**
 * How a glass surface bends and frosts what is behind it, following Apple's layering:
 * [Chrome] is the navigation layer (tab bar, toolbars, floating buttons): lens refraction with depth, light frost.
 * [Control] is for buttons and small controls: crisp short lens, almost no frost.
 * [Material] is the content material for cards: heavy frost, no lens, a soft ambient rim, so text stays legible.
 */
@Immutable
data class GlassStyle(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val highlightAlpha: Float,
    val shadowRadius: Dp,
    val kind: Kind,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
) {
    enum class Kind { Chrome, Material, Control }

    val hasLens: Boolean get() = lensHeight > 0.dp && lensAmount > 0.dp

    companion object {
        val Chrome = GlassStyle(
            blur = 8.dp, lensHeight = 24.dp, lensAmount = 24.dp,
            highlightAlpha = 1f, shadowRadius = 24.dp, kind = Kind.Chrome, depthEffect = true,
        )
        val Material = GlassStyle(
            blur = 24.dp, lensHeight = 0.dp, lensAmount = 0.dp,
            highlightAlpha = 0.6f, shadowRadius = 12.dp, kind = Kind.Material,
        )
        val Control = GlassStyle(
            blur = 2.dp, lensHeight = 12.dp, lensAmount = 24.dp,
            highlightAlpha = 1f, shadowRadius = 12.dp, kind = Kind.Control, depthEffect = true,
        )
    }
}

/** How [liquidGlass] paints a tint over the glass. */
enum class GlassTintMode {
    /** Translucent colour wash over the neutral fill (tinted cards, chat bubbles). */
    Wash,

    /**
     * iOS `.glassProminent`: the backdrop is hue-shifted to the tint (BlendMode.Hue) and the tint is laid over it at
     * its own alpha, without the neutral fill. Glass stays visible while labels keep their contrast.
     */
    Prominent,
}

/** Fills, rim and ambient colours for glass in the current appearance. Built by [glassColorsFor]. */
@Immutable
data class GlassColors(
    val chromeFill: Color,
    val materialFill: Color,
    val controlFill: Color,
    /** Used when glass cannot sample a backdrop (Android 10/11, reduced transparency, no GlassRoot): near opaque. */
    val solidFill: Color,
    val rim: Color,
    val shadow: Color,
    val base: Color,
    val aurora: List<Color>,
    /** Quiet fill for controls drawn inside a glass container (iOS tertiarySystemFill). */
    val insetFill: Color = rim,
) {
    fun fillFor(kind: GlassStyle.Kind): Color = when (kind) {
        GlassStyle.Kind.Chrome -> chromeFill
        GlassStyle.Kind.Material -> materialFill
        GlassStyle.Kind.Control -> controlFill
    }
}

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

/** Which rendering path glass takes on this device and in this context. */
enum class GlassRendering {
    /** Android 13+: blur, lens refraction and the shader-based specular rim. */
    Full,

    /** Android 12/12L: RenderEffect blur but no RuntimeShader, so no lens; frost and fill are raised instead. */
    Lite,

    /** Android 10/11, no backdrop to sample, or reduced transparency: a near-opaque fill with a hairline rim. */
    Solid,
}

/** The [GlassRendering] for a surface that would sample [backdrop]. */
@Composable
fun glassRendering(backdrop: Backdrop?): GlassRendering = when {
    backdrop == null || LocalReducedTransparency.current -> GlassRendering.Solid
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> GlassRendering.Solid
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> GlassRendering.Lite
    else -> GlassRendering.Full
}

/**
 * Draws a Liquid Glass surface in [shape]: the [backdrop] is saturated, frosted and (for Chrome and Control) refracted
 * at the edges, then the fill and optional [tint] are painted, a specular rim that follows the device tilt
 * ([LocalGlassLight]) and a soft shadow are layered on top. Content drawn after this modifier is clipped to [shape].
 *
 * Everything that animates must be read inside [layerBlock] (or other draw lambdas), never in composition: the
 * modifier chain is remembered and only rebuilt when its inputs change.
 *
 * [exportedBackdrop] records the finished glass into a layer so glass nested inside can sample it.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    style: GlassStyle = GlassStyle.Material,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.background,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    tintMode: GlassTintMode = GlassTintMode.Wash,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val colors = LocalGlassColors.current
    val rendering = glassRendering(backdrop)
    val light = LocalGlassLight.current ?: FixedGlassLight
    val glass = remember(rendering, shape, style, tint, tintMode, colors, backdrop, layerBlock, exportedBackdrop, light) {
        if (rendering == GlassRendering.Solid || backdrop == null) {
            solidGlass(shape, colors, tint, tintMode, layerBlock)
        } else {
            backdropGlass(rendering, shape, style, colors, tint, tintMode, backdrop, layerBlock, exportedBackdrop, light)
        }
    }
    return this.then(glass)
}

private fun solidGlass(
    shape: Shape,
    colors: GlassColors,
    tint: Color,
    tintMode: GlassTintMode,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
): Modifier {
    val fill = when {
        tint.isSpecified && tintMode == GlassTintMode.Prominent -> tint.copy(alpha = 1f)
        else -> colors.solidFill
    }
    return Modifier
        .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
        .background(fill, shape)
        .then(if (tint.isSpecified && tintMode == GlassTintMode.Wash) Modifier.background(tint, shape) else Modifier)
        .border(GlassTokens.Hairline, colors.rim, shape)
        .clip(shape)
}

private fun backdropGlass(
    rendering: GlassRendering,
    shape: Shape,
    style: GlassStyle,
    colors: GlassColors,
    tint: Color,
    tintMode: GlassTintMode,
    backdrop: Backdrop,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    exportedBackdrop: LayerBackdrop?,
    light: GlassLight,
): Modifier {
    val lite = rendering == GlassRendering.Lite
    val blur = if (lite) liteBlur(style) else style.blur
    val baseFill = colors.fillFor(style.kind)
    val fill = if (lite) baseFill.copy(alpha = (baseFill.alpha + LiteFillBoost).coerceAtMost(1f)) else baseFill
    val prominent = tint.isSpecified && tintMode == GlassTintMode.Prominent
    val shadowColor = if (style.kind == GlassStyle.Kind.Material) {
        colors.shadow.copy(alpha = colors.shadow.alpha * 0.6f)
    } else {
        colors.shadow
    }
    return Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blur.toPx())
            if (style.hasLens) {
                lens(
                    style.lensHeight.toPx(),
                    style.lensAmount.toPx(),
                    depthEffect = style.depthEffect,
                    chromaticAberration = style.chromaticAberration,
                )
            }
        },
        highlight = when {
            lite -> {
                { Highlight(width = 0.5.dp, alpha = style.highlightAlpha, style = HighlightStyle.Plain) }
            }
            style.kind == GlassStyle.Kind.Material -> {
                { Highlight(width = 0.5.dp, alpha = style.highlightAlpha, style = MaterialRim) }
            }
            else -> {
                {
                    Highlight(
                        width = GlassTokens.Hairline,
                        alpha = style.highlightAlpha,
                        style = HighlightStyle.Default(angle = light.angle, falloff = SpecularFalloff),
                    )
                }
            }
        },
        shadow = if (style.shadowRadius > 0.dp) {
            { Shadow(radius = style.shadowRadius, color = shadowColor) }
        } else {
            null
        },
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = {
            if (prominent) {
                drawRect(tint.copy(alpha = 1f), blendMode = BlendMode.Hue)
                drawRect(tint)
            } else {
                drawRect(fill)
                if (tint.isSpecified) drawRect(tint)
            }
        },
    )
}

/**
 * Flat material for a control that sits inside another glass surface: no backdrop sampling (so no hole through the
 * parent frost), a quiet fill (or [tint]) and the hairline rim. Content drawn after it is clipped to [shape].
 */
@Composable
fun Modifier.glassInset(
    shape: Shape,
    tint: Color = Color.Unspecified,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val colors = LocalGlassColors.current
    val inset = remember(shape, tint, colors, layerBlock) {
        Modifier
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .background(if (tint.isSpecified) tint else colors.insetFill, shape)
            .border(GlassTokens.Hairline, colors.rim, shape)
            .clip(shape)
    }
    return this.then(inset)
}

/**
 * Scale that follows the press state of [interactionSource]. Kept for simple surfaces; interactive glass uses
 * [InteractiveHighlight] instead, which also lights up under the finger. Snaps when reduced motion is on.
 */
@Composable
fun rememberPressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = VbMotion.orSnap(LocalReducedMotion.current, VbMotion.press()),
        label = "glassPress",
    )
}

/** Android 12 has no lens: stronger frost keeps labels legible over busy content. */
private fun liteBlur(style: GlassStyle): Dp = when (style.kind) {
    GlassStyle.Kind.Chrome -> maxOf(style.blur, 16.dp)
    GlassStyle.Kind.Control -> maxOf(style.blur, 12.dp)
    GlassStyle.Kind.Material -> maxOf(style.blur, 28.dp)
}

private const val LiteFillBoost = 0.15f
private const val SpecularFalloff = 1.5f
private val MaterialRim = HighlightStyle.Ambient(intensity = 0.22f)
