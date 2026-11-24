package vn.uit.healthos.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import javax.inject.Inject

enum class FriendsSegment { FRIENDS, REQUESTS, SEARCH }

data class FriendRowUi(
    val friend: FriendDto,
    val isOnline: Boolean,
    val conversationId: String?,
    val lastMessage: String? = null,
    val lastMessageMine: Boolean = false,
    val unread: Int = 0,
) {
    val userId: String get() = friend.user.id
}

data class FriendsUiState(
    val segment: FriendsSegment = FriendsSegment.FRIENDS,
    val friends: List<FriendRowUi> = emptyList(),
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
    val query: String = "",
    val searchResults: List<FriendSearchResultDto> = emptyList(),
    /** Query whose results are shown; null before the first search. */
    val searchedQuery: String? = null,
    val searching: Boolean = false,
    val searchError: UiText? = null,
    /** First load with nothing to show yet. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Why the friend list could not load; shown only while the list is empty. */
    val loadError: UiText? = null,
    /** Request, user or friend ids with an action in flight. */
    val busyIds: Set<String> = emptySet(),
    val notice: UiText? = null,
    val noticeIsError: Boolean = false,
    val confirmUnfriend: FriendDto? = null,
)

sealed interface FriendsEvent {
    data class OpenChat(val conversationId: String, val peerId: String, val peerName: String) : FriendsEvent
}

