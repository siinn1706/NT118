package vn.uit.healthos.feature.leaderboard

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxFlusher
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.feature.steps.OUTBOX_TYPE_METRIC_INGEST
import vn.uit.healthos.feature.steps.StepCounterRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class LeaderboardPeriod(val apiValue: String) { DAY("day"), WEEK("week") }

enum class LeaderboardScope(val apiValue: String) { FRIENDS("friends"), GLOBAL("global") }

data class LeaderboardSnapshot(val board: LeaderboardDto, val fetchedAtMs: Long)

interface LeaderboardRepository {
    /** Last board saved for this period and scope, for offline viewing. */
    suspend fun cached(period: LeaderboardPeriod, scope: LeaderboardScope): LeaderboardSnapshot?

    /** Uploads this phone's step total for today so the user's own row is current. Best effort. */
    suspend fun syncTodaySteps()

    suspend fun fetch(period: LeaderboardPeriod, scope: LeaderboardScope): ApiResult<LeaderboardSnapshot>

    /** `off`, `friends` or `global`. */
    suspend fun visibility(): ApiResult<String>
}

@Singleton
class NetworkLeaderboardRepository @Inject constructor(
    private val api: LeaderboardApi,
    private val cacheDao: LeaderboardCacheDao,
    private val stepCounterRepository: StepCounterRepository,
    private val outboxFlusher: OutboxFlusher,
    private val json: Json,
) : LeaderboardRepository {

    override suspend fun cached(period: LeaderboardPeriod, scope: LeaderboardScope): LeaderboardSnapshot? {
        val row = cacheDao.get(key(period, scope)) ?: return null
        val board = try {
            json.decodeFromString(LeaderboardDto.serializer(), row.payloadJson)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return LeaderboardSnapshot(board, row.fetchedAtMs)
    }

    override suspend fun syncTodaySteps() {
        stepCounterRepository.enqueueTodaySync()
        // Send it now rather than waiting for the outbox worker; give up quickly when the network is slow.
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { outboxFlusher.flush(OUTBOX_TYPE_METRIC_INGEST) }
    }

    override suspend fun fetch(period: LeaderboardPeriod, scope: LeaderboardScope): ApiResult<LeaderboardSnapshot> =
        when (val result = apiCall(json) { api.steps(period.apiValue, scope.apiValue, LIMIT).data }) {
            is ApiResult.Success -> {
                val snapshot = LeaderboardSnapshot(result.data, System.currentTimeMillis())
                cacheDao.put(
                    LeaderboardCacheEntity(
                        cacheKey = key(period, scope),
                        payloadJson = json.encodeToString(LeaderboardDto.serializer(), result.data),
                        fetchedAtMs = snapshot.fetchedAtMs,
                    ),
                )
                ApiResult.Success(snapshot)
            }
            is ApiResult.Failure -> result
        }

    override suspend fun visibility(): ApiResult<String> =
        apiCall(json) { api.preferences().data.leaderboardVisibility }

    private fun key(period: LeaderboardPeriod, scope: LeaderboardScope) = "${period.apiValue}:${scope.apiValue}"

    private companion object {
        const val LIMIT = 50
        const val SYNC_TIMEOUT_MS = 5_000L
    }
}
