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

    private companion object {
        const val MAX_SHIFT = 16
        const val JITTER = 0.2
    }
}

/**
 * The app's single connection to the backend `/ws` socket.
 *
 * It stays connected only while [active] is true (signed in and the app in the foreground), authenticating each
 * connection with a fresh short-lived ticket sent as the first frame. Server pings are answered with `pong`, and a
 * dropped connection is retried with [ReconnectBackoff] (1 to 30 seconds). Features read [events] and watch [state]
 * to re-join their rooms after a reconnect.
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
                    val opened = connectOnce()
                    if (opened) attempt = 0
                    val wait = backoff.delayFor(attempt)
                    attempt++
                    log("Realtime socket closed; reconnecting in $wait ms")
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

    /** Runs one connection until it closes; returns whether it opened. */
    private suspend fun connectOnce(): Boolean {
        _state.value = RealtimeState.CONNECTING
        val ticket = when (val result = ticketProvider()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                log("Realtime ticket unavailable: ${result.error.code}")
                _state.value = RealtimeState.DISCONNECTED
                return false
            }
        }
        val opened = AtomicBoolean(false)
        val closed = CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(RealtimeFrames.auth(json, ticket))
                opened.set(true)
                socket = webSocket
                _state.value = RealtimeState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parser.parse(text) ?: return
                if (event == RealtimeEvent.Ping) {
                    webSocket.send(RealtimeFrames.pong(json))
                    return
                }
                if (event is RealtimeEvent.Error) log("Realtime error frame: ${event.code}")
                _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Realtime socket failed: ${t.javaClass.simpleName}")
                closed.complete(Unit)
            }
        }
        val webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            closed.await()
        } finally {
            // Also runs when the app leaves the foreground or signs out (collectLatest cancels this coroutine).
            if (!webSocket.close(NORMAL_CLOSURE, null)) webSocket.cancel()
            if (socket === webSocket) socket = null
            _state.value = RealtimeState.DISCONNECTED
        }
        return opened.get()
    }

    private companion object {
        const val TAG = "RealtimeSocket"
        const val NORMAL_CLOSURE = 1000
        const val EVENT_BUFFER = 64
    }
}
