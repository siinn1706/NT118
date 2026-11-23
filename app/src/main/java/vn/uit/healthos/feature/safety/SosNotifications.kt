package vn.uit.healthos.feature.safety

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import vn.uit.healthos.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Channels and notifications of the SOS flow, shared by the controller and both hosting services. */
object SosNotifications {
    const val MONITOR_CHANNEL_ID = "healthos_fall_monitor"
    const val SOS_CHANNEL_ID = "healthos_sos_alert"
    const val SOS_NOTIFICATION_ID = 7002
    private const val REQUEST_OPEN = 7020
    private const val TAG = "SosNotifications"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    context.getString(R.string.safety_channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        if (nm.getNotificationChannel(SOS_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    SOS_CHANNEL_ID,
                    context.getString(R.string.safety_channel_sos_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }

    /** Posts the notification matching the event phase, or removes it once the SOS was cancelled. */
    fun post(context: Context, event: SosEvent?) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (event == null || event.phase == SosPhase.CANCELLED) {
            nm.cancel(SOS_NOTIFICATION_ID)
            return
        }
        ensureChannels(context)
        try {
            nm.notify(SOS_NOTIFICATION_ID, build(context, event))
        } catch (e: SecurityException) {
            Log.w(TAG, "SOS notification not permitted", e)
        }
    }

    /** Notification for a hosting service; falls back to a neutral "preparing" one when no event is active. */
    fun forService(context: Context, event: SosEvent?): Notification {
        ensureChannels(context)
        if (event != null && event.phase != SosPhase.CANCELLED) return build(context, event)
        return NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.safety_sos_notif_starting))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    fun build(context: Context, event: SosEvent): Notification {
        val openPi = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            SosCountdownActivity.openIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        return when (event.phase) {
            SosPhase.COUNTDOWN -> {
                val title = if (event.trigger == SosTrigger.FALL) {
                    context.getString(R.string.safety_fall_alert_title)
                } else {
                    context.getString(R.string.safety_sos_notif_manual_title)
                }
                val sendAt = Instant.ofEpochMilli(event.deadlineMs)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                builder
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.safety_sos_notif_countdown_body, sendAt))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(openPi, true)
                    .setOngoing(true)
                    .setWhen(event.deadlineMs)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(
                        0,
                        context.getString(R.string.safety_im_ok_cancel),
                        SosEventReceiver.actionIntent(context, SosEventReceiver.ACTION_CANCEL, event.id),
                    )
                    .addAction(
                        0,
                        context.getString(R.string.safety_sos_action_send_now),
                        SosEventReceiver.actionIntent(context, SosEventReceiver.ACTION_SEND_NOW, event.id),
                    )
                    .build()
            }

            SosPhase.DISPATCHING -> builder
                .setContentTitle(context.getString(R.string.safety_sos_notif_dispatching_title))
                .setContentText(context.getString(R.string.safety_sos_dispatching))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()

            SosPhase.DONE, SosPhase.CANCELLED -> builder
                .setContentTitle(context.getString(R.string.safety_sos_result_title))
                .setContentText(
                    context.getString(R.string.safety_sos_sms_summary, event.smsSentCount, event.contacts.size),
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
        }
    }
}
