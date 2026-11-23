package vn.uit.healthos.feature.safety

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SOS lifecycle outside any Activity: countdown timer, alarm, wake lock, notification and dispatch.
 * State lives in [SosEventStore], keyed by the event id, so UI recreation never restarts or re-sends anything.
 * The process is kept alive by a foreground service: [FallDetectionService] for falls, [SosAlertService] otherwise.
 */
@Singleton
class SosController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: SosEventStore,
    private val dispatcher: SosDispatcher,
    private val safetyPreferences: SafetyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarm = SosAlarm(context)
    private val lock = Any()
    private var countdownJob: Job? = null
    private var countdownEventId: String? = null
    private var dispatchJob: Job? = null
    private var dispatchEventId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    val event: StateFlow<SosEvent?> = store.event

    /** Starts a new SOS, or returns the one already in progress. */
    fun arm(trigger: SosTrigger): SosEvent {
        val now = System.currentTimeMillis()
        val event = checkNotNull(
            store.update {
                SosEvents.arm(SosEvents.dropStaleCountdown(it, now), UUID.randomUUID().toString(), trigger, now)
            },
        )
        resume(event)
        return event
    }

    /** Re-attaches timers and dispatch to a persisted active event, e.g. after the process was recreated. */
    fun restore(): SosEvent? {
        val current = store.update { SosEvents.dropStaleCountdown(it, System.currentTimeMillis()) }
        if (current != null && current.isActive) resume(current)
        if (current?.phase == SosPhase.CANCELLED) SosNotifications.post(context, current)
        return current
    }

    fun cancel(eventId: String) {
        val event = store.update { SosEvents.cancel(it, eventId, System.currentTimeMillis()) }
        if (event == null || event.id != eventId || event.phase != SosPhase.CANCELLED) return
        synchronized(lock) {
            if (countdownEventId == eventId) {
                countdownJob?.cancel()
                countdownJob = null
                countdownEventId = null
            }
        }
        finishSideEffects(event)
    }

    fun sendNow(eventId: String) {
        beginDispatch(eventId)
    }

    fun recordSmsPart(eventId: String, phoneKey: String, part: Int, outcome: SmsPartOutcome) {
        store.update { SosEvents.recordSmsPart(it, eventId, phoneKey, part, outcome) }
    }

    /** Clears everything tied to the signed-in user. Call on logout. */
    suspend fun clearUserData() {
        store.event.value?.let { cancel(it.id) }
        store.clear()
        SosNotifications.post(context, null)
        FallDetectionService.stop(context)
        safetyPreferences.clearUserData()
    }

    private fun resume(event: SosEvent) {
        when (event.phase) {
            SosPhase.COUNTDOWN -> startCountdown(event)
            SosPhase.DISPATCHING -> startDispatch(event)
            SosPhase.DONE, SosPhase.CANCELLED -> Unit
        }
    }

    private fun startCountdown(event: SosEvent) {
        synchronized(lock) {
            if (countdownEventId == event.id && countdownJob?.isActive == true) return
            acquireWakeLock()
            alarm.start()
            SosNotifications.post(context, event)
            countdownEventId = event.id
            countdownJob = scope.launch {
                delay((event.deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L))
                beginDispatch(event.id)
            }
        }
    }

    private fun beginDispatch(eventId: String) {
        val event = store.update { SosEvents.beginDispatch(it, eventId) }
        if (event == null || event.id != eventId || event.phase != SosPhase.DISPATCHING) return
        startDispatch(event)
    }

    private fun startDispatch(event: SosEvent) {
        synchronized(lock) {
            if (dispatchEventId == event.id && dispatchJob?.isActive == true) return
            if (countdownEventId == event.id) {
                countdownJob?.cancel()
                countdownJob = null
                countdownEventId = null
            }
            alarm.stop()
            acquireWakeLock()
            SosNotifications.post(context, event)
            dispatchEventId = event.id
            dispatchJob = scope.launch {
                try {
                    dispatcher.dispatch(event.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "SOS dispatch failed for ${event.id}", e)
                } finally {
                    val done = store.update { SosEvents.complete(it, event.id, System.currentTimeMillis()) }
                    finishSideEffects(done?.takeIf { it.id == event.id })
                }
            }
        }
    }

    private fun finishSideEffects(event: SosEvent?) {
        alarm.stop()
        if (event != null) SosNotifications.post(context, event)
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        synchronized(lock) {
            val existing = wakeLock
            if (existing != null && existing.isHeld) return
            val pm = context.getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        synchronized(lock) {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        }
    }

    private companion object {
        const val TAG = "SosController"
        const val WAKE_LOCK_TAG = "healthos:sos"
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L
    }
}

/** Looping alarm sound and vibration played during the countdown, regardless of which screen is visible. */
private class SosAlarm(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun start() {
        if (ringtone != null || vibrator != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "SOS alarm sound unavailable", e)
        }
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 300), 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
            vibrator = v
        } catch (e: RuntimeException) {
            Log.w(TAG, "SOS vibration unavailable", e)
        }
    }

    @Synchronized
    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private companion object {
        const val TAG = "SosAlarm"
    }
}
