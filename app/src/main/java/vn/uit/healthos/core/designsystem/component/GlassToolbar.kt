package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.rememberInteractiveHighlight

/**
 * 44dp circular Liquid Glass button for toolbar actions (back, close, more), in the Chrome style with a 48dp touch
 * target. Glows under the finger and swells elastically. Inside glass it draws as an inset circle.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.chrome,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = Capsule()
    val highlight = rememberInteractiveHighlight()
    val pressLayer = remember(highlight) { highlight.pressLayerBlock(bulge = 6.dp) }
    val surface = if (LocalInsideGlass.current) {
        Modifier.glassInset(shape, layerBlock = pressLayer)
    } else {
        Modifier.liquidGlass(shape = shape, style = GlassStyle.Chrome, backdrop = backdrop, layerBlock = pressLayer)
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(GlassTokens.NavButtonSize)
            .then(surface)
            .then(highlight.modifier)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) onSurface else onSurface.copy(alpha = 0.38f),
        ) {
            content()
        }
    }
}

/**
 * Grouped toolbar capsule (the iOS 26 GlassEffectContainer idiom): one Chrome glass surface samples the backdrop once
 * and holds several [GlassToolbarItem] slots, instead of a row of separate glass buttons.
 */
@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.chrome,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = Capsule()
    val surface = if (LocalInsideGlass.current) {
        Modifier.glassInset(shape)
    } else {
        Modifier.liquidGlass(shape = shape, style = GlassStyle.Chrome, backdrop = backdrop)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = modifier.then(surface).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** One 44dp icon slot inside a [GlassToolbar]: glows under the finger, icon dips slightly while pressed. */
@Composable
fun GlassToolbarItem(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val highlight = rememberInteractiveHighlight()
    val color = when {
        !enabled -> LocalContentColor.current.copy(alpha = 0.38f)
        tint.isSpecified -> tint
        else -> LocalContentColor.current
    }
    Box(
        modifier = modifier
            .size(GlassTokens.NavButtonSize)
            .clip(Capsule())
            .then(highlight.modifier)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = 1f - 0.08f * highlight.pressProgress
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

/**
 * Large title header for tab screens (iOS large navigation title): a 34sp bold title with an optional subtitle on the
 * start side and an optional grouped [GlassToolbar] of actions on the end side. Place it as the first item of the
 * screen's scrolling content so it scrolls away like on iOS.
 */
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (actions != null) GlassToolbar(content = actions)
    }
}
