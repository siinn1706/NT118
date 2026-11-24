package vn.uit.healthos.feature.friends

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeMessage
import vn.uit.healthos.core.ui.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun user(id: String, name: String = id.replaceFirstChar { it.uppercase() }, online: Boolean = false) =
        FriendUserDto(id = id, username = id, displayName = name, isOnline = online)

    private fun friend(id: String, conversation: String? = "conv-$id", online: Boolean = false) =
        FriendDto(user = user(id, online = online), since = "2026-11-01T00:00:00+00:00", directConversationId = conversation)

    private fun request(id: String, userId: String, direction: String = "incoming") =
        FriendRequestDto(id = id, status = "pending", direction = direction, user = user(userId), createdAt = "2026-11-24T00:00:00+00:00")

    private class FakeRepository : FriendsRepository {
        override val updates = MutableSharedFlow<FriendsUpdate>(extraBufferCapacity = 16)
        var friends: ApiResult<List<FriendDto>> = ApiResult.Success(emptyList())
        var incoming: List<FriendRequestDto> = emptyList()
        var outgoing: List<FriendRequestDto> = emptyList()
        var conversations: List<ConversationSummaryDto> = emptyList()
        var searchResult: ApiResult<List<FriendSearchResultDto>> = ApiResult.Success(emptyList())
        var sendResult: ApiResult<FriendRequestDto>? = null
        var acceptResult: ApiResult<FriendDto>? = null
        var simpleResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var openDirectResult: ApiResult<String> = ApiResult.Success("new-conv")
        val searches = mutableListOf<String>()
        val joined = mutableListOf<String>()
        val unfriended = mutableListOf<String>()
        var friendsCalls = 0

        override suspend fun friends(): ApiResult<List<FriendDto>> {
            friendsCalls++
            return friends
        }
        override suspend fun requests(incoming: Boolean) = ApiResult.Success(if (incoming) this.incoming else outgoing)
        override suspend fun sendRequest(userId: String) = requireNotNull(sendResult)
        override suspend fun accept(requestId: String) = requireNotNull(acceptResult)
        override suspend fun decline(requestId: String) = simpleResult
        override suspend fun cancel(requestId: String) = simpleResult
        override suspend fun unfriend(userId: String): ApiResult<Unit> {
            unfriended += userId
            return simpleResult
        }
        override suspend fun search(query: String): ApiResult<List<FriendSearchResultDto>> {
            searches += query
            return searchResult
        }
        override suspend fun conversations() = ApiResult.Success(conversations)
        override suspend fun openDirect(userId: String) = openDirectResult
        override fun joinConversations(conversationIds: Collection<String>) {
            joined += conversationIds
        }
    }

    @Test
    fun `loads friends with presence and last message, requests, and joins their rooms`() = runTest {
        val repository = FakeRepository().apply {
            friends = ApiResult.Success(listOf(friend("an"), friend("binh", online = true)))
            incoming = listOf(request("r1", "chi"))
            outgoing = listOf(request("r2", "dung", direction = "outgoing"))
            conversations = listOf(
                ConversationSummaryDto(
                    id = "conv-an",
                    participants = listOf(ConversationParticipantDto("an", "An", isOnline = true)),
                    lastMessage = RealtimeMessage(id = "m1", conversationId = "conv-an", senderId = "an", content = "Đi bộ không?"),
                    unreadCount = 2,
                ),
            )
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        val an = state.friends.first { it.userId == "an" }
        assertTrue(an.isOnline)
        assertEquals("Đi bộ không?", an.lastMessage)
        assertFalse(an.lastMessageMine)
        assertEquals(2, an.unread)
        assertTrue(state.friends.first { it.userId == "binh" }.isOnline)
        assertEquals(listOf("r1"), state.incoming.map { it.id })
        assertEquals(listOf("r2"), state.outgoing.map { it.id })
        assertEquals(listOf("conv-an", "conv-binh"), repository.joined)
    }

    @Test
    fun `load failure with nothing cached shows the error state`() = runTest {
        val repository = FakeRepository().apply { friends = ApiResult.Failure(ApiError(ApiError.NETWORK, "offline")) }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()
        assertNotNull(vm.state.value.loadError)
        assertFalse(vm.state.value.loading)

        repository.friends = ApiResult.Success(listOf(friend("an")))
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.loadError)
        assertFalse(vm.state.value.refreshing)
        assertEquals(1, vm.state.value.friends.size)
    }

    @Test
    fun `search is debounced and trimmed`() = runTest {
        val repository = FakeRepository().apply {
            searchResult = ApiResult.Success(listOf(FriendSearchResultDto("u1", "minhanh", "Minh Anh")))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        vm.onQueryChange("m")
        advanceTimeBy(100)
        vm.onQueryChange(" minh ")
        advanceTimeBy(FriendsViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(listOf("minh"), repository.searches)
        assertEquals("minh", vm.state.value.searchedQuery)
        assertEquals(1, vm.state.value.searchResults.size)

        vm.onQueryChange("")
        assertTrue(vm.state.value.searchResults.isEmpty())
        assertNull(vm.state.value.searchedQuery)
    }

    @Test
    fun `sending a request marks the result as outgoing`() = runTest {
        val result = FriendSearchResultDto("u1", "minhanh", "Minh Anh")
        val repository = FakeRepository().apply {
            searchResult = ApiResult.Success(listOf(result))
            sendResult = ApiResult.Success(request("r9", "u1", direction = "outgoing"))
        }
        val vm = FriendsViewModel(repository)
        vm.onQueryChange("minh")
        advanceUntilIdle()

        vm.sendRequest(result)
        advanceUntilIdle()

        val updated = vm.state.value.searchResults.single()
        assertEquals(FriendsViewModel.OUTGOING, updated.friendshipStatus)
        assertEquals("r9", updated.requestId)
        assertEquals(listOf("r9"), vm.state.value.outgoing.map { it.id })
        assertEquals(UiText.Res(R.string.friends_request_sent_to, listOf("Minh Anh")), vm.state.value.notice)
        assertTrue(vm.state.value.busyIds.isEmpty())
    }

    @Test
    fun `a request to someone who already invited us becomes a friendship`() = runTest {
        val result = FriendSearchResultDto("u1", "minhanh", "Minh Anh", friendshipStatus = "incoming", requestId = "r1")
        val repository = FakeRepository().apply {
            incoming = listOf(request("r1", "u1"))
            searchResult = ApiResult.Success(listOf(result))
            sendResult = ApiResult.Success(request("r1", "u1").copy(status = "accepted"))
        }
        val vm = FriendsViewModel(repository)
        vm.onQueryChange("minh")
        advanceUntilIdle()

        repository.friends = ApiResult.Success(listOf(friend("u1")))
        vm.sendRequest(result)
        advanceUntilIdle()

        assertEquals(FriendsViewModel.FRIENDS, vm.state.value.searchResults.single().friendshipStatus)
        assertEquals(listOf("u1"), vm.state.value.friends.map { it.userId })
    }

    @Test
    fun `send errors use the friends wording`() = runTest {
        val result = FriendSearchResultDto("u1", "minhanh", "Minh Anh")
        val repository = FakeRepository().apply {
            sendResult = ApiResult.Failure(ApiError("FRIEND_REQUEST_EXISTS", "exists", httpStatus = 409))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()
        vm.sendRequest(result)
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.friends_error_request_exists), vm.state.value.notice)
        assertTrue(vm.state.value.noticeIsError)
        assertEquals(UiText.Res(R.string.friends_error_rate_limited), friendsErrorText(ApiError("HTTP_429", "slow", httpStatus = 429)))
    }

    @Test
    fun `accepting moves the request into the friend list`() = runTest {
        val repository = FakeRepository().apply {
            incoming = listOf(request("r1", "chi"))
            acceptResult = ApiResult.Success(friend("chi", conversation = "conv-chi"))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        vm.accept(vm.state.value.incoming.single())
        advanceUntilIdle()

        assertTrue(vm.state.value.incoming.isEmpty())
        assertEquals(listOf("chi"), vm.state.value.friends.map { it.userId })
        assertTrue("conv-chi" in repository.joined)
    }

    @Test
    fun `a request that no longer exists disappears on decline`() = runTest {
        val repository = FakeRepository().apply {
            incoming = listOf(request("r1", "chi"))
            simpleResult = ApiResult.Failure(ApiError("FRIEND_REQUEST_NOT_FOUND", "gone", httpStatus = 404))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        vm.decline(vm.state.value.incoming.single())
        advanceUntilIdle()

        assertTrue(vm.state.value.incoming.isEmpty())
        assertEquals(UiText.Res(R.string.friends_error_request_gone), vm.state.value.notice)
    }

    @Test
    fun `cancelling an outgoing request removes it`() = runTest {
        val repository = FakeRepository().apply { outgoing = listOf(request("r2", "dung", direction = "outgoing")) }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        vm.cancel("r2", "dung")
        advanceUntilIdle()
        assertTrue(vm.state.value.outgoing.isEmpty())
    }

    @Test
    fun `unfriend needs confirmation`() = runTest {
        val repository = FakeRepository().apply { friends = ApiResult.Success(listOf(friend("an"))) }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        val target = vm.state.value.friends.single().friend
        vm.requestUnfriend(target)
        vm.dismissUnfriend()
        advanceUntilIdle()
        assertTrue(repository.unfriended.isEmpty())

        vm.requestUnfriend(target)
        assertEquals(target, vm.state.value.confirmUnfriend)
        vm.confirmUnfriend()
        advanceUntilIdle()

        assertEquals(listOf("an"), repository.unfriended)
        assertTrue(vm.state.value.friends.isEmpty())
        assertNull(vm.state.value.confirmUnfriend)
    }

    @Test
    fun `opening a chat uses the existing conversation or creates one`() = runTest {
        val repository = FakeRepository().apply {
            friends = ApiResult.Success(listOf(friend("an"), friend("binh", conversation = null)))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        vm.events.test {
            vm.openChat(vm.state.value.friends.first { it.userId == "an" })
            assertEquals(FriendsEvent.OpenChat("conv-an", "an", "An"), awaitItem())

            vm.openChat(vm.state.value.friends.first { it.userId == "binh" })
            advanceUntilIdle()
            assertEquals(FriendsEvent.OpenChat("new-conv", "binh", "Binh"), awaitItem())
        }
        assertEquals("new-conv", vm.state.value.friends.first { it.userId == "binh" }.conversationId)
    }

    @Test
    fun `realtime requests, acceptances, presence and messages update the lists`() = runTest {
        val repository = FakeRepository().apply {
            friends = ApiResult.Success(listOf(friend("an")))
            outgoing = listOf(request("r2", "dung", direction = "outgoing"))
        }
        val vm = FriendsViewModel(repository)
        advanceUntilIdle()

        repository.updates.emit(FriendsUpdate.RequestReceived(request("r5", "em")))
        repository.updates.emit(FriendsUpdate.RequestReceived(request("r5", "em")))
        repository.updates.emit(FriendsUpdate.RequestAccepted("r2", friend("dung")))
        repository.updates.emit(FriendsUpdate.Presence("an", true))
        repository.updates.emit(
            FriendsUpdate.Message(RealtimeMessage(id = "m2", conversationId = "conv-an", senderId = "an", content = "Chào")),
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("r5"), state.incoming.map { it.id })
        assertTrue(state.outgoing.isEmpty())
        assertEquals(listOf("an", "dung"), state.friends.map { it.userId })
        val an = state.friends.first { it.userId == "an" }
        assertTrue(an.isOnline)
        assertEquals("Chào", an.lastMessage)
        assertEquals(1, an.unread)
    }

    @Test
    fun `reconnecting reloads everything`() = runTest {
        val repository = FakeRepository()
        FriendsViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, repository.friendsCalls)

        repository.updates.emit(FriendsUpdate.Reconnected)
        advanceUntilIdle()
        assertEquals(2, repository.friendsCalls)
    }
}
