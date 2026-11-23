package vn.uit.healthos.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SseClientTest {

    private lateinit var server: MockWebServer
    private val sseClient = SseClient(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun streamOf(count: Int): MockResponse {
        val body = buildString {
            for (i in 1..count) append("event: delta\ndata: {\"seq\":$i}\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }

    private fun request(): Request = Request.Builder().url(server.url("/stream")).build()

    @Test
    fun deliversEveryEventWhenTheCollectorIsSlowerThanTheStream() = runBlocking {
        val total = 500
        server.enqueue(streamOf(total))

        val received = mutableListOf<String>()
        withTimeout(10_000) {
            sseClient.events(request()).collect { event ->
                // Stall on the first event so the whole body is read while the collector is busy.
                if (received.isEmpty()) delay(300)
                received += event.data
            }
        }

        assertEquals(total, received.size)
        assertEquals((1..total).map { "{\"seq\":$it}" }, received)
    }

    @Test
    fun cancellingTheCollectorReleasesABlockedReader() = runBlocking {
        server.enqueue(streamOf(2_000))

        val firstFive = withTimeout(10_000) {
            sseClient.events(request()).take(5).toList()
        }

        assertEquals(5, firstFive.size)
        assertEquals("delta", firstFive.first().type)
    }

    @Test
    fun httpErrorFailsTheFlowWithItsStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"x","message":"Upstream"}}"""))

        try {
            withTimeout(10_000) { sseClient.events(request()).collect() }
            fail("Expected SseException")
        } catch (e: SseException) {
            assertEquals(503, e.httpStatus)
            assertEquals("HTTP_503", e.toApiError().code)
            assertEquals(503, e.toApiError().httpStatus)
        }
    }

    @Test
    fun transportFailureMapsToNetworkError() {
        val error = SseException("timeout", httpStatus = null).toApiError()

        assertEquals(ApiError.NETWORK, error.code)
        assertNull(error.httpStatus)
    }

    @Test
    fun successfulResponseThatIsNotAStreamMapsToUnexpectedResponse() {
        val error = SseException("Invalid content-type", httpStatus = 200).toApiError()

        assertEquals(ApiError.PARSE, error.code)
    }
}
