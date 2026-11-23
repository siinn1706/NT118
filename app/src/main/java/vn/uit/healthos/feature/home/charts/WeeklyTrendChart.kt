package vn.uit.healthos.feature.home.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.feature.home.TrendDayPoint
import vn.uit.healthos.feature.home.TrendMetric

@Composable
fun WeeklyTrendChart(
    selectedMetric: TrendMetric,
    stepPoints: List<TrendDayPoint>,
    heartRatePoints: List<TrendDayPoint>,
    onSelectMetric: (TrendMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePoints = when (selectedMetric) {
        TrendMetric.STEPS -> stepPoints
        TrendMetric.HEART_RATE -> heartRatePoints
    }

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_trend_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMetric == TrendMetric.STEPS,
                onClick = { onSelectMetric(TrendMetric.STEPS) },
                label = { Text(stringResource(R.string.home_metric_steps)) },
            )
            FilterChip(
                selected = selectedMetric == TrendMetric.HEART_RATE,
                onClick = { onSelectMetric(TrendMetric.HEART_RATE) },
                label = { Text(stringResource(R.string.home_metric_hr)) },
            )
        }
        Spacer(Modifier.height(12.dp))

        if (activePoints.size < 2) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_trend_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val modelProducer = remember { CartesianChartModelProducer() }
            LaunchedEffect(activePoints) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(activePoints.map { it.value })
                    }
                }
            }
            val labels = remember(activePoints) { activePoints.map { it.label } }
            val bottomFormatter = remember(labels) {
                CartesianValueFormatter { _, x, _ ->
                    labels.getOrNull(x.toInt()) ?: ""
                }
            }
            ProvideVicoTheme(rememberM3VicoTheme()) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }
    }
}
