package vn.uit.healthos.navigation

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.glass.DampedDragAnimation
import vn.uit.healthos.core.designsystem.glass.FixedGlassLight
import vn.uit.healthos.core.designsystem.glass.GlassColors
import vn.uit.healthos.core.designsystem.glass.GlassLight
import vn.uit.healthos.core.designsystem.glass.GlassRendering
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.InteractiveHighlight
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.LocalGlassLight
import vn.uit.healthos.core.designsystem.glass.glassRendering
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.sign

private val BarInnerPadding = 4.dp
private val LensHeight = GlassTokens.TabBarHeight - BarInnerPadding * 2

/** Vertical space the floating tab bar occupies above the system navigation bar. */
val TabBarFootprint = GlassTokens.TabBarHeight + 16.dp

/** Scale of the tab glyphs: 1 in the visible row, grows with the lens press inside the magnified copy. */
private val LocalTabGlyphScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Floating Liquid Glass tab bar in the iOS 26 style, ported from Kyant's LiquidBottomTabs.
 *
 * Layers, bottom to top: the Chrome glass bar holding the real tabs (semantics, clicks); an invisible copy of the
 * bar whose glyphs are tinted with the accent, recorded as a layer; and the selection lens, which samples that copy.
 * At rest the lens shows the accent glyphs of the selected tab on a quiet capsule. On touch down anywhere on the bar
 * the lens lifts (it grows, gains a specular rim, shadow and inner shadow, and refracts the magnified accent glyphs
 * with chromatic aberration); dragging moves it under the finger with velocity squash and stretch while the whole bar
 * rubber-bands a few dp along the drag, and releasing settles it on the nearest tab and selects it.
 *
 * Every animated value is read in layer and draw lambdas only, so the bar never recomposes during an animation, and
 * the modifier chains keep the same shape (alphas animate instead of nodes being added or removed). RTL aware.
 * Snaps instead of springing when reduced motion is on.
 */
