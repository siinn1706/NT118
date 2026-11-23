package vn.uit.healthos.feature.safety

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground host for an SOS that is not already hosted by [FallDetectionService] (manual SOS, widget, or a fall
 * SOS whose monitoring was switched off). It only keeps the process alive and the notification attached; the
 * countdown and dispatch belong to [SosController]. Must be started while the app is visible.
 */
@AndroidEntryPoint
class SosAlertService : Service() {

    @Inject
    lateinit var sosController: SosController

    @Inject
    lateinit var locationProvider: LocationProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val event = sosController.restore()
        startForegroundWithOptionalLocation(
            service = this,
            notificationId = SosNotifications.SOS_NOTIFICATION_ID,
            notification = SosNotifications.forService(this, event),
            baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            withLocation = intent != null && locationProvider.hasPermission(),
        )
        if (event == null || !event.isActive) {
            finish(event)
            return START_NOT_STICKY
        }
        if (watchJob?.isActive != true) {
            watchJob = scope.launch {
                finish(sosController.event.first { it == null || !it.isActive })
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** A finished SOS keeps its result notification; a cancelled one disappears with the service. */
    private fun finish(event: SosEvent?) {
        val keepResult = event?.phase == SosPhase.DONE
        ServiceCompat.stopForeground(
            this,
            if (keepResult) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE,
        )
        // Re-post as a plain notification so it is dismissible once detached from the service.
        if (keepResult) SosNotifications.post(this, event)
        stopSelf()
    }

    companion object {
        private const val TAG = "SosAlertService"

        /** Hosts the current SOS in the foreground. Returns false when the system refused the start. */
        fun host(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, SosAlertService::class.java))
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "SOS host service not allowed to start", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "SOS host service rejected", e)
            false
        }
    }
}

/**
 * Starts [service] in the foreground with [baseType], adding the `location` type when requested so an SOS started
 * while the app is visible keeps GPS access after the app goes to the background. If the system refuses location
 * (no while-in-use grant, e.g. a sticky restart), it falls back to [baseType] alone.
 */
internal fun startForegroundWithOptionalLocation(
    service: Service,
    notificationId: Int,
    notification: Notification,
    baseType: Int,
    withLocation: Boolean,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Before Android 14 the manifest types apply and a missing while-in-use grant only limits access.
        service.startForeground(notificationId, notification)
        return
    }
    if (withLocation) {
        try {
            ServiceCompat.startForeground(
                service,
                notificationId,
                notification,
                baseType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            return
        } catch (e: SecurityException) {
            Log.w("ForegroundStart", "Location foreground type refused; continuing without it", e)
        }
    }
    ServiceCompat.startForeground(service, notificationId, notification, baseType)
}
