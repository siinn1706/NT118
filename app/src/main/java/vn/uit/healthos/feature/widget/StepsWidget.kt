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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import vn.uit.healthos.R
import vn.uit.healthos.feature.home.TodayMetrics

/** Today's steps (2x1 bar, 2x2 ring) with a small action that queues the day's total for sync. */
class StepsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(BAR, SQUARE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = widgetEntryPoint(context).todaySnapshotRepository()
        val initial = repository.today()
        provideContent {
            val flow = remember { repository.observeToday() }
            val content by flow.collectAsState(initial)
            val tall = LocalSize.current.height >= SQUARE.height
            when (val current = content) {
                is WidgetContent.Visible -> StepsContent(current.value, tall)
                else -> PrivacyNotice(current, compact = !tall)
            }
        }
    }

    companion object {
        val BAR = DpSize(110.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 110.dp)
    }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

@Composable
private fun StepsContent(metrics: TodayMetrics, tall: Boolean) {
    val context = LocalContext.current
    val progress = progressOf(metrics.stepsToday.toDouble(), metrics.stepGoal.toDouble())
    val goal = context.getString(R.string.widget_steps_goal, formatCount(metrics.stepGoal))
    val sync = actionRunCallback<SyncStepsAction>()
    val syncLabel = context.getString(R.string.widget_steps_sync)
    val syncDescription = context.getString(R.string.widget_steps_sync_cd)

    GlassPanel(onClick = openAppAction(WidgetIntents.Destination.HOME), padding = if (tall) 12.dp else 10.dp) {
        if (tall) {
            Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetHeader(R.drawable.widget_ic_walk, context.getString(R.string.widget_steps_name))
                Spacer(GlanceModifier.height(6.dp))
                ProgressRing(
                    progress = progress,
                    ringColor = WidgetColors.RING_GOOD,
                    size = 64.dp,
                    contentDescription = null,
                ) {
                    ValueText(formatCount(metrics.stepsToday), fontSize = 15.sp)
                    CaptionText(context.getString(R.string.widget_steps_unit))
                }
                Spacer(GlanceModifier.height(4.dp))
                CaptionText(goal)
                Spacer(GlanceModifier.height(6.dp))
                PillButton(
                    text = syncLabel,
                    onClick = sync,
                    style = PillStyle.CHIP,
                    icon = R.drawable.widget_ic_sync,
                    contentDescription = syncDescription,
                )
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetIcon(R.drawable.widget_ic_walk, WidgetColors.accent, size = 20.dp)
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        ValueText(formatCount(metrics.stepsToday), fontSize = 18.sp)
                        Spacer(GlanceModifier.width(4.dp))
                        CaptionText(context.getString(R.string.widget_steps_unit))
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    ProgressBar(progress)
                }
                Spacer(GlanceModifier.width(8.dp))
                PillButton(
                    text = "",
                    onClick = sync,
                    style = PillStyle.CHIP,
                    icon = R.drawable.widget_ic_sync,
                    contentDescription = syncDescription,
                )
            }
        }
    }
}
