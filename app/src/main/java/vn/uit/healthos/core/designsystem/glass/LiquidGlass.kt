package vn.uit.healthos.core.designsystem.glass

import com.kyant.shapes.Capsule
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Paint
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
import com.kyant.backdrop.effects.colorControls
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
): Modifier = glassSurface(shape, style, tint, backdrop, layerBlock, tintMode, exportedBackdrop, materialize = null)

/**
 * [liquidGlass] with an optional [materialize] provider (0 = absent, 1 = fully present). The provider is only read in
 * effect, draw and layer lambdas, so animating it redraws the surface without recomposing.
 */
@Composable
internal fun Modifier.glassSurface(
    shape: Shape,
    style: GlassStyle,
    tint: Color,
    backdrop: Backdrop?,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    tintMode: GlassTintMode,
    exportedBackdrop: LayerBackdrop?,
    materialize: (() -> Float)?,
): Modifier {
    val colors = LocalGlassColors.current
    val rendering = glassRendering(backdrop)
    val light = LocalGlassLight.current ?: FixedGlassLight
    // Glass that can only see the static ambient backdrop (controls placed straight on the background) refracts a soft
    // gradient, which is invisible, yet re-sampled and re-rendered it on every scroll frame. Only glass over real
    // content (chrome sampling the screens, morphing surfaces) samples live.
    val seesOnlyAmbient = backdrop != null && backdrop === LocalGlassBackdrops.current?.background
    val glass = remember(
        rendering, shape, style, tint, tintMode, colors, backdrop, layerBlock, exportedBackdrop, light, materialize,
        seesOnlyAmbient,
    ) {
        if (rendering == GlassRendering.Solid || backdrop == null) {
            solidGlass(shape, colors, tint, tintMode, layerBlock, materialize)
        } else if (
            (style.kind == GlassStyle.Kind.Material || seesOnlyAmbient) && materialize == null && exportedBackdrop == null
        ) {
            contentMaterial(shape, colors, colors.fillFor(style.kind), tint, tintMode, layerBlock)
        } else {
            backdropGlass(
                rendering, shape, style, colors, tint, tintMode, backdrop, layerBlock, exportedBackdrop, light, materialize,
            )
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
    materialize: (() -> Float)?,
): Modifier {
    val fill = when {
        tint.isSpecified && tintMode == GlassTintMode.Prominent -> tint.copy(alpha = 1f)
        else -> colors.solidFill
    }
    return Modifier
        .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
        // A solid surface has no refraction to ramp, so it can only fade along the same density curve.
        .then(if (materialize != null) Modifier.graphicsLayer { alpha = glassDensity(materialize()) } else Modifier)
        .background(fill, shape)
        .then(if (tint.isSpecified && tintMode == GlassTintMode.Wash) Modifier.background(tint, shape) else Modifier)
        .border(GlassTokens.Hairline, colors.rim, shape)
        .clip(shape)
}

/**
 * Content-layer material (cards, banners, bubbles): a translucent fill with a soft top sheen, a hairline rim and a
 * hardware shadow, without sampling the backdrop. HIG reserves Liquid Glass proper for the navigation layer, and the
 * ambient backdrop behind content is already a soft gradient, so a live blur there looked the same but re-rendered
 * every card on every scroll frame (about 13 ms of render-thread work per frame on a Galaxy S21).
 */
private fun contentMaterial(
    shape: Shape,
    colors: GlassColors,
    baseFill: Color,
    tint: Color,
    tintMode: GlassTintMode,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
): Modifier {
    // Continuous (squircle) corners are a generic Path outline: shadow, background and clip along an arbitrary path are
    // re-rasterised on the render thread every frame. A plain rounded rect is hardware fast and visually identical at
    // card radii.
    @Suppress("NAME_SHADOWING")
    val shape = fastOutlineShape(shape)
    val fill = baseFill.copy(alpha = (baseFill.alpha + ContentMaterialFillBoost).coerceAtMost(1f))
    val prominent = tint.isSpecified && tintMode == GlassTintMode.Prominent
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = ContentMaterialSheen),
        0.35f to Color.White.copy(alpha = 0f),
    )
    return Modifier
        .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
        .shadow(
            elevation = ContentMaterialElevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadow,
            spotColor = colors.shadow,
        )
        .background(if (prominent) tint else fill, shape)
        .then(if (tint.isSpecified && !prominent) Modifier.background(tint, shape) else Modifier)
        .background(sheen, shape)
        .border(GlassTokens.Hairline, colors.rim, shape)
        .clip(shape)
}

