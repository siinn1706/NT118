package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.rememberPressScale

private val ButtonMinHeight = 52.dp

/** Prominent Liquid Glass capsule tinted with the brand colour (the equivalent of iOS `.glassProminent`). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val active = enabled && !loading
    GlassButton(
        onClick = onClick,
        enabled = active,
        tint = if (enabled) scheme.primary.copy(alpha = 0.94f) else scheme.onSurface.copy(alpha = 0.10f),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier,
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

/** Clear Liquid Glass capsule with tinted label (the equivalent of iOS `.glass`). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    GlassButton(
        onClick = onClick,
        enabled = enabled,
        tint = Color.Unspecified,
        contentColor = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier,
    ) {
        ButtonContent(text, loading = false, leadingIcon = leadingIcon)
    }
}

@Composable
private fun GlassButton(
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color,
    contentColor: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = Capsule()
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = ButtonMinHeight)
                .liquidGlass(
                    shape = shape,
                    style = GlassStyle.Control,
                    tint = tint,
                    layerBlock = {
                        scaleX = scale
                        scaleY = scale
                    },
                )
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
        leadingIcon != null -> Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        color = LocalContentColor.current,
        textAlign = TextAlign.Center,
    )
}
