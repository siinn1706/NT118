package vn.uit.healthos.feature.meds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseCacheSyncTest {

    private fun dose(
        id: String,
        status: String,
        planId: String = "plan-1",
        snoozedUntilMs: Long? = null,
    ) = DoseCacheEntity(
        occurrenceId = id,
        reminderId = "rem-1",
        planId = planId,
        planName = "Metformin",
        strength = "500 mg",
        scheduledAtMs = 1_000L,
        status = status,
        snoozedUntilMs = snoozedUntilMs,
    )

    @Test
    fun pausedPlanDosesThatTurnCancelledLoseTheirAlarm() {
        val cached = listOf(dose("a", "pending"), dose("b", "pending"))
        val fetched = mapOf("a" to dose("a", "cancelled"), "b" to dose("b", "pending"))

        val diff = diffDoseCache(cached, fetched, authoritative = true, queuedActionIds = emptySet())

        assertEquals(listOf("a"), diff.cancelAlarmIds)
        assertTrue(diff.deleteIds.isEmpty())
        assertEquals("cancelled", diff.upserts.first { it.occurrenceId == "a" }.status)
    }

    @Test
    fun dosesMissingFromAnAuthoritativeFetchAreDeletedAndSilenced() {
        val cached = listOf(dose("a", "pending"), dose("gone", "pending"))
        val fetched = mapOf("a" to dose("a", "pending"))

        val diff = diffDoseCache(cached, fetched, authoritative = true, queuedActionIds = emptySet())

        assertEquals(listOf("gone"), diff.deleteIds)
        assertEquals(listOf("gone"), diff.cancelAlarmIds)
    }

    @Test
    fun partialFetchNeverDeletesCachedDoses() {
        val cached = listOf(dose("a", "pending"), dose("other", "pending"))
        val fetched = mapOf("a" to dose("a", "pending"))

        val diff = diffDoseCache(cached, fetched, authoritative = false, queuedActionIds = emptySet())

        assertTrue(diff.deleteIds.isEmpty())
        assertTrue(diff.cancelAlarmIds.isEmpty())
    }

    @Test
    fun queuedOfflineActionKeepsLocalStatusAndAlarmStaysOff() {
        val cached = listOf(dose("a", "done"), dose("s", "snoozed", snoozedUntilMs = 9_000L))
        val fetched = mapOf("a" to dose("a", "fired"), "s" to dose("s", "pending"))

        val diff = diffDoseCache(cached, fetched, authoritative = true, queuedActionIds = setOf("a", "s"))
        val byId = diff.upserts.associateBy { it.occurrenceId }

        assertEquals("done", byId.getValue("a").status)
        assertEquals("snoozed", byId.getValue("s").status)
        assertEquals(9_000L, byId.getValue("s").snoozedUntilMs)
        assertEquals(listOf("a"), diff.cancelAlarmIds)
    }

    @Test
    fun serverCancellationWinsOverQueuedAction() {
        val cached = listOf(dose("a", "snoozed", snoozedUntilMs = 9_000L))
        val fetched = mapOf("a" to dose("a", "cancelled"))

        val diff = diffDoseCache(cached, fetched, authoritative = true, queuedActionIds = setOf("a"))

        assertEquals("cancelled", diff.upserts.single().status)
        assertEquals(listOf("a"), diff.cancelAlarmIds)
    }

    @Test
    fun occurrenceFeedKeepsPlanMetadataCachedFromTodayFeed() {
        val cached = listOf(dose("a", "pending"))
        val fromOccurrences = dose("a", "fired", planId = "").copy(planName = "Reminder title", strength = null)

        val diff = diffDoseCache(cached, mapOf("a" to fromOccurrences), authoritative = true, queuedActionIds = emptySet())
        val merged = diff.upserts.single()

        assertEquals("plan-1", merged.planId)
        assertEquals("Metformin", merged.planName)
        assertEquals("500 mg", merged.strength)
        assertEquals("fired", merged.status)
        assertTrue(diff.cancelAlarmIds.isEmpty())
    }
}
