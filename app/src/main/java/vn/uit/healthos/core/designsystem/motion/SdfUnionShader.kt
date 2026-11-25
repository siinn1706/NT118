package vn.uit.healthos.core.designsystem.motion

import android.graphics.BlurMaskFilter
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import org.intellij.lang.annotations.Language
import vn.uit.healthos.core.designsystem.glass.GlassRendering
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.LocalGlassLight
import vn.uit.healthos.core.designsystem.glass.glassRendering
import vn.uit.healthos.core.designsystem.glass.glassSurface
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Two capsules of glass merged with a smooth-min union, so they flow into each other through a liquid neck when they
 * are closer than about half of [blendRadius] (in pixels) and part like a drop when pulled apart. [first] and
 * [second] are the capsule bounds in this composable's local coordinates.
 *
 * On Android 13+ with full glass, one backdrop pass covers the whole box and an AGSL shader (ported from Kyant's
 * catalog SdfShader, with the texture replaced by the analytic union) masks, refracts, tints and lights it. The bounds
 * and blend are read inside the effect and draw lambdas, so moving the capsules redraws without recomposing.
 * On Android 10 to 12, or with reduced transparency, the two capsules and a pinched bridge whose width follows the
 * blend are combined into one path and drawn as ordinary glass; that path is rebuilt in composition.
 *
 * [content] is drawn on top, unclipped (icons or labels positioned over each capsule).
 */
@Composable
fun GlassMorphPair(
    first: () -> Rect,
    second: () -> Rect,
    blendRadius: () -> Float,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Chrome,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.background,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val rendering = glassRendering(backdrop)
    val glass = if (rendering == GlassRendering.Full && backdrop != null && SdfUnionShaderAvailable) {
        sdfUnionGlass(first, second, blendRadius, style, tint, backdrop)
    } else {
        val shape = UnionShape(first(), second(), blendRadius())
        Modifier.glassSurface(
            shape = shape,
            // The lens shader only understands rounded rectangles, so the merged path is frosted without it.
            style = style.copy(lensHeight = 0.dp, lensAmount = 0.dp),
            tint = tint,
            backdrop = backdrop,
            layerBlock = null,
            tintMode = GlassTintMode.Wash,
            exportedBackdrop = null,
            materialize = null,
        )
    }
    Box(modifier) {
        Box(Modifier.matchParentSize().then(glass))
        content()
    }
}

@Composable
private fun sdfUnionGlass(
    first: () -> Rect,
    second: () -> Rect,
    blendRadius: () -> Float,
    style: GlassStyle,
    tint: Color,
    backdrop: Backdrop,
): Modifier {
    val colors = LocalGlassColors.current
    val light = LocalGlassLight.current
    val baseFill = colors.fillFor(style.kind)
    val fill = if (tint.isSpecified) blendOver(baseFill, tint) else baseFill
    val shadowColor = colors.shadow
    return remember(first, second, blendRadius, style, fill, shadowColor, backdrop, light) {
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        Modifier
            .drawBehind {
                // A soft shadow under the merged body, from the same geometry as the fallback path.
                val radius = style.shadowRadius.toPx()
                if (radius <= 0f) return@drawBehind
                shadowPaint.color = shadowColor.toArgb()
                shadowPaint.maskFilter = BlurMaskFilter(radius / 2f, BlurMaskFilter.Blur.NORMAL)
                val path = unionPath(first(), second(), blendRadius())
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(0f, radius / 6f)
                    canvas.nativeCanvas.drawPath(path.asAndroidPath(), shadowPaint)
                    canvas.nativeCanvas.restore()
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur(style.blur.toPx())
                    sdfUnion(
                        first = first(),
                        second = second(),
                        blendRadius = blendRadius(),
                        refractionHeight = max(style.lensHeight.toPx(), MinRefractionHeight.toPx()),
                        refractionAmount = max(style.lensAmount.toPx(), MinRefractionAmount.toPx()),
                        fill = fill,
                        rimAlpha = style.highlightAlpha * RimStrength,
                        rimWidth = RimWidth.toPx(),
                        lightAngle = light?.angle ?: DefaultLightAngle,
                    )
                },
                highlight = null,
                shadow = null,
            )
    }
}

