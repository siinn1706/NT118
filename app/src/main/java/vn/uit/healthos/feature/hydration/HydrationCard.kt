package vn.uit.healthos.feature.hydration

import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.GlassProgressBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.AdaptiveRow
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.theme.tabular

@Composable
fun HydrationCard(
    currentMl: Int,
    targetMl: Int,
    heatBonusMl: Int,
    onAddCup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeTarget = targetMl.coerceAtLeast(500)
    val progress = (currentMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)

    BentoCard(modifier = modifier.fillMaxWidth()) {
        // The add button stays beside the title while the title block keeps most of the width; with a large font
        // it moves under the title instead of squeezing it.
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.home_hydration_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (heatBonusMl > 0) {
                        Text(
                            text = stringResource(R.string.home_hydration_heat_bonus, heatBonusMl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            SecondaryButton(
                text = stringResource(R.string.home_hydration_add_250),
                onClick = onAddCup,
                leadingIcon = Icons.Rounded.Add,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.home_hydration_value, currentMl, safeTarget),
                style = MaterialTheme.typography.titleMedium.tabular(),
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        GlassProgressBar(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            height = 8.dp,
        )
    }
}
