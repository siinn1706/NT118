package vn.uit.healthos.feature.meds

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import vn.uit.healthos.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_MED_TZID = "Asia/Ho_Chi_Minh"

@Singleton
class DoseAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(doses: List<DoseCacheEntity>, nowMs: Long = System.currentTimeMillis()) {
        doses.forEach { dose ->
            val triggerAt = dose.snoozedUntilMs ?: dose.scheduledAtMs
            if (triggerAt > nowMs && DoseStatus.of(dose.status).isOpen) {
                scheduleSingle(dose, triggerAt)
            } else {
                cancel(dose.occurrenceId)
            }
        }
    }

    fun scheduleSingle(dose: DoseCacheEntity, triggerAtMs: Long) {
        val manager = alarmManager ?: return
        val fireIntent = buildFirePendingIntent(dose)
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            val info = AlarmManager.AlarmClockInfo(triggerAtMs, showIntent)
            manager.setAlarmClock(info, fireIntent)
        }.onFailure {
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, fireIntent) }
        }
    }

    fun scheduleSnooze(dose: DoseCacheEntity, minutes: Int = 10, nowMs: Long = System.currentTimeMillis()): Long {
        val triggerAt = nowMs + minutes * 60_000L
        scheduleSingle(dose, triggerAt)
        return triggerAt
    }

    fun cancel(occurrenceId: String) {
        val manager = alarmManager ?: return
        val intent = Intent(context, DoseAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCodeFor(occurrenceId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pi != null) {
            manager.cancel(pi)
            pi.cancel()
        }
    }

    fun dismissNotification(occurrenceId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(requestCodeFor(occurrenceId))
    }

    /** Cancels the alarm and removes any reminder notification already shown for this dose. */
    fun stop(occurrenceId: String) {
        cancel(occurrenceId)
        dismissNotification(occurrenceId)
    }

    private fun buildFirePendingIntent(dose: DoseCacheEntity): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_OCCURRENCE_ID, dose.occurrenceId)
            putExtra(EXTRA_REMINDER_ID, dose.reminderId)
            putExtra(EXTRA_PLAN_NAME, dose.planName)
            putExtra(EXTRA_STRENGTH, dose.strength)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(dose.occurrenceId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_PLAN_NAME = "extra_plan_name"
        const val EXTRA_STRENGTH = "extra_strength"

        fun requestCodeFor(occurrenceId: String): Int = occurrenceId.hashCode() and 0x7FFFFFFF
    }
}