private fun fastOutlineShape(shape: Shape): Shape = when (shape) {
    is RoundedRectangle -> RoundedCornerShape(shape.cornerRadius)
    is Capsule -> RoundedCornerShape(percent = 50)
    else -> shape
}

private val ContentMaterialElevation = 6.dp
private const val ContentMaterialFillBoost = 0.08f
private const val ContentMaterialSheen = 0.10f

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
    materialize: (() -> Float)?,
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
            // Density stays 1 without a materialize provider, which keeps the static surface unchanged.
            val density = densityOf(materialize)
            if (density >= 1f) vibrancy() else colorControls(saturation = 1f + (VibrantSaturation - 1f) * density)
            blur(blur.toPx() * density)
            if (style.hasLens && density > 0f) {
                lens(
                    style.lensHeight.toPx() * density,
                    style.lensAmount.toPx() * density,
                    depthEffect = style.depthEffect,
                    chromaticAberration = style.chromaticAberration,
                )
            }
        },
        highlight = when {
            lite -> {
                {
                    Highlight(
                        width = 0.5.dp,
                        alpha = style.highlightAlpha * densityOf(materialize),
                        style = HighlightStyle.Plain,
                    )
                }
            }
            style.kind == GlassStyle.Kind.Material -> {
                {
                    Highlight(
                        width = 0.5.dp,
                        alpha = style.highlightAlpha * densityOf(materialize),
                        style = MaterialRim,
                    )
                }
            }
            else -> {
                {
                    Highlight(
                        width = GlassTokens.Hairline,
                        alpha = style.highlightAlpha * densityOf(materialize),
                        style = HighlightStyle.Default(angle = light.angle, falloff = SpecularFalloff),
                    )
                }
            }
        },
        shadow = if (style.shadowRadius > 0.dp) {
            { Shadow(radius = style.shadowRadius, color = shadowColor, alpha = densityOf(materialize)) }
        } else {
            null
        },
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawBackdrop = { drawBackdrop ->
            // While dissolving, the refracted sample gives way to what is really behind the surface; the surface and
            // its content are not faded as a whole.
            val density = densityOf(materialize)
            when {
                density >= 1f -> drawBackdrop()
                density > 0f -> {
                    val canvas = drawContext.canvas
                    canvas.saveLayer(
                        Rect(-size.width, -size.height, size.width * 2f, size.height * 2f),
                        Paint().apply { alpha = density },
                    )
                    drawBackdrop()
                    canvas.restore()
                }
            }
        },
        onDrawSurface = {
            val density = densityOf(materialize)
            if (prominent) {
                drawRect(tint.copy(alpha = density), blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = tint.alpha * density))
            } else {
                drawRect(fill.copy(alpha = fill.alpha * density))
                if (tint.isSpecified) drawRect(tint.copy(alpha = tint.alpha * density))
            }
        },
    )
}

private fun densityOf(materialize: (() -> Float)?): Float = if (materialize != null) glassDensity(materialize()) else 1f

/**
 * How dense a glass surface is at materialize [progress] (0 = absent, 1 = present): fully dense down to
 * [MaterializeDenseFrom], then it dissolves along a smoothstep to nothing at 0. Refraction, frost, rim, fill and
 * shadow all scale with it, instead of the surface fading by alpha.
 */
fun glassDensity(progress: Float): Float {
    if (progress >= MaterializeDenseFrom) return 1f
    if (progress <= 0f) return 0f
    val x = progress / MaterializeDenseFrom
    return x * x * (3f - 2f * x)
}

/** Materialize progress below which glass starts to dissolve (the last quarter of the way out). */
const val MaterializeDenseFrom = 0.25f

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
private const val VibrantSaturation = 1.5f
private const val SpecularFalloff = 1.5f
private val MaterialRim = HighlightStyle.Ambient(intensity = 0.22f)
