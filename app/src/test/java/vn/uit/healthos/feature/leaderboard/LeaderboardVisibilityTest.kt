package vn.uit.healthos.feature.leaderboard

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

class LeaderboardVisibilityTest {

    @Test
    fun `an unsent change from Settings wins over the stale server value`() = runTest {
        var flushes = 0
        val result = resolveVisibility(
            pending = { "off" },
            flushPending = { flushes++ },
            fetch = { ApiResult.Success("friends") },
        )

        assertEquals(ApiResult.Success("off"), result)
        assertEquals(1, flushes)
    }

    @Test
    fun `once the change is sent the server value is read`() = runTest {
        var pending: String? = "off"
        val result = resolveVisibility(
            pending = { pending },
            flushPending = { pending = null },
            fetch = { ApiResult.Success("off") },
        )

        assertEquals(ApiResult.Success("off"), result)
    }

    @Test
    fun `without a pending change nothing is flushed and errors pass through`() = runTest {
        val offline = ApiResult.Failure(ApiError(ApiError.NETWORK, "offline"))
        var flushes = 0
        val result = resolveVisibility(pending = { null }, flushPending = { flushes++ }, fetch = { offline })

        assertEquals(offline, result)
        assertEquals(0, flushes)
    }
}