@Composable
fun BottomBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = TopLevelDestination.entries
    val count = destinations.size
    val selectedIndex = destinations.indexOfFirst { currentDestination.isOn(it) }.coerceAtLeast(0)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val chromeBackdrop = LocalGlassBackdrops.current?.chrome
    val backdrop: Backdrop = chromeBackdrop ?: emptyBackdrop()
    val rendering = glassRendering(chromeBackdrop)
    val colors = LocalGlassColors.current
    val accent = MaterialTheme.colorScheme.primary
    val light = LocalGlassLight.current ?: FixedGlassLight
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val tabsLayer = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(backdrop, tabsLayer)

    val scope = rememberCoroutineScope()
    // Raw horizontal drag distance; the bar follows a few dp of it (rubber band), see [panelShift].
    val panelDrag = remember { Animatable(0f) }
    val animation = remember(scope, count) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(count - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = PressedLensScale,
            reducedMotion = { reduced.value },
            onDrag = { _, _ -> },
        )
    }
    val density = LocalDensity.current
    val highlight = remember(scope, animation, isLtr, count, density) {
        InteractiveHighlight(
            animationScope = scope,
            reducedMotion = { reduced.value },
            // The glow sits on the lens, not under the raw finger position.
            position = { size, _ ->
                with(density) {
                    val geometry = TabGeometry(size.width, BarInnerPadding.toPx(), count, isLtr)
                    Offset(
                        geometry.lensCenterX(animation.value) + panelShift(panelDrag.value, size.width),
                        size.height / 2f,
                    )
                }
            },
        )
    }

    // Selection made elsewhere (a tab click, back navigation): the lens travels there, lifting on the way.
    LaunchedEffect(animation, selectedIndex) {
        if (animation.targetValue.fastRoundToInt() != selectedIndex) animation.animateToValue(selectedIndex.toFloat())
    }

    // Both rows span the whole bar, so the bar width is their own width.
    val panelLayer: GraphicsLayerScope.() -> Unit = remember(panelDrag) {
        { translationX = panelShift(panelDrag.value, size.width) }
    }
    val barPressLayer: GraphicsLayerScope.() -> Unit = remember(animation) {
        {
            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f), animation.pressProgress)
            scaleX = scale
            scaleY = scale
        }
    }
    val copySurface = remember(backdrop, rendering, colors, animation) {
        tabCopySurface(backdrop, rendering, colors, animation)
    }
    val lens = remember(lensBackdrop, animation, isLtr, count, panelDrag, colors, light) {
        selectionLens(lensBackdrop, animation, isLtr, count, panelDrag, colors, light)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = GlassTokens.TabBarMargin, vertical = 8.dp)
            .height(GlassTokens.TabBarHeight)
            // Touch handling for the whole bar: lift on touch down, drag the lens, settle on release. The parent sees
            // events after the tabs, never consumes the down or a tap (so tab clicks and accessibility actions keep
            // working) and consumes a horizontal drag past the touch slop, which cancels the click under the finger.
            .pointerInput(animation, highlight, isLtr, count) {
                awaitEachGesture {
                    val geometry = TabGeometry(size.width.toFloat(), BarInnerPadding.toPx(), count, isLtr)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    animation.press()
                    highlight.press(down.position)
                    val touched = geometry.indexAt(down.position.x)
                    // Grabbing the lens keeps the finger where it held it; elsewhere the lens comes to the finger.
                    val grab = (animation.value - touched).takeIf { abs(it) < 0.5f } ?: 0f
                    var dragging = false
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                        val dx = change.positionChange().x
                        travelled += dx
                        if (!dragging && abs(travelled) > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            val target = (geometry.indexAt(change.position.x) + grab)
                                .fastCoerceIn(0f, (count - 1).toFloat())
                            animation.updateValue(target)
                            scope.launch { panelDrag.snapTo(panelDrag.value + dx) }
                        }
                    }
                    animation.release()
                    highlight.release()
                    scope.launch {
                        if (reduced.value) panelDrag.snapTo(0f) else panelDrag.animateTo(0f, PanelSpring)
                    }
                    if (dragging) {
                        val target = animation.targetValue.fastRoundToInt().coerceIn(0, count - 1)
                        animation.animateToValue(target.toFloat())
                        if (target != currentSelected) currentOnSelect(destinations[target])
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 1. The bar with the real, accessible tabs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer(panelLayer)
                .liquidGlass(
                    shape = Capsule(),
                    style = GlassStyle.Chrome,
                    backdrop = chromeBackdrop,
                    layerBlock = barPressLayer,
                )
                .then(highlight.modifier)
                .padding(BarInnerPadding)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                TabItem(
                    destination = destination,
                    selected = index == selectedIndex,
                    color = MaterialTheme.colorScheme.onSurface,
                    onClick = { onSelect(destination) },
                )
            }
        }

        // 2. Invisible copy of the bar with accent glyphs, only recorded for the lens to sample and magnify.
        CompositionLocalProvider(
            LocalTabGlyphScale provides { lerp(1f, MagnifiedGlyphScale, animation.pressProgress) },
        ) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsLayer)
                    .graphicsLayer(panelLayer)
                    .then(copySurface)
                    .height(LensHeight)
                    .fillMaxWidth()
                    .padding(horizontal = BarInnerPadding)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    TabItem(destination = destination, selected = index == selectedIndex, color = accent, onClick = null)
                }
            }
        }

        // 3. The selection lens, drawn last, samples the bar copy. It has no pointer input, so touches reach the tabs.
        Box(
            Modifier
                .padding(horizontal = BarInnerPadding)
                .then(lens)
                .height(LensHeight)
                .fillMaxWidth(1f / count),
        )
    }
}

/** Horizontal layout of the tabs inside a bar [barWidth] px wide with [inner] px of padding at each end. */
private class TabGeometry(val barWidth: Float, val inner: Float, val count: Int, val isLtr: Boolean) {
    val tabWidth: Float = ((barWidth - inner * 2f) / count).coerceAtLeast(1f)

    /** Fractional tab index whose centre is under [x] (bar coordinates). */
    fun indexAt(x: Float): Float {
        val fromStart = if (isLtr) x else barWidth - x
        return (fromStart - inner) / tabWidth - 0.5f
    }

