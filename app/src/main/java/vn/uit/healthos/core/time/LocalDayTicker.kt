package vn.uit.healthos.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Emits the current local date, then the new date each time local midnight passes.
 *
 * `delay` follows the uptime clock, which stops while the device sleeps, so a single delay until midnight
 * can wake hours late. The wall clock is therefore re-read at least every [MAX_DAY_CHECK_INTERVAL_MS].
 * Day rows are keyed with [ZoneId.systemDefault], so the ticker uses the same zone by default.
 */
fun localDayTicker(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
    while (true) {
        val now = ZonedDateTime.now(zone)
        emit(now.toLocalDate())
        delay(nextDayCheckDelayMs(now))
    }
}.distinctUntilChanged()

internal const val MAX_DAY_CHECK_INTERVAL_MS = 60_000L

/** Milliseconds to wait before re-reading the clock: until the next local midnight, capped at one minute. */
internal fun nextDayCheckDelayMs(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    val untilMidnight = Duration.between(now, nextMidnight).toMillis()
    return untilMidnight.coerceIn(1L, MAX_DAY_CHECK_INTERVAL_MS)
}
