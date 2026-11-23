package vn.uit.healthos.feature.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts fall detection after a reboot or an app update when the user had it on. Both broadcasts exempt the app
 * from the background foreground-service start ban, and `health` is not among the types Android 15 blocks from
 * BOOT_COMPLETED. The `location` type is skipped because a background start never gets while-in-use location;
 * it comes back the next time monitoring is started from the visible app.
 */
@AndroidEntryPoint
class SafetyBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var safetyPreferences: SafetyPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (safetyPreferences.current().fallDetectionEnabled) {
                    FallDetectionService.start(appContext, withLocation = false)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
