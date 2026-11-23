package vn.uit.healthos.feature.meds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pure mapping from notification action to (outboxType, newCacheStatus, payload). */
data class DoseActionSpec(
    val outboxType: String,
    val cacheStatus: String,
    val payload: DoseActionOutboxPayload,
)

fun mapDoseAction(
    action: String,
    reminderId: String,
    occurrenceId: String,
    snoozeMinutes: Int = 10,
): DoseActionSpec? = when (action) {
    DoseActionReceiver.ACTION_DONE -> DoseActionSpec(
        outboxType = OUTBOX_TYPE_DOSE_DONE,
        cacheStatus = DoseStatus.DONE.wire,
        payload = DoseActionOutboxPayload(reminderId, occurrenceId),
    )
    DoseActionReceiver.ACTION_SKIP -> DoseActionSpec(
        outboxType = OUTBOX_TYPE_DOSE_SKIP,
        cacheStatus = DoseStatus.SKIPPED.wire,
        payload = DoseActionOutboxPayload(reminderId, occurrenceId),
    )
    DoseActionReceiver.ACTION_SNOOZE -> DoseActionSpec(
        outboxType = OUTBOX_TYPE_DOSE_SNOOZE,
        cacheStatus = DoseStatus.SNOOZED.wire,
        payload = DoseActionOutboxPayload(reminderId, occurrenceId, snoozeMinutes),
    )
    else -> null
}

/** Handles notification actions offline-first through [MedsRepository.recordDoseAction]. */
@AndroidEntryPoint
class DoseActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MedsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val occurrenceId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_OCCURRENCE_ID) ?: return
        val reminderId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_REMINDER_ID) ?: return
        if (mapDoseAction(action, reminderId, occurrenceId) == null) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Used only when the dose is no longer cached; the repository prefers the cached row.
                val fallback = DoseCacheEntity(
                    occurrenceId = occurrenceId,
                    reminderId = reminderId,
                    planId = "",
                    planName = intent.getStringExtra(DoseAlarmScheduler.EXTRA_PLAN_NAME).orEmpty(),
                    strength = intent.getStringExtra(DoseAlarmScheduler.EXTRA_STRENGTH),
                    scheduledAtMs = System.currentTimeMillis(),
                    status = DoseStatus.PENDING.wire,
                )
                repository.recordDoseAction(fallback, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record dose action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "vn.uit.healthos.action.DOSE_DONE"
        const val ACTION_SKIP = "vn.uit.healthos.action.DOSE_SKIP"
        const val ACTION_SNOOZE = "vn.uit.healthos.action.DOSE_SNOOZE"
        private const val TAG = "DoseActionReceiver"
    }
}
