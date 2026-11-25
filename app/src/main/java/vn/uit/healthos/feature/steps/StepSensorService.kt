package vn.uit.healthos.feature.steps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import vn.uit.healthos.MainActivity
import vn.uit.healthos.R
import vn.uit.healthos.feature.widget.WidgetKind
import vn.uit.healthos.feature.widget.WidgetUpdater
import javax.inject.Inject

/**
 * Foreground service (`health`) listening to `Sensor.TYPE_STEP_COUNTER` so step updates stay live
 * while the phone is in the user's pocket.
 *
 * Without ACTIVITY_RECOGNITION (revoked in Settings) Android 14+ refuses the `health` type, and Android 12+ refuses a
 * foreground start from the background (e.g. a sticky restart). In both cases the service stops itself quietly and
 * asks not to be restarted, instead of crashing in a restart loop.
 */
@AndroidEntryPoint
class StepSensorService : Service(), SensorEventListener {

    @Inject
    lateinit var stepCounterRepository: StepCounterRepository

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var lastSyncedAtMs: Long = 0L
    private var lastWidgetRefreshAtMs: Long? = null
    private var lastWidgetSteps: Int = 0

    /** True once the service runs in the foreground and listens to the sensor. */
    private var counting = false

    /** The counter is cumulative, so only the newest reading matters; one consumer keeps them in order. */
    private val readings = Channel<Long>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        if (!StepServicePolicy.hasActivityRecognition(this) || !enterForeground()) {
            stopSelf()
            return
        }

        sensorManager = getSystemService(SensorManager::class.java)
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            counting = true
            scope.launch { for (raw in readings) applyReading(raw) }
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            stepCounterRepository.schedulePeriodicSync()
        } else {
            stopSelf()
        }
    }

    /** Returns false when the system refuses the foreground start (permission revoked, background start blocked). */
    private fun enterForeground(): Boolean {
        ensureChannel()
        val notification = buildNotification(steps = 0)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Step counting needs the activity recognition permission", e)
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
            Log.w(TAG, "Foreground start not allowed right now", e)
            false
        }
    }

    // Only a service that is really counting asks to be restarted; a refused start must not loop.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        if (counting) START_STICKY else START_NOT_STICKY

    override fun onSensorChanged(event: SensorEvent?) {
        val raw = event?.values?.firstOrNull()?.toLong() ?: return
        readings.trySend(raw)
    }

    private suspend fun applyReading(raw: Long) {
        val updated = stepCounterRepository.onSensorCounterReading(raw)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(updated.steps))

        val now = System.currentTimeMillis()
        if (now - lastSyncedAtMs >= SYNC_DEBOUNCE_MS) {
            lastSyncedAtMs = now
            stepCounterRepository.enqueueTodaySync()
        }
        if (StepServicePolicy.shouldRefreshWidgets(lastWidgetRefreshAtMs, lastWidgetSteps, now, updated.steps)) {
            lastWidgetRefreshAtMs = now
            lastWidgetSteps = updated.steps
            widgetUpdater.update(WidgetKind.STEP_DEPENDENT)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        if (counting) sensorManager?.unregisterListener(this)
        counting = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.steps_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.steps_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(steps: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.steps_notification_title))
            .setContentText(getString(R.string.steps_notification_body, steps))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val TAG = "StepSensorService"
        private const val CHANNEL_ID = "healthos_steps"
        private const val NOTIFICATION_ID = 3001
        private const val SYNC_DEBOUNCE_MS = 5 * 60_000L

        /**
         * Starts counting when ACTIVITY_RECOGNITION is granted. Checking here matters: a service started with
         * `startForegroundService` that never reaches `startForeground` is killed by the system with an exception.
         */
        fun start(context: Context) {
            if (!StepServicePolicy.hasActivityRecognition(context)) return
            val intent = Intent(context, StepSensorService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Background start refused (ForegroundServiceStartNotAllowedException); Home starts it again.
                Log.w(TAG, "Could not start step counting", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not start step counting", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepSensorService::class.java))
        }
    }
}
