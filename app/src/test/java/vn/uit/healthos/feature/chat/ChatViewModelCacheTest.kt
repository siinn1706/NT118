package vn.uit.healthos.feature.chat

import android.content.ContextWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.uit.healthos.core.data.db.FRESHNESS_WINDOW_MS
import vn.uit.healthos.core.network.DataEnvelope
import vn.uit.healthos.core.network.SseClient

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCacheTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Serves the newest page of the conversation; [gate] holds the page back until the test releases it. */
    private class FakeChatApi : ChatApi {
        var pages = 0
        var latest: List<ChatMessageDto> = emptyList()
        var gate = CompletableDeferred<Unit>().apply { complete(Unit) }

        override suspend fun getOrCreateAiConversation() = DataEnvelope(ConversationDto(id = "conv"))

        override suspend fun getMessages(conversationId: String, before: String?, limit: Int): MessageListResponseDto {
            pages++
            gate.await()
            // The server pages newest first.
            return MessageListResponseDto(data = latest.reversed(), hasMore = true, nextCursor = "newest-cursor")
        }

        override suspend fun uploadImage(image: MultipartBody.Part): DataEnvelope<ChatAttachmentDto> = error("unused")

        override suspend fun createReminder(body: QuickReminderCreateBody): DataEnvelope<QuickReminderDto> =
            error("unused")
    }

    private val api = FakeChatApi()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = ChatRepository(api, SseClient(OkHttpClient()), json)

    private fun dto(id: String, user: Boolean = false) =
        ChatMessageDto(id = id, conversationId = "conv", senderKind = if (user) "user" else "assistant", content = id)

    private fun ui(id: String, user: Boolean = false) = ChatMessageUi(id = id, isUser = user, content = id)

    private fun viewModel() = ChatViewModel(repository, json, ContextWrapper(null))

    @Test
    fun `a new chat screen opens on the conversation kept in memory without loading`() = runTest {
        repository.rememberSession(
            ChatSession("conv", listOf(ui("q", user = true), ui("a")), hasMoreHistory = false, nextCursor = null, syncedAtMs = System.currentTimeMillis()),
        )

        val vm = viewModel()
        assertEquals(listOf("q", "a"), vm.state.value.messages.map { it.id })
        assertFalse(vm.state.value.loading)

        vm.onVisible()
        advanceUntilIdle()
        assertEquals("a fresh conversation is not refetched on a tab switch", 0, api.pages)
    }

    @Test
    fun `a stale conversation reloads in the background and keeps older history the reader loaded`() = runTest {
        repository.rememberSession(
            ChatSession(
                conversationId = "conv",
                messages = listOf(ui("old"), ui("q", user = true), ui("a")),
                hasMoreHistory = true,
                nextCursor = "older-cursor",
                syncedAtMs = System.currentTimeMillis() - FRESHNESS_WINDOW_MS - 1,
            ),
        )
        api.latest = listOf(dto("q", user = true), dto("a"), dto("from-web"))
        api.gate = CompletableDeferred()

        val vm = viewModel()
        vm.onVisible()
        runCurrent()

        assertTrue(vm.state.value.loading)
        assertEquals("cached messages stay while reloading", 3, vm.state.value.messages.size)

        api.gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("old", "q", "a", "from-web"), state.messages.map { it.id })
        assertEquals("older-cursor", state.nextCursor)
        assertFalse(state.loading)
        assertEquals(state.messages, repository.session.value?.messages)
    }

    @Test
    fun `the first ever visit loads with placeholders and remembers the conversation`() = runTest {
        api.latest = listOf(dto("hello"))

        val vm = viewModel()
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.messages.isEmpty())

        advanceUntilIdle()

        assertEquals(listOf("hello"), vm.state.value.messages.map { it.id })
        assertEquals(listOf("hello"), repository.session.value?.messages?.map { it.id })
        assertEquals(1, api.pages)

        // The tab is shown again right away: nothing more to fetch.
        vm.onVisible()
        advanceUntilIdle()
        assertEquals(1, api.pages)
    }

    @Test
    fun `the newest page replaces the list unless it overlaps what is shown`() {
        val shown = listOf(ui("a"), ui("b"))

        assertEquals(listOf(ui("x"), ui("y")), mergeLatestPage(shown, listOf(ui("x"), ui("y"))))
        assertEquals(listOf(ui("a"), ui("b"), ui("c")), mergeLatestPage(shown, listOf(ui("b"), ui("c"))))
    }
}
