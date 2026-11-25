package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.rememberInteractiveHighlight

private val RowMinHeight = 56.dp
private val IconBadgeSize = 30.dp
private val IconBadgeShape = RoundedRectangle(8.dp)

/**
 * Settings-style list row (iOS inset grouped list), meant to sit in a BentoCard with
 * `contentPadding = PaddingValues(vertical = 4.dp)`: an optional leading icon (a white glyph on a coloured squircle
 * when [iconBackground] is given, otherwise a tinted glyph), [title], optional [subtitle], then either a [value] text,
 * a custom [trailing] slot (a GlassSwitch, for example) or nothing, and a chevron when the row navigates.
 *
 * Clickable rows ([onClick]) glow softly under the finger, announce themselves as buttons and are at least 56dp tall;
 * [showChevron] defaults to true for them. [divider] draws an inset hairline under the row for grouped lists.
 * A row whose [trailing] is a toggle should pass `onClick = null` and let the toggle own the action, or pass the toggle
 * action as [onClick], its state as [toggled] and a read-only trailing control: the row is then announced as a switch
 * with its on/off state, in one focus stop.
 */
@Composable
fun GlassListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    iconBackground: Color = Color.Unspecified,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    toggled: Boolean? = null,
    showChevron: Boolean = onClick != null && toggled == null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    divider: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val highlight = rememberInteractiveHighlight(intensity = 0.7f)
    val dividerColor = LocalGlassColors.current.rim
    val titleColor = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        destructive -> scheme.error
        else -> scheme.onSurface
    }
    val textStart = 16.dp + if (leadingIcon != null) IconBadgeSize + 14.dp else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .then(highlight.modifier)
                        .then(
                            if (toggled != null) {
                                Modifier.toggleable(
                                    value = toggled,
                                    interactionSource = null,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Switch,
                                    onValueChange = { onClick() },
                                )
                            } else {
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = onClick,
                                )
                            },
                        )
                        .then(if (enabled) highlight.gestureModifier else Modifier)
                } else {
                    Modifier
                },
            )
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val y = size.height - GlassTokens.Hairline.toPx() / 2f
                        val inset = textStart.toPx()
                        val (start, end) = if (layoutDirection == LayoutDirection.Ltr) {
                            inset to size.width
                        } else {
                            0f to size.width - inset
                        }
                        drawLine(dividerColor, Offset(start, y), Offset(end, y), strokeWidth = GlassTokens.Hairline.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingIcon != null) {
            if (iconBackground.isSpecified) {
                Box(
                    Modifier
                        .size(IconBadgeSize)
                        .glassInset(IconBadgeShape, tint = iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            } else {
                Box(Modifier.size(IconBadgeSize), contentAlignment = Alignment.Center) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = if (iconTint.isSpecified) iconTint else scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
