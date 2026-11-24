package vn.uit.healthos.feature.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeEvent
import vn.uit.healthos.core.network.RealtimeState
import vn.uit.healthos.core.ui.UiText
import java.util.UUID
import javax.inject.Inject

data class DmMessageUi(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val status: String,
    val createdAtMs: Long,
    val clientMessageId: String?,
)

data class DirectChatUiState(
    val peerName: String = "",
    /** Null until a presence event for the peer arrives. */
    val peerOnline: Boolean? = null,
    val messages: List<DmMessageUi> = emptyList(),
    val input: String = "",
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    /** Newest page could not load; cached messages stay visible. */
    val loadError: UiText? = null,
    val peerTyping: Boolean = false,
    val connected: Boolean = false,
)

/**
 * One direct conversation. History is read from the Room cache, which the network fills page by page (cursor
 * `before`). Sending is optimistic: the message shows at once, goes over the socket with a client message id, and the
 * server's `msg:ack` swaps the temporary id for the real one. Without a socket, or without an ack in time, the
 * message goes to the outbox, which delivers it over REST when online; the server deduplicates on the client id.
 */
@HiltViewModel
class DirectChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DirectChatRepository,
) : ViewModel() {

    private val conversationId: String = requireNotNull(savedStateHandle[ARG_CONVERSATION_ID])
    private val peerId: String = requireNotNull(savedStateHandle[ARG_PEER_ID])

    private val _state = MutableStateFlow(DirectChatUiState(peerName = savedStateHandle[ARG_PEER_NAME] ?: ""))
    val state: StateFlow<DirectChatUiState> = _state.asStateFlow()

    private var nextCursor: String? = null
    private var firstPageLoaded = false
    private var lastMarkedRead: String? = null
    private var typingActive = false
    private var typingSentAtMs = 0L
    private var peerTypingReset: Job? = null

    init {
        viewModelScope.launch {
            repository.observeMessages(conversationId).collect { rows ->
                val me = repository.currentUserId()
                val messages = rows.map { row ->
                    DmMessageUi(
                        id = row.id,
                        content = row.content,
                        isMine = row.status != DmStatus.SENT || (row.senderId != null && row.senderId == me),
                        status = row.status,
                        createdAtMs = row.createdAtMs,
                        clientMessageId = row.clientMessageId,
                    )
                }
                _state.update { it.copy(messages = messages) }
                markReadIfNeeded(rows)
            }
        }
        viewModelScope.launch { repository.events.collect(::onEvent) }
        viewModelScope.launch {
            var first = true
            repository.connection.collect { connection ->
                val connected = connection == RealtimeState.CONNECTED
                _state.update { it.copy(connected = connected) }
                if (connected) {
                    repository.joinRoom(conversationId)
                    // After a reconnect, fetch what arrived while the socket was down.
                    if (!first) loadLatest(trimCache = false)
                }
                first = false
            }
        }
        loadLatest(trimCache = true)
    }

    /** Fetches the newest messages again (retry after a failed load). */
    fun reload() = loadLatest(trimCache = false)

    private fun loadLatest(trimCache: Boolean) {
        viewModelScope.launch {
            when (val result = repository.loadLatest(conversationId, trimCache)) {
                is ApiResult.Success -> {
                    if (!firstPageLoaded) {
                        nextCursor = result.data.nextCursor
                        _state.update { it.copy(hasMore = result.data.hasMore) }
                        firstPageLoaded = true
                    }
                    _state.update { it.copy(loading = false, loadError = null) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, loadError = UiText.Res(R.string.dm_load_error))
                }
            }
        }
    }

    fun loadOlder() {
        val cursor = nextCursor ?: return
        val current = _state.value
        if (!current.hasMore || current.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            when (val result = repository.loadOlder(conversationId, cursor)) {
                is ApiResult.Success -> {
                    nextCursor = result.data.nextCursor
                    _state.update { it.copy(loadingOlder = false, hasMore = result.data.hasMore && result.data.nextCursor != null) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingOlder = false, loadError = UiText.Res(R.string.dm_load_error))
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
        if (text.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (!typingActive || now - typingSentAtMs > TYPING_REFRESH_MS) {
                repository.sendTyping(conversationId, true)
                typingActive = true
                typingSentAtMs = now
            }
        } else {
            stopTyping()
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(input = "") }
        stopTyping()
        val clientMessageId = UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.insertPending(conversationId, text, clientMessageId)
            deliver(text, clientMessageId)
        }
    }

    /** Sends a failed message again with the same client id, so it can never be stored twice. */
    fun retry(message: DmMessageUi) {
        val clientMessageId = message.clientMessageId ?: return
        if (message.status != DmStatus.FAILED) return
        viewModelScope.launch {
            repository.markPending(clientMessageId)
            deliver(message.content, clientMessageId)
        }
    }

    private suspend fun deliver(text: String, clientMessageId: String) {
        if (repository.sendOverSocket(conversationId, text, clientMessageId)) {
            repository.queueIfUnacknowledged(conversationId, text, clientMessageId)
        } else {
            repository.enqueue(conversationId, text, clientMessageId)
        }
    }

    private suspend fun onEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MessageAck -> {
                val clientMessageId = event.clientMessageId ?: return
                repository.applyAck(clientMessageId, event.serverMessageId, event.createdAt)
            }
            is RealtimeEvent.MessageNew -> {
                if (event.message.conversationId != conversationId) return
                repository.store(event.message)
                if (event.message.senderId == peerId) setPeerTyping(false)
            }
            is RealtimeEvent.Typing -> {
                if (event.conversationId == conversationId && event.userId == peerId) setPeerTyping(event.isTyping)
            }
            is RealtimeEvent.Presence -> {
                if (event.userId == peerId) _state.update { it.copy(peerOnline = event.isOnline) }
            }
            else -> Unit
        }
    }

    private fun setPeerTyping(typing: Boolean) {
        peerTypingReset?.cancel()
        _state.update { it.copy(peerTyping = typing) }
        if (typing) {
            // The peer's "stopped typing" can be lost; never show the indicator forever.
            peerTypingReset = viewModelScope.launch {
                delay(PEER_TYPING_TIMEOUT_MS)
                _state.update { it.copy(peerTyping = false) }
            }
        }
    }

    private fun markReadIfNeeded(rows: List<DmMessageEntity>) {
        val latestFromPeer = rows.lastOrNull { it.senderId == peerId && it.status == DmStatus.SENT } ?: return
        if (latestFromPeer.id == lastMarkedRead) return
        lastMarkedRead = latestFromPeer.id
        viewModelScope.launch { repository.markRead(conversationId, latestFromPeer.id) }
    }

    private fun stopTyping() {
        if (!typingActive) return
        typingActive = false
        repository.sendTyping(conversationId, false)
    }

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }

    companion object {
        const val ARG_CONVERSATION_ID = "conversationId"
        const val ARG_PEER_ID = "peerId"
        const val ARG_PEER_NAME = "peerName"
        const val TYPING_REFRESH_MS = 3_000L
        const val PEER_TYPING_TIMEOUT_MS = 6_000L
    }
}
