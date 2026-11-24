package vn.uit.healthos.feature.widget

import vn.uit.healthos.feature.meds.DoseCacheEntity
import vn.uit.healthos.feature.meds.DoseStatus
import java.time.Instant
import java.time.ZoneId

/** The dose to answer next, plus the other open doses still due later the same local day. */
data class DoseAgenda(
    val next: DoseCacheEntity?,
    val laterToday: List<DoseCacheEntity>,
)

object NextDoseSelector {
    /** A dose stays "next" for this long after it was due, so a late reminder can still be marked taken. */
    const val OVERDUE_GRACE_MS = 2 * 3600_000L

    /** How far back the widget reads the cache, so doses snoozed past their schedule are still found. */
    const val LOOKBACK_MS = 24 * 3600_000L

    /** How far ahead the widget looks for the next dose (covers tomorrow morning after the last evening dose). */
    const val LOOKAHEAD_MS = 36 * 3600_000L

    const val MAX_LATER_DOSES = 3

    /** When a dose is due: the snooze end if snoozed, otherwise its schedule. */
    fun dueAtMs(dose: DoseCacheEntity): Long = dose.snoozedUntilMs ?: dose.scheduledAtMs

    fun select(doses: List<DoseCacheEntity>, nowMs: Long, zone: ZoneId): DoseAgenda {
        val open = doses
            .filter { DoseStatus.of(it.status).isOpen && dueAtMs(it) >= nowMs - OVERDUE_GRACE_MS }
            .sortedWith(compareBy(::dueAtMs, DoseCacheEntity::occurrenceId))
        val next = open.firstOrNull() ?: return DoseAgenda(next = null, laterToday = emptyList())
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val later = open.drop(1)
            .filter { Instant.ofEpochMilli(dueAtMs(it)).atZone(zone).toLocalDate() == today }
            .take(MAX_LATER_DOSES)
        return DoseAgenda(next = next, laterToday = later)
    }
}
