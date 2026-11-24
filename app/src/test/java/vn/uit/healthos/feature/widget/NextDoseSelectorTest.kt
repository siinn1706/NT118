package vn.uit.healthos.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.feature.meds.DoseCacheEntity
import java.time.LocalDate
import java.time.ZoneId

class NextDoseSelectorTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.of(2026, 11, 24)

    private fun at(hour: Int, minute: Int = 0, plusDays: Long = 0): Long =
        day.plusDays(plusDays).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun dose(
        id: String,
        scheduledAtMs: Long,
        status: String = "pending",
        snoozedUntilMs: Long? = null,
    ) = DoseCacheEntity(
        occurrenceId = id,
        reminderId = "r-$id",
        planId = "p",
        planName = "Thuốc $id",
        strength = null,
        scheduledAtMs = scheduledAtMs,
        status = status,
        snoozedUntilMs = snoozedUntilMs,
    )

    @Test
    fun picksTheEarliestOpenDoseAndListsTheRestOfToday() {
        val doses = listOf(
            dose("evening", at(20)),
            dose("noon", at(12)),
            dose("taken", at(9), status = "done"),
            dose("afternoon", at(16)),
            dose("tomorrow", at(8, plusDays = 1)),
        )

        val agenda = NextDoseSelector.select(doses, nowMs = at(10), zone = zone)

        assertEquals("noon", agenda.next?.occurrenceId)
        assertEquals(listOf("afternoon", "evening"), agenda.laterToday.map { it.occurrenceId })
    }

    @Test
    fun snoozedDoseIsDueAtTheEndOfTheSnooze() {
        val snoozed = dose("snoozed", at(7), status = "snoozed", snoozedUntilMs = at(10, 10))
        val later = dose("later", at(10, 5))

        val agenda = NextDoseSelector.select(listOf(later, snoozed), nowMs = at(10), zone = zone)

        assertEquals("later", agenda.next?.occurrenceId)
        assertEquals(listOf("snoozed"), agenda.laterToday.map { it.occurrenceId })
        assertEquals(at(10, 10), NextDoseSelector.dueAtMs(snoozed))
    }

    @Test
    fun recentlyMissedDoseStaysAnswerableWithinTheGracePeriod() {
        val late = dose("late", at(9))
        val stale = dose("stale", at(6))

        val agenda = NextDoseSelector.select(listOf(stale, late), nowMs = at(10), zone = zone)

        assertEquals("late", agenda.next?.occurrenceId)
        assertTrue(agenda.laterToday.isEmpty())
    }

    @Test
    fun afterTheLastDoseTheNextOneIsTomorrowWithNothingLaterToday() {
        val agenda = NextDoseSelector.select(
            listOf(dose("tomorrow", at(8, plusDays = 1)), dose("done", at(20), status = "done")),
            nowMs = at(23),
            zone = zone,
        )

        assertEquals("tomorrow", agenda.next?.occurrenceId)
        assertTrue(agenda.laterToday.isEmpty())
    }

    @Test
    fun noOpenDosesGivesAnEmptyAgenda() {
        val agenda = NextDoseSelector.select(listOf(dose("x", at(12), status = "cancelled")), at(10), zone)

        assertNull(agenda.next)
        assertTrue(agenda.laterToday.isEmpty())
    }

    @Test
    fun laterListIsCapped() {
        val doses = (11..20).map { dose("d$it", at(it)) }

        val agenda = NextDoseSelector.select(doses, nowMs = at(10), zone = zone)

        assertEquals(NextDoseSelector.MAX_LATER_DOSES, agenda.laterToday.size)
    }
}
