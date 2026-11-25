package vn.uit.healthos.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReconnectBackoffTest {

    @Test
    fun `delay doubles from one second and is capped at thirty`() {
        val backoff = ReconnectBackoff(random = { 0.0 })
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map { backoff.delayFor(it) },
        )
        assertEquals(30_000L, backoff.delayFor(500))
    }

    @Test
    fun `jitter only shortens the delay and never below the base`() {
        val backoff = ReconnectBackoff(random = { 1.0 })
        assertEquals(1_000L, backoff.delayFor(0))
        assertEquals(3_200L, backoff.delayFor(2))
        assertEquals(24_000L, backoff.delayFor(10))
    }
}

class RealtimeSocketTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    /** Frames the fake backend received, and the server side of the latest connection. */
    private val received = LinkedBlockingQueue<String>()
    @Volatile private var serverSocket: WebSocket? = null

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSocket = webSocket
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received.put(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    /** Reconnect delays the socket logged, in order. */
    private val waits = LinkedBlockingQueue<Long>()

    private fun socket(active: MutableStateFlow<Boolean>, tickets: AtomicInteger = AtomicInteger()) = RealtimeSocket(
        client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
        url = server.url("/ws"),
        json = json,
        ticketProvider = { ApiResult.Success("ticket-${tickets.incrementAndGet()}") },
        active = active,
        scope = scope,
        backoff = ReconnectBackoff(baseMs = 50, maxMs = 400, random = { 0.0 }),
        log = { line -> WAIT.find(line)?.let { waits.put(it.groupValues[1].toLong()) } },
    )

    /** Server side that reads the auth frame, optionally answers with [reply], then closes with [code]. */
    private fun rejecting(code: Int, reply: String? = null) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            received.put(text)
            reply?.let(webSocket::send)
            webSocket.close(code, "rejected")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    }

    private fun nextWait(): Long = requireNotNull(waits.poll(5, TimeUnit.SECONDS)) { "no reconnect scheduled" }

    private fun nextFrame(): String = requireNotNull(received.poll(5, TimeUnit.SECONDS)) { "no frame received" }

    @Test
    fun `authenticates with the ticket first, answers pings and emits typed events`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val socket = socket(MutableStateFlow(true))
        socket.start()

        withTimeout(5_000) { socket.state.first { it == RealtimeState.CONNECTED } }
        assertTrue(nextFrame().contains(""""ticket":"ticket-1""""))

        serverSocket!!.send("""{"event":"ping","payload":{},"timestamp":"t"}""")
        assertTrue(nextFrame().contains(""""event":"pong""""))

        val event = scope.async(start = CoroutineStart.UNDISPATCHED) { socket.events.first { it is RealtimeEvent.MessageAck } }
        serverSocket!!.send("""{"event":"msg:ack","payload":{"client_message_id":"k1","server_message_id":"m1"}}""")
        assertEquals(RealtimeEvent.MessageAck("k1", "m1", null), withTimeout(5_000) { event.await() })

        assertTrue(socket.send("typing", JsonObject(mapOf("conversation_id" to JsonPrimitive("c1")))))
        assertTrue(nextFrame().contains(""""event":"typing""""))
    }

    @Test
    fun `reconnects with a fresh ticket after the server drops the connection`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val tickets = AtomicInteger()
        val socket = socket(MutableStateFlow(true), tickets)
        socket.start()

        withTimeout(5_000) { socket.state.first { it == RealtimeState.CONNECTED } }
        assertTrue(nextFrame().contains("ticket-1"))
        serverSocket!!.close(4401, "bye")

        assertTrue(nextFrame().contains("ticket-2"))
        withTimeout(5_000) { socket.state.first { it == RealtimeState.CONNECTED } }
        assertEquals(2, tickets.get())
    }

    @Test
    fun `an open the server then rejects does not reset the backoff`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().withWebSocketUpgrade(rejecting(4401))) }
        server.enqueue(MockResponse().withWebSocketUpgrade(rejecting(4401, reply = PING)))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        socket(MutableStateFlow(true)).start()

        assertEquals(listOf(50L, 100L, 200L), List(3) { nextWait() })
        // A connection that delivered a real frame counts as accepted, so the next wait starts over.
        assertEquals(50L, nextWait())
    }

    @Test
    fun `a server at its connection limit is retried after the longest delay`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(rejecting(1013)))
        server.enqueue(MockResponse().withWebSocketUpgrade(rejecting(4008)))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        socket(MutableStateFlow(true)).start()

        assertEquals(listOf(400L, 400L), List(2) { nextWait() })
    }

    @Test
    fun `a handshake that completes after leaving the foreground stays disconnected`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener).setHeadersDelay(500, TimeUnit.MILLISECONDS))
        val active = MutableStateFlow(true)
        val socket = socket(active)
        socket.start()
        withTimeout(5_000) { socket.state.first { it == RealtimeState.CONNECTING } }

        active.value = false
        withTimeout(5_000) { socket.state.first { it == RealtimeState.DISCONNECTED } }
        Thread.sleep(1_000)

        assertEquals(RealtimeState.DISCONNECTED, socket.state.value)
        assertFalse(socket.send("typing", JsonObject(emptyMap())))
        assertEquals(null, received.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `disconnects when inactive and refuses to send`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val active = MutableStateFlow(true)
        val socket = socket(active)
        socket.start()
        withTimeout(5_000) { socket.state.first { it == RealtimeState.CONNECTED } }

        active.value = false
        withTimeout(5_000) { socket.state.first { it == RealtimeState.DISCONNECTED } }
        assertFalse(socket.send("typing", JsonObject(emptyMap())))
    }

    private companion object {
        val WAIT = Regex("""reconnecting in (\d+) ms""")
        const val PING = """{"event":"ping","payload":{},"timestamp":"t"}"""
    }
}