/** Friend list, requests and username search, kept current by the realtime socket. */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: FriendsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private val _events = Channel<FriendsEvent>(Channel.BUFFERED)
    val events: Flow<FriendsEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { repository.updates.collect(::onUpdate) }
        load(pullToRefresh = false)
    }

    fun refresh() = load(pullToRefresh = true)

    fun selectSegment(segment: FriendsSegment) = _state.update { it.copy(segment = segment) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun load(pullToRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = pullToRefresh, loadError = null) }
            val friends = async { repository.friends() }
            val incoming = async { repository.requests(incoming = true) }
            val outgoing = async { repository.requests(incoming = false) }
            val conversations = async { repository.conversations() }

            val conversationList = (conversations.await() as? ApiResult.Success)?.data.orEmpty()
            when (val result = friends.await()) {
                is ApiResult.Success -> {
                    val rows = result.data.map { friendRow(it, conversationList) }
                    _state.update { it.copy(friends = rows) }
                    repository.joinConversations(rows.mapNotNull { it.conversationId })
                }
                is ApiResult.Failure -> _state.update {
                    if (it.friends.isEmpty()) it.copy(loadError = friendsErrorText(result.error)) else it.withError(result.error)
                }
            }
            (incoming.await() as? ApiResult.Success)?.let { res -> _state.update { it.copy(incoming = res.data) } }
            (outgoing.await() as? ApiResult.Success)?.let { res -> _state.update { it.copy(outgoing = res.data) } }
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, searchError = null) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchResults = emptyList(), searchedQuery = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(trimmed)
        }
    }

    fun searchNow() {
        val trimmed = _state.value.query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(trimmed) }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true, searchError = null) }
        when (val result = repository.search(query.take(MAX_QUERY))) {
            is ApiResult.Success -> _state.update {
                it.copy(searchResults = result.data, searchedQuery = query, searching = false)
            }
            is ApiResult.Failure -> _state.update {
                it.copy(searching = false, searchError = friendsErrorText(result.error))
            }
        }
    }

    fun sendRequest(result: FriendSearchResultDto) = withBusy(result.id) {
        when (val response = repository.sendRequest(result.id)) {
            is ApiResult.Success -> {
                val request = response.data
                if (request.status == STATUS_ACCEPTED) {
                    // They had already invited us: the server accepted both ways.
                    setSearchStatus(result.id, FRIENDS, null)
                    _state.update {
                        it.copy(
                            incoming = it.incoming.filterNot { r -> r.user.id == result.id },
                            notice = UiText.Res(R.string.friends_now_friends, listOf(result.displayName)),
                            noticeIsError = false,
                        )
                    }
                    load(pullToRefresh = false)
                } else {
                    setSearchStatus(result.id, OUTGOING, request.id)
                    _state.update {
                        it.copy(
                            outgoing = listOf(request) + it.outgoing.filterNot { r -> r.id == request.id },
                            notice = UiText.Res(R.string.friends_request_sent_to, listOf(result.displayName)),
                            noticeIsError = false,
                        )
                    }
                }
            }
            is ApiResult.Failure -> _state.update { it.withError(response.error) }
        }
    }

    fun accept(request: FriendRequestDto) = withBusy(request.id) {
        when (val response = repository.accept(request.id)) {
            is ApiResult.Success -> {
                addFriend(response.data)
                _state.update {
                    it.copy(
                        incoming = it.incoming.filterNot { r -> r.id == request.id },
                        notice = UiText.Res(R.string.friends_now_friends, listOf(request.user.displayName)),
                        noticeIsError = false,
                    )
                }
            }
            is ApiResult.Failure -> onRequestFailure(request, response.error, incoming = true)
        }
    }

    fun decline(request: FriendRequestDto) = withBusy(request.id) {
        when (val response = repository.decline(request.id)) {
            is ApiResult.Success -> {
                setSearchStatus(request.user.id, null, null)
                _state.update { it.copy(incoming = it.incoming.filterNot { r -> r.id == request.id }) }
            }
            is ApiResult.Failure -> onRequestFailure(request, response.error, incoming = true)
        }
    }

    fun cancel(requestId: String, userId: String) = withBusy(requestId) {
        when (val response = repository.cancel(requestId)) {
            is ApiResult.Success -> {
                setSearchStatus(userId, null, null)
                _state.update { it.copy(outgoing = it.outgoing.filterNot { r -> r.id == requestId }) }
            }
            is ApiResult.Failure -> {
                if (response.error.httpStatus == HTTP_NOT_FOUND) {
                    _state.update { it.copy(outgoing = it.outgoing.filterNot { r -> r.id == requestId }) }
                }
                _state.update { it.withError(response.error) }
            }
        }
    }

    fun requestUnfriend(friend: FriendDto) = _state.update { it.copy(confirmUnfriend = friend) }

    fun dismissUnfriend() = _state.update { it.copy(confirmUnfriend = null) }

    fun confirmUnfriend() {
        val friend = _state.value.confirmUnfriend ?: return
        _state.update { it.copy(confirmUnfriend = null) }
        withBusy(friend.user.id) {
            when (val response = repository.unfriend(friend.user.id)) {
                is ApiResult.Success -> {
                    setSearchStatus(friend.user.id, null, null)
                    _state.update {
                        it.copy(
                            friends = it.friends.filterNot { row -> row.userId == friend.user.id },
                            notice = UiText.Res(R.string.friends_unfriended, listOf(friend.user.displayName)),
                            noticeIsError = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.withError(response.error) }
            }
        }
    }

    fun openChat(row: FriendRowUi) {
        // Opening the conversation marks it read.
        _state.update { it.copy(friends = it.friends.map { r -> if (r.userId == row.userId) r.copy(unread = 0) else r }) }
        val existing = row.conversationId
        if (existing != null) {
            _events.trySend(FriendsEvent.OpenChat(existing, row.userId, row.friend.user.displayName))
            return
        }
        withBusy(row.userId) {
            when (val response = repository.openDirect(row.userId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(friends = it.friends.map { r -> if (r.userId == row.userId) r.copy(conversationId = response.data) else r })
                    }
                    _events.trySend(FriendsEvent.OpenChat(response.data, row.userId, row.friend.user.displayName))
                }
                is ApiResult.Failure -> _state.update { it.withError(response.error) }
            }
        }
    }

    private fun onUpdate(update: FriendsUpdate) {
        when (update) {
            is FriendsUpdate.RequestReceived -> {
                val request = update.request
                setSearchStatus(request.user.id, INCOMING, request.id)
                _state.update {
                    if (it.incoming.any { r -> r.id == request.id }) {
                        it
                    } else {
                        it.copy(
                            incoming = listOf(request) + it.incoming,
                            notice = UiText.Res(R.string.friends_request_received, listOf(request.user.displayName)),
                            noticeIsError = false,
                        )
                    }
                }
            }
            is FriendsUpdate.RequestAccepted -> {
                addFriend(update.friend)
                _state.update {
                    it.copy(
                        outgoing = it.outgoing.filterNot { r -> r.id == update.requestId },
                        notice = UiText.Res(R.string.friends_now_friends, listOf(update.friend.user.displayName)),
                        noticeIsError = false,
                    )
                }
            }
            is FriendsUpdate.Presence -> _state.update {
                it.copy(friends = it.friends.map { row -> if (row.userId == update.userId) row.copy(isOnline = update.isOnline) else row })
            }
            is FriendsUpdate.Message -> _state.update {
                val message = update.message
                it.copy(
                    friends = it.friends.map { row ->
                        if (row.conversationId != message.conversationId) return@map row
                        val fromFriend = message.senderId == row.userId
                        row.copy(
                            lastMessage = message.content,
                            lastMessageMine = !fromFriend,
                            unread = if (fromFriend) row.unread + 1 else row.unread,
                        )
                    },
                )
            }
            FriendsUpdate.Reconnected -> load(pullToRefresh = false)
        }
    }

    private fun addFriend(friend: FriendDto) {
        setSearchStatus(friend.user.id, FRIENDS, null)
        _state.update {
            if (it.friends.any { row -> row.userId == friend.user.id }) return@update it
            val row = FriendRowUi(friend, friend.user.isOnline, friend.directConversationId)
            it.copy(friends = (it.friends + row).sortedBy { r -> r.friend.user.displayName.lowercase() })
        }
        friend.directConversationId?.let { repository.joinConversations(listOf(it)) }
    }

    private fun onRequestFailure(request: FriendRequestDto, error: ApiError, incoming: Boolean) {
        _state.update {
            val gone = error.httpStatus == HTTP_NOT_FOUND
            it.copy(
                incoming = if (incoming && gone) it.incoming.filterNot { r -> r.id == request.id } else it.incoming,
            ).withError(error)
        }
    }

    private fun setSearchStatus(userId: String, status: String?, requestId: String?) = _state.update {
        it.copy(
            searchResults = it.searchResults.map { r ->
                if (r.id == userId) r.copy(friendshipStatus = status, requestId = requestId) else r
            },
        )
    }

    private fun withBusy(id: String, block: suspend () -> Unit) {
        if (id in _state.value.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - id) }
            }
        }
    }

    private fun FriendsUiState.withError(error: ApiError) = copy(notice = friendsErrorText(error), noticeIsError = true)

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val MAX_QUERY = 50
        private const val HTTP_NOT_FOUND = 404
        private const val STATUS_ACCEPTED = "accepted"
        const val FRIENDS = "friends"
        const val OUTGOING = "outgoing"
        const val INCOMING = "incoming"
    }
}

