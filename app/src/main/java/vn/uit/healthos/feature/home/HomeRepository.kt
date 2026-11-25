package vn.uit.healthos.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

data class CachedDashboard(
    val summary: DashboardSummaryDto?,
    val aiAdvice: DashboardAiAdviceDto?,
    val exercises: List<ExerciseSuggestionDto>,
    val vitals: List<ExtendedVitalPointDto>,
    val updatedAtMs: Long,
)

@Singleton
class HomeRepository @Inject constructor(
    private val api: DashboardApi,
    private val cacheDao: DashboardCacheDao,
    private val json: Json,
) {
    fun observeCached(): Flow<CachedDashboard?> = cacheDao.observe().map { entity ->
        entity?.let {
            CachedDashboard(
                summary = it.summaryJson?.let { raw ->
                    runCatching { json.decodeFromString(DashboardSummaryDto.serializer(), raw) }.getOrNull()
                },
                aiAdvice = it.aiAdviceJson?.let { raw ->
                    runCatching { json.decodeFromString(DashboardAiAdviceDto.serializer(), raw) }.getOrNull()
                },
                exercises = it.exercisesJson?.let { raw ->
                    runCatching {
                        json.decodeFromString(ListSerializer(ExerciseSuggestionDto.serializer()), raw)
                    }.getOrNull()
                }.orEmpty(),
                vitals = it.vitalsJson?.let { raw ->
                    runCatching {
                        json.decodeFromString(ListSerializer(ExtendedVitalPointDto.serializer()), raw)
                    }.getOrNull()
                }.orEmpty(),
                updatedAtMs = it.updatedAtMs,
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun refresh(): ApiResult<Unit> = coroutineScope {
        val summaryDef = async { apiCall(json) { api.getSummary().data } }
        val adviceDef = async { apiCall(json) { api.getAiAdvice().data } }
        val exercisesDef = async { apiCall(json) { api.getExerciseSuggestions().data } }
        val vitalsDef = async { apiCall(json) { api.getVitalsExtended(days = 7).data } }

        val summaryRes = summaryDef.await()
        val adviceRes = adviceDef.await()
        val exercisesRes = exercisesDef.await()
        val vitalsRes = vitalsDef.await()

        val existing = cacheDao.get()
        val anySuccess = summaryRes is ApiResult.Success ||
            adviceRes is ApiResult.Success ||
            exercisesRes is ApiResult.Success ||
            vitalsRes is ApiResult.Success

        if (anySuccess) {
            cacheDao.upsert(
                DashboardCacheEntity(
                    summaryJson = (summaryRes as? ApiResult.Success)?.data
                        ?.let { json.encodeToString(DashboardSummaryDto.serializer(), it) }
                        ?: existing?.summaryJson,
                    aiAdviceJson = (adviceRes as? ApiResult.Success)?.data
                        ?.let { json.encodeToString(DashboardAiAdviceDto.serializer(), it) }
                        ?: existing?.aiAdviceJson,
                    exercisesJson = (exercisesRes as? ApiResult.Success)?.data
                        ?.let { json.encodeToString(ListSerializer(ExerciseSuggestionDto.serializer()), it) }
                        ?: existing?.exercisesJson,
                    vitalsJson = (vitalsRes as? ApiResult.Success)?.data
                        ?.let { json.encodeToString(ListSerializer(ExtendedVitalPointDto.serializer()), it) }
                        ?: existing?.vitalsJson,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
            ApiResult.Success(Unit)
        } else {
            val failure = (summaryRes as? ApiResult.Failure)
                ?: (adviceRes as? ApiResult.Failure)
                ?: (exercisesRes as? ApiResult.Failure)
                ?: (vitalsRes as ApiResult.Failure)
            failure
        }
    }
}