/**
 * Applies the smooth-union glass shader to a backdrop effect chain: content outside the union of [first] and
 * [second] (bounds in the drawing node's local pixels, as capsules) becomes transparent, the edges refract, [fill] is
 * laid over and a specular rim follows [lightAngle] (degrees). No-op below Android 13.
 */
fun BackdropEffectScope.sdfUnion(
    first: Rect,
    second: Rect,
    blendRadius: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    fill: Color,
    rimAlpha: Float,
    rimWidth: Float,
    lightAngle: Float,
) {
    val offset = -padding
    runtimeShaderEffect(SdfUnionShaderKey, SdfUnionShaderString, "content") {
        setFloatUniform("offset", offset, offset)
        setFloatUniform("rectA", first.left, first.top, first.right, first.bottom)
        setFloatUniform("rectB", second.left, second.top, second.right, second.bottom)
        setFloatUniform("radii", first.minDimension / 2f, second.minDimension / 2f)
        setFloatUniform("blend", blendRadius.coerceAtLeast(0f))
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", refractionAmount)
        setFloatUniform("fill", fill.red, fill.green, fill.blue, fill.alpha)
        setFloatUniform("rimAlpha", rimAlpha)
        setFloatUniform("rimWidth", rimWidth)
        setFloatUniform("lightAngle", lightAngle)
    }
}

/** Outline of the union of two capsules joined by a neck, for the pre-Android 13 path and the shadow. */
private class UnionShape(val first: Rect, val second: Rect, val blendRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(unionPath(first, second, blendRadius))

    override fun equals(other: Any?): Boolean =
        other is UnionShape && other.first == first && other.second == second && other.blendRadius == blendRadius

    override fun hashCode(): Int = (first.hashCode() * 31 + second.hashCode()) * 31 + blendRadius.hashCode()
}

/**
 * Path of two capsules plus, while they are within the smooth-min reach, a bridge between their centres that pinches
 * in the middle to [sdfNeckHalfWidth]. Approximates the SDF union where no runtime shader is available.
 */
internal fun unionPath(first: Rect, second: Rect, blendRadius: Float): Path {
    val a = Path().apply { addRoundRect(capsule(first)) }
    val b = Path().apply { addRoundRect(capsule(second)) }
    var union = Path.combine(PathOperation.Union, a, b)
    val neck = sdfNeckHalfWidth(first, second, blendRadius)
    if (neck > 0f) {
        val from = first.center
        val to = second.center
        val length = (to - from).getDistance()
        if (length > 0f) {
            val dir = (to - from) / length
            val normal = Offset(-dir.y, dir.x)
            val end = min(first.minDimension, second.minDimension) / 2f * BridgeEndFraction
            val mid = (from + to) / 2f
            // A quadratic through the midpoint at +-neck: control = 2 * midpoint - average of the ends.
            val control = 2f * neck - end
            val bridge = Path().apply {
                moveTo(from.x + normal.x * end, from.y + normal.y * end)
                quadraticTo(mid.x + normal.x * control, mid.y + normal.y * control, to.x + normal.x * end, to.y + normal.y * end)
                lineTo(to.x - normal.x * end, to.y - normal.y * end)
                quadraticTo(mid.x - normal.x * control, mid.y - normal.y * control, from.x - normal.x * end, from.y - normal.y * end)
                close()
            }
            union = Path.combine(PathOperation.Union, union, bridge)
        }
    }
    return union
}

/** Shortest distance between the edges of two rectangles, 0 when they touch or overlap. */
fun rectGap(first: Rect, second: Rect): Float {
    val dx = max(0f, max(first.left - second.right, second.left - first.right))
    val dy = max(0f, max(first.top - second.bottom, second.top - first.bottom))
    return hypot(dx, dy)
}

/**
 * Half width of the neck joining two capsules under a quadratic smooth-min of radius [blendRadius]: the shapes only
 * join while their gap is under half the radius, and the neck thins like a drop (square-root profile) until it
 * breaks. Equal to the narrower capsule's half thickness (times the bridge end fraction) when they touch.
 */
