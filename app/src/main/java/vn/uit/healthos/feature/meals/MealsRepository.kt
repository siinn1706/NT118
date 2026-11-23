package vn.uit.healthos.feature.meals

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Today's meals and the last seven local days of calorie totals, both grouped by the device's local date. */
data class MealsOverview(
    val todayMeals: List<MealResponseDto>,
    val weeklyCalories: List<CalorieSummaryPointDto>,
)

@Singleton
class MealsRepository @Inject constructor(
    private val api: MealsApi,
    private val pendingMealDao: PendingMealDao,
    private val outboxRepository: OutboxRepository,
    private val json: Json,
) {
    private val poller = MealPhotoPoller()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * Today's offline-created meals still waiting to sync. A row survives only while a `meal.create` outbox item
     * exists: the outbox drops items after too many attempts without telling the handler, so rows left with no
     * queued item are orphans and are removed here instead of lingering as "pending" forever.
     */
    fun observeTodayPendingMeals(): Flow<List<PendingMealEntity>> = combine(
        pendingMealDao.observeForDate(LocalDate.now(zone).toString()),
        outboxRepository.pendingCount(OUTBOX_TYPE_MEAL_CREATE),
    ) { rows, queued ->
        // Re-read the count: the two Room flows refresh independently, so a stale zero must not delete a fresh row.
        if (rows.isNotEmpty() && queued == 0 && outboxRepository.pendingCount(OUTBOX_TYPE_MEAL_CREATE).first() == 0) {
            rows.forEach { pendingMealDao.deleteById(it.localId) }
            emptyList()
        } else {
            rows
        }
    }

    suspend fun getMealsOverview(): ApiResult<MealsOverview> {
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6)
        return when (val res = listMealsForLocalDays(weekStart, today)) {
            is ApiResult.Success -> ApiResult.Success(
                MealsOverview(
                    todayMeals = MealLocalDays.withinLocalDays(res.data, today, today, zone),
                    weeklyCalories = MealLocalDays.dailyCalories(res.data, weekStart, today, zone),
                ),
            )
            is ApiResult.Failure -> res
        }
    }

    /** Fetches every meal logged in local days [from]..[to], following pages until the server runs out. */
    private suspend fun listMealsForLocalDays(from: LocalDate, to: LocalDate): ApiResult<List<MealResponseDto>> {
        val (utcFrom, utcTo) = MealLocalDays.utcQueryDates(from, to, zone)
        val meals = mutableListOf<MealResponseDto>()
        var page = 1
        while (true) {
            val res = apiCall(json) {
                api.listMeals(
                    dateFrom = utcFrom.toString(),
                    dateTo = utcTo.toString(),
                    page = page,
                    perPage = LIST_PAGE_SIZE,
                ).data
            }
            val items = when (res) {
                is ApiResult.Success -> res.data
                is ApiResult.Failure -> return res
            }
            meals += items
            if (items.size < LIST_PAGE_SIZE || page >= LIST_MAX_PAGES) break
            page++
        }
        return ApiResult.Success(MealLocalDays.withinLocalDays(meals, from, to, zone))
    }

    suspend fun uploadMealPhoto(jpegBytes: ByteArray, hintName: String? = null): ApiResult<AnalysisJobResponseDto> {
        val body = jpegBytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("image", "meal.jpg", body)
        val namePart = hintName?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
        return apiCall(json) { api.analyzePhoto(image = part, name = namePart) }
    }

    suspend fun pollAnalysis(jobId: String): MealPollOutcome = poller.poll {
        apiCall(json) { api.getAnalysisStatus(jobId) }
    }

    suspend fun updateAnalyzedMeal(mealId: String, body: MealUpdateBody): ApiResult<MealResponseDto> =
        apiCall(json) { api.updateMeal(mealId, body).data }

    suspend fun deleteMeal(mealId: String): ApiResult<Unit> =
        apiCall(json) { api.deleteMeal(mealId) }

    /**
     * Removes a server meal the user abandoned (a cancelled, failed or discarded photo analysis). When the delete
     * cannot reach the server it is queued in the outbox, so the abandoned meal never stays in the user's log.
     */
    suspend fun discardMeal(mealId: String) {
        val res = deleteMeal(mealId)
        if (res is ApiResult.Failure && res.error.isRetryable) {
            outboxRepository.enqueue(
                type = OUTBOX_TYPE_MEAL_DELETE,
                payload = MealDeleteOutboxPayload(mealId = mealId),
                serializer = MealDeleteOutboxPayload.serializer(),
                dedupeKey = "$OUTBOX_TYPE_MEAL_DELETE:$mealId",
            )
        }
    }

    suspend fun searchIngredients(query: String): ApiResult<List<IngredientDto>> =
        apiCall(json) { api.searchIngredients(query = query.trim().ifBlank { null }, perPage = 20).data }

    /**
     * Saves a manual meal directly when online; on a retryable failure queues it into the offline outbox
     * and inserts a local [PendingMealEntity] so it shows up immediately as "pending sync".
     */
    suspend fun createManualMeal(body: MealCreateBody): ManualMealSaveResult {
        val idempotencyKey = UUID.randomUUID().toString()
        val loggedAtIso = body.loggedAt ?: Instant.now().toString()
        val bodyWithTime = body.copy(loggedAt = loggedAtIso)
        return when (val res = apiCall(json) { api.createMeal(idempotencyKey = idempotencyKey, body = bodyWithTime).data }) {
            is ApiResult.Success -> ManualMealSaveResult.Synced(res.data)
            is ApiResult.Failure -> {
                if (res.error.isRetryable) {
                    val localId = UUID.randomUUID().toString()
                    // Queue first: a pending row is only kept while its outbox item exists (see observeTodayPendingMeals).
                    outboxRepository.enqueue(
                        type = OUTBOX_TYPE_MEAL_CREATE,
                        payload = MealCreateOutboxPayload(
                            localId = localId,
                            idempotencyKey = idempotencyKey,
                            body = bodyWithTime,
                        ),
                        serializer = MealCreateOutboxPayload.serializer(),
                    )
                    pendingMealDao.upsert(
                        PendingMealEntity(
                            localId = localId,
                            name = bodyWithTime.name,
                            calories = bodyWithTime.ingredients.sumOf { it.calories ?: it.manualCalories ?: 0.0 },
                            mealType = bodyWithTime.mealType,
                            loggedDate = (MealLocalDays.localDateOf(loggedAtIso, zone) ?: LocalDate.now(zone)).toString(),
                            loggedAtIso = loggedAtIso,
                        ),
                    )
                    ManualMealSaveResult.QueuedOffline
                } else {
                    ManualMealSaveResult.Failed(res.error)
                }
            }
        }
    }

    private companion object {
        const val LIST_PAGE_SIZE = 100
        const val LIST_MAX_PAGES = 10
    }
}

sealed interface ManualMealSaveResult {
    data class Synced(val meal: MealResponseDto) : ManualMealSaveResult
    data object QueuedOffline : ManualMealSaveResult
    data class Failed(val error: ApiError) : ManualMealSaveResult
}
