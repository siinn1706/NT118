package vn.uit.healthos.feature.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsStatusTest {

    private val queued = SmsRecord(launch = SmsLaunch.QUEUED, partCount = 3)

    @Test
    fun launchOutcomesMapToStatus() {
        assertEquals(SmsStatus.SENDING, SmsRecord().status)
        assertEquals(SmsStatus.NO_PERMISSION, SmsRecord(launch = SmsLaunch.NO_PERMISSION).status)
        assertEquals(SmsStatus.FAILED, SmsRecord(launch = SmsLaunch.INVALID_NUMBER).status)
        assertEquals(SmsStatus.FAILED, SmsRecord(launch = SmsLaunch.ERROR).status)
    }

    @Test
    fun sentOnlyWhenEveryPartIsSent() {
        val partial = queued.record(0, SmsPartOutcome.SENT).record(1, SmsPartOutcome.SENT)
        assertEquals(SmsStatus.SENDING, partial.status)
        assertEquals(SmsStatus.SENT, partial.record(2, SmsPartOutcome.SENT).status)
    }

    @Test
    fun anyFailedPartFailsTheMessage() {
        val failed = queued.record(0, SmsPartOutcome.SENT).record(1, SmsPartOutcome.SEND_FAILED)
        assertEquals(SmsStatus.FAILED, failed.record(2, SmsPartOutcome.SENT).status)
        assertEquals(SmsStatus.FAILED, queued.record(0, SmsPartOutcome.DELIVERY_FAILED).status)
    }

    @Test
    fun deliveredWhenEveryPartIsDelivered() {
        var record = queued
        (0 until 3).forEach { record = record.record(it, SmsPartOutcome.SENT) }
        record = record.record(0, SmsPartOutcome.DELIVERED).record(1, SmsPartOutcome.DELIVERED)
        assertEquals(SmsStatus.SENT, record.status)
        assertEquals(SmsStatus.DELIVERED, record.record(2, SmsPartOutcome.DELIVERED).status)
    }

    @Test
    fun repeatedAndOutOfRangeReportsAreIgnored() {
        val record = queued.record(0, SmsPartOutcome.SENT).record(0, SmsPartOutcome.SENT).record(7, SmsPartOutcome.SENT)
        assertEquals(setOf(0), record.sentOk)
    }

    @Test
    fun gsmDeliveryStatusRanges() {
        assertEquals(SmsPartOutcome.DELIVERED, SmsDeliveryReport.outcome("3gpp", 0x00))
        assertNull(SmsDeliveryReport.outcome("3gpp", 0x20))
        assertEquals(SmsPartOutcome.DELIVERY_FAILED, SmsDeliveryReport.outcome("3gpp", 0x41))
    }

    @Test
    fun otherFormatsOnlyTrustCompletionOrMissingPdu() {
        assertEquals(SmsPartOutcome.DELIVERED, SmsDeliveryReport.outcome("3gpp2", 0))
        assertNull(SmsDeliveryReport.outcome("3gpp2", 0x30000))
        assertEquals(SmsPartOutcome.DELIVERED, SmsDeliveryReport.outcome(null, null))
    }
}
