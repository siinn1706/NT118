package vn.uit.healthos.core.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.data.db.FRESHNESS_WINDOW_MS
import vn.uit.healthos.core.data.db.Snapshot
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstLoaderTest {

    private val offline = ApiError(ApiError.NETWORK, "offline")

    /** A repository stand-in: a cache flow plus a fetch that writes it, held open until the test releases it. */
    private class FakeSource(initial: Snapshot<String>?, private val clock: () -> Long) {
        val cache = MutableStateFlow(initial)
        var fetches = 0
        var nextResult: ApiResult<String> = ApiResult.Success("fresh")
        var gate = CompletableDeferred<Unit>().apply { complete(Unit) }

        suspend fun fetch(): ApiResult<Unit> {
            fetches++
            gate.await()
            return when (val result = nextResult) {
                is ApiResult.Success -> {
                    cache.value = Snapshot(result.data, clock())
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }

        fun hold() {
            gate = CompletableDeferred()
        }
    }

    private var now = 1_000_000L

    private fun TestScope.loaderFor(source: FakeSource) =
        CacheFirstLoader(backgroundScope, source.cache, source::fetch, clock = { now })

    @Test
    fun `shows cached data immediately and skips the fetch while it is fresh`() = runTest {
        val source = FakeSource(Snapshot("cached", now - 10_000), clock = { now })
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()

        assertEquals("cached", loader.state.value.data)
        assertFalse(loader.state.value.showSkeleton)
        assertFalse(loader.state.value.refreshing)
        assertEquals(0, source.fetches)
    }

    @Test
    fun `a background refresh keeps the cached content on screen until the new data arrives`() = runTest {
        val source = FakeSource(Snapshot("cached", now - FRESHNESS_WINDOW_MS - 1), clock = { now })
        source.hold()
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()

        val during = loader.state.value
        assertEquals("cached", during.data)
        assertTrue(during.refreshing)
        assertFalse(during.userRefreshing)
        assertFalse(during.showSkeleton)

        source.gate.complete(Unit)
        runCurrent()

        assertEquals("fresh", loader.state.value.data)
        assertFalse(loader.state.value.refreshing)
        assertEquals(1, source.fetches)
    }

    @Test
    fun `a failed refresh keeps the cached content and reports the error`() = runTest {
        val source = FakeSource(Snapshot("cached", now - FRESHNESS_WINDOW_MS - 1), clock = { now })
        source.nextResult = ApiResult.Failure(offline)
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()

        assertEquals("cached", loader.state.value.data)
        assertEquals(offline, loader.state.value.error)
        assertFalse(loader.state.value.refreshing)
    }

    @Test
    fun `the first ever load shows skeletons, then the data`() = runTest {
        val source = FakeSource(initial = null, clock = { now })
        source.hold()
        val loader = loaderFor(source)

        assertTrue("skeleton before the cache is read", loader.state.value.showSkeleton)
        runCurrent()
        assertTrue("an empty cache is not an empty day: still loading", loader.state.value.showSkeleton)
        loader.refreshIfStale()
        runCurrent()
        assertTrue(loader.state.value.showSkeleton)
        assertTrue(loader.state.value.refreshing)

        source.gate.complete(Unit)
        runCurrent()

        assertEquals("fresh", loader.state.value.data)
        assertFalse(loader.state.value.showSkeleton)
    }

    @Test
    fun `a first load that fails shows the error instead of skeletons`() = runTest {
        val source = FakeSource(initial = null, clock = { now })
        source.nextResult = ApiResult.Failure(offline)
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()

        assertNull(loader.state.value.data)
        assertEquals(offline, loader.state.value.error)
        assertFalse(loader.state.value.showSkeleton)
    }

    @Test
    fun `revisits inside the freshness window do not refetch, later ones do`() = runTest {
        val source = FakeSource(initial = null, clock = { now })
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()
        assertEquals(1, source.fetches)

        now += FRESHNESS_WINDOW_MS - 1
        loader.refreshIfStale()
        runCurrent()
        assertEquals("tab switches within the window reuse the cache", 1, source.fetches)

        now += 1
        loader.refreshIfStale()
        runCurrent()
        assertEquals(2, source.fetches)
    }

    @Test
    fun `a snapshot marked stale by a local write is refetched however young it is`() = runTest {
        val source = FakeSource(Snapshot("edited", now - 1_000, stale = true), clock = { now })
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()

        assertEquals(1, source.fetches)
        assertEquals("fresh", loader.state.value.data)
    }

    @Test
    fun `visits while a refresh runs do not start another one`() = runTest {
        val source = FakeSource(initial = null, clock = { now })
        source.hold()
        val loader = loaderFor(source)

        loader.refreshIfStale()
        loader.refreshIfStale()
        runCurrent()
        loader.refreshIfStale()
        runCurrent()

        assertEquals(1, source.fetches)
        source.gate.complete(Unit)
        runCurrent()
        assertEquals(1, source.fetches)
    }

    @Test
    fun `pull-to-refresh fetches even when fresh and after the refresh in flight`() = runTest {
        val source = FakeSource(Snapshot("cached", now - FRESHNESS_WINDOW_MS - 1), clock = { now })
        source.hold()
        val loader = loaderFor(source)

        loader.refreshIfStale()
        runCurrent()
        loader.refresh()
        runCurrent()
        assertEquals("the forced refresh waits for the one in flight", 1, source.fetches)

        source.gate.complete(Unit)
        runCurrent()

        assertEquals(2, source.fetches)
        assertFalse(loader.state.value.refreshing)
        assertEquals("fresh", loader.state.value.data)
    }

    @Test
    fun `a user refresh is flagged so only it shows the pull indicator`() = runTest {
        val source = FakeSource(Snapshot("cached", now), clock = { now })
        source.hold()
        val loader = loaderFor(source)

        loader.refresh()
        runCurrent()

        assertTrue(loader.state.value.refreshing)
        assertTrue(loader.state.value.userRefreshing)
        assertEquals("cached", loader.state.value.data)

        source.gate.complete(Unit)
        runCurrent()
        assertFalse(loader.state.value.userRefreshing)
    }
}
