package vn.uit.healthos.core.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

data class SseEvent(val type: String, val data: String)

class SseException(message: String, val httpStatus: Int?, cause: Throwable? = null) : IOException(message, cause) {

    /** Lets callers show a localized message through [toUiText] instead of the transport's English text. */
    fun toApiError(): ApiError = ApiError(
        code = when {
            httpStatus == null -> ApiError.NETWORK
            httpStatus in 200..299 -> ApiError.PARSE
            else -> "HTTP_$httpStatus"
        },
        message = message.orEmpty(),
        httpStatus = httpStatus,
    )
}

/** Server-sent events over the authenticated OkHttp client; the flow completes when the server closes the stream. */
class SseClient(private val client: OkHttpClient) {

    fun events(request: Request): Flow<SseEvent> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // Runs on OkHttp's reader thread: blocking it when the buffer is full pauses the socket read
                // instead of dropping events. A cancelled collector closes the channel, which unblocks the send.
                trySendBlocking(SseEvent(type ?: "message", data))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(SseException(t?.message ?: "Stream failed", response?.code, t))
            }
        }
        val source = EventSources.createFactory(client)
            .newEventSource(request.newBuilder().header("Accept", "text/event-stream").build(), listener)
        awaitClose { source.cancel() }
    }
}
