package vn.uit.healthos.feature.meals

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

const val OUTBOX_TYPE_MEAL_CREATE = "meal.create"
const val OUTBOX_TYPE_MEAL_DELETE = "meal.delete"

@Serializable
data class MealCreateOutboxPayload(
    val localId: String,
    val idempotencyKey: String,
    val body: MealCreateBody,
)

@Serializable
data class MealDeleteOutboxPayload(
    val mealId: String,
)

/**
 * Sends offline-queued manual meals to `POST /v1/meals`. The local pending row is cleared on success and on a
 * permanent failure, because the outbox drops a permanently failed item and nothing would ever sync that row.
 */
@Singleton
class MealOutboxHandler @Inject constructor(
    private val mealsApi: MealsApi,
    private val pendingMealDao: PendingMealDao,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = runCatching {
            json.decodeFromString(MealCreateOutboxPayload.serializer(), payloadJson)
        }.getOrElse { e ->
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid meal payload"))
        }
        val result = apiCall(json) {
            mealsApi.createMeal(
                idempotencyKey = payload.idempotencyKey,
                body = payload.body,
            )
        }
        if (result !is ApiResult.Failure || !result.error.isRetryable) {
            pendingMealDao.deleteById(payload.localId)
        }
        return result.map { }
    }
}

/** Replays a queued `DELETE /v1/meals/{id}`; a meal that is already gone counts as deleted. */
@Singleton
class MealDeleteOutboxHandler @Inject constructor(
    private val mealsApi: MealsApi,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = runCatching {
            json.decodeFromString(MealDeleteOutboxPayload.serializer(), payloadJson)
        }.getOrElse { e ->
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid meal delete payload"))
        }
        return when (val result = apiCall(json) { mealsApi.deleteMeal(payload.mealId) }) {
            is ApiResult.Success -> result
            is ApiResult.Failure -> if (result.error.httpStatus == 404) ApiResult.Success(Unit) else result
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MealOutboxModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_MEAL_CREATE)
    abstract fun bindMealCreateHandler(handler: MealOutboxHandler): OutboxHandler

    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_MEAL_DELETE)
    abstract fun bindMealDeleteHandler(handler: MealDeleteOutboxHandler): OutboxHandler
}
