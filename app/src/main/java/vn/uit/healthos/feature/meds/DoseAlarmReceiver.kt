package vn.uit.healthos.feature.meds

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.uit.healthos.MainActivity
import vn.uit.healthos.R
import vn.uit.healthos.feature.widget.WidgetKind
import vn.uit.healthos.feature.widget.WidgetUpdater
import javax.inject.Inject

/**
 * Fires a high-priority medication reminder notification with Taken / Skip / Snooze 10m actions, but only while the
 * cached dose is still open. Doses that were taken, skipped, missed or cancelled (plan paused or deleted) stay silent,
 * as do alarms whose dose is no longer cached at all.
 */
@AndroidEntryPoint
class DoseAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var doseCacheDao: DoseCacheDao

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_OCCURRENCE_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dose = doseCacheDao.getById(occurrenceId)
                if (dose != null && DoseStatus.of(dose.status).isOpen) {
                    showNotification(context, dose)
                    // The dose that is due now becomes (or stays) the widget's "next dose".
                    widgetUpdater.update(WidgetKind.NEXT_DOSE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show dose reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, dose: DoseCacheEntity) {
        val occurrenceId = dose.occurrenceId
        val reminderId = dose.reminderId
        val planName = dose.planName.ifBlank { context.getString(R.string.nav_meds) }
        val strength = dose.strength

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, nm)

        val notifId = DoseAlarmScheduler.requestCodeFor(occurrenceId)
        val openAppIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = if (!strength.isNullOrBlank()) {
            context.getString(R.string.meds_alarm_body_with_strength, planName, strength)
        } else {
            context.getString(R.string.meds_alarm_body, planName)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.meds_alarm_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                context.getString(R.string.meds_action_taken),
                actionIntent(context, DoseActionReceiver.ACTION_DONE, occurrenceId, reminderId, planName, strength, notifId * 3 + 1),
            )
            .addAction(
                0,
                context.getString(R.string.meds_action_skip),
                actionIntent(context, DoseActionReceiver.ACTION_SKIP, occurrenceId, reminderId, planName, strength, notifId * 3 + 2),
            )
            .addAction(
                0,
                context.getString(R.string.meds_action_snooze),
                actionIntent(context, DoseActionReceiver.ACTION_SNOOZE, occurrenceId, reminderId, planName, strength, notifId * 3 + 3),
            )
            .build()

        nm.notify(notifId, notification)
    }

    private fun actionIntent(
        context: Context,
        action: String,
        occurrenceId: String,
        reminderId: String,
        planName: String,
        strength: String?,
        requestCode: Int,
    ): PendingIntent {
        val i = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra(DoseAlarmScheduler.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(DoseAlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(DoseAlarmScheduler.EXTRA_PLAN_NAME, planName)
            putExtra(DoseAlarmScheduler.EXTRA_STRENGTH, strength)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode and 0x7FFFFFFF,
            i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.meds_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.meds_channel_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "healthos_med_alarms"
        private const val TAG = "DoseAlarmReceiver"
    }
}
