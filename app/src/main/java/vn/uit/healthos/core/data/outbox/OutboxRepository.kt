package vn.uit.healthos.core.data.outbox

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxRepository @Inject constructor(
    private val dao: OutboxDao,
    private val workManager: WorkManager,
    private val json: Json,
) {
    val pendingCount: Flow<Int> = dao.observeCount()

    fun pendingCount(type: String): Flow<Int> = dao.observeCountByType(type)

    /** Queues [payload]; with a [dedupeKey], older pending items carrying the same key are dropped first. */
    suspend fun <T> enqueue(type: String, payload: T, serializer: KSerializer<T>, dedupeKey: String? = null) {
        if (dedupeKey != null) dao.deleteByDedupeKey(dedupeKey)
        dao.insert(
            OutboxEntity(
                type = type,
                payloadJson = json.encodeToString(serializer, payload),
                createdAt = System.currentTimeMillis(),
                dedupeKey = dedupeKey,
            ),
        )
        scheduleSync()
    }

    fun cancelSync() {
        workManager.cancelUniqueWork(OutboxWorker.UNIQUE_NAME)
    }

    /** Runs the outbox as soon as the device is online; chains after a run already in progress. */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(OutboxWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
