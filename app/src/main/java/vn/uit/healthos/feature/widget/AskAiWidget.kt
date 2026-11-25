package vn.uit.healthos.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import vn.uit.healthos.R

/**
 * Search-bar style entry to the AI doctor (4x1); at 4x2 it adds suggestion chips. Every tap opens the chat with the
 * prompt prefilled in the composer, so nothing is sent until the user confirms in the app.
 */
class AskAiWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(BAR, CHIPS))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prompts = SUGGESTIONS.map(context::getString)
        provideContent {
            AskAiContent(prompts, showChips = LocalSize.current.height >= CHIPS.height)
        }
    }

    companion object {
        val BAR = DpSize(180.dp, 40.dp)
        val CHIPS = DpSize(180.dp, 100.dp)

        /** The same suggestions the chat screen offers. */
        val SUGGESTIONS = listOf(R.string.chat_prompt_meals, R.string.chat_prompt_hr, R.string.chat_prompt_exercise)
    }
}

class AskAiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AskAiWidget()
}

@Composable
private fun AskAiContent(prompts: List<String>, showChips: Boolean) {
    val context = LocalContext.current
    GlassPanel(onClick = null, padding = 8.dp, contentAlignment = Alignment.CenterStart) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_chip_bg))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .clickable(openAppAction(WidgetIntents.Destination.CHAT)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetIcon(R.drawable.widget_ic_sparkle, WidgetColors.accent, size = 18.dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = context.getString(R.string.widget_ask_hint),
                    style = TextStyle(color = WidgetColors.onPanelMuted, fontSize = 14.sp),
                    maxLines = 1,
                )
            }
            if (showChips) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    prompts.forEachIndexed { index, prompt ->
                        if (index > 0) Spacer(GlanceModifier.width(6.dp))
                        PillButton(
                            text = prompt,
                            onClick = openAppAction(WidgetIntents.Destination.CHAT, prompt),
                            style = PillStyle.CHIP,
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
