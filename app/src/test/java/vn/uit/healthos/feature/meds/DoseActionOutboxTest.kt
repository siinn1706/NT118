package vn.uit.healthos.feature.meds

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vn.uit.healthos.core.network.ApiResult

class DoseActionOutboxTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MedsApi
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MedsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mapsAllThreeNotificationActionsToOutboxSpecs() {
        val done = mapDoseAction(DoseActionReceiver.ACTION_DONE, "rem-1", "occ-1")
        val skip = mapDoseAction(DoseActionReceiver.ACTION_SKIP, "rem-1", "occ-1")
        val snooze = mapDoseAction(DoseActionReceiver.ACTION_SNOOZE, "rem-1", "occ-1", snoozeMinutes = 10)

        assertNotNull(done)
        assertEquals(OUTBOX_TYPE_DOSE_DONE, done?.outboxType)
        assertEquals("done", done?.cacheStatus)

        assertEquals(OUTBOX_TYPE_DOSE_SKIP, skip?.outboxType)
        assertEquals("skipped", skip?.cacheStatus)

        assertEquals(OUTBOX_TYPE_DOSE_SNOOZE, snooze?.outboxType)
        assertEquals("snoozed", snooze?.cacheStatus)
        assertEquals(10, snooze?.payload?.snoozeMinutes)
    }

    @Test
    fun doseDoneAndSnoozeHandlersCallMatchingReminderEndpoints() = runTest {
        val occJson = """{"data":{"id":"occ-1","reminder_id":"rem-1","title":"Med","type":"medicine","scheduled_at":"2026-11-23T08:00:00Z","status":"done"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(occJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(occJson))

        val payload = json.encodeToString(
            DoseActionOutboxPayload.serializer(),
            DoseActionOutboxPayload("rem-1", "occ-1", snoozeMinutes = 10),
        )

        val doneRes = DoseDoneOutboxHandler(api, json).send(payload)
        assertTrue(doneRes is ApiResult.Success)
        val req1 = server.takeRequest()
        assertEquals("/v1/reminders/rem-1/done", req1.path)
        assertTrue(req1.body.readUtf8().contains("occ-1"))

        val snoozeRes = DoseSnoozeOutboxHandler(api, json).send(payload)
        assertTrue(snoozeRes is ApiResult.Success)
        val req2 = server.takeRequest()
        assertEquals("/v1/reminders/rem-1/snooze", req2.path)
        assertTrue(req2.body.readUtf8().contains("\"minutes\":10"))
    }
}