fun sdfNeckHalfWidth(first: Rect, second: Rect, blendRadius: Float): Float {
    if (blendRadius <= 0f) return 0f
    val reach = blendRadius / 2f
    val gap = rectGap(first, second)
    if (gap >= reach) return 0f
    val end = min(first.minDimension, second.minDimension) / 2f * BridgeEndFraction
    return end * sqrt(1f - gap / reach)
}

private fun capsule(rect: Rect): RoundRect = RoundRect(rect, CornerRadius(rect.minDimension / 2f))

private fun blendOver(base: Color, top: Color): Color {
    val a = top.alpha + base.alpha * (1f - top.alpha)
    if (a <= 0f) return Color.Transparent
    fun ch(b: Float, t: Float) = (t * top.alpha + b * base.alpha * (1f - top.alpha)) / a
    return Color(ch(base.red, top.red), ch(base.green, top.green), ch(base.blue, top.blue), a)
}

/**
 * True when the union shader compiled on this device (Android 13+). A driver that rejects it gets the path fallback
 * instead of a crash, and the reason is logged.
 */
private val SdfUnionShaderAvailable: Boolean by lazy {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && compileSdfUnionShader()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileSdfUnionShader(): Boolean = try {
    RuntimeShader(SdfUnionShaderString)
    true
} catch (e: IllegalArgumentException) {
    Log.e("SdfUnionShader", "SDF union shader rejected, using the path fallback", e)
    false
}

private const val SdfUnionShaderKey = "HealthOsSdfUnion"
private const val BridgeEndFraction = 0.9f
private const val RimStrength = 0.55f
private const val DefaultLightAngle = 45f
private val RimWidth = 1.5.dp
private val MinRefractionHeight = 12.dp
private val MinRefractionAmount = 12.dp

/**
 * Smooth-min union of two rounded boxes. Based on Kyant's catalog SdfShader (refraction from the SDF normal with a
 * circular edge profile, bevel lighting from the light direction), with the SDF computed analytically instead of
 * sampled from a texture.
 */
@Language("AGSL")
private const val SdfUnionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float4 rectA;
uniform float4 rectB;
uniform float2 radii;
uniform float blend;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float4 fill;
uniform float rimAlpha;
uniform float rimWidth;
uniform float lightAngle;

float sdRoundBox(float2 p, float4 r, float radius) {
    float2 c = (r.xy + r.zw) * 0.5;
    float2 h = (r.zw - r.xy) * 0.5;
    float2 q = abs(p - c) - h + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float smin(float a, float b, float k) {
    if (k <= 0.0) {
        return min(a, b);
    }
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float scene(float2 p) {
    return smin(sdRoundBox(p, rectA, radii.x), sdRoundBox(p, rectB, radii.y), blend);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = scene(p);
    if (sd > 1.0) {
        return half4(0.0);
    }
    float coverage = clamp(0.5 - sd, 0.0, 1.0);

    float2 grad = float2(
        scene(p + float2(1.0, 0.0)) - scene(p - float2(1.0, 0.0)),
        scene(p + float2(0.0, 1.0)) - scene(p - float2(0.0, 1.0))
    );
    float gl = length(grad);
    float2 normal = gl > 0.0001 ? grad / gl : float2(0.0);

    float depth = max(-sd, 0.0);
    float edge = refractionHeight > 0.0 ? 1.0 - min(1.0, depth / refractionHeight) : 0.0;
    float intensity = circleMap(edge);
    float2 refractedCoord = coord - intensity * refractionAmount * normal;

    half4 color = content.eval(refractedCoord);
    color.rgb = mix(color.rgb, half3(fill.rgb), half(fill.a));
    color.a = mix(color.a, 1.0, half(fill.a));

    float rad = lightAngle * 3.1415926 / 180.0;
    float2 lightDir = float2(cos(rad), sin(rad));
    float bevel = clamp(dot(normal, lightDir), 0.0, 1.0);
    color.rgb *= 1.0 + 0.5 * intensity * bevel;
    float rim = 1.0 - smoothstep(0.0, rimWidth, depth);
    float facing = abs(dot(normal, lightDir));
    color.rgb += half3(rimAlpha * rim * (0.3 + 0.7 * facing));
    color.rgb = min(color.rgb, half3(color.a));

    return color * coverage;
}
"""
