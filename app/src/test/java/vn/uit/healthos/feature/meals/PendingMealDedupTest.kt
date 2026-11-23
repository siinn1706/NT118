package vn.uit.healthos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Test

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
        val state = MealsUiState(
            todayMeals = listOf(server("m1", "Phở bò", "2026-11-23T01:00:00Z")),
            pendingMeals = listOf(pending("p1", "Phở bò", "2026-11-23T01:00:00Z")),
        )

        assertEquals(500.0, state.todayTotalCalories, 0.0)
        assertEquals(emptyList<PendingMealEntity>(), state.visiblePendingMeals)
    }
}
