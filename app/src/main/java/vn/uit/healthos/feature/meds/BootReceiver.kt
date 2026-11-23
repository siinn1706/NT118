package vn.uit.healthos.feature.meds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reschedules all cached future dose alarms after a device reboot. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var doseCacheDao: DoseCacheDao

    @Inject
    lateinit var alarmScheduler: DoseAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val upcoming = doseCacheDao.upcomingPending(now)
                alarmScheduler.scheduleAll(upcoming, now)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
