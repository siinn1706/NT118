package vn.uit.healthos.feature.chat

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class ChatAttachmentDto(
    val url: String,
    val name: String,
    val size: Long,
    @SerialName("mime_type") val mimeType: String,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_display_name") val senderDisplayName: String? = null,
    @SerialName("sender_kind") val senderKind: String = "user",
    val content: String = "",
    @SerialName("content_type") val contentType: String = "text",
    val attachments: List<ChatAttachmentDto>? = null,
    @SerialName("ai_metadata") val aiMetadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ConversationDto(
    val id: String,
    val type: String = "ai",
    val title: String? = null,
)

@Serializable
data class MessageListResponseDto(
    val data: List<ChatMessageDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/**
 * Request bodies are encoded with `encodeDefaults = false`, so any field the server must receive
 * (or whose server-side default differs) has no Kotlin default and is always written.
 */
@Serializable
data class SendMessageBody(
    val content: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    val attachments: List<ChatAttachmentDto>? = null,
    val locale: String,
)

@Serializable
data class QuickReminderCreateBody(
    val type: String,
    val title: String,
    val time: String,
    val repeat: String,
    val note: String? = null,
)

@Serializable
data class QuickReminderDto(
    val id: String,
    val title: String,
    val time: String,
)

interface ChatApi {
    @POST("v1/conversations/ai")
    suspend fun getOrCreateAiConversation(): DataEnvelope<ConversationDto>

    @GET("v1/conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 30,
    ): MessageListResponseDto

    @Multipart
    @POST("v1/conversations/uploads/image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): DataEnvelope<ChatAttachmentDto>

    @POST("v1/reminders")
    suspend fun createReminder(@Body body: QuickReminderCreateBody): DataEnvelope<QuickReminderDto>
}

@Module
@InstallIn(SingletonComponent::class)
object ChatNetworkModule {
    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)
}
