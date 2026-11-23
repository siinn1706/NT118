package vn.uit.healthos.feature.safety.card

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import vn.uit.healthos.R
import vn.uit.healthos.feature.safety.SosCountdownActivity

/** Home-screen Glance widget offering one-tap Emergency Card and SOS countdown. */
class EmergencyCardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cardIntent = Intent(context, EmergencyCardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val sosIntent = SosCountdownActivity.manualSosIntent(context)
        val cardLabel = context.getString(R.string.safety_widget_card_btn)
        val sosLabel = context.getString(R.string.safety_widget_sos_btn)

        provideContent {
            GlanceTheme {
                WidgetBody(
                    cardLabel = cardLabel,
                    sosLabel = sosLabel,
                    cardIntent = cardIntent,
                    sosIntent = sosIntent,
                )
            }
        }
    }
}

@Composable
private fun WidgetBody(
    cardLabel: String,
    sosLabel: String,
    cardIntent: Intent,
    sosIntent: Intent,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            text = cardLabel,
            onClick = actionStartActivity(cardIntent),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Button(
            text = sosLabel,
            onClick = actionStartActivity(sosIntent),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

class EmergencyCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EmergencyCardWidget()
}