internal fun friendRow(friend: FriendDto, conversations: List<ConversationSummaryDto>): FriendRowUi {
    val conversation = conversations.firstOrNull { it.id == friend.directConversationId }
        ?: conversations.firstOrNull { c -> c.type == "direct" && c.participants.any { it.id == friend.user.id } }
    val online = conversation?.participants?.firstOrNull { it.id == friend.user.id }?.isOnline ?: friend.user.isOnline
    val last = conversation?.lastMessage?.takeUnless { it.isRecalled }
    return FriendRowUi(
        friend = friend,
        isOnline = online || friend.user.isOnline,
        conversationId = friend.directConversationId ?: conversation?.id,
        lastMessage = last?.content,
        lastMessageMine = last != null && last.senderId != friend.user.id,
        unread = conversation?.unreadCount ?: 0,
    )
}

/** Vietnamese text for the friends API's error codes; anything else uses the app-wide mapping. */
fun friendsErrorText(error: ApiError): UiText = when (error.code) {
    "FRIEND_SELF" -> UiText.Res(R.string.friends_error_self)
    "USER_NOT_FOUND" -> UiText.Res(R.string.friends_error_user_not_found)
    "FRIEND_ALREADY" -> UiText.Res(R.string.friends_error_already)
    "FRIEND_REQUEST_EXISTS" -> UiText.Res(R.string.friends_error_request_exists)
    "FRIEND_BLOCKED" -> UiText.Res(R.string.friends_error_blocked)
    "FRIEND_REQUEST_NOT_FOUND" -> UiText.Res(R.string.friends_error_request_gone)
    "FRIEND_NOT_FOUND" -> UiText.Res(R.string.friends_error_not_friend)
    else -> if (error.httpStatus == 429) UiText.Res(R.string.friends_error_rate_limited) else error.toUiText()
}
