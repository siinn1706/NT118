package vn.uit.healthos.feature.leaderboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.ui.UiText
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(rank: Int, id: String, steps: Int, me: Boolean = false) =
        LeaderboardEntryDto(rank = rank, userId = id, username = id, displayName = id, steps = steps, isMe = me)

    private fun board(period: LeaderboardPeriod, scope: LeaderboardScope, entries: List<LeaderboardEntryDto>) = LeaderboardDto(
        period = period.apiValue,
        scope = scope.apiValue,
        rangeStart = "2026-11-23",
        rangeEnd = "2026-11-25",
        entries = entries,
        me = entries.firstOrNull { it.isMe },
    )

    private class FakeRepository : LeaderboardRepository {
        val calls = mutableListOf<String>()
        val cache = mutableMapOf<Pair<LeaderboardPeriod, LeaderboardScope>, LeaderboardSnapshot>()
        val boards = mutableMapOf<Pair<LeaderboardPeriod, LeaderboardScope>, ApiResult<LeaderboardDto>>()
        var visibility: ApiResult<String> = ApiResult.Success("friends")
        var now = 1_000L

        override suspend fun cached(period: LeaderboardPeriod, scope: LeaderboardScope) = cache[period to scope]

        override suspend fun syncTodaySteps() {
            calls += "sync"
        }

        override suspend fun fetch(period: LeaderboardPeriod, scope: LeaderboardScope): ApiResult<LeaderboardSnapshot> {
            calls += "fetch:${period.apiValue}:${scope.apiValue}"
            return when (val result = boards[period to scope] ?: ApiResult.Failure(ApiError(ApiError.NETWORK, "offline"))) {
                is ApiResult.Success -> ApiResult.Success(LeaderboardSnapshot(result.data, now))
                is ApiResult.Failure -> result
            }
        }

        override suspend fun visibility(): ApiResult<String> {
            calls += "visibility"
            return visibility
        }
    }

    @Test
    fun `syncs today's steps before loading and splits podium from the rest`() = runTest {
        val repository = FakeRepository()
        val entries = listOf(
            entry(1, "a", 9000),
            entry(2, "b", 8000),
            entry(2, "me", 8000, me = true),
            entry(4, "d", 100),
        )
        repository.boards[LeaderboardPeriod.DAY to LeaderboardScope.FRIENDS] =
            ApiResult.Success(board(LeaderboardPeriod.DAY, LeaderboardScope.FRIENDS, entries))
        val vm = LeaderboardViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("sync", "visibility", "fetch:day:friends"), repository.calls)
        val state = vm.state.value
        assertFalse(state.loading)
        assertFalse(state.fromCache)
        assertEquals(listOf("a", "b", "me"), state.podium.map { it.userId })
        assertEquals(listOf("d"), state.rest.map { it.userId })
        assertEquals("me", state.board?.me?.userId)
        assertEquals(1_000L, state.updatedAtMs)
        assertEquals("friends", state.visibility)
        assertFalse(state.hiddenFromBoards)
    }

    @Test
    fun `offline shows the saved board with its time and a soft error`() = runTest {
        val repository = FakeRepository()
        val saved = board(LeaderboardPeriod.DAY, LeaderboardScope.FRIENDS, listOf(entry(1, "a", 10)))
        repository.cache[LeaderboardPeriod.DAY to LeaderboardScope.FRIENDS] = LeaderboardSnapshot(saved, 500L)
        val vm = LeaderboardViewModel(repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(saved, state.board)
        assertTrue(state.fromCache)
        assertEquals(500L, state.updatedAtMs)
        assertEquals(UiText.Res(R.string.leaderboard_load_error), state.error)
        assertFalse(state.loading)
    }

    @Test
    fun `offline with nothing saved shows the error state`() = runTest {
        val vm = LeaderboardViewModel(FakeRepository())
        advanceUntilIdle()
        assertNull(vm.state.value.board)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `switching period and scope loads that board without syncing again`() = runTest {
        val repository = FakeRepository()
        repository.boards[LeaderboardPeriod.WEEK to LeaderboardScope.GLOBAL] =
            ApiResult.Success(board(LeaderboardPeriod.WEEK, LeaderboardScope.GLOBAL, listOf(entry(1, "z", 50_000))))
        val vm = LeaderboardViewModel(repository)
        advanceUntilIdle()
        repository.calls.clear()

        vm.selectPeriod(LeaderboardPeriod.WEEK)
        vm.selectScope(LeaderboardScope.GLOBAL)
        advanceUntilIdle()

        // The first switch's load is cancelled by the second.
        assertEquals(listOf("fetch:week:global"), repository.calls)
        assertEquals("z", vm.state.value.board?.entries?.single()?.userId)
        assertNull(vm.state.value.error)

        vm.selectScope(LeaderboardScope.GLOBAL)
        advanceUntilIdle()
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `pull to refresh syncs steps again`() = runTest {
        val repository = FakeRepository()
        repository.boards[LeaderboardPeriod.DAY to LeaderboardScope.FRIENDS] =
            ApiResult.Success(board(LeaderboardPeriod.DAY, LeaderboardScope.FRIENDS, emptyList()))
        val vm = LeaderboardViewModel(repository)
        advanceUntilIdle()
        repository.calls.clear()

        vm.refresh()
        assertTrue(vm.state.value.refreshing || repository.calls.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("sync", "visibility", "fetch:day:friends"), repository.calls)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `visibility off and friends-only drive the settings hints`() = runTest {
        val repository = FakeRepository().apply { visibility = ApiResult.Success("off") }
        val vm = LeaderboardViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.hiddenFromBoards)

        repository.visibility = ApiResult.Success("friends")
        repository.calls.clear()
        vm.refreshVisibility()
        advanceUntilIdle()
        assertFalse(vm.state.value.hiddenFromBoards)
        // A changed setting reloads the board.
        assertEquals(listOf("visibility", "fetch:day:friends"), repository.calls)

        vm.selectScope(LeaderboardScope.GLOBAL)
        advanceUntilIdle()
        assertTrue(vm.state.value.missingFromGlobal)

        repository.calls.clear()
        vm.refreshVisibility()
        advanceUntilIdle()
        assertEquals(listOf("visibility"), repository.calls)
    }

    @Test
    fun `numbers and dates are formatted for Vietnamese readers`() {
        assertEquals("12.345", formatSteps(12_345))
        assertEquals("0", formatSteps(0))
        assertEquals("23/11", formatDay("2026-11-23"))
        assertEquals("bad", formatDay("bad"))
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val at = 1_795_575_600_000L // 10:00 on 25/11 in Vietnam
        assertEquals("10:00", formatUpdatedAt(at, zone, nowMs = at + 60_000))
        assertEquals("10:00 25/11", formatUpdatedAt(at, zone, nowMs = at + 86_400_000))
    }
}
