package vn.uit.healthos.feature.steps

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_steps")
data class DailyStepEntity(
    /** Local ISO date `YYYY-MM-DD`. */
    @PrimaryKey val date: String,
    val steps: Int,
    /** Last raw cumulative value reported by `Sensor.TYPE_STEP_COUNTER`. */
    val lastSensorValue: Long,
    val updatedAtMs: Long,
)

@Dao
interface DailyStepDao {
    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    fun observeDay(date: String): Flow<DailyStepEntity?>

    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    suspend fun getDay(date: String): DailyStepEntity?

    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 7): Flow<List<DailyStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyStepEntity)
}

/**
 * Pure step delta calculator for Android's cumulative `TYPE_STEP_COUNTER`.
 * Handles:
 * - First reading of a day / after app install (anchors baseline without jumping).
 * - Normal monotonic increment within the same day.
 * - Device reboot mid-day (`newRaw < previous.lastSensorValue` resets baseline and adds `newRaw`).
 * - Midnight rollover (`todayDate != previous.date` starts a fresh day from 0 + delta since last reading).
 */
object StepDeltaCalculator {
    fun applySensorReading(
        todayDate: String,
        newRawCounter: Long,
        existingToday: DailyStepEntity?,
        latestOverall: DailyStepEntity?,
        nowMs: Long,
    ): DailyStepEntity {
        val raw = newRawCounter.coerceAtLeast(0L)
        if (existingToday != null) {
            val delta = when {
                raw >= existingToday.lastSensorValue -> raw - existingToday.lastSensorValue
                // Counter reset after reboot: raw counts steps taken since boot.
                else -> raw
            }
            val safeDelta = delta.coerceIn(0L, 100_000L).toInt()
            return existingToday.copy(
                steps = (existingToday.steps + safeDelta).coerceAtMost(200_000),
                lastSensorValue = raw,
                updatedAtMs = nowMs,
            )
        }
        // New day rollover: if we have a previous day's reading from the same boot, credit only steps since then.
        val initialSteps = if (latestOverall != null && latestOverall.date != todayDate) {
            val delta = if (raw >= latestOverall.lastSensorValue) raw - latestOverall.lastSensorValue else raw
            delta.coerceIn(0L, 50_000L).toInt()
        } else {
            0
        }
        return DailyStepEntity(
            date = todayDate,
            steps = initialSteps,
            lastSensorValue = raw,
            updatedAtMs = nowMs,
        )
    }
}
