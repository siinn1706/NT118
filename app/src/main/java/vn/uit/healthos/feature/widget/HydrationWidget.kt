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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import vn.uit.healthos.R
import vn.uit.healthos.feature.home.TodayMetrics

/** Water intake against the day's goal with a one-tap "+1 cup" (250 ml); at 1x1 only the button remains. */
class HydrationWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(BUTTON, BAR))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = widgetEntryPoint(context).todaySnapshotRepository()
        val initial = repository.today()
        provideContent {
            val flow = remember { repository.observeToday() }
            val content by flow.collectAsState(initial)
            val buttonOnly = LocalSize.current.width < BAR.width
            when (val current = content) {
                is WidgetContent.Visible ->
                    if (buttonOnly) AddCupOnly() else HydrationContent(current.value)
                else -> PrivacyNotice(current, compact = true)
            }
        }
    }

    companion object {
        const val CUP_ML = 250
        val BUTTON = DpSize(40.dp, 40.dp)
        val BAR = DpSize(110.dp, 40.dp)
    }
}

class HydrationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HydrationWidget()
}

@Composable
private fun AddCupOnly() {
    val context = LocalContext.current
    GlassPanel(onClick = null, padding = 6.dp, contentAlignment = Alignment.Center) {
        PillButton(
            text = "",
            onClick = actionRunCallback<AddWaterAction>(),
            style = PillStyle.ACCENT,
            modifier = GlanceModifier.fillMaxSize(),
            icon = R.drawable.widget_ic_water,
            contentDescription = context.getString(R.string.widget_hydration_add_cd),
        )
    }
}

@Composable
private fun HydrationContent(metrics: TodayMetrics) {
    val context = LocalContext.current
    GlassPanel(onClick = openAppAction(WidgetIntents.Destination.HOME), padding = 10.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetIcon(R.drawable.widget_ic_water, WidgetColors.accent, size = 16.dp)
                    Spacer(GlanceModifier.width(4.dp))
                    ValueText(
                        context.getString(
                            R.string.widget_hydration_value,
                            formatCount(metrics.hydrationMl),
                            formatCount(metrics.hydrationTargetMl),
                        ),
                        fontSize = 14.sp,
                    )
                }
                Spacer(GlanceModifier.height(5.dp))
                ProgressBar(progressOf(metrics.hydrationMl.toDouble(), metrics.hydrationTargetMl.toDouble()))
            }
            Spacer(GlanceModifier.width(8.dp))
            PillButton(
                text = context.getString(R.string.widget_hydration_add),
                onClick = actionRunCallback<AddWaterAction>(),
                style = PillStyle.ACCENT,
                contentDescription = context.getString(R.string.widget_hydration_add_cd),
            )
        }
    }
}
