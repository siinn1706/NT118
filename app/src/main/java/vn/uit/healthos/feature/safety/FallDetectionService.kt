package vn.uit.healthos.feature.safety

import android.annotation.SuppressLint
import android.app.Notification
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
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vn.uit.healthos.MainActivity
import vn.uit.healthos.R
import javax.inject.Inject

/**
 * Foreground service (`health`) sampling the accelerometer at ~50 Hz into [FallDetector]. A suspected fall arms
 * the SOS directly in this already-foreground process: [SosController] runs the countdown, posts the full-screen
 * alert with Cancel / Send-now actions, and dispatches when the timer ends even if no screen is ever shown.
 *
 * Screen-off reliability: the wake-up accelerometer is used when the device has one (it wakes the CPU to deliver
 * batched samples); otherwise a partial wake lock is held for as long as monitoring runs.
 */
@AndroidEntryPoint
class FallDetectionService : Service(), SensorEventListener {

    @Inject
    lateinit var safetyPreferences: SafetyPreferences

    @Inject
    lateinit var sosController: SosController

    @Inject
    lateinit var locationProvider: LocationProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = FallDetector()
    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitoring = false

    override fun onCreate() {
        super.onCreate()
        SosNotifications.ensureChannels(this)
        sosController.restore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithOptionalLocation(
            service = this,
            notificationId = ONGOING_NOTIFICATION_ID,
            notification = buildOngoingNotification(testMode = false),
            baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            withLocation = intent != null &&
                intent.getBooleanExtra(EXTRA_WITH_LOCATION, true) &&
                locationProvider.hasPermission(),
        )
        if (!monitoring && !startMonitoring()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startMonitoring(): Boolean {
        val manager = getSystemService(SensorManager::class.java) ?: return false
        val wakeUpSensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        val sensor = wakeUpSensor ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        if (wakeUpSensor == null) acquireMonitoringWakeLock()
        val maxLatencyUs = if (wakeUpSensor != null) WAKE_UP_BATCH_LATENCY_US else 0
        if (!manager.registerListener(this, sensor, SAMPLING_PERIOD_US, maxLatencyUs)) {
            releaseMonitoringWakeLock()
            return false
        }
        sensorManager = manager
        monitoring = true

        scope.launch {
            safetyPreferences.state.collectLatest { local ->
                detector.updateConfig(
                    if (local.testModeEnabled) FallDetectorConfig.TEST_MODE else FallDetectorConfig.STANDARD,
                )
                try {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(local.testModeEnabled))
                } catch (e: SecurityException) {
                    Log.w(TAG, "Monitoring notification not permitted", e)
                }
            }
        }
        return true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return
        val tsMs = event.timestamp / 1_000_000L
        val detected = detector.onAccelerometerSample(values[0], values[1], values[2], tsMs)
        if (detected is FallDetectionEvent.FallSuspected) {
            sosController.arm(SosTrigger.FALL)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        monitoring = false
        releaseMonitoringWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Held only while monitoring without a wake-up sensor and released in onDestroy; a timeout would silently
    // stop fall detection with the screen off.
    @SuppressLint("WakelockTimeout")
    private fun acquireMonitoringWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MONITOR_WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMonitoringWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun buildOngoingNotification(testMode: Boolean): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, SosNotifications.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.safety_monitor_notif_title))
            .setContentText(
                if (testMode) {
                    getString(R.string.safety_monitor_notif_test)
                } else {
                    getString(R.string.safety_monitor_notif_active)
                },
            )
            .setOngoing(true)
            .setContentIntent(openPi)
            .build()
    }

    companion object {
        private const val TAG = "FallDetectionService"
        private const val ONGOING_NOTIFICATION_ID = 7001
        private const val MONITOR_WAKE_LOCK_TAG = "healthos:fall-monitor"

        // 20,000 us = 50 Hz; wake-up samples may be batched for up to 1 s (timestamps keep detection exact).
        private const val SAMPLING_PERIOD_US = 20_000
        private const val WAKE_UP_BATCH_LATENCY_US = 1_000_000
        private const val EXTRA_WITH_LOCATION = "with_location"

        /**
         * Must be called while the app is visible or from an exempt broadcast such as BOOT_COMPLETED
         * (Android 12+ background start restrictions). [withLocation] false skips the `location` type, which a
         * service started from the background is not granted; the SOS then goes out without a map link.
         */
        fun start(context: Context, withLocation: Boolean = true) {
            val intent = Intent(context, FallDetectionService::class.java)
                .putExtra(EXTRA_WITH_LOCATION, withLocation)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Fall detection not allowed to start", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Fall detection rejected", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FallDetectionService::class.java))
        }
    }
}
