package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalContainerRadius
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.concentricRadius
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import com.kyant.shapes.RoundedRectangle
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp

/** Meaning of a [GlassBanner]: picks the icon, the tint of the glass and how urgently TalkBack announces it. */
enum class GlassBannerTone { Info, Success, Warning, Error }

/**
 * Inline message on glass (replaces coloured Material Surfaces used as banners): a tone icon, an optional bold
 * [title], the [message], an optional [actionLabel] button and an optional dismiss button. The frosted material is
 * washed with the tone colour; text stays onSurface so it keeps full contrast. Inside a glass card it becomes an inset
 * with a concentric radius.
 *
 * The banner is a polite live region (assertive for [GlassBannerTone.Error]), so TalkBack reads it when it appears or
 * its text changes.
 */
@Composable
fun GlassBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: GlassBannerTone = GlassBannerTone.Info,
    title: String? = null,
    icon: ImageVector = tone.defaultIcon,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val dark = LocalGlassColors.current.base.luminance() < 0.5f
    val palette = bannerPalette(tone, dark)
    val inside = LocalInsideGlass.current
    val containerRadius = LocalContainerRadius.current
    val radius = if (inside && containerRadius != Dp.Unspecified) containerRadius else BannerRadius
    val shape = remember(radius) { RoundedRectangle(radius) }
    val surface = if (inside) {
        Modifier.glassInset(shape, tint = palette.tint)
    } else {
        Modifier.liquidGlass(shape, GlassStyle.Material, tint = palette.tint)
    }
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalInsideGlass provides true,
        LocalContainerRadius provides concentricRadius(radius, 12.dp),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(surface)
                .semantics(mergeDescendants = true) {
                    liveRegion = if (tone == GlassBannerTone.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
                }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss != null) 4.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = palette.icon, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null && onAction != null) {
                    PlainButton(
                        text = actionLabel,
                        onClick = onAction,
                        color = palette.action,
                        modifier = Modifier.padding(top = 2.dp),
                        contentPadding = 0.dp,
                    )
                }
            }
            if (onDismiss != null) {
                GlassIconButton(onClick = onDismiss, contentDescription = stringResource(R.string.action_close)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val BannerRadius = GlassTokens.ControlRadius

private val GlassBannerTone.defaultIcon: ImageVector
    get() = when (this) {
        GlassBannerTone.Info -> Icons.Rounded.Info
        GlassBannerTone.Success -> Icons.Rounded.CheckCircle
        GlassBannerTone.Warning -> Icons.Rounded.WarningAmber
        GlassBannerTone.Error -> Icons.Rounded.ErrorOutline
    }

private class BannerPalette(val icon: Color, val tint: Color, val action: Color)

@Composable
private fun bannerPalette(tone: GlassBannerTone, dark: Boolean): BannerPalette {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        GlassBannerTone.Info -> BannerPalette(scheme.primary, scheme.primaryContainer.copy(alpha = 0.45f), scheme.primary)
        GlassBannerTone.Success -> {
            // Text-safe greens (>= 4.5:1 on the light and dark materials).
            val green = if (dark) Color(0xFF7EE2A0) else Color(0xFF1B7A3D)
            BannerPalette(green, green.copy(alpha = if (dark) 0.16f else 0.12f), green)
        }
        GlassBannerTone.Warning -> {
            val amber = if (dark) Color(0xFFFCD34D) else Color(0xFF9A4A00)
            BannerPalette(amber, amber.copy(alpha = if (dark) 0.16f else 0.12f), amber)
        }
        GlassBannerTone.Error -> BannerPalette(scheme.error, scheme.errorContainer.copy(alpha = 0.5f), scheme.error)
    }
}
