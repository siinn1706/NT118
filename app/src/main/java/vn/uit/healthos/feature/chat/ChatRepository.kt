package vn.uit.healthos.feature.chat

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.SseClient
import vn.uit.healthos.core.network.SseEvent
import vn.uit.healthos.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** The AI conversation as the Chat tab last showed it. */
data class ChatSession(
    val conversationId: String,
    val messages: List<ChatMessageUi>,
    val hasMoreHistory: Boolean,
    val nextCursor: String?,
    /** When the messages were last loaded from the server. */
    val syncedAtMs: Long,
)

@Singleton
class ChatRepository @Inject constructor(
    private val api: ChatApi,
    private val sseClient: SseClient,
    private val json: Json,
) {
    private val _session = MutableStateFlow<ChatSession?>(null)

    /**
     * The conversation kept in memory for the app's lifetime, so the Chat tab reopens on it at once, even when its
     * screen was recreated (a widget prompt opens a fresh Chat entry).
     */
    val session: StateFlow<ChatSession?> = _session.asStateFlow()

    fun rememberSession(session: ChatSession) {
        _session.value = session
    }

    fun clearSession() {
        _session.value = null
    }

    suspend fun getOrCreateAiConversation(): ApiResult<ConversationDto> =
        apiCall(json) { api.getOrCreateAiConversation().data }

    suspend fun getMessages(
        conversationId: String,
        before: String? = null,
        limit: Int = 30,
    ): ApiResult<MessageListResponseDto> =
        apiCall(json) { api.getMessages(conversationId = conversationId, before = before, limit = limit) }

    suspend fun uploadImage(jpegBytes: ByteArray): ApiResult<ChatAttachmentDto> {
        val body = jpegBytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("image", "chat.jpg", body)
        return apiCall(json) { api.uploadImage(part).data }
    }

    fun streamReply(conversationId: String, body: SendMessageBody): Flow<SseEvent> {
        val url = "${BuildConfig.HEALTHOS_BASE_URL}v1/conversations/$conversationId/messages/stream"
        val jsonBody = json.encodeToString(SendMessageBody.serializer(), body)
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
        return sseClient.events(request)
    }

    suspend fun createQuickReminder(body: QuickReminderCreateBody): ApiResult<QuickReminderDto> =
        apiCall(json) { api.createReminder(body).data }
}

@Module
@InstallIn(SingletonComponent::class)
object ChatSessionModule {
    @Provides
    @IntoSet
    fun provideChatSessionCleanup(repository: ChatRepository): UserDataCleanup =
        UserDataCleanup { repository.clearSession() }
}
