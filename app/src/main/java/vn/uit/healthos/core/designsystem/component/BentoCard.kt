package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalContainerRadius
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.concentricRadius
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.rememberInteractiveHighlight

/**
 * Content card in the frosted glass material (no lens: Liquid Glass proper is reserved for chrome and controls), with
 * continuous 26dp corners. [containerColor] tints the material (for example primaryContainer for a highlighted card);
 * leave it unspecified for the neutral material. Clickable cards glow softly under the finger, without bulging.
 *
 * Inside, [LocalInsideGlass] is true (glass controls draw as insets) and [LocalContainerRadius] gives the concentric
 * radius for nested shapes.
 */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = GlassTokens.CardShape
    val tinted = containerColor.isSpecified
    val contentColor = if (tinted) {
        MaterialTheme.colorScheme.contentColorFor(containerColor).takeIf { it.isSpecified }
            ?: MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val highlight = rememberInteractiveHighlight(intensity = CardGlowIntensity)
    val glassTint = if (tinted) containerColor.copy(alpha = containerColor.alpha * 0.62f) else Color.Unspecified
    // A card inside another glass surface becomes an inset group instead of sampling the backdrop again.
    val surface = if (LocalInsideGlass.current) {
        Modifier.glassInset(shape, tint = glassTint)
    } else {
        Modifier.liquidGlass(shape = shape, style = GlassStyle.Material, tint = glassTint)
    }
    val inset = minOf(
        contentPadding.calculateTopPadding(),
        contentPadding.calculateStartPadding(LocalLayoutDirection.current),
    )

    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalInsideGlass provides true,
        LocalContainerRadius provides concentricRadius(GlassTokens.CardRadius, inset),
    ) {
        Column(
            modifier = modifier
                .then(surface)
                .then(
                    if (onClick != null) {
                        Modifier
                            .then(highlight.modifier)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick,
                            )
                            .then(highlight.gestureModifier)
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

private const val CardGlowIntensity = 0.55f