    /** Centre of the lens in bar coordinates for a fractional [value]. */
    fun lensCenterX(value: Float): Float {
        val fromStart = inner + (value + 0.5f) * tabWidth
        return if (isLtr) fromStart else barWidth - fromStart
    }
}

/** The few dp the whole bar follows a drag, easing out towards the bar width. */
private fun Density.panelShift(drag: Float, barWidth: Float): Float {
    if (barWidth <= 0f) return 0f
    val fraction = (drag / barWidth).fastCoerceIn(-1f, 1f)
    return 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
}

/**
 * Surface of the invisible bar copy: the same frosted glass as the bar, whose own lens grows with the press so the
 * glyphs bend as they are magnified. Opaque fill when glass renders solid.
 */
private fun tabCopySurface(
    backdrop: Backdrop,
    rendering: GlassRendering,
    colors: GlassColors,
    animation: DampedDragAnimation,
): Modifier = if (rendering == GlassRendering.Solid) {
    Modifier.background(colors.solidFill, Capsule())
} else {
    Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
            val progress = animation.pressProgress
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
        },
        highlight = null,
        shadow = null,
        // Only the lens reads this copy, and only while pressed: at rest, sampling and blurring the whole screen into
        // an invisible layer cost a full extra screen draw on every frame.
        onDrawBackdrop = { drawBackdrop -> if (animation.pressProgress > 0f) drawBackdrop() },
        onDrawSurface = { drawRect(colors.chromeFill) },
    )
}

/**
 * The selection lens. Shadow, inner shadow and rim are always present and fade with the press, so the node chain never
 * changes shape mid-animation. The squash and stretch come from the drag velocity.
 */
private fun selectionLens(
    backdrop: Backdrop,
    animation: DampedDragAnimation,
    isLtr: Boolean,
    count: Int,
    panelDrag: Animatable<Float, *>,
    colors: GlassColors,
    light: GlassLight,
): Modifier {
    val darkGlass = colors.base.luminance() < 0.5f
    val restFill = if (darkGlass) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    return Modifier
        .graphicsLayer {
            // The lens box is laid out at the start edge and is exactly one tab wide.
            val tabWidth = size.width
            val barWidth = tabWidth * count + BarInnerPadding.toPx() * 2f
            val travel = animation.value * tabWidth
            translationX = (if (isLtr) travel else -travel) + panelShift(panelDrag.value, barWidth)
        }
        .drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                val progress = animation.pressProgress
                lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
            },
            highlight = {
                Highlight(
                    width = GlassTokens.Hairline,
                    alpha = animation.pressProgress,
                    style = HighlightStyle.Default(angle = light.angle, falloff = 1.5f),
                )
            },
            shadow = { Shadow(radius = 12.dp, color = colors.shadow, alpha = animation.pressProgress) },
            innerShadow = {
                val progress = animation.pressProgress
                InnerShadow(radius = 8.dp * progress, alpha = progress)
            },
            layerBlock = {
                val velocity = animation.velocity / 10f
                scaleX = animation.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                scaleY = animation.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
            },
            // At rest the lens is a flat tinted pill: skip drawing (and re-rendering) the sampled backdrop until pressed.
            onDrawBackdrop = { drawBackdrop -> if (animation.pressProgress > 0f) drawBackdrop() },
            onDrawSurface = {
                val progress = animation.pressProgress
                drawRect(restFill, alpha = 1f - progress)
                drawRect(Color.Black.copy(alpha = 0.03f * progress))
            },
        )
}

@Composable
private fun RowScope.TabItem(
    destination: TopLevelDestination,
    selected: Boolean,
    color: Color,
    onClick: (() -> Unit)?,
) {
    val glyphScale = LocalTabGlyphScale.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val scale = glyphScale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        // Shrinks instead of truncating when the user's font size is large (Dynamic Type style).
        BasicText(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}

fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Lens size while pressed relative to its resting size (78dp over 56dp, as in Kyant's catalog). */
private const val PressedLensScale = 78f / 56f
private const val MagnifiedGlyphScale = 1.2f
private val PanelSpring = spring(1f, 300f, 0.5f)
