package vn.uit.healthos.feature.meds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MedsStatusTest {

    @Test
    fun everyBackendOccurrenceStatusMapsToItsOwnState() {
        val backendStatuses = listOf("pending", "fired", "done", "skipped", "snoozed", "missed", "cancelled")
        val mapped = backendStatuses.map(DoseStatus::of)

        assertFalse(mapped.contains(DoseStatus.UNKNOWN))
        assertEquals(backendStatuses.size, mapped.toSet().size)
        assertEquals(DoseStatus.FIRED, DoseStatus.of("FIRED"))
        assertEquals(DoseStatus.UNKNOWN, DoseStatus.of("taken"))
    }

    @Test
    fun onlyPendingFiredAndSnoozedDosesOfferActionsAndRing() {
        val open = DoseStatus.entries.filter { it.isOpen }.toSet()

        assertEquals(setOf(DoseStatus.PENDING, DoseStatus.FIRED, DoseStatus.SNOOZED), open)
        assertTrue(DoseStatus.of("fired").isOpen)
        assertFalse(DoseStatus.of("cancelled").isOpen)
        assertFalse(DoseStatus.of("something-new").isOpen)
    }

    @Test
    fun refillWarningFollowsProjectedRefillDate() {
        val now = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()

        assertEquals(5, refillWarningDays(10, "2026-11-28T00:00:00+00:00", now))
        assertEquals(REFILL_WARNING_DAYS, refillWarningDays(14, "2026-11-30T00:00:00Z", now))
        assertNull(refillWarningDays(40, "2026-12-13T00:00:00Z", now))
        assertEquals(0, refillWarningDays(3, "2026-11-20T00:00:00Z", now))
    }

    @Test
    fun refillWarningHandlesMissingOrEmptySupply() {
        val now = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()

        assertNull(refillWarningDays(null, "2026-11-24T00:00:00Z", now))
        assertEquals(0, refillWarningDays(0, null, now))
        assertNull(refillWarningDays(3, null, now))
        assertNull(refillWarningDays(3, "not-a-date", now))
    }

    @Test
    fun parsesBackendTimestampsWithZuluOrNumericOffset() {
        val expected = Instant.parse("2026-11-23T01:00:00Z").toEpochMilli()

        assertEquals(expected, parseIsoMillis("2026-11-23T01:00:00Z"))
        assertEquals(expected, parseIsoMillis("2026-11-23T08:00:00+07:00"))
        assertEquals(expected, parseIsoMillis("2026-11-23T01:00:00.000000+00:00"))
        assertNull(parseIsoMillis("2026-11-23"))
    }
}
