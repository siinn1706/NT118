package vn.uit.healthos.feature.ppg

import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.feature.steps.MetricIngestPayload
import vn.uit.healthos.feature.steps.OUTBOX_TYPE_METRIC_INGEST
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PpgRepository @Inject constructor(
    private val outboxRepository: OutboxRepository,
) {
    /**
     * Enqueues a verified camera PPG heart-rate reading into the HealthOS metric ingest outbox.
     * Uses `metric_type = "heart_rate"` and records `source_app = "camera_ppg"` (since backend
     * `WearableSourceEnum` restricts `source` to canonical providers like `health_connect`).
     */
    suspend fun saveHeartRate(bpm: Int) {
        val safeBpm = bpm.coerceIn(30, 220)
        val now = System.currentTimeMillis()
        outboxRepository.enqueue(
            type = OUTBOX_TYPE_METRIC_INGEST,
            payload = MetricIngestPayload(
                externalIdPrefix = "ppg-$now-${UUID.randomUUID().toString().take(8)}",
                metricType = "heart_rate",
                value = safeBpm.toDouble(),
                unit = "bpm",
                recordedAtIso = Instant.ofEpochMilli(now).toString(),
                versionMs = now,
                sourceApp = "camera_ppg",
            ),
            serializer = MetricIngestPayload.serializer(),
        )
    }
}
