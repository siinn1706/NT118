package vn.uit.healthos.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vn.uit.healthos.core.data.session.SessionEvents
import vn.uit.healthos.core.data.session.SessionTokens
import vn.uit.healthos.core.data.session.TokenStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {

    private class InMemoryTokenStore(initial: SessionTokens?) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val tokens: Flow<SessionTokens?> = state
        override fun current(): SessionTokens? = state.value
        override suspend fun save(tokens: SessionTokens) { state.value = tokens }
        override suspend fun clear() { state.value = null }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val server = MockWebServer()
    private val refreshCalls = AtomicInteger()
    private var refreshStatus = 200
    private lateinit var store: InMemoryTokenStore
    private lateinit var events: SessionEvents
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/auth/refresh" -> {
                    refreshCalls.incrementAndGet()
                    Thread.sleep(200)
                    if (refreshStatus == 200) {
                        MockResponse().setResponseCode(200).setBody(
                            """{"data":{"access_token":"new","refresh_token":"r2","user_id":"u1"}}""",
                        )
                    } else {
                        MockResponse().setResponseCode(refreshStatus)
                            .setBody("""{"error":{"code":"AUTH_INVALID_REFRESH","message":"bad"}}""")
                    }
                }
                else -> if (request.getHeader("Authorization") == "Bearer new") {
                    MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                } else {
                    MockResponse().setResponseCode(401)
                }
            }
        }
        server.start()

        store = InMemoryTokenStore(SessionTokens("old", "r1", "u1"))
        events = SessionEvents()
        val refreshApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TokenRefreshApi::class.java)
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, refreshApi, events))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parallel 401s trigger a single refresh and both requests succeed`() {
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val codes = (1..2).map {
            pool.submit<Int> {
                start.await()
                client.newCall(Request.Builder().url(server.url("/v1/data")).build()).execute().use { it.code }
            }
        }
        start.countDown()
        val results = codes.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(listOf(200, 200), results)
        assertEquals(1, refreshCalls.get())
        assertEquals(SessionTokens("new", "r2", "u1"), store.current())
    }

    @Test
    fun `rejected refresh token clears the session`() {
        refreshStatus = 401
        val code = client.newCall(Request.Builder().url(server.url("/v1/data")).build()).execute().use { it.code }

        assertEquals(401, code)
        assertNull(store.current())
    }

    @Test
    fun `server error during refresh keeps the session`() {
        refreshStatus = 503
        val code = client.newCall(Request.Builder().url(server.url("/v1/data")).build()).execute().use { it.code }

        assertEquals(401, code)
        assertEquals("old", store.current()?.accessToken)
    }
}
