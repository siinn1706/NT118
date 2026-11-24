package vn.uit.healthos.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.VbMotion
import vn.uit.healthos.core.designsystem.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

private val BarInnerPadding = 4.dp

/** Vertical space the floating tab bar occupies above the system navigation bar. */
val TabBarFootprint = GlassTokens.TabBarHeight + 16.dp

/**
 * Floating Liquid Glass tab bar in the iOS 26 style. The selection is a glass pill that springs between tabs; dragging
 * across the bar lifts the pill into a larger lens that follows the finger and settles on the nearest tab.
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

    val backdrop = LocalGlassBackdrops.current?.chrome
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = VbMotion.snappy(),
        label = "tabLift",
    )

    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            if (reducedMotion) {
                position.snapTo(selectedIndex.toFloat())
            } else {
                position.animateTo(selectedIndex.toFloat(), VbMotion.snappy())
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = GlassTokens.TabBarMargin, vertical = 8.dp)
            .height(GlassTokens.TabBarHeight),
    ) {
        val tabWidth = (maxWidth - BarInnerPadding * 2) / count
        val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }

        fun settle() {
            val target = position.value.roundToInt().coerceIn(0, count - 1)
            dragging = false
            scope.launch { position.animateTo(target.toFloat(), VbMotion.snappy()) }
            if (target != currentSelected) currentOnSelect(destinations[target])
        }

        // The bar itself.
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(
                    shape = Capsule(),
                    style = GlassStyle.Chrome,
                    backdrop = backdrop,
                    layerBlock = {
                        val s = 1f + 0.015f * lift
                        scaleX = s
                        scaleY = s
                    },
                ),
        )

        // Selection pill: a quiet tinted capsule at rest, a raised clear lens while dragging.
        Box(
            Modifier
                .padding(BarInnerPadding)
                .offset { IntOffset((position.value * tabWidthPx).roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .liquidGlass(
                    shape = Capsule(),
                    style = if (lift > 0.01f) GlassStyle.Control else GlassStyle.Control.copy(shadowRadius = 0.dp),
                    tint = scheme.primary.copy(alpha = 0.16f * (1f - lift)),
                    backdrop = backdrop,
                    layerBlock = {
                        scaleX = 1f + 0.22f * lift
                        scaleY = 1f + 0.30f * lift
                    },
                ),
        )

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = BarInnerPadding)
                .selectableGroup()
                .pointerInput(count, tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            position.snapTo((position.value + dragAmount / tabWidthPx).coerceIn(0f, (count - 1).toFloat()))
                        }
                    }
                },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                val proximity = (1f - abs(position.value - index)).coerceIn(0f, 1f)
                TabItem(
                    destination = destination,
                    selected = index == selectedIndex,
                    emphasis = proximity,
                    lift = lift,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    destination: TopLevelDestination,
    selected: Boolean,
    emphasis: Float,
    lift: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = lerp(scheme.onSurface.copy(alpha = 0.82f), scheme.primary, emphasis)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val s = 1f + 0.14f * lift * emphasis
                    scaleX = s
                    scaleY = s
                },
        )
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
