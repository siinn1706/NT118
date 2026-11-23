package vn.uit.healthos.feature.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.feature.profile.EmergencyContactDto

class SosEventsTest {

    private val t0 = 1_000_000L

    private fun armed(trigger: SosTrigger = SosTrigger.FALL) = SosEvents.arm(null, "e1", trigger, t0)

    private fun dispatching() = checkNotNull(SosEvents.beginDispatch(armed(), "e1"))

    private fun contact(name: String, phone: String, email: String? = null) =
        EmergencyContactDto(name = name, phone = phone, relationship = "", email = email)

    @Test
    fun armSetsDeadlineFromTriggerCountdown() {
        val fall = armed(SosTrigger.FALL)
        assertEquals(SosPhase.COUNTDOWN, fall.phase)
        assertEquals(t0 + 30_000L, fall.deadlineMs)
        assertEquals(t0 + 5_000L, armed(SosTrigger.MANUAL).deadlineMs)
    }

    @Test
    fun armWhileActiveKeepsTheExistingEvent() {
        val first = armed()
        assertSame(first, SosEvents.arm(first, "e2", SosTrigger.MANUAL, t0 + 1_000L))
        val inFlight = dispatching()
        assertSame(inFlight, SosEvents.arm(inFlight, "e2", SosTrigger.MANUAL, t0 + 1_000L))
    }

    @Test
    fun armAfterFinishedEventStartsANewOne() {
        val cancelled = SosEvents.cancel(armed(), "e1", t0 + 1)
        val next = SosEvents.arm(cancelled, "e2", SosTrigger.MANUAL, t0 + 2)
        assertEquals("e2", next.id)
        assertEquals(SosPhase.COUNTDOWN, next.phase)
    }

    @Test
    fun cancelOnlyAppliesToTheCountingDownTarget() {
        assertEquals(SosPhase.COUNTDOWN, SosEvents.cancel(armed(), "other", t0)?.phase)
        assertEquals(SosPhase.CANCELLED, SosEvents.cancel(armed(), "e1", t0)?.phase)
        assertEquals(SosPhase.DISPATCHING, SosEvents.cancel(dispatching(), "e1", t0)?.phase)
        assertNull(SosEvents.cancel(null, "e1", t0))
    }

    @Test
    fun beginDispatchIsIdempotentAndIgnoresCancelledEvents() {
        val once = dispatching()
        assertSame(once, SosEvents.beginDispatch(once, "e1"))
        val cancelled = SosEvents.cancel(armed(), "e1", t0)
        assertEquals(SosPhase.CANCELLED, SosEvents.beginDispatch(cancelled, "e1")?.phase)
    }

    @Test
    fun addContactsDeduplicatesByNormalisedPhone() {
        val event = SosEvents.addContacts(
            dispatching(),
            "e1",
            listOf(contact("A", "0912 345 678"), contact("A again", "+84912345678"), contact("Blank", " ")),
        )
        assertEquals(listOf("A"), event?.contacts?.map { it.name })

        val refreshed = SosEvents.addContacts(event, "e1", listOf(contact("A", "84912345678"), contact("B", "0987654321")))
        assertEquals(listOf("A", "B"), refreshed?.contacts?.map { it.name })
    }

    @Test
    fun addContactsIsIgnoredOutsideDispatch() {
        val event = SosEvents.addContacts(armed(), "e1", listOf(contact("A", "0912345678")))
        assertTrue(event?.contacts.orEmpty().isEmpty())
    }

    @Test
    fun smsReportsUpdateTheMatchingRowEvenAfterCompletion() {
        var event = SosEvents.addContacts(dispatching(), "e1", listOf(contact("A", "0912345678")))
        event = SosEvents.recordLaunch(event, "e1", "0912345678", SmsLaunch.QUEUED, 2)
        event = SosEvents.complete(event, "e1", t0 + 40_000L)
        event = SosEvents.recordSmsPart(event, "e1", "0912345678", 0, SmsPartOutcome.SENT)
        event = SosEvents.recordSmsPart(event, "e1", "0912345678", 1, SmsPartOutcome.SENT)
        assertEquals(SosPhase.DONE, event?.phase)
        assertEquals(SmsStatus.SENT, event?.contacts?.single()?.sms?.status)
        assertEquals(1, event?.smsSentCount)
    }

    @Test
    fun smsReportForAnotherEventIsIgnored() {
        var event = SosEvents.addContacts(dispatching(), "e1", listOf(contact("A", "0912345678")))
        event = SosEvents.recordLaunch(event, "e1", "0912345678", SmsLaunch.QUEUED, 1)
        val unchanged = SosEvents.recordSmsPart(event, "old", "0912345678", 0, SmsPartOutcome.SENT)
        assertEquals(SmsStatus.SENDING, unchanged?.contacts?.single()?.sms?.status)
    }

    @Test
    fun completeOnlyFromDispatching() {
        assertEquals(SosPhase.COUNTDOWN, SosEvents.complete(armed(), "e1", t0)?.phase)
        assertEquals(SosPhase.DONE, SosEvents.complete(dispatching(), "e1", t0)?.phase)
    }

    @Test
    fun abandonedCountdownIsClosedInsteadOfDispatched() {
        val event = armed()
        val justExpired = event.deadlineMs + 1_000L
        assertSame(event, SosEvents.dropStaleCountdown(event, justExpired))

        val longAgo = event.deadlineMs + SosEvents.STALE_COUNTDOWN_MS + 1L
        assertEquals(SosPhase.CANCELLED, SosEvents.dropStaleCountdown(event, longAgo)?.phase)

        val inFlight = dispatching()
        assertSame(inFlight, SosEvents.dropStaleCountdown(inFlight, longAgo))
    }

    @Test
    fun remainingSecondsRoundsUpAndNeverGoesNegative() {
        val event = armed(SosTrigger.MANUAL)
        assertEquals(5, SosEvents.remainingSeconds(event, t0))
        assertEquals(5, SosEvents.remainingSeconds(event, t0 + 1))
        assertEquals(1, SosEvents.remainingSeconds(event, t0 + 4_001L))
        assertEquals(0, SosEvents.remainingSeconds(event, t0 + 60_000L))
    }

    @Test
    fun phoneKeyFoldsVietnameseCountryCode() {
        assertEquals("0912345678", SosEvents.phoneKey("+84 912-345-678"))
        assertEquals("0912345678", SosEvents.phoneKey("84912345678"))
        assertEquals("0912345678", SosEvents.phoneKey("0912.345.678"))
        assertEquals("", SosEvents.phoneKey(" - "))
    }
}
