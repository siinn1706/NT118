package vn.uit.healthos.core.network

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

enum class RealtimeState { DISCONNECTED, CONNECTING, CONNECTED }

/** Reconnect delays: doubling from [baseMs] up to [maxMs], with up to 20 % jitter taken off (never below [baseMs]). */
class ReconnectBackoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val random: () -> Double = { Random.nextDouble() },
) {
    fun delayFor(attempt: Int): Long {
        val exponent = attempt.coerceIn(0, MAX_SHIFT)
        val ceiling = (baseMs shl exponent).coerceAtMost(maxMs)
        val jittered = ceiling - (ceiling * JITTER * random()).toLong()
        return jittered.coerceIn(baseMs, maxMs)
    }

    /** The capped delay (still jittered), used when the server says it is full. */
    fun maxDelay(): Long = delayFor(MAX_SHIFT)

    internal companion object {
        const val MAX_SHIFT = 16
        const val JITTER = 0.2
    }
}

/**
 * The app's single connection to the backend `/ws` socket.
 *
 * It stays connected only while [active] is true (signed in and the app in the foreground), authenticating each
 * connection with a fresh short-lived ticket sent as the first frame. Server pings are answered with `pong`, and a
 * dropped connection is retried with [ReconnectBackoff] (1 to 30 seconds).
 *
 * The backend checks the ticket and its connection limits only after the socket opened, so an open alone does not
 * reset the backoff: the connection must deliver a non-error frame or stay up for [healthyAfterMs]. A close with
 * 1013 (server at its global limit) or 4008 (too many sockets for this user) is retried after the longest delay.
 * Features read [events] and watch [state] to re-join their rooms after a reconnect.
 */
class RealtimeSocket(
    private val client: OkHttpClient,
    private val url: HttpUrl,
    private val json: Json,
    private val ticketProvider: suspend () -> ApiResult<String>,
    private val active: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val log: (String) -> Unit = { Log.d(TAG, it) },
    private val healthyAfterMs: Long = HEALTHY_AFTER_MS,
    private val nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) {
    private val parser = RealtimeEventParser(json)
    private val started = AtomicBoolean(false)

    private val _events = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(RealtimeState.DISCONNECTED)
    val state: StateFlow<RealtimeState> = _state.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    /** How one connection attempt ended: whether the server accepted it, and the close code it sent, if any. */
    private class Outcome(val healthy: Boolean, val closeCode: Int?)

    /** Starts following [active]; calling it again does nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            active.distinctUntilChanged().collectLatest { shouldConnect ->
                if (!shouldConnect) {
                    _state.value = RealtimeState.DISCONNECTED
                    return@collectLatest
                }
                var attempt = 0
                while (true) {
                    val outcome = connectOnce()
                    if (outcome.healthy) attempt = 0
                    val wait = if (outcome.closeCode in SERVER_FULL_CODES) {
                        backoff.maxDelay()
                    } else {
                        backoff.delayFor(attempt)
                    }
                    attempt = (attempt + 1).coerceAtMost(ReconnectBackoff.MAX_SHIFT)
                    log("Realtime socket closed (code ${outcome.closeCode}); reconnecting in $wait ms")
                    delay(wait)
                }
            }
        }
    }

    /** Sends a client event; false when not connected (the caller falls back to REST). */
    fun send(event: String, payload: JsonObject): Boolean {
        val current = socket ?: return false
        if (_state.value != RealtimeState.CONNECTED) return false
        return current.send(RealtimeFrames.event(json, event, payload))
    }

    /** Runs one connection until it closes. */
    private suspend fun connectOnce(): Outcome {
        _state.value = RealtimeState.CONNECTING
        val ticket = when (val result = ticketProvider()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                log("Realtime ticket unavailable: ${result.error.code}")
                _state.value = RealtimeState.DISCONNECTED
                return Outcome(healthy = false, closeCode = null)
            }
        }
        // Once this attempt has finished (closed, or cancelled while the handshake was still running), OkHttp can
        // still deliver onOpen for it; the lock and flag keep that late socket from being published as CONNECTED.
        val lock = Any()
        var finished = false
        var openedAt: Long? = null
        val acceptedByServer = AtomicBoolean(false)
        val closeCode = AtomicInteger(NO_CODE)
        val closed = CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (finished) {
                        webSocket.cancel()
                        return
                    }
                    webSocket.send(RealtimeFrames.auth(json, ticket))
                    openedAt = nowMs()
                    socket = webSocket
                    _state.value = RealtimeState.CONNECTED
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (synchronized(lock) { finished }) return
                val event = parser.parse(text)
                if (event !is RealtimeEvent.Error) acceptedByServer.set(true)
                if (event == null) return
                if (event == RealtimeEvent.Ping) {
                    webSocket.send(RealtimeFrames.pong(json))
                    return
                }
                if (event is RealtimeEvent.Error) log("Realtime error frame: ${event.code}")
                _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeCode.compareAndSet(NO_CODE, code)
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeCode.compareAndSet(NO_CODE, code)
                release(webSocket)
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Realtime socket failed: ${t.javaClass.simpleName}")
                release(webSocket)
                closed.complete(Unit)
            }

            private fun release(webSocket: WebSocket) = synchronized(lock) {
                if (socket === webSocket) {
                    socket = null
                    _state.value = RealtimeState.DISCONNECTED
                }
            }
        }
        val webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            closed.await()
        } finally {
            // Also runs when the app leaves the foreground or signs out (collectLatest cancels this coroutine).
            synchronized(lock) {
                finished = true
                if (socket === webSocket) socket = null
                _state.value = RealtimeState.DISCONNECTED
            }
            if (!webSocket.close(NORMAL_CLOSURE, null)) webSocket.cancel()
        }
        val opened = openedAt
        val livedLongEnough = opened != null && nowMs() - opened >= healthyAfterMs
        return Outcome(
            healthy = acceptedByServer.get() || livedLongEnough,
            closeCode = closeCode.get().takeUnless { it == NO_CODE },
        )
    }

    private companion object {
        const val TAG = "RealtimeSocket"
        const val NORMAL_CLOSURE = 1000
        const val EVENT_BUFFER = 64
        const val NO_CODE = -1
        const val HEALTHY_AFTER_MS = 10_000L
        const val NANOS_PER_MS = 1_000_000L

        /** 1013 "try again later" (global connection limit) and 4008 (per-user connection limit). */
        val SERVER_FULL_CODES = setOf(1013, 4008)
    }
}
