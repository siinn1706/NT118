package vn.uit.healthos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MealLocalDaysTest {

    private val saigon = ZoneId.of("Asia/Saigon")
    private val today = LocalDate.of(2026, 11, 23)

    private fun meal(id: String, loggedAt: String, calories: Double? = null) = MealResponseDto(
        id = id,
        name = "Món $id",
        loggedAt = loggedAt,
        nutritionResult = calories?.let { NutritionResultDto(calories = it) },
    )

    @Test
    fun queryCoversPreviousUtcDateForTimezonesAheadOfUtc() {
        assertEquals(
            LocalDate.of(2026, 11, 22) to LocalDate.of(2026, 11, 23),
            MealLocalDays.utcQueryDates(today, today, saigon),
        )
    }

    @Test
    fun queryCoversNextUtcDateForTimezonesBehindUtc() {
        assertEquals(
            LocalDate.of(2026, 11, 23) to LocalDate.of(2026, 11, 24),
            MealLocalDays.utcQueryDates(today, today, ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun queryForUtcZoneIsTheSameDates() {
        val from = today.minusDays(6)
        assertEquals(from to today, MealLocalDays.utcQueryDates(from, today, ZoneId.of("UTC")))
    }

    @Test
    fun breakfastBeforeSevenLocalIsKeptOnTheLocalDay() {
        val meals = listOf(
            meal("late", "2026-11-23T15:00:00Z"), // 22:00 on the 23rd
            meal("breakfast", "2026-11-22T23:30:00Z"), // 06:30 on the 23rd
            meal("yesterday", "2026-11-22T16:59:59Z"), // 23:59:59 on the 22nd
            meal("tomorrow", "2026-11-23T17:00:00Z"), // 00:00 on the 24th
        )

        val todayMeals = MealLocalDays.withinLocalDays(meals, today, today, saigon)

        assertEquals(listOf("late", "breakfast"), todayMeals.map { it.id })
    }

    @Test
    fun dailyCaloriesGroupByLocalDateOldestFirst() {
        val meals = listOf(
            meal("a", "2026-11-23T01:00:00Z", 500.0), // 23rd
            meal("b", "2026-11-22T22:00:00Z", 300.0), // 05:00 on the 23rd
            meal("c", "2026-11-22T10:00:00Z", 400.0), // 22nd
            meal("d", "2026-11-22T11:00:00Z"), // 22nd, not analysed yet
            meal("old", "2026-11-10T10:00:00Z", 900.0), // outside the week
        )

        val points = MealLocalDays.dailyCalories(meals, today.minusDays(6), today, saigon)

        assertEquals(
            listOf(
                CalorieSummaryPointDto(date = "2026-11-22", totalCalories = 400.0),
                CalorieSummaryPointDto(date = "2026-11-23", totalCalories = 800.0),
            ),
            points,
        )
    }

    @Test
    fun parsesServerTimestampFormats() {
        val expected = Instant.parse("2026-11-23T05:00:00.123456Z")
        assertEquals(expected, MealLocalDays.parseInstant("2026-11-23T05:00:00.123456Z"))
        assertEquals(expected, MealLocalDays.parseInstant("2026-11-23T05:00:00.123456+00:00"))
        assertEquals(expected, MealLocalDays.parseInstant("2026-11-23T12:00:00.123456+07:00"))
        assertEquals(expected, MealLocalDays.parseInstant("2026-11-23T05:00:00.123456"))
        assertNull(MealLocalDays.parseInstant("not a date"))
    }

    @Test
    fun mealWithUnparseableTimestampIsLeftOut() {
        val meals = listOf(meal("bad", "garbage", 100.0))
        assertEquals(emptyList<MealResponseDto>(), MealLocalDays.withinLocalDays(meals, today, today, saigon))
        assertEquals(emptyList<CalorieSummaryPointDto>(), MealLocalDays.dailyCalories(meals, today, today, saigon))
    }
}
