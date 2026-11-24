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
import androidx.glance.action.actionParametersOf
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import vn.uit.healthos.R
import vn.uit.healthos.feature.meds.DEFAULT_MED_TZID
import vn.uit.healthos.feature.meds.DoseCacheEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Next open dose with Taken / Snooze 10' actions (3x1); at 3x2 it also lists the doses left today. */
class NextDoseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ROW, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val agenda = observeAgenda(entryPoint)
        val initial = agenda.first()
        provideContent {
            val flow = remember { agenda }
            val content by flow.collectAsState(initial)
            val list = LocalSize.current.height >= LIST.height
            when (val current = content) {
                is WidgetContent.Visible -> NextDoseContent(current.value, list)
                else -> PrivacyNotice(current, compact = !list)
            }
        }
    }

    private fun observeAgenda(entryPoint: WidgetEntryPoint): Flow<WidgetContent<DoseAgenda>> {
        val nowMs = System.currentTimeMillis()
        val doses = entryPoint.doseCacheDao().observeInWindow(
            // Wider than the grace period: a dose scheduled long ago can be snoozed until now.
            startMs = nowMs - NextDoseSelector.LOOKBACK_MS,
            endMs = nowMs + NextDoseSelector.LOOKAHEAD_MS,
        )
        return combine(entryPoint.todaySnapshotRepository().observeAccess(), doses) { access, cached ->
            WidgetPrivacy.gate(access) { NextDoseSelector.select(cached, System.currentTimeMillis(), MED_ZONE) }
        }.distinctUntilChanged()
    }

    companion object {
        val ROW = DpSize(180.dp, 40.dp)
        val LIST = DpSize(180.dp, 110.dp)
        internal val MED_ZONE: ZoneId = ZoneId.of(DEFAULT_MED_TZID)
    }
}

class NextDoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextDoseWidget()
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(NextDoseWidget.MED_ZONE).toLocalTime().format(TIME_FORMAT)

private fun DoseCacheEntity.title(fallback: String): String =
    listOf(planName.ifBlank { fallback }, strength.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun NextDoseContent(agenda: DoseAgenda, list: Boolean) {
    val context = LocalContext.current
    val fallbackName = context.getString(R.string.nav_meds)
    GlassPanel(onClick = openAppAction(WidgetIntents.Destination.MEDS), padding = 10.dp) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            if (list) {
                WidgetHeader(R.drawable.widget_ic_pill, context.getString(R.string.widget_dose_title))
                Spacer(GlanceModifier.height(6.dp))
            }
            val next = agenda.next
            if (next == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!list) {
                        WidgetIcon(R.drawable.widget_ic_pill, WidgetColors.accent, size = 18.dp)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    CaptionText(context.getString(R.string.widget_dose_none), maxLines = 2)
                }
            } else {
                NextDoseRow(next, next.title(fallbackName))
            }
            if (list && agenda.laterToday.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                CaptionText(context.getString(R.string.widget_dose_later))
                agenda.laterToday.forEach { dose ->
                    Spacer(GlanceModifier.height(2.dp))
                    CaptionText(
                        "${formatTime(NextDoseSelector.dueAtMs(dose))}  ${dose.title(fallbackName)}",
                        color = WidgetColors.onPanel,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextDoseRow(dose: DoseCacheEntity, title: String) {
    val context = LocalContext.current
    val due = formatTime(NextDoseSelector.dueAtMs(dose))
    val whenText = if (dose.snoozedUntilMs != null) {
        context.getString(R.string.widget_dose_snoozed_until, due)
    } else {
        context.getString(R.string.widget_dose_at, due)
    }
    val params = actionParametersOf(DoseWidgetActions.OCCURRENCE_ID to dose.occurrenceId)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            ValueText(title, fontSize = 14.sp)
            CaptionText(whenText)
        }
        Spacer(GlanceModifier.width(6.dp))
        PillButton(
            text = context.getString(R.string.widget_dose_taken),
            onClick = actionRunCallback<DoseTakenAction>(params),
            style = PillStyle.ACCENT,
            contentDescription = context.getString(R.string.widget_dose_taken_cd, title),
        )
        Spacer(GlanceModifier.width(4.dp))
        PillButton(
            text = context.getString(R.string.widget_dose_snooze),
            onClick = actionRunCallback<DoseSnoozeAction>(params),
            style = PillStyle.CHIP,
            contentDescription = context.getString(R.string.widget_dose_snooze_cd, title),
        )
    }
}
