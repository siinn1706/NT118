package vn.uit.healthos.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * Thumb of a liquid selector (switch, slider, segmented control): a solid [restColor] capsule at rest that turns into
 * a clear, chromatic lens over [backdrop] while pressed, squashing and stretching with the drag velocity of
 * [animation]. Every animated value is read inside draw and layer lambdas; the modifier chain never changes shape
 * during the animation.
 *
 * With [restBehind] the rest colour is painted under the sampled backdrop instead of over it, so content recorded in
 * the backdrop (the selected label of a segmented control) stays visible on the resting thumb.
 *
 * When glass renders solid (Android 10/11, reduced transparency) the thumb stays an opaque capsule that only scales;
 * a [restBehind] thumb keeps drawing its backdrop on top of the opaque fill, without any lens.
 */
@Composable
internal fun Modifier.liquidThumb(
    animation: DampedDragAnimation,
    backdrop: Backdrop,
    restColor: Color,
    lensHeight: Dp = 10.dp,
    lensAmount: Dp = 14.dp,
    velocityDivisor: Float = 10f,
    restBehind: Boolean = false,
    restBlur: Dp = 8.dp,
): Modifier {
    val rendering = glassRendering(backdrop)
    val colors = LocalGlassColors.current
    val thumb = remember(animation, backdrop, restColor, rendering, colors, lensHeight, lensAmount, velocityDivisor, restBehind, restBlur) {
        val shape = Capsule()
        val solid = rendering == GlassRendering.Solid
        if (solid && !restBehind) {
            Modifier
                .graphicsLayer {
                    scaleX = animation.scaleX
                    scaleY = animation.scaleY
                }
                .shadow(2.dp, shape)
                .background(restColor, shape)
                .border(GlassTokens.Hairline, colors.rim, shape)
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (!solid) {
                        val progress = animation.pressProgress
                        blur(restBlur.toPx() * (1f - progress))
                        lens(lensHeight.toPx() * progress, lensAmount.toPx() * progress, chromaticAberration = true)
                    }
                },
                highlight = {
                    val progress = if (solid) 0f else animation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = progress,
                    )
                },
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.10f)) },
                innerShadow = {
                    val progress = if (solid) 0f else animation.pressProgress
                    InnerShadow(radius = 4.dp * progress, alpha = progress)
                },
                layerBlock = {
                    val velocity = if (solid) 0f else animation.velocity / velocityDivisor
                    scaleX = animation.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                    scaleY = animation.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
                },
                onDrawBehind = if (restBehind) {
                    { drawRect(restColor, alpha = if (solid) 1f else 1f - animation.pressProgress) }
                } else {
                    null
                },
                onDrawSurface = if (restBehind) {
                    null
                } else {
                    { drawRect(restColor, alpha = 1f - animation.pressProgress) }
                },
            )
        }
    }
    return this.then(thumb)
}

/**
 * Draws [draw] (a backdrop) faded by [alpha]; nothing at all while the alpha is zero. Used so a thumb only shows the
 * glass behind it while pressed. Pass a remembered [paint].
 */
internal fun DrawScope.drawFaded(alpha: Float, paint: Paint, draw: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 1f) {
        draw()
        return
    }
    paint.alpha = alpha
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
    draw()
    canvas.restore()
}
