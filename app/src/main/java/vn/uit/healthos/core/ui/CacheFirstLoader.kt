package vn.uit.healthos.core.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.db.FRESHNESS_WINDOW_MS
import vn.uit.healthos.core.data.db.Snapshot
import vn.uit.healthos.core.data.db.isFresh
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

/** What a cache-first tab shows: the last known data straight away, plus whether a background refresh is running. */
data class CacheFirstState<out T>(
    val data: T? = null,
    val updatedAtMs: Long? = null,
    /** A refresh is running; with data on screen this is only a subtle hint, never a blocking spinner. */
    val refreshing: Boolean = false,
    /** The running refresh was asked for by the user (pull-to-refresh, the refresh button, retry). */
    val userRefreshing: Boolean = false,
    /** Why the last refresh failed, cleared by the next success. */
    val error: ApiError? = null,
    /** At least one fetch has finished, whatever its outcome. */
    val fetched: Boolean = false,
) {
    /** Nothing cached and no fetch finished yet: the tab shows skeleton placeholders, not an empty state. */
    val showSkeleton: Boolean get() = data == null && !fetched
}

/**
 * Cache-first loading for one tab. [cache] emits the persisted or in-memory snapshot (a repository writes it when
 * [fetch] succeeds); the state carries it as soon as it is read. [refreshIfStale] fetches only when the snapshot is
 * missing, stale or older than [windowMs]; [refresh] always fetches. A refresh never clears data already shown.
 */
class CacheFirstLoader<T : Any>(
    private val scope: CoroutineScope,
    cache: Flow<Snapshot<T>?>,
    private val fetch: suspend () -> ApiResult<*>,
    private val windowMs: Long = FRESHNESS_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(CacheFirstState<T>())
    val state: StateFlow<CacheFirstState<T>> = _state.asStateFlow()

    private val firstRead = CompletableDeferred<Unit>()

    @Volatile
    private var snapshot: Snapshot<T>? = null
    private var refreshJob: Job? = null

    init {
        scope.launch {
            cache.collect { latest ->
                snapshot = latest
                _state.update { it.copy(data = latest?.value, updatedAtMs = latest?.updatedAtMs) }
                firstRead.complete(Unit)
            }
        }
    }

    /** Called on every visit: refreshes in the background unless the cached data is still fresh. */
    fun refreshIfStale() = launchRefresh(userRequested = false, force = false)

    /** Refreshes now, whatever the cache's age; [userRequested] shows the pull-to-refresh indicator. */
    fun refresh(userRequested: Boolean = true) = launchRefresh(userRequested = userRequested, force = true)

    private fun launchRefresh(userRequested: Boolean, force: Boolean) {
        val running = refreshJob
        if (running?.isActive == true && !force) return
        refreshJob = scope.launch {
            // A forced refresh queues behind the one in flight, which may have started before a write it must see.
            running?.join()
            firstRead.await()
            if (!force && snapshot.isFresh(clock(), windowMs)) return@launch
            _state.update { it.copy(refreshing = true, userRefreshing = userRequested) }
            // A cancelled refresh (the screen went away) leaves the previous outcome as it was.
            var error: ApiError? = _state.value.error
            var fetched = _state.value.fetched
            try {
                error = (fetch() as? ApiResult.Failure)?.error
                fetched = true
            } finally {
                _state.update { it.copy(refreshing = false, userRefreshing = false, error = error, fetched = fetched) }
            }
        }
    }
}
