package vn.uit.healthos.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import vn.uit.healthos.feature.meds.DoseActionReceiver

/*
 * Widget buttons that write data. Each re-checks [WidgetAccess] because a widget can still show a button rendered
 * before the user signed out or turned the app lock on. Writes go through the same repositories as the app, which
 * queue the server sync in the outbox and refresh the widgets.
 */

/** "+1 cup": logs 250 ml of water. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = widgetEntryPoint(context)
        if (entryPoint.todaySnapshotRepository().access() != WidgetAccess.VISIBLE) return
        entryPoint.hydrationRepository().addWater(HydrationWidget.CUP_ML)
    }
}

/** Queues today's step total for upload. */
class SyncStepsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = widgetEntryPoint(context)
        if (entryPoint.todaySnapshotRepository().access() != WidgetAccess.VISIBLE) return
        entryPoint.stepCounterRepository().enqueueTodaySync()
    }
}

object DoseWidgetActions {
    val OCCURRENCE_ID = ActionParameters.Key<String>("occurrence_id")

    internal suspend fun record(context: Context, parameters: ActionParameters, action: String) {
        val occurrenceId = parameters[OCCURRENCE_ID] ?: return
        val entryPoint = widgetEntryPoint(context)
        if (entryPoint.todaySnapshotRepository().access() != WidgetAccess.VISIBLE) return
        val dose = entryPoint.doseCacheDao().getById(occurrenceId) ?: return
        // recordDoseAction ignores doses that are no longer open, so a double tap cannot send two actions.
        entryPoint.medsRepository().recordDoseAction(dose, action)
    }
}

class DoseTakenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        DoseWidgetActions.record(context, parameters, DoseActionReceiver.ACTION_DONE)
}

class DoseSnoozeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        DoseWidgetActions.record(context, parameters, DoseActionReceiver.ACTION_SNOOZE)
}
