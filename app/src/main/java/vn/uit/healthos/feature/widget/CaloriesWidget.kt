package vn.uit.healthos.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import vn.uit.healthos.R
import vn.uit.healthos.feature.home.TodayMetrics
import kotlin.math.abs
import kotlin.math.roundToInt

/** Calories eaten today against the target (2x2), with a shortcut to photograph a meal. */
class CaloriesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = widgetEntryPoint(context).todaySnapshotRepository()
        val initial = repository.today()
        provideContent {
            val flow = remember { repository.observeToday() }
            val content by flow.collectAsState(initial)
            when (val current = content) {
                is WidgetContent.Visible -> CaloriesContent(current.value)
                else -> PrivacyNotice(current, compact = false)
            }
        }
    }
}

class CaloriesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaloriesWidget()
}

@Composable
private fun CaloriesContent(metrics: TodayMetrics) {
    val context = LocalContext.current
    val eaten = metrics.caloriesIn?.roundToInt()
    val target = metrics.calorieTarget
    val balance = when {
        target == null -> context.getString(R.string.widget_calories_no_target)
        eaten == null -> context.getString(R.string.widget_calories_target, formatCount(target))
        eaten <= target -> context.getString(R.string.widget_calories_remaining, formatCount(target - eaten))
        else -> context.getString(R.string.widget_calories_over, formatCount(abs(eaten - target)))
    }
    GlassPanel(onClick = openAppAction(WidgetIntents.Destination.HOME)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetHeader(R.drawable.widget_ic_flame, context.getString(R.string.widget_calories_name), WidgetColors.energy)
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = progressOf((eaten ?: 0).toDouble(), (target ?: 0).toDouble()),
                    ringColor = WidgetColors.RING_ENERGY,
                    size = 56.dp,
                    contentDescription = null,
                ) {
                    ValueText(eaten?.let(::formatCount) ?: "--", fontSize = 13.sp)
                    CaptionText(context.getString(R.string.widget_calories_unit))
                }
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    CaptionText(balance, maxLines = 2, color = WidgetColors.onPanel)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            PillButton(
                text = context.getString(R.string.widget_calories_snap),
                onClick = openAppAction(WidgetIntents.Destination.MEAL_SNAP),
                style = PillStyle.ACCENT,
                modifier = GlanceModifier.fillMaxWidth(),
                icon = R.drawable.widget_ic_camera,
            )
        }
    }
}
