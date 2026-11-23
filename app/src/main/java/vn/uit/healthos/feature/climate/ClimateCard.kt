package vn.uit.healthos.feature.climate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun ClimateCard(
    conditions: ClimateConditions,
    advice: ClimateAdvice,
    modifier: Modifier = Modifier,
) {
    val timeText = Instant.ofEpochMilli(conditions.fetchedAtMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = LocalVitalColors.current.energy,
                    modifier = Modifier.size(22.dp),
                )
                Text(stringResource(R.string.climate_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = stringResource(R.string.climate_source_time, timeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ClimateStat(
                label = stringResource(R.string.climate_temp_label),
                value = "${conditions.temperatureC.roundToInt()}°C",
                sub = stringResource(R.string.climate_feels_like, conditions.apparentTempC.roundToInt()),
            )
            ClimateStat(
                label = stringResource(R.string.climate_uv_label),
                value = conditions.uvIndex?.let { String.format(Locale.US, "%.1f", it) } ?: NO_READING,
                sub = null,
            )
            ClimateStat(
                label = stringResource(R.string.climate_aqi_label),
                value = conditions.usAqi?.toString() ?: NO_READING,
                sub = conditions.pm25?.let { "PM2.5 ${it.roundToInt()}" },
            )
        }

        if (advice.outdoorWarning != OutdoorWarning.NONE) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = outdoorWarningText(advice.outdoorWarning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Air,
                    contentDescription = null,
                    tint = LocalVitalColors.current.good,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.climate_good_outdoor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClimateStat(label: String, value: String, sub: String?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleLarge.tabular())
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun outdoorWarningText(warning: OutdoorWarning): String = when (warning) {
    OutdoorWarning.HIGH_AQI -> stringResource(R.string.climate_warn_aqi)
    OutdoorWarning.HIGH_UV -> stringResource(R.string.climate_warn_uv)
    OutdoorWarning.HIGH_AQI_AND_UV -> stringResource(R.string.climate_warn_aqi_uv)
    OutdoorWarning.NONE -> ""
}

private const val NO_READING = "—"
