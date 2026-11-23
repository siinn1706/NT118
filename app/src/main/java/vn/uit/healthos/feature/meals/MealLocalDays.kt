package vn.uit.healthos.feature.meals

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * The server filters `GET /v1/meals` by UTC calendar days, so a local day east of UTC spans two UTC dates.
 * These helpers widen the query to cover the local days and then regroup meals by the user's local date.
 */
internal object MealLocalDays {

    /** UTC `date_from`/`date_to` whose server window contains every instant of local days [from]..[to]. */
    fun utcQueryDates(from: LocalDate, to: LocalDate, zone: ZoneId): Pair<LocalDate, LocalDate> {
        val start = from.atStartOfDay(zone).toInstant()
        val lastInstant = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
        return start.atZone(ZoneOffset.UTC).toLocalDate() to lastInstant.atZone(ZoneOffset.UTC).toLocalDate()
    }

    /** Parses an ISO-8601 timestamp; a value without an offset is treated as UTC, as the server stores UTC. */
    fun parseInstant(iso: String): Instant? = try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
        } catch (noOffsetError: DateTimeParseException) {
            null
        }
    }

    fun localDateOf(iso: String, zone: ZoneId): LocalDate? = parseInstant(iso)?.atZone(zone)?.toLocalDate()

    /** Meals logged within local days [from]..[to], keeping the input order. */
    fun withinLocalDays(
        meals: List<MealResponseDto>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<MealResponseDto> = meals.filter { meal ->
        localDateOf(meal.loggedAt, zone)?.isWithin(from, to) == true
    }

    /** Calorie totals per local day for the days in [from]..[to] that have at least one meal, oldest first. */
    fun dailyCalories(
        meals: List<MealResponseDto>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<CalorieSummaryPointDto> = meals
        .mapNotNull { meal ->
            val date = localDateOf(meal.loggedAt, zone)?.takeIf { it.isWithin(from, to) } ?: return@mapNotNull null
            date to (meal.nutritionResult?.calories ?: 0.0)
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .entries
        .sortedBy { it.key.toEpochDay() }
        .map { (date, calories) -> CalorieSummaryPointDto(date = date.toString(), totalCalories = calories.sum()) }

    private fun LocalDate.isWithin(from: LocalDate, to: LocalDate): Boolean = !isBefore(from) && !isAfter(to)
}

private const val SAME_MEAL_TOLERANCE_MS = 1_000L

/**
 * Pending rows whose meal is not yet in [serverMeals]. A queued meal can reach the server before its pending row is
 * cleared (for example when the create response was lost and the retry is still queued); such a row is matched to its
 * server meal by name and `logged_at` and hidden so the day's calories never count it twice.
 */
internal fun unsyncedPendingMeals(
    pending: List<PendingMealEntity>,
    serverMeals: List<MealResponseDto>,
): List<PendingMealEntity> {
    if (pending.isEmpty() || serverMeals.isEmpty()) return pending
    val unmatched = serverMeals.toMutableList()
    return pending.filter { row ->
        val rowAt = MealLocalDays.parseInstant(row.loggedAtIso) ?: return@filter true
        val index = unmatched.indexOfFirst { meal ->
            val mealAt = MealLocalDays.parseInstant(meal.loggedAt)
            meal.name == row.name &&
                mealAt != null &&
                Duration.between(mealAt, rowAt).abs().toMillis() < SAME_MEAL_TOLERANCE_MS
        }
        if (index >= 0) {
            unmatched.removeAt(index)
            false
        } else {
            true
        }
    }
}
