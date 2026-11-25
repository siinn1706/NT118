package vn.uit.healthos.feature.friends

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeEvent
import vn.uit.healthos.core.network.RealtimeMessage
import vn.uit.healthos.core.network.RealtimeSocket
import vn.uit.healthos.core.network.RealtimeState
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.network.map
import javax.inject.Inject
import javax.inject.Singleton

/** Realtime changes the friends screen reacts to. */
sealed interface FriendsUpdate {
    data class RequestReceived(val request: FriendRequestDto) : FriendsUpdate
    data class RequestAccepted(val requestId: String, val friend: FriendDto) : FriendsUpdate
    data class Presence(val userId: String, val isOnline: Boolean) : FriendsUpdate
    data class Message(val message: RealtimeMessage) : FriendsUpdate

    /** The socket connected again: rooms must be re-joined and missed changes refetched. */
    data object Reconnected : FriendsUpdate
}

interface FriendsRepository {
    val updates: Flow<FriendsUpdate>

    suspend fun friends(): ApiResult<List<FriendDto>>
    suspend fun requests(incoming: Boolean): ApiResult<List<FriendRequestDto>>
    suspend fun sendRequest(userId: String): ApiResult<FriendRequestDto>
    suspend fun accept(requestId: String): ApiResult<FriendDto>
    suspend fun decline(requestId: String): ApiResult<Unit>
    suspend fun cancel(requestId: String): ApiResult<Unit>
    suspend fun unfriend(userId: String): ApiResult<Unit>
    suspend fun search(query: String): ApiResult<List<FriendSearchResultDto>>
    suspend fun conversations(): ApiResult<List<ConversationSummaryDto>>

    /** Returns the direct conversation with [userId], creating it when the friendship predates it. */
    suspend fun openDirect(userId: String): ApiResult<String>

    /** Joins the conversation rooms so presence (`user.status`) of those friends reaches this client. */
    fun joinConversations(conversationIds: Collection<String>)
}

@Singleton
class NetworkFriendsRepository @Inject constructor(
    private val api: FriendsApi,
    private val realtime: RealtimeSocket,
    private val json: Json,
) : FriendsRepository {

    init {
        realtime.start()
    }

    override val updates: Flow<FriendsUpdate> = merge(
        realtime.events.mapNotNull(::toUpdate),
        // The first value is the current state, not a reconnect.
        realtime.state.drop(1).filter { it == RealtimeState.CONNECTED }.map { FriendsUpdate.Reconnected },
    )

    override suspend fun friends() = apiCall(json) { api.friends().data }

    override suspend fun requests(incoming: Boolean) =
        apiCall(json) { api.requests(if (incoming) "incoming" else "outgoing").data }

    override suspend fun sendRequest(userId: String) =
        apiCall(json) { api.sendRequest(SendFriendRequestBody(userId = userId)).data }

    override suspend fun accept(requestId: String) = apiCall(json) { api.accept(requestId).data }

    override suspend fun decline(requestId: String) = apiCall(json) { api.decline(requestId) }

    override suspend fun cancel(requestId: String) = apiCall(json) { api.cancel(requestId) }

    override suspend fun unfriend(userId: String) = apiCall(json) { api.unfriend(userId) }

    override suspend fun search(query: String) = apiCall(json) { api.search(query, SEARCH_LIMIT).data }

    override suspend fun conversations() = apiCall(json) { api.conversations().data }

    override suspend fun openDirect(userId: String): ApiResult<String> =
        apiCall(json) { api.openDirect(CreateDirectConversationBody(userId)).data }.map { it.id }

    override fun joinConversations(conversationIds: Collection<String>) {
        conversationIds.forEach { id ->
            realtime.send("conv:join", JsonObject(mapOf("conversation_id" to JsonPrimitive(id))))
        }
    }

    private fun toUpdate(event: RealtimeEvent): FriendsUpdate? = when (event) {
        is RealtimeEvent.FriendRequestReceived ->
            decode(FriendRequestDto.serializer(), event.request)?.let { FriendsUpdate.RequestReceived(it) }
        is RealtimeEvent.FriendRequestAccepted ->
            decode(FriendDto.serializer(), event.friend)?.let { FriendsUpdate.RequestAccepted(event.requestId, it) }
        is RealtimeEvent.Presence -> FriendsUpdate.Presence(event.userId, event.isOnline)
        is RealtimeEvent.MessageNew -> FriendsUpdate.Message(event.message)
        else -> null
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, element: JsonObject): T? = try {
        json.decodeFromJsonElement(serializer, element)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val SEARCH_LIMIT = 20
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendsBindModule {
    @Binds
    abstract fun bindFriendsRepository(impl: NetworkFriendsRepository): FriendsRepository

    @Binds
    abstract fun bindDirectChatRepository(impl: RoomDirectChatRepository): DirectChatRepository
}
