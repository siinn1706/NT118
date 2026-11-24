package vn.uit.healthos.core.designsystem.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import vn.uit.healthos.core.designsystem.theme.VbMotion

/**
 * Root of the glass world: paints the ambient backdrop once, records it as a layer and exposes it through
 * [LocalGlassBackdrops]; provides the tilt-driven [LocalGlassLight]; hosts in-window overlays such as GlassDialog
 * ([LocalGlassOverlayHost]) above [content]. Screens draw on top with a transparent background.
 *
 * Roots can be nested (for example a screen previewing another theme): the aurora is laid out in window coordinates
 * so nested roots line up with their parent, and the parent's light sensor is reused.
 */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val background = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val backdrops = remember(background) { GlassBackdrops(background = background, chrome = background) }
    val parentLight = LocalGlassLight.current
    val light = parentLight ?: rememberGlassLight(enabled = !LocalReducedMotion.current)
    val host = remember { GlassOverlayHost() }
    val overlayBackdrop = rememberCombinedBackdrop(background, contentLayer)
    val overlayBackdrops = remember(overlayBackdrop) { GlassBackdrops(overlayBackdrop, overlayBackdrop) }

    Box(modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize().layerBackdrop(background))
        CompositionLocalProvider(
            LocalGlassBackdrops provides backdrops,
            LocalGlassLight provides light,
            LocalGlassOverlayHost provides host,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentLayer)
                    // A modal overlay hides everything behind it from accessibility services and keyboard focus, like
                    // a real dialog window.
                    .then(
                        if (host.hasModal) {
                            Modifier
                                .clearAndSetSemantics {}
                                .focusProperties { onEnter = { cancelFocusChange() } }
                                .focusGroup()
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }
            CompositionLocalProvider(LocalGlassBackdrops provides overlayBackdrops) {
                GlassOverlayLayer(host)
            }
        }
    }
}

/**
 * Static aurora of soft accent-coloured light behind every screen. It gives glass something to refract; it is kept
 * still on purpose so that frosted cards do not have to re-render every frame. Blobs are placed in window
 * coordinates, so the background is continuous across nested roots. With an empty aurora only the base is painted.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val windowInfo = LocalWindowInfo.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Spacer(
        modifier
            .onPlaced { origin = it.positionInWindow() }
            .drawWithCache {
                val window = windowInfo.containerSize
                val w = if (window.width > 0) window.width.toFloat() else size.width
                val h = if (window.height > 0) window.height.toFloat() else size.height
                val topLeft = origin
                val blobs = if (colors.aurora.isEmpty()) {
                    emptyList()
                } else {
                    AuroraLayout.mapIndexed { index, (fx, fy, fr) ->
                        val color = colors.aurora[index % colors.aurora.size]
                        val center = Offset(w * fx, h * fy)
                        val radius = w * fr
                        Triple(
                            Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
                            center,
                            radius,
                        )
                    }
                }
                onDrawBehind {
                    // Painted over the whole window (also outside this node when nested) so no seam shows.
                    translate(-topLeft.x, -topLeft.y) {
                        drawRect(colors.base, size = Size(w, h))
                        blobs.forEach { (brush, center, radius) ->
                            drawCircle(brush = brush, radius = radius, center = center)
                        }
                    }
                }
            },
    )
}

/** Blob centres and radii as fractions of the window (x, y of the centre, radius relative to the width). */
private val AuroraLayout = listOf(
    Triple(0.05f, 0.04f, 0.85f),
    Triple(1.0f, 0.34f, 0.75f),
    Triple(0.0f, 0.70f, 0.72f),
    Triple(0.95f, 1.0f, 0.80f),
)

/** Overlays (dialogs) drawn inside the nearest [GlassRoot], above its content, in the same window. */
val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/** Keeps the overlays shown by GlassDialog. One per [GlassRoot]. */
@Stable
class GlassOverlayHost internal constructor() {
    internal val entries = mutableStateListOf<GlassOverlayEntry>()

    /** True while at least one modal overlay is shown or entering. */
    val hasModal: Boolean get() = entries.any { it.visibility.targetState }

    internal fun show(entry: GlassOverlayEntry) {
        if (entry !in entries) entries.add(entry)
        entry.visibility.targetState = true
    }

    internal fun hide(entry: GlassOverlayEntry) {
        entry.visibility.targetState = false
    }
}

/** One overlay; [content] is refreshed on every recomposition of its owner and kept for the exit animation. */
internal class GlassOverlayEntry(content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val visibility = MutableTransitionState(false)
    var content by mutableStateOf(content)
}

@Composable
private fun GlassOverlayLayer(host: GlassOverlayHost) {
    val reducedMotion = LocalReducedMotion.current
    host.entries.forEach { entry ->
        key(entry) {
            AnimatedVisibility(
                visibleState = entry.visibility,
                enter = fadeIn(VbMotion.orSnap(reducedMotion, VbMotion.enter())),
                exit = fadeOut(VbMotion.orSnap(reducedMotion, VbMotion.exit())),
            ) {
                entry.content(this)
            }
            if (entry.visibility.isIdle && !entry.visibility.currentState && !entry.visibility.targetState) {
                LaunchedEffect(entry) { host.entries.remove(entry) }
            }
        }
    }
}
