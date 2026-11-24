package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.rememberInteractiveHighlight
import vn.uit.healthos.core.designsystem.glass.rememberPressScale
import vn.uit.healthos.core.designsystem.theme.ProminentTintAlpha

private val ButtonMinHeight = 52.dp

/**
 * Prominent Liquid Glass capsule (iOS `.glassProminent`): the backdrop is hue-shifted to the tint and washed with it
 * at [vn.uit.healthos.core.designsystem.theme.ProminentTintAlpha], so the glass stays alive while the label keeps
 * 4.5:1 contrast. Lights up under the finger and swells with elastic press physics. Inside a glass card it draws as a
 * solid inset capsule instead (no glass-in-glass).
 *
 * [destructive] switches to the error colours. [tint] and [contentColor] override the colours (for example a
 * surface-coloured "I'm OK" button); leave them unspecified for the accent.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val baseTint = when {
        tint.isSpecified -> tint
        destructive -> scheme.error
        else -> scheme.primary
    }
    val baseContent = when {
        contentColor.isSpecified -> contentColor
        destructive -> scheme.onError
        else -> scheme.onPrimary
    }
    GlassButtonSurface(
        onClick = onClick,
        enabled = enabled && !loading,
        tint = if (enabled) baseTint.copy(alpha = ProminentTintAlpha) else scheme.onSurface.copy(alpha = 0.10f),
        tintMode = if (enabled) GlassTintMode.Prominent else GlassTintMode.Wash,
        contentColor = if (enabled) baseContent else scheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier,
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

/**
 * Clear Liquid Glass capsule with a tinted label (iOS `.glass`). [destructive] tints the label with the error colour;
 * [tint] washes the glass and [contentColor] overrides the label colour.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val label = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        contentColor.isSpecified -> contentColor
        destructive -> scheme.error
        else -> scheme.primary
    }
    GlassButtonSurface(
        onClick = onClick,
        enabled = enabled,
        tint = tint,
        tintMode = GlassTintMode.Wash,
        contentColor = label,
        modifier = modifier,
    ) {
        ButtonContent(text, loading = false, leadingIcon = leadingIcon)
    }
}

/**
 * Borderless text button (iOS plain style) for secondary actions inside content: tinted label, at least 48dp tall,
 * dims and shrinks slightly while pressed. [color] overrides the accent; [destructive] uses the error colour.
 * [contentPadding] is the horizontal padding around the label (0 to align the label with surrounding text).
 */
@Composable
fun PlainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
    color: Color = Color.Unspecified,
    contentPadding: Dp = 12.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val label = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        color.isSpecified -> color
        destructive -> scheme.error
        else -> scheme.primary
    }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, pressedScale = 0.94f)
    CompositionLocalProvider(LocalContentColor provides label) {
        Row(
            modifier = modifier
                .defaultMinSize(minWidth = GlassTokens.MinTouchTarget, minHeight = GlassTokens.MinTouchTarget)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - (1f - scale) * 6f).coerceIn(0f, 1f)
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = contentPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.titleSmall, color = label)
        }
    }
}

/**
 * Shared interactive glass capsule behind every glass button: Liquid Glass on the backdrop, or an inset capsule when
 * already inside glass ([LocalInsideGlass]); radial glow under the finger, bulge and elastic offset while pressed.
 */
@Composable
internal fun GlassButtonSurface(
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color,
    tintMode: GlassTintMode,
    contentColor: Color,
    modifier: Modifier,
    shape: Shape = Capsule(),
    style: GlassStyle = GlassStyle.Control,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.background,
    minHeight: Dp = ButtonMinHeight,
    contentPadding: Dp = 24.dp,
    verticalPadding: Dp = 14.dp,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val highlight = rememberInteractiveHighlight()
    val pressLayer = remember(highlight) { highlight.pressLayerBlock() }
    val surface = if (LocalInsideGlass.current) {
        val insetTint = if (tint.isSpecified && tintMode == GlassTintMode.Prominent) tint.copy(alpha = 1f) else tint
        Modifier.glassInset(shape, tint = insetTint, layerBlock = pressLayer)
    } else {
        Modifier.liquidGlass(shape, style, tint, backdrop, layerBlock = pressLayer, tintMode = tintMode)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = minHeight)
                .then(surface)
                .then(highlight.modifier)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick,
                )
                .then(if (enabled) highlight.gestureModifier else Modifier)
                .padding(horizontal = contentPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    when {
        loading -> GlassSpinner(size = 20.dp, color = LocalContentColor.current)
        leadingIcon != null -> Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        color = LocalContentColor.current,
        textAlign = TextAlign.Center,
    )
}
