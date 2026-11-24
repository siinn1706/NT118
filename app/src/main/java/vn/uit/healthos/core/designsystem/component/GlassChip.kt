package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.GlassTokens

/**
 * Capsule chip on glass. With [selected] = null it is an assist chip (a compact action button); with a Boolean it is
 * a filter chip that announces its selected state, shows a check mark when selected and is tinted with the accent.
 * Glass on the ambient background, inset inside glass cards; 48dp touch target, glow and press physics like buttons.
 */
@Composable
fun GlassChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val isSelected = selected == true
    val contentColor = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        isSelected -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    GlassButtonSurface(
        onClick = onClick,
        enabled = enabled,
        tint = if (isSelected) scheme.primaryContainer.copy(alpha = 0.85f) else Color.Unspecified,
        tintMode = GlassTintMode.Wash,
        contentColor = contentColor,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { if (selected != null) this.selected = selected },
        minHeight = GlassTokens.MinTouchTarget - 8.dp,
        contentPadding = 14.dp,
        verticalPadding = 8.dp,
        role = if (selected != null) Role.Checkbox else Role.Button,
    ) {
        val icon = if (isSelected) Icons.Rounded.Check else leadingIcon
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
