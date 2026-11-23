package vn.uit.healthos.feature.meds

import java.time.OffsetDateTime

/**
 * Backend `ReminderOccurrenceStatusEnum` values. A dose is [isOpen] while its alarm may still ring and the user can
 * still answer it with Taken / Skip / Snooze; every other status is final for the local alarm.
 */
enum class DoseStatus(val wire: String, val isOpen: Boolean) {
    PENDING("pending", isOpen = true),
    FIRED("fired", isOpen = true),
    SNOOZED("snoozed", isOpen = true),
    DONE("done", isOpen = false),
    SKIPPED("skipped", isOpen = false),
    MISSED("missed", isOpen = false),
    CANCELLED("cancelled", isOpen = false),
    UNKNOWN("", isOpen = false),
    ;

    companion object {
        fun of(wire: String): DoseStatus {
            val normalized = wire.trim().lowercase()
            return entries.firstOrNull { it != UNKNOWN && it.wire == normalized } ?: UNKNOWN
        }
    }
}

/** Backend refill alerts start at 7 days of projected supply left. */
const val REFILL_WARNING_DAYS = 7

private const val DAY_MS = 24 * 3600_000L

/**
 * Whole days of supply left according to the backend projection (`next_refill_estimated_at`), or null when no
 * warning is due. Returns 0 when the supply is exhausted or the projected refill date has passed.
 */
fun refillWarningDays(supplyUnits: Int?, nextRefillEstimatedAt: String?, nowMs: Long): Int? {
    if (supplyUnits == null) return null
    if (supplyUnits <= 0) return 0
    val dueMs = nextRefillEstimatedAt?.let(::parseIsoMillis) ?: return null
    val days = Math.floorDiv(dueMs - nowMs, DAY_MS).coerceAtLeast(0L).toInt()
    return days.takeIf { it <= REFILL_WARNING_DAYS }
}

/** Parses backend ISO-8601 timestamps with either `Z` or a numeric offset. */
fun parseIsoMillis(iso: String): Long? =
    runCatching { OffsetDateTime.parse(iso.trim()).toInstant().toEpochMilli() }.getOrNull()
