package vn.uit.healthos.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import javax.inject.Inject

data class LeaderboardUiState(
    val period: LeaderboardPeriod = LeaderboardPeriod.DAY,
    val scope: LeaderboardScope = LeaderboardScope.FRIENDS,
    val board: LeaderboardDto? = null,
    val updatedAtMs: Long? = null,
    /** The board shown is the saved copy (the latest fetch failed or is still running). */
    val fromCache: Boolean = false,
    /** Nothing to show yet. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    /** `off`, `friends`, `global`, or null while unknown. */
    val visibility: String? = null,
) {
    val podium: List<LeaderboardEntryDto> get() = board?.entries.orEmpty().take(PODIUM_SIZE)
    val rest: List<LeaderboardEntryDto> get() = board?.entries.orEmpty().drop(PODIUM_SIZE)
    val hiddenFromBoards: Boolean get() = visibility == VISIBILITY_OFF
    val missingFromGlobal: Boolean get() = scope == LeaderboardScope.GLOBAL && visibility == VISIBILITY_FRIENDS

    companion object {
        const val PODIUM_SIZE = 3
        const val VISIBILITY_OFF = "off"
        const val VISIBILITY_FRIENDS = "friends"
    }
}

/** Step leaderboard for today or this week, among friends or everyone, with the last result kept for offline use. */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(syncSteps = true, pullToRefresh = false)
    }

    fun selectPeriod(period: LeaderboardPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load(syncSteps = false, pullToRefresh = false)
    }

    fun selectScope(scope: LeaderboardScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        load(syncSteps = false, pullToRefresh = false)
    }

    fun refresh() = load(syncSteps = true, pullToRefresh = true)

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Re-reads the visibility setting when the screen comes back (from Settings, say); a change reloads the board,
     * since it decides whether the user appears on it.
     */
    fun refreshVisibility() {
        viewModelScope.launch {
            val result = repository.visibility() as? ApiResult.Success ?: return@launch
            val previous = _state.value.visibility
            _state.update { it.copy(visibility = result.data) }
            if (previous != null && previous != result.data) load(syncSteps = false, pullToRefresh = false)
        }
    }

    private fun load(syncSteps: Boolean, pullToRefresh: Boolean) {
        loadJob?.cancel()
        val period = _state.value.period
        val scope = _state.value.scope
        loadJob = viewModelScope.launch {
            val cached = repository.cached(period, scope)
            _state.update {
                it.copy(
                    board = cached?.board,
                    updatedAtMs = cached?.fetchedAtMs,
                    fromCache = cached != null,
                    loading = cached == null,
                    refreshing = pullToRefresh,
                    error = null,
                )
            }
            if (syncSteps) {
                repository.syncTodaySteps()
                (repository.visibility() as? ApiResult.Success)?.let { res -> _state.update { it.copy(visibility = res.data) } }
            }
            when (val result = repository.fetch(period, scope)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        board = result.data.board,
                        updatedAtMs = result.data.fetchedAtMs,
                        fromCache = false,
                        loading = false,
                        refreshing = false,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.board == null) result.error.toUiText() else UiText.Res(R.string.leaderboard_load_error),
                    )
                }
            }
        }
    }
}
