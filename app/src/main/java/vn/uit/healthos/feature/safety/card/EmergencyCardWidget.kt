package vn.uit.healthos.feature.safety.card

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import vn.uit.healthos.feature.widget.SosWidget

/**
 * Receiver for the SOS / Emergency Card widget. The class name predates [SosWidget] and stays as is, so widgets the
 * user already placed on the home screen keep working after the update.
 */
class EmergencyCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SosWidget()
}
