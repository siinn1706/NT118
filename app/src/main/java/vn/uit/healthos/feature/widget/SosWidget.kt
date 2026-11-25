package vn.uit.healthos.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import vn.uit.healthos.R
import vn.uit.healthos.feature.safety.SosCountdownActivity
import vn.uit.healthos.feature.safety.card.EmergencyCardActivity

/**
 * SOS button (1x1), plus the Emergency Card at 3x1. SOS always goes through the cancellable countdown screen. The
 * widget never shows names or medical details; signed in and unlocked, it only says how many contacts will be alerted.
 */
class SosWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(BUTTON, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val access = entryPoint.todaySnapshotRepository().access()
        val status = when (access) {
            WidgetAccess.VISIBLE -> {
                val contacts = entryPoint.safetyPreferences().cachedContacts().size
                if (contacts > 0) {
                    context.resources.getQuantityString(R.plurals.widget_sos_contacts, contacts, contacts)
                } else {
                    context.getString(R.string.widget_sos_no_contacts)
                }
            }
            else -> context.getString(R.string.widget_sos_hint)
        }
        provideContent {
            if (LocalSize.current.width >= WIDE.width) SosWide(status) else SosButtonOnly()
        }
    }

    companion object {
        val BUTTON = DpSize(40.dp, 40.dp)
        val WIDE = DpSize(180.dp, 40.dp)
    }
}

@Composable
private fun sosAction(): Action = actionStartActivity(SosCountdownActivity.manualSosIntent(LocalContext.current))

@Composable
private fun SosButtonOnly() {
    val context = LocalContext.current
    GlassPanel(onClick = null, padding = 6.dp, contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_pill_sos_bg))
                .clickable(sosAction())
                .semantics { contentDescription = context.getString(R.string.widget_sos_cd) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = context.getString(R.string.widget_sos_button),
                style = TextStyle(color = WidgetColors.onSos, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SosWide(status: String) {
    val context = LocalContext.current
    val cardIntent = Intent(context, EmergencyCardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    GlassPanel(onClick = null, padding = 10.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                ValueText(context.getString(R.string.widget_sos_name), fontSize = 14.sp, color = WidgetColors.sosText)
                CaptionText(status)
            }
            Spacer(GlanceModifier.width(6.dp))
            PillButton(
                text = context.getString(R.string.safety_widget_card_btn),
                onClick = actionStartActivity(cardIntent),
                style = PillStyle.CHIP,
            )
            Spacer(GlanceModifier.width(6.dp))
            PillButton(
                text = context.getString(R.string.widget_sos_button),
                onClick = sosAction(),
                style = PillStyle.SOS,
                contentDescription = context.getString(R.string.widget_sos_cd),
            )
        }
    }
}
