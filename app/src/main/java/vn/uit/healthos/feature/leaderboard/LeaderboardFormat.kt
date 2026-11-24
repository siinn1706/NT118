package vn.uit.healthos.feature.leaderboard

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VietnameseLocale: Locale = Locale.forLanguageTag("vi-VN")

/** Step counts with Vietnamese digit grouping: 12345 becomes "12.345". */
internal fun formatSteps(steps: Int): String = NumberFormat.getIntegerInstance(VietnameseLocale).format(steps)

/** A board date (`yyyy-MM-dd`) as `dd/MM`; anything unparsable is shown as received. */
internal fun formatDay(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(DayFormat) }.getOrDefault(isoDate)

/** `HH:mm` for today, `HH:mm dd/MM` for an older fetch. */
internal fun formatUpdatedAt(epochMs: Long, zone: ZoneId = ZoneId.systemDefault(), nowMs: Long = System.currentTimeMillis()): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return if (time.toLocalDate() == today) time.format(TimeFormat) else time.format(TimeDayFormat)
}

private val DayFormat = DateTimeFormatter.ofPattern("dd/MM")
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val TimeDayFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")
