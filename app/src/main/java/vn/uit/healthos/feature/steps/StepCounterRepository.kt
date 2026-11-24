package vn.uit.healthos.feature.steps

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.time.localDayTicker
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterRepository @Inject constructor(
    private val dao: DailyStepDao,
    private val outboxRepository: OutboxRepository,
    private val workManager: WorkManager,
) {
    fun observeRecentDays(limit: Int = 7): Flow<List<DailyStepEntity>> =
        dao.observeRecent(limit)

    /** Steps counted on this phone today; switches to the new day's count at local midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodaySteps(): Flow<Int> =
        localDayTicker().flatMapLatest { day -> dao.observeDay(day.toString()).map { it?.steps ?: 0 } }

    /** Sensor readings are read-modify-write on the same row, so they must apply strictly in arrival order. */
    private val readingMutex = Mutex()

    suspend fun onSensorCounterReading(rawCounterValue: Long, nowMs: Long = System.currentTimeMillis()): DailyStepEntity {
        val (updated, finishedDay) = readingMutex.withLock {
            val today = todayIso()
            val existingToday = dao.getDay(today)
            val latest = dao.observeRecent(1).first().firstOrNull()
            val updated = StepDeltaCalculator.applySensorReading(
                todayDate = today,
                newRawCounter = rawCounterValue,
                existingToday = existingToday,
                latestOverall = latest,
                nowMs = nowMs,
            )
            dao.upsert(updated)
            updated to latest?.date?.takeIf { existingToday == null && it != today }
        }
        // First reading of a new day: upload the final count of the day that just ended.
        if (finishedDay != null) enqueueDaySync(finishedDay)
        return updated
    }

    suspend fun enqueueTodaySync() = enqueueDaySync(todayIso())

    /**
     * Called when a different account signs in on this phone. The pedometer history belongs to the previous account,
     * and so does the sensor anchor: dropping both makes the next reading start the new account's day at zero, so
     * only steps taken after sign-in are counted and synced for it.
     */
    suspend fun resetForNewAccount() = readingMutex.withLock {
        dao.deleteAll()
    }

    /** Queues the day's total; a newer total for the same day replaces any still-pending one. */
    private suspend fun enqueueDaySync(date: String) {
        val entity = dao.getDay(date) ?: return
        if (entity.steps <= 0) return
        val prefix = "steps-$date"
        outboxRepository.enqueue(
            type = OUTBOX_TYPE_METRIC_INGEST,
            payload = MetricIngestPayload(
                externalIdPrefix = prefix,
                metricType = "steps",
                value = entity.steps.toDouble(),
                unit = "steps",
                recordedAtIso = Instant.ofEpochMilli(entity.updatedAtMs).toString(),
                versionMs = entity.updatedAtMs,
            ),
            serializer = MetricIngestPayload.serializer(),
            dedupeKey = prefix,
        )
    }

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            StepSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun todayIso(): String = LocalDate.now().toString()
}

@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: StepCounterRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        repository.enqueueTodaySync()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "healthos_step_sync"
    }
}
