package vn.uit.healthos.feature.friends

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeEvent
import vn.uit.healthos.core.network.RealtimeMessage
import vn.uit.healthos.core.network.RealtimeState

@OptIn(ExperimentalCoroutinesApi::class)
class DirectChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** In-memory cache standing in for Room, with the same placeholder-replacement rule as [DmMessageDao]. */
    private class FakeRepository : DirectChatRepository {
        val rows = MutableStateFlow<List<DmMessageEntity>>(emptyList())
        override val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 16)
        override val connection = MutableStateFlow(RealtimeState.CONNECTED)
        var socketUp = true
        val socketSends = mutableListOf<String>()
        val queued = mutableListOf<String>()
        val awaitingAck = mutableListOf<String>()
        val typing = mutableListOf<Boolean>()
        val joined = mutableListOf<String>()
        val readUpTo = mutableListOf<String>()
        val pages = mutableListOf<String?>()
        var latest: ApiResult<DmPage> = ApiResult.Success(DmPage(hasMore = true, nextCursor = "cursor-1"))
        var older: ApiResult<DmPage> = ApiResult.Success(DmPage(hasMore = false, nextCursor = null))
        var trims = 0

        override fun currentUserId() = "me"
        override fun observeMessages(conversationId: String): StateFlow<List<DmMessageEntity>> = rows

        override suspend fun loadLatest(conversationId: String, trimCache: Boolean): ApiResult<DmPage> {
            if (trimCache) trims++
            pages += null
            return latest
        }

        override suspend fun loadOlder(conversationId: String, before: String): ApiResult<DmPage> {
            pages += before
            return older
        }

        override suspend fun insertPending(conversationId: String, content: String, clientMessageId: String) {
            rows.value = rows.value + DmMessageEntity(
                id = DmMessageEntity.localId(clientMessageId),
                conversationId = conversationId,
                senderId = "me",
                content = content,
                clientMessageId = clientMessageId,
                createdAtMs = 10_000L + rows.value.size,
                status = DmStatus.PENDING,
            )
        }

        override fun sendOverSocket(conversationId: String, content: String, clientMessageId: String): Boolean {
            if (socketUp) socketSends += clientMessageId
            return socketUp
        }

        override suspend fun enqueue(conversationId: String, content: String, clientMessageId: String) {
            queued += clientMessageId
        }

        override fun queueIfUnacknowledged(conversationId: String, content: String, clientMessageId: String) {
            awaitingAck += clientMessageId
        }

        override suspend fun applyAck(clientMessageId: String, serverMessageId: String, createdAt: String?) {
            rows.value = rows.value.map {
                if (it.clientMessageId == clientMessageId) {
                    it.copy(id = serverMessageId, status = DmStatus.SENT, createdAtMs = parseServerTime(createdAt) ?: it.createdAtMs)
                } else {
                    it
                }
            }
        }

        override suspend fun store(message: RealtimeMessage) {
            rows.value = rows.value.filterNot { it.id == message.id } + message.toEntity()
        }

        override suspend fun markPending(clientMessageId: String) {
            rows.value = rows.value.map {
                if (it.clientMessageId == clientMessageId) it.copy(status = DmStatus.PENDING) else it
            }
        }

        override suspend fun markRead(conversationId: String, lastMessageId: String): ApiResult<Unit> {
            readUpTo += lastMessageId
            return ApiResult.Success(Unit)
        }

        override fun sendTyping(conversationId: String, isTyping: Boolean) {
            typing += isTyping
        }

        override fun joinRoom(conversationId: String) {
            joined += conversationId
        }
    }

    private fun viewModel(repository: FakeRepository) = DirectChatViewModel(
        SavedStateHandle(
            mapOf(
                DirectChatViewModel.ARG_CONVERSATION_ID to "c1",
                DirectChatViewModel.ARG_PEER_ID to "peer",
                DirectChatViewModel.ARG_PEER_NAME to "Minh Anh",
            ),
        ),
        repository,
    )

    private fun peerMessage(id: String, content: String = "Hi", at: String = "2026-11-25T03:00:00+00:00") =
        RealtimeMessage(id = id, conversationId = "c1", senderId = "peer", content = content, createdAt = at)

    @Test
    fun `opening loads the newest page, trims the cache once and joins the room`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertTrue(state.hasMore)
        assertEquals("Minh Anh", state.peerName)
        assertEquals(1, repository.trims)
        assertEquals(listOf("c1"), repository.joined)
    }

    @Test
    fun `send shows the message at once and the ack swaps in the server id`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onInputChange("  Chào bạn  ")
        vm.send()
        advanceUntilIdle()

        val pending = vm.state.value.messages.single()
        assertEquals("", vm.state.value.input)
        assertEquals("Chào bạn", pending.content)
        assertTrue(pending.isMine)
        assertEquals(DmStatus.PENDING, pending.status)
        val clientId = requireNotNull(pending.clientMessageId)
        assertEquals(listOf(clientId), repository.socketSends)
        assertEquals(listOf(clientId), repository.awaitingAck)
        assertTrue(repository.queued.isEmpty())

        repository.events.emit(RealtimeEvent.MessageAck(clientId, "server-1", "2026-11-25T03:00:00+00:00"))
        advanceUntilIdle()

        val sent = vm.state.value.messages.single()
        assertEquals("server-1", sent.id)
        assertEquals(DmStatus.SENT, sent.status)
        assertTrue(sent.isMine)
        assertEquals(1_795_575_600_000L, sent.createdAtMs)
    }

    @Test
    fun `without a socket the message goes to the outbox`() = runTest {
        val repository = FakeRepository().apply { socketUp = false }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onInputChange("Offline")
        vm.send()
        advanceUntilIdle()

        val message = vm.state.value.messages.single()
        assertEquals(listOf(message.clientMessageId), repository.queued)
        assertTrue(repository.awaitingAck.isEmpty())
    }

    @Test
    fun `blank input sends nothing`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        vm.onInputChange("   ")
        vm.send()
        advanceUntilIdle()
        assertTrue(vm.state.value.messages.isEmpty())
        assertTrue(repository.socketSends.isEmpty())
    }

    @Test
    fun `a failed message is retried with the same client id`() = runTest {
        val repository = FakeRepository().apply { socketUp = false }
        repository.rows.value = listOf(
            DmMessageEntity("local:k9", "c1", "me", "Lỗi", "k9", 1L, DmStatus.FAILED),
        )
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.retry(vm.state.value.messages.single())
        advanceUntilIdle()

        assertEquals(listOf("k9"), repository.queued)
        assertEquals(DmStatus.PENDING, vm.state.value.messages.single().status)
    }

    @Test
    fun `older pages follow the cursor until the server has no more`() = runTest {
        val repository = FakeRepository()
        repository.older = ApiResult.Success(DmPage(hasMore = true, nextCursor = "cursor-2"))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.loadOlder()
        advanceUntilIdle()
        assertTrue(vm.state.value.hasMore)

        repository.older = ApiResult.Success(DmPage(hasMore = false, nextCursor = null))
        vm.loadOlder()
        advanceUntilIdle()
        assertFalse(vm.state.value.hasMore)

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals(listOf(null, "cursor-1", "cursor-2"), repository.pages)
    }

    @Test
    fun `a reconnect fetches missed messages without trimming or resetting the cursor`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.loadOlder()
        advanceUntilIdle()

        repository.connection.value = RealtimeState.DISCONNECTED
        advanceUntilIdle()
        assertFalse(vm.state.value.connected)
        repository.connection.value = RealtimeState.CONNECTED
        advanceUntilIdle()

        assertTrue(vm.state.value.connected)
        assertEquals(1, repository.trims)
        assertEquals(listOf(null, "cursor-1", null), repository.pages)
        assertEquals(listOf("c1", "c1"), repository.joined)
    }

    @Test
    fun `failed first load keeps cached messages and reports the error`() = runTest {
        val repository = FakeRepository()
        repository.latest = ApiResult.Failure(ApiError(ApiError.NETWORK, "offline"))
        repository.rows.value = listOf(peerMessage("m1").toEntity())
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.loadError)
        assertEquals(1, vm.state.value.messages.size)
    }

    @Test
    fun `incoming messages are stored, marked read and end the typing indicator`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.events.emit(RealtimeEvent.Typing("c1", "peer", true))
        // Only run what is due now: advancing time would let the indicator's own timeout clear it.
        runCurrent()
        assertTrue(vm.state.value.peerTyping)

        repository.events.emit(RealtimeEvent.MessageNew(peerMessage("m1")))
        repository.events.emit(RealtimeEvent.MessageNew(peerMessage("x1").copy(conversationId = "other")))
        runCurrent()

        assertFalse(vm.state.value.peerTyping)
        val message = vm.state.value.messages.single()
        assertFalse(message.isMine)
        assertEquals(listOf("m1"), repository.readUpTo)
    }

    @Test
    fun `peer typing clears itself when the stop event is lost`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.events.emit(RealtimeEvent.Typing("c1", "peer", true))
        advanceTimeBy(DirectChatViewModel.PEER_TYPING_TIMEOUT_MS + 1)
        advanceUntilIdle()
        assertFalse(vm.state.value.peerTyping)
    }

    @Test
    fun `typing is announced once and stopped when the input is cleared`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        vm.onInputChange("C")
        vm.onInputChange("Ch")
        vm.onInputChange("")
        assertEquals(listOf(true, false), repository.typing)
    }

    @Test
    fun `presence of the peer is shown`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        repository.events.emit(RealtimeEvent.Presence("peer", true))
        repository.events.emit(RealtimeEvent.Presence("someone-else", false))
        advanceUntilIdle()
        assertEquals(true, vm.state.value.peerOnline)
    }
}
