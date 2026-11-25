package vn.uit.healthos.core.designsystem.component

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import vn.uit.healthos.core.designsystem.theme.tabular

/**
 * Bento tile showing one metric: icon + label, large tabular value + unit, optional caption. The value sits under
 * the label in a narrow tile and at the end of the label line in a wide one.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val spoken = listOfNotNull(label, value, unit, caption).joinToString(" ")
    BentoCard(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        onClick = onClick,
    ) {
        // Label and value share a line when the tile is wide enough for both unbroken (a full-width tile); in a
        // narrow tile the value goes under the label. As one text, the unit wraps as a whole word with the value.
        val unitStyle = MaterialTheme.typography.bodyMedium
        val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
        AdaptiveRow(minFlexibleFraction = 1f, verticalSpacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        append(" ")
                        withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontWeight = unitStyle.fontWeight, color = unitColor)) {
                            append(unit)
                        }
                    }
                },
                style = MaterialTheme.typography.headlineMedium.tabular(),
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
