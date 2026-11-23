package vn.uit.healthos.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LocalDayTickerTest {

    private val saigon = ZoneId.of("Asia/Saigon")

    private fun at(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, 23, hour, minute, second, nano, saigon)

    @Test
    fun waitsExactlyUntilMidnightInTheLastMinute() {
        assertEquals(30_000L, nextDayCheckDelayMs(at(23, 59, 30)))
    }

    @Test
    fun rechecksAtLeastEveryMinuteEarlierInTheDay() {
        assertEquals(MAX_DAY_CHECK_INTERVAL_MS, nextDayCheckDelayMs(at(8, 0)))
        assertEquals(MAX_DAY_CHECK_INTERVAL_MS, nextDayCheckDelayMs(at(0, 0)))
    }

    @Test
    fun neverReturnsAZeroDelayJustBeforeMidnight() {
        assertEquals(1L, nextDayCheckDelayMs(at(23, 59, 59, 999_999_999)))
    }

    @Test
    fun usesTheLocalMidnightOfTheGivenZone() {
        // 16:59:50 UTC is 23:59:50 in Saigon: midnight is 10 s away there, not 7 h.
        val utcInstant = ZonedDateTime.of(2026, 11, 23, 16, 59, 50, 0, ZoneId.of("UTC"))

        assertEquals(10_000L, nextDayCheckDelayMs(utcInstant.withZoneSameInstant(saigon)))
    }
}
