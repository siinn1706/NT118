package vn.uit.healthos.feature.meals

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vn.uit.healthos.core.network.ApiResult

class MealOutboxHandlerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MealsApi
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun postsPayloadToMealsEndpointAndDeletesPendingRow() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """{"data":{"id":"m-1","name":"Cơm tấm","status":"manual","logged_at":"2026-11-23T05:00:00Z"}}""",
                ),
        )
        val fakeDao = FakePendingMealDao()
        val handler = MealOutboxHandler(api, fakeDao, json)
        val payload = MealCreateOutboxPayload(
            localId = "loc-1",
            idempotencyKey = "idem-123",
            body = MealCreateBody(
                name = "Cơm tấm",
                mealType = "lunch",
                ingredients = listOf(MealCreateIngredientBody(ingredientName = "Cơm tấm", grams = 300.0, calories = 650.0)),
            ),
        )
        val payloadJson = json.encodeToString(MealCreateOutboxPayload.serializer(), payload)

        val result = handler.send(payloadJson)

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/meals", recorded.path)
        assertEquals("idem-123", recorded.getHeader("Idempotency-Key"))
        assertTrue(recorded.body.readUtf8().contains("Cơm tấm"))
        assertEquals(listOf("loc-1"), fakeDao.deletedIds)
    }

    private fun queuedPayloadJson(localId: String = "loc-1") = json.encodeToString(
        MealCreateOutboxPayload.serializer(),
        MealCreateOutboxPayload(
            localId = localId,
            idempotencyKey = "idem-$localId",
            body = MealCreateBody(name = "Phở bò", ingredients = listOf(MealCreateIngredientBody(ingredientName = "Phở bò"))),
        ),
    )

    @Test
    fun permanentFailureRemovesPendingRowSoItIsNotStuck() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"error":{"code":"VALIDATION_ERROR","message":"Invalid"}}"""),
        )
        val fakeDao = FakePendingMealDao()

        val result = MealOutboxHandler(api, fakeDao, json).send(queuedPayloadJson())

        assertTrue(result is ApiResult.Failure)
        assertFalse((result as ApiResult.Failure).error.isRetryable)
        assertEquals(listOf("loc-1"), fakeDao.deletedIds)
    }

    @Test
    fun retryableFailureKeepsPendingRowForTheNextAttempt() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val fakeDao = FakePendingMealDao()

        val result = MealOutboxHandler(api, fakeDao, json).send(queuedPayloadJson())

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error.isRetryable)
        assertTrue(fakeDao.deletedIds.isEmpty())
    }

    @Test
    fun queuedDeleteTreatsAlreadyDeletedMealAsDone() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":{"code":"NOT_FOUND","message":"Meal not found."}}"""),
        )
        val payloadJson = json.encodeToString(MealDeleteOutboxPayload.serializer(), MealDeleteOutboxPayload("m-9"))

        val result = MealDeleteOutboxHandler(api, json).send(payloadJson)

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/v1/meals/m-9", recorded.path)
    }

    @Test
    fun queuedDeleteRetriesWhenServerIsDown() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val payloadJson = json.encodeToString(MealDeleteOutboxPayload.serializer(), MealDeleteOutboxPayload("m-9"))

        val result = MealDeleteOutboxHandler(api, json).send(payloadJson)

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error.isRetryable)
    }

    private class FakePendingMealDao : PendingMealDao {
        val deletedIds = mutableListOf<String>()
        override fun observeForDate(date: String): Flow<List<PendingMealEntity>> = flowOf(emptyList())
        override suspend fun upsert(entity: PendingMealEntity) = Unit
        override suspend fun deleteById(localId: String) {
            deletedIds.add(localId)
        }
    }
}
