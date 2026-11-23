package vn.uit.healthos.feature.meds

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
import javax.inject.Inject
import javax.inject.Singleton

const val OUTBOX_TYPE_DOSE_DONE = "dose.done"
const val OUTBOX_TYPE_DOSE_SKIP = "dose.skip"
const val OUTBOX_TYPE_DOSE_SNOOZE = "dose.snooze"

val DOSE_OUTBOX_TYPES: Set<String> = setOf(OUTBOX_TYPE_DOSE_DONE, OUTBOX_TYPE_DOSE_SKIP, OUTBOX_TYPE_DOSE_SNOOZE)

@Serializable
data class DoseActionOutboxPayload(
    val reminderId: String,
    val occurrenceId: String,
    val snoozeMinutes: Int = 10,
)

@Singleton
class DoseDoneOutboxHandler @Inject constructor(
    private val api: MedsApi,
    private val json: Json,
) : OutboxHandler {
    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = decodeDosePayload(json, payloadJson) ?: return parseFailure()
        return apiCall(json) {
            api.markDoseDone(payload.reminderId, ReminderOccurrenceActionBody(payload.occurrenceId))
        }.map { }
    }
}

@Singleton
class DoseSkipOutboxHandler @Inject constructor(
    private val api: MedsApi,
    private val json: Json,
) : OutboxHandler {
    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = decodeDosePayload(json, payloadJson) ?: return parseFailure()
        return apiCall(json) {
            api.skipDose(payload.reminderId, ReminderOccurrenceActionBody(payload.occurrenceId))
        }.map { }
    }
}

@Singleton
class DoseSnoozeOutboxHandler @Inject constructor(
    private val api: MedsApi,
    private val json: Json,
) : OutboxHandler {
    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = decodeDosePayload(json, payloadJson) ?: return parseFailure()
        return apiCall(json) {
            api.snoozeDose(
                payload.reminderId,
                ReminderSnoozeBody(occurrenceId = payload.occurrenceId, minutes = payload.snoozeMinutes),
            )
        }.map { }
    }
}

internal fun decodeDosePayload(json: Json, raw: String): DoseActionOutboxPayload? =
    runCatching { json.decodeFromString(DoseActionOutboxPayload.serializer(), raw) }.getOrNull()

private fun parseFailure(): ApiResult<Unit> =
    ApiResult.Failure(ApiError(ApiError.PARSE, "Invalid dose outbox payload"))

@Module
@InstallIn(SingletonComponent::class)
abstract class DoseOutboxModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_DOSE_DONE)
    abstract fun bindDoseDone(handler: DoseDoneOutboxHandler): OutboxHandler

    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_DOSE_SKIP)
    abstract fun bindDoseSkip(handler: DoseSkipOutboxHandler): OutboxHandler

    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_DOSE_SNOOZE)
    abstract fun bindDoseSnooze(handler: DoseSnoozeOutboxHandler): OutboxHandler
}
