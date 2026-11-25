package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.theme.AccentPreset

/**
 * Row of round accent swatches (one per [AccentPreset], in the current light or dark tone) for appearance settings.
 * Each swatch is a 48dp radio-style target announced with the preset name; the selected one shows a check and a ring.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(
    selected: AccentPreset,
    onSelect: (AccentPreset) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = LocalGlassColors.current.base.luminance() < 0.5f
    val ring = MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccentPreset.entries.forEach { preset ->
            val colors = preset.colors(dark)
            val isSelected = preset == selected
            val name = stringResource(preset.label)
            Box(
                modifier = Modifier
                    .size(GlassTokens.MinTouchTarget)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(preset) },
                    )
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .then(if (isSelected) Modifier.border(2.dp, ring, Capsule()) else Modifier)
                        .padding(4.dp)
                        .glassInset(Capsule(), tint = colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
