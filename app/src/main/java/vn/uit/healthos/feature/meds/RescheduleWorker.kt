package vn.uit.healthos.feature.meds

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Runs every 12 h to refresh the 48 h dose cache (when online) and reschedule exact alarms. */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: MedsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        repository.refreshAndReschedule()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "healthos_meds_reschedule"

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
