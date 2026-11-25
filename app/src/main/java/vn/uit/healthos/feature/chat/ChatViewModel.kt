package vn.uit.healthos.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import vn.uit.healthos.R
import vn.uit.healthos.core.data.db.FRESHNESS_WINDOW_MS
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.SseException
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.meals.ImageDownscaler
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ReminderDraftUi(
    val title: String,
    val time: String = "08:00",
    val type: String = "medicine",
    val repeat: String = "daily",
    val note: String = "",
)

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val nextCursor: String? = null,
    val inputText: String = "",
    val pendingAttachment: ChatAttachmentDto? = null,
    val uploadingImage: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isStreaming: Boolean = false,
    val emergencyBannerVisible: Boolean = false,
    val errorMessage: UiText? = null,
    val infoMessage: UiText? = null,
    val retryAvailable: Boolean = false,
    val reminderDraft: ReminderDraftUi? = null,
    val savingReminder: Boolean = false,
    /** When the messages were last loaded from the server; null before the first load. */
    val syncedAtMs: Long? = null,
)

/**
 * Puts the newest page of the conversation in place of the messages shown. When the page overlaps what is shown, the
 * older history the reader already loaded above it is kept.
 */
internal fun mergeLatestPage(shown: List<ChatMessageUi>, latest: List<ChatMessageUi>): List<ChatMessageUi> {
    val first = latest.firstOrNull() ?: return latest
    val index = shown.indexOfFirst { it.id == first.id }
    return if (index > 0) shown.subList(0, index) + latest else latest
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val json: Json,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(restoredState(repository.session.value))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var failedSend: PendingSend? = null

    init {
        // Every change to the conversation is kept in memory, so the next Chat screen opens on it at once.
        viewModelScope.launch {
            _state
                .distinctUntilChanged { old, new ->
                    old.messages === new.messages &&
                        old.conversationId == new.conversationId &&
                        old.nextCursor == new.nextCursor &&
                        old.hasMoreHistory == new.hasMoreHistory &&
                        old.syncedAtMs == new.syncedAtMs
                }
                .collect { s ->
                    val id = s.conversationId ?: return@collect
                    repository.rememberSession(ChatSession(id, s.messages, s.hasMoreHistory, s.nextCursor, s.syncedAtMs ?: 0L))
                }
        }
        if (repository.session.value == null) loadConversation()
    }

    /**
     * The tab became visible: reloads the newest messages in the background only when the conversation was last
     * loaded over a minute ago and nothing is in flight (loading, a reply streaming, a failed send waiting for retry).
     */
    fun onVisible() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.isStreaming || s.retryAvailable) return
        val synced = s.syncedAtMs
        if (synced != null && System.currentTimeMillis() - synced in 0 until FRESHNESS_WINDOW_MS) return
        loadConversation()
    }

    /**
     * Loads the conversation's newest page. Messages already shown stay on screen while it loads; a failure is only
     * reported while there is nothing to show.
     */
    fun loadConversation() {
        viewModelScope.launch {
            val shownBefore = _state.value.messages
            _state.update { it.copy(loading = true, errorMessage = null) }
            val conv = when (val res = repository.getOrCreateAiConversation()) {
                is ApiResult.Success -> res.data
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(loading = false, errorMessage = res.error.toUiText().takeIf { _ -> it.messages.isEmpty() })
                    }
                    return@launch
                }
            }
            when (val msgsRes = repository.getMessages(conv.id, limit = 30)) {
                is ApiResult.Success -> {
                    val latest = msgsRes.data.data.reversed().map { it.toUi() }
                    _state.update { cur ->
                        // A message sent while this was loading is newer than the page: keep the live list.
                        if (cur.messages !== shownBefore || cur.isStreaming) {
                            return@update cur.copy(conversationId = conv.id, loading = false)
                        }
                        val merged = mergeLatestPage(cur.messages, latest)
                        val keptOlder = merged.size > latest.size
                        cur.copy(
                            conversationId = conv.id,
                            messages = merged,
                            hasMoreHistory = if (keptOlder) cur.hasMoreHistory else msgsRes.data.hasMore,
                            nextCursor = if (keptOlder) cur.nextCursor else msgsRes.data.nextCursor,
                            emergencyBannerVisible = merged.lastOrNull()?.isEmergency == true,
                            loading = false,
                            syncedAtMs = System.currentTimeMillis(),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            conversationId = conv.id,
                            loading = false,
                            errorMessage = msgsRes.error.toUiText().takeIf { _ -> it.messages.isEmpty() },
                        )
                    }
                }
            }
        }
    }

    fun loadMoreHistory() {
        val s = _state.value
        val convId = s.conversationId ?: return
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || !s.hasMoreHistory) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val res = repository.getMessages(convId, before = cursor, limit = 30)) {
                is ApiResult.Success -> {
                    val older = res.data.data.reversed().map { it.toUi() }
                    _state.update { cur ->
                        cur.copy(
                            messages = older + cur.messages,
                            hasMoreHistory = res.data.hasMore,
                            nextCursor = res.data.nextCursor,
                            loadingMore = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(inputText = value, errorMessage = null, infoMessage = null) }
    }

    fun attachPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploadingImage = true, errorMessage = null) }
            val bytes = ImageDownscaler.downscaleToJpegBytes(context, uri)
            if (bytes == null) {
                _state.update {
                    it.copy(uploadingImage = false, errorMessage = UiText.Res(R.string.chat_attachment_unreadable))
                }
                return@launch
            }
            when (val res = repository.uploadImage(bytes)) {
                is ApiResult.Success -> _state.update {
                    it.copy(uploadingImage = false, pendingAttachment = res.data)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(uploadingImage = false, errorMessage = res.error.toUiText())
                }
            }
        }
    }

    fun clearAttachment() {
        _state.update { it.copy(pendingAttachment = null) }
    }

    fun sendPrompt(overrideText: String? = null) {
        val current = _state.value
        val convId = current.conversationId ?: return
        if (current.isStreaming) return

        val attachment = current.pendingAttachment
        val text = (overrideText ?: current.inputText).trim()
        if (text.isBlank() && attachment == null) return

        val userMessage = ChatMessageUi(
            id = "local-u-${UUID.randomUUID()}",
            isUser = true,
            content = text.ifBlank { appContext.getString(R.string.chat_image_sent) },
            attachmentUrls = listOfNotNull(attachment?.url),
        )
        val body = SendMessageBody(
            content = text.ifBlank { appContext.getString(R.string.chat_image_default_prompt) },
            contentType = if (attachment != null && text.isBlank()) "image" else "text",
            clientMessageId = UUID.randomUUID().toString(),
            attachments = listOfNotNull(attachment).takeIf { it.isNotEmpty() },
            locale = ChatLocale.of(Locale.getDefault()),
        )

        failedSend = null
        _state.update {
            it.copy(
                inputText = if (overrideText == null) "" else it.inputText,
                pendingAttachment = null,
                messages = it.messages + userMessage,
                errorMessage = null,
                retryAvailable = false,
            )
        }
        startStream(convId, body)
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _state.update { s ->
            s.copy(
                isStreaming = false,
                messages = s.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
    }

    /**
     * Re-streams the failed request with the same `client_message_id`: the server returns the
     * already-persisted user message instead of inserting a duplicate, so only the failed
     * assistant bubble is replaced locally.
     */
    fun retryLastFailed() {
        val pending = failedSend ?: return
        val current = _state.value
        val convId = current.conversationId ?: return
        if (current.isStreaming) return

        failedSend = null
        _state.update { s ->
            s.copy(
                messages = s.messages.filterNot { it.id == pending.assistantTempId },
                errorMessage = null,
                retryAvailable = false,
            )
        }
        startStream(convId, pending.body)
    }

    private fun startStream(convId: String, body: SendMessageBody) {
        val assistantTempId = "local-a-${UUID.randomUUID()}"
        _state.update {
            it.copy(
                messages = it.messages + ChatMessageUi(
                    id = assistantTempId,
                    isUser = false,
                    content = "",
                    isStreaming = true,
                ),
                isStreaming = true,
            )
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val assembler = ChatStreamAssembler(json)
            var failed = false
            repository.streamReply(convId, body)
                .catch { exc ->
                    if (exc is CancellationException) throw exc
                    if (!failed) {
                        failed = true
                        val error = (exc as? SseException)?.toApiError()?.toUiText()
                        markStreamFailed(assistantTempId, body, assembler, error)
                    }
                }
                .collect { sse ->
                    if (failed) return@collect
                    val frame = assembler.consume(sse)
                    if (frame is ChatStreamFrame.Error) {
                        failed = true
                        markStreamFailed(assistantTempId, body, assembler)
                        return@collect
                    }
                    val liveText = assembler.currentText()
                    _state.update { s ->
                        s.copy(
                            emergencyBannerVisible = s.emergencyBannerVisible || assembler.emergencyDetected,
                            messages = s.messages.map { msg ->
                                if (msg.id == assistantTempId) {
                                    msg.copy(
                                        content = liveText,
                                        isEmergency = assembler.emergencyDetected,
                                        isStreaming = !assembler.completed,
                                    )
                                } else {
                                    msg
                                }
                            },
                        )
                    }
                }

            if (failed) return@launch
            if (!assembler.completed) {
                markStreamFailed(assistantTempId, body, assembler)
                return@launch
            }

            val finalText = assembler.currentText()
            val finalId = assembler.assistantMessageId ?: assistantTempId
            val isEmergency = assembler.emergencyDetected || EmergencyDetector.isEmergencyText(finalText)
            _state.update { s ->
                s.copy(
                    isStreaming = false,
                    emergencyBannerVisible = s.emergencyBannerVisible || isEmergency,
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantTempId) {
                            msg.copy(
                                id = finalId,
                                content = finalText,
                                isStreaming = false,
                                isEmergency = isEmergency,
                            )
                        } else {
                            msg
                        }
                    },
                )
            }
        }
    }

    /**
     * Ends the stream, keeps the server's `display_text` (or the partial reply) in the bubble, and arms Retry.
     * [error] describes a transport failure; without one the screen shows the generic interruption text.
     */
    private fun markStreamFailed(
        assistantTempId: String,
        body: SendMessageBody,
        assembler: ChatStreamAssembler,
        error: UiText? = null,
    ) {
        failedSend = PendingSend(body = body, assistantTempId = assistantTempId)
        val content = assembler.error?.displayText ?: assembler.currentText()
        _state.update { s ->
            s.copy(
                isStreaming = false,
                retryAvailable = true,
                errorMessage = error,
                messages = s.messages.map { msg ->
                    if (msg.id == assistantTempId) {
                        msg.copy(content = content, isStreaming = false, isError = true)
                    } else {
                        msg
                    }
                },
            )
        }
    }

    fun openQuickReminder(answerContent: String) {
        val firstLine = answerContent.lineSequence()
            .map { it.replace("*", "").replace("#", "").trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(80)
            ?: appContext.getString(R.string.chat_reminder_default_title)
        _state.update {
            it.copy(
                reminderDraft = ReminderDraftUi(
                    title = firstLine,
                    time = "08:00",
                    type = "medicine",
                    repeat = "daily",
                    note = answerContent.take(300),
                ),
            )
        }
    }

    fun updateReminderDraft(draft: ReminderDraftUi) {
        _state.update { it.copy(reminderDraft = draft) }
    }

    fun dismissReminderDraft() {
        _state.update { it.copy(reminderDraft = null) }
    }

    fun saveQuickReminder() {
        val draft = _state.value.reminderDraft ?: return
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(savingReminder = true) }
            val res = repository.createQuickReminder(
                QuickReminderCreateBody(
                    type = draft.type,
                    title = draft.title.trim(),
                    time = draft.time.trim(),
                    repeat = draft.repeat,
                    note = draft.note.trim().ifBlank { null },
                ),
            )
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        savingReminder = false,
                        reminderDraft = null,
                        infoMessage = UiText.Res(R.string.chat_reminder_created, listOf(res.data.time)),
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(savingReminder = false, errorMessage = res.error.toUiText())
                }
            }
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    private data class PendingSend(val body: SendMessageBody, val assistantTempId: String)

    /** Opens on the conversation kept in memory, or on a first load when there is none yet. */
    private fun restoredState(session: ChatSession?): ChatUiState {
        session ?: return ChatUiState(loading = true)
        // A reply cut off when the previous screen went away is no longer streaming.
        val messages = session.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        return ChatUiState(
            conversationId = session.conversationId,
            messages = messages,
            hasMoreHistory = session.hasMoreHistory,
            nextCursor = session.nextCursor,
            emergencyBannerVisible = messages.lastOrNull()?.isEmergency == true,
            syncedAtMs = session.syncedAtMs,
        )
    }

    private fun ChatMessageDto.toUi(): ChatMessageUi = ChatMessageUi(
        id = id,
        isUser = senderKind.equals("user", ignoreCase = true),
        content = content,
        attachmentUrls = attachments.orEmpty().map { it.url },
        isEmergency = EmergencyDetector.isEmergencyMessage(this),
    )
}
