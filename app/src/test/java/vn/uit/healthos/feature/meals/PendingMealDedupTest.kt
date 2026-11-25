package vn.uit.healthos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PendingMealDedupTest {

    private fun pending(localId: String, name: String, loggedAt: String, calories: Double = 500.0) = PendingMealEntity(
        localId = localId,
        name = name,
        calories = calories,
        mealType = "lunch",
        loggedDate = "2026-11-23",
        loggedAtIso = loggedAt,
    )

    private fun server(id: String, name: String, loggedAt: String) = MealResponseDto(
        id = id,
        name = name,
        loggedAt = loggedAt,
        nutritionResult = NutritionResultDto(calories = 500.0),
    )

    @Test
    fun keepsAllPendingRowsWhenNoServerMealMatches() {
        val rows = listOf(pending("p1", "Phở bò", "2026-11-23T01:00:00.123Z"))
        val meals = listOf(server("m1", "Cơm tấm", "2026-11-23T01:00:00.123Z"))

        assertEquals(rows, unsyncedPendingMeals(rows, meals))
    }

    @Test
    fun hidesPendingRowWhoseMealAlreadyReachedTheServer() {
        val rows = listOf(
            pending("p1", "Phở bò", "2026-11-23T01:00:00.123456789Z"),
            pending("p2", "Bánh mì", "2026-11-23T02:00:00Z"),
        )
        // Server stores microseconds and answers with an explicit offset.
        val meals = listOf(server("m1", "Phở bò", "2026-11-23T01:00:00.123457+00:00"))

        assertEquals(listOf("p2"), unsyncedPendingMeals(rows, meals).map { it.localId })
    }

    @Test
    fun sameNameAtADifferentTimeIsADifferentMeal() {
        val rows = listOf(pending("p1", "Phở bò", "2026-11-23T01:00:00Z"))
        val meals = listOf(server("m1", "Phở bò", "2026-11-23T01:00:05Z"))

        assertEquals(rows, unsyncedPendingMeals(rows, meals))
    }

    @Test
    fun eachServerMealHidesAtMostOnePendingRow() {
        val rows = listOf(
            pending("p1", "Trà đá", "2026-11-23T01:00:00Z"),
            pending("p2", "Trà đá", "2026-11-23T01:00:00Z"),
        )
        val meals = listOf(server("m1", "Trà đá", "2026-11-23T01:00:00Z"))

        assertEquals(listOf("p2"), unsyncedPendingMeals(rows, meals).map { it.localId })
    }

    @Test
    fun dayTotalCountsASyncedQueuedMealOnce() {
        val day = mealsDayOf(
            week = listOf(server("m1", "Phở bò", "2026-11-23T01:00:00Z")),
            pending = listOf(pending("p1", "Phở bò", "2026-11-23T01:00:00Z")),
            today = LocalDate.of(2026, 11, 23),
            zone = ZoneOffset.UTC,
        )

        assertEquals(500.0, day.todayTotalCalories, 0.0)
        assertEquals(emptyList<PendingMealEntity>(), day.visiblePendingMeals)
    }
}

class MealsDayTest {

    private fun meal(id: String, loggedAt: String, kcal: Double) = MealResponseDto(
        id = id,
        name = id,
        loggedAt = loggedAt,
        nutritionResult = NutritionResultDto(calories = kcal),
    )

    @Test
    fun splitsTheCachedWeekIntoTodayAndDailyTotalsByLocalDate() {
        // 23:30 UTC on the 22nd is already the 23rd in Ho Chi Minh City (UTC+7).
        val week = listOf(
            meal("late", "2026-11-22T23:30:00Z", 300.0),
            meal("yesterday", "2026-11-22T05:00:00Z", 700.0),
            meal("old", "2026-11-10T05:00:00Z", 900.0),
        )
        val day = mealsDayOf(week, emptyList(), LocalDate.of(2026, 11, 23), java.time.ZoneId.of("Asia/Ho_Chi_Minh"))

        assertEquals(listOf("late"), day.todayMeals.map { it.id })
        assertEquals(300.0, day.todayTotalCalories, 0.0)
        assertEquals(
            listOf("2026-11-22" to 700.0, "2026-11-23" to 300.0),
            day.weeklyCalories.map { it.date to it.totalCalories },
        )
    }
}
