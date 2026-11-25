package vn.uit.healthos.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import vn.uit.healthos.R
import vn.uit.healthos.feature.home.TodayMetrics
import vn.uit.healthos.feature.home.VitalLevel
import vn.uit.healthos.feature.home.vitalLevelOf
import kotlin.math.roundToInt

/** Vital Score ring (2x2); widened to 3x2 it adds step, water and calorie bars. */
class VitalScoreWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = widgetEntryPoint(context).todaySnapshotRepository()
        val initial = repository.today()
        provideContent {
            val flow = remember { repository.observeToday() }
            val content by flow.collectAsState(initial)
            when (val current = content) {
                is WidgetContent.Visible -> VitalScoreContent(current.value, wide = LocalSize.current.width >= WIDE.width)
                else -> PrivacyNotice(current, compact = false)
            }
        }
    }

    companion object {
        val SQUARE = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(180.dp, 110.dp)
    }
}

class VitalScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VitalScoreWidget()
}

@Composable
private fun VitalScoreContent(metrics: TodayMetrics, wide: Boolean) {
    GlassPanel(onClick = openAppAction(WidgetIntents.Destination.HOME)) {
        if (wide) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScoreColumn(metrics)
                Spacer(GlanceModifier.width(14.dp))
                Column(modifier = GlanceModifier.defaultWeight()) { MetricBars(metrics) }
            }
        } else {
            ScoreColumn(metrics)
        }
    }
}

@Composable
private fun ScoreColumn(metrics: TodayMetrics) {
    val context = LocalContext.current
    val score = metrics.vitalScore
    val level = score?.let(::vitalLevelOf)
    val percent = score?.let { (it * 100f).roundToInt() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WidgetHeader(R.drawable.widget_ic_heart, context.getString(R.string.widget_vital_name))
        Spacer(GlanceModifier.height(6.dp))
        ProgressRing(
            progress = score ?: 0f,
            ringColor = WidgetColors.ringColorFor(level),
            size = 64.dp,
            contentDescription = percent?.let { context.getString(R.string.widget_vital_score_cd, it) },
        ) {
            ValueText(percent?.toString() ?: "--", fontSize = 20.sp)
        }
        Spacer(GlanceModifier.height(4.dp))
        CaptionText(
            text = context.getString(
                when (level) {
                    VitalLevel.GOOD -> R.string.widget_vital_good
                    VitalLevel.WATCH -> R.string.widget_vital_watch
                    VitalLevel.ALERT -> R.string.widget_vital_alert
                    null -> R.string.widget_vital_empty
                },
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun MetricBars(metrics: TodayMetrics) {
    val context = LocalContext.current
    MetricBar(
        label = context.getString(R.string.widget_metric_steps),
        value = formatCount(metrics.stepsToday),
        progress = progressOf(metrics.stepsToday.toDouble(), metrics.stepGoal.toDouble()),
        color = WidgetColors.accent,
    )
    Spacer(GlanceModifier.height(8.dp))
    MetricBar(
        label = context.getString(R.string.widget_metric_water),
        value = "${formatCount(metrics.hydrationMl)} ml",
        progress = progressOf(metrics.hydrationMl.toDouble(), metrics.hydrationTargetMl.toDouble()),
        color = WidgetColors.accent,
    )
    Spacer(GlanceModifier.height(8.dp))
    MetricBar(
        label = context.getString(R.string.widget_metric_calories),
        value = metrics.caloriesIn?.let { "${formatCount(it.roundToInt())} kcal" } ?: "--",
        progress = progressOf(metrics.caloriesIn ?: 0.0, (metrics.calorieTarget ?: 0).toDouble()),
        color = WidgetColors.energy,
    )
}

@Composable
private fun MetricBar(label: String, value: String, progress: Float, color: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CaptionText(label)
        Spacer(GlanceModifier.defaultWeight())
        CaptionText(value, color = WidgetColors.onPanel)
    }
    Spacer(GlanceModifier.height(3.dp))
    ProgressBar(progress, color)
}
