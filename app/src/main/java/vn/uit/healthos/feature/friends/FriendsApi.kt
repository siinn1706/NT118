package vn.uit.healthos.feature.friends

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import vn.uit.healthos.core.data.db.AppDatabase
import vn.uit.healthos.core.network.DataEnvelope
import vn.uit.healthos.core.network.RealtimeMessage
import javax.inject.Singleton

@Serializable
data class FriendUserDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
)

@Serializable
data class FriendDto(
    val user: FriendUserDto,
    val since: String,
    @SerialName("direct_conversation_id") val directConversationId: String? = null,
)

@Serializable
data class FriendRequestDto(
    val id: String,
    val status: String,
    /** `incoming` or `outgoing`, seen from the signed-in user. */
    val direction: String,
    val user: FriendUserDto,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FriendSearchResultDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /** `friends`, `outgoing`, `incoming`, or null when a request can be sent. */
    @SerialName("friendship_status") val friendshipStatus: String? = null,
    @SerialName("request_id") val requestId: String? = null,
)

/** Exactly one of [username] and [userId] is set; nulls are omitted on the wire. */
@Serializable
data class SendFriendRequestBody(
    val username: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

@Serializable
data class ConversationParticipantDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_online") val isOnline: Boolean = false,
)

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val type: String = "direct",
    val participants: List<ConversationParticipantDto> = emptyList(),
    @SerialName("last_message") val lastMessage: RealtimeMessage? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class ConversationListDto(
    val data: List<ConversationSummaryDto> = emptyList(),
)

@Serializable
data class CreateDirectConversationBody(@SerialName("target_user_id") val targetUserId: String)

@Serializable
data class DirectMessageListDto(
    val data: List<RealtimeMessage> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    /** ISO timestamp of the oldest message returned; pass it back as `before` for the next older page. */
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/** `content_type` and `client_message_id` are always written (no defaults), so the server can deduplicate retries. */
@Serializable
data class SendDirectMessageBody(
    val content: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("client_message_id") val clientMessageId: String,
)

@Serializable
data class MarkReadBody(@SerialName("last_read_message_id") val lastReadMessageId: String)

interface FriendsApi {
    @GET("v1/friends")
    suspend fun friends(): DataEnvelope<List<FriendDto>>

    @GET("v1/friends/requests")
    suspend fun requests(@Query("direction") direction: String): DataEnvelope<List<FriendRequestDto>>

    @POST("v1/friends/requests")
    suspend fun sendRequest(@Body body: SendFriendRequestBody): DataEnvelope<FriendRequestDto>

    @POST("v1/friends/requests/{requestId}/accept")
    suspend fun accept(@Path("requestId") requestId: String): DataEnvelope<FriendDto>

    @POST("v1/friends/requests/{requestId}/decline")
    suspend fun decline(@Path("requestId") requestId: String)

    @DELETE("v1/friends/requests/{requestId}")
    suspend fun cancel(@Path("requestId") requestId: String)

    @DELETE("v1/friends/{userId}")
    suspend fun unfriend(@Path("userId") userId: String)

    @GET("v1/friends/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int): DataEnvelope<List<FriendSearchResultDto>>

    @GET("v1/conversations")
    suspend fun conversations(): ConversationListDto

    @POST("v1/conversations/direct")
    suspend fun openDirect(@Body body: CreateDirectConversationBody): DataEnvelope<ConversationSummaryDto>

    @GET("v1/conversations/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: String,
        @Query("before") before: String?,
        @Query("limit") limit: Int,
    ): DirectMessageListDto

    @POST("v1/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendDirectMessageBody,
    ): DataEnvelope<RealtimeMessage>

    @POST("v1/conversations/{conversationId}/read")
    suspend fun markRead(@Path("conversationId") conversationId: String, @Body body: MarkReadBody)
}

@Module
@InstallIn(SingletonComponent::class)
object FriendsNetworkModule {
    @Provides
    @Singleton
    fun provideFriendsApi(retrofit: Retrofit): FriendsApi = retrofit.create(FriendsApi::class.java)

    @Provides
    fun provideDmMessageDao(db: AppDatabase): DmMessageDao = db.dmMessageDao()
}
