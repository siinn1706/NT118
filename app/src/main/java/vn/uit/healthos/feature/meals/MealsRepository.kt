package vn.uit.healthos.feature.meals

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import vn.uit.healthos.core.data.db.Snapshot
import vn.uit.healthos.core.data.db.SnapshotCacheDao
import vn.uit.healthos.core.data.db.SnapshotStore
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealsRepository @Inject constructor(
    private val api: MealsApi,
    private val pendingMealDao: PendingMealDao,
    private val outboxRepository: OutboxRepository,
    snapshotCacheDao: SnapshotCacheDao,
    private val json: Json,
) {
    private val poller = MealPhotoPoller()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Every meal of the last seven local days as the server last returned them, with this device's own writes. */
    private val weekStore = SnapshotStore(
        dao = snapshotCacheDao,
        json = json,
        key = WEEK_CACHE_KEY,
        serializer = ListSerializer(MealResponseDto.serializer()),
    )

    /** The cached week of meals, at once from memory or disk; the Meals tab groups it by local day. */
    val cachedWeek: Flow<Snapshot<List<MealResponseDto>>?> get() = weekStore.snapshots

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

    /** Fetches the last seven local days of meals into the cache. */
    suspend fun refreshWeek(): ApiResult<Unit> {
        val today = LocalDate.now(zone)
        return when (val res = listMealsForLocalDays(today.minusDays(MealLocalDays.WEEK_DAYS - 1L), today)) {
            is ApiResult.Success -> {
                weekStore.put(res.data)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> res
        }
    }

    /** Forgets the cached meals; the next visit loads the signed-in account's own. */
    suspend fun clearCache() = weekStore.clear()

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
        val res = apiCall(json) { api.analyzePhoto(image = part, name = namePart) }
        // The upload created a server meal the cache does not hold yet.
        if (res is ApiResult.Success) weekStore.invalidate()
        return res
    }

    suspend fun pollAnalysis(jobId: String): MealPollOutcome = poller.poll {
        apiCall(json) { api.getAnalysisStatus(jobId) }
    }

    suspend fun updateAnalyzedMeal(mealId: String, body: MealUpdateBody): ApiResult<MealResponseDto> {
        val res = apiCall(json) { api.updateMeal(mealId, body).data }
        if (res is ApiResult.Success) weekStore.edit { meals -> meals.withMeal(res.data) }
        return res
    }

    suspend fun deleteMeal(mealId: String): ApiResult<Unit> {
        val res = apiCall(json) { api.deleteMeal(mealId) }
        if (res is ApiResult.Success) weekStore.edit { meals -> meals.filterNot { it.id == mealId } }
        return res
    }

    /**
     * Removes a server meal the user abandoned (a cancelled, failed or discarded photo analysis). When the delete
     * cannot reach the server it is queued in the outbox, so the abandoned meal never stays in the user's log.
     */
    suspend fun discardMeal(mealId: String) {
        val res = deleteMeal(mealId)
        // The abandoned meal leaves the cached list now, even while its delete still waits in the outbox.
        if (res is ApiResult.Failure) weekStore.edit { meals -> meals.filterNot { it.id == mealId } }
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
            is ApiResult.Success -> {
                weekStore.edit { meals -> meals.withMeal(res.data) }
                ManualMealSaveResult.Synced(res.data)
            }
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

    private fun List<MealResponseDto>.withMeal(meal: MealResponseDto): List<MealResponseDto> {
        val index = indexOfFirst { it.id == meal.id }
        return if (index >= 0) toMutableList().apply { set(index, meal) } else listOf(meal) + this
    }

    private companion object {
        const val LIST_PAGE_SIZE = 100
        const val LIST_MAX_PAGES = 10
        const val WEEK_CACHE_KEY = "meals_week"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object MealsCacheModule {
    @Provides
    @IntoSet
    fun provideMealsCacheCleanup(repository: MealsRepository): UserDataCleanup = UserDataCleanup { repository.clearCache() }
}

sealed interface ManualMealSaveResult {
    data class Synced(val meal: MealResponseDto) : ManualMealSaveResult
    data object QueuedOffline : ManualMealSaveResult
    data class Failed(val error: ApiError) : ManualMealSaveResult
}
