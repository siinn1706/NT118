package vn.uit.healthos.feature.steps

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxHandler
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.network.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

const val OUTBOX_TYPE_METRIC_INGEST = "metric_ingest"

@Serializable
data class MetricIngestPayload(
    val externalIdPrefix: String,
    val metricType: String,
    val value: Double,
    val unit: String,
    val recordedAtIso: String,
    val versionMs: Long,
    val sourceApp: String = "vn.uit.healthos",
    val idempotencyKey: String = UUID.randomUUID().toString(),
)

/**
 * Sends queued health metrics (steps, hydration_ml, heart_rate) via `POST /v1/devices/{id}/ingest`.
 * Registers the phone once on demand and builds `external_id = "<prefix>-<deviceId>"` for server dedup.
 */
@Singleton
class StepSyncHandler @Inject constructor(
    private val deviceRegistration: DeviceRegistration,
    private val devicesApi: DevicesApi,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = runCatching {
            json.decodeFromString(MetricIngestPayload.serializer(), payloadJson)
        }.getOrElse { e ->
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid metric payload"))
        }

        val first = when (val attempt = ingestWithRegisteredDevice(payload)) {
            is IngestAttempt.Done -> return attempt.result
            is IngestAttempt.DeviceGone -> attempt
        }
        // 404: the cached device no longer exists server-side (re-seeded DB or another account). Register again once.
        deviceRegistration.invalidate(first.deviceId)
        return when (val retry = ingestWithRegisteredDevice(payload)) {
            is IngestAttempt.Done -> retry.result
            is IngestAttempt.DeviceGone -> ApiResult.Failure(retry.error)
        }
    }

    private sealed interface IngestAttempt {
        data class Done(val result: ApiResult<Unit>) : IngestAttempt
        data class DeviceGone(val deviceId: String, val error: ApiError) : IngestAttempt
    }

    private suspend fun ingestWithRegisteredDevice(payload: MetricIngestPayload): IngestAttempt {
        val deviceId = when (val reg = deviceRegistration.getOrRegisterDeviceId()) {
            is ApiResult.Success -> reg.data
            is ApiResult.Failure -> return IngestAttempt.Done(reg)
        }

        val externalId = "${payload.externalIdPrefix}-$deviceId".take(128)
        val batch = HealthIngestBatch(
            provider = "health_connect",
            records = listOf(
                HealthRecordIn(
                    externalId = externalId,
                    externalVersion = payload.versionMs,
                    metricType = payload.metricType,
                    value = payload.value,
                    unit = payload.unit,
                    recordedAt = payload.recordedAtIso,
                    sourceApp = payload.sourceApp,
                ),
            ),
        )
        val result = apiCall(json) {
            devicesApi.ingest(
                deviceId = deviceId,
                idempotencyKey = payload.idempotencyKey,
                body = batch,
            )
        }.map { }
        return if (result is ApiResult.Failure && result.error.httpStatus == 404) {
            IngestAttempt.DeviceGone(deviceId, result.error)
        } else {
            IngestAttempt.Done(result)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StepOutboxModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_METRIC_INGEST)
    abstract fun bindMetricIngestHandler(handler: StepSyncHandler): OutboxHandler
}
