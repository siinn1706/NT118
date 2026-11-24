package vn.uit.healthos.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Wipes rows that belong to the signed-in account. `daily_steps` stays here because it holds the sensor baseline;
 * the step repository clears it (and the baseline) itself when a different account signs in.
 */
@Dao
abstract class UserDataDao {
    @Query("DELETE FROM outbox")
    protected abstract suspend fun clearOutbox()

    @Query("DELETE FROM dashboard_cache")
    protected abstract suspend fun clearDashboardCache()

    @Query("DELETE FROM hydration_logs")
    protected abstract suspend fun clearHydrationLogs()

    @Query("DELETE FROM pending_meals")
    protected abstract suspend fun clearPendingMeals()

    @Query("DELETE FROM dose_cache")
    protected abstract suspend fun clearDoseCache()

    @Transaction
    open suspend fun clearAll() {
        clearOutbox()
        clearDashboardCache()
        clearHydrationLogs()
        clearPendingMeals()
        clearDoseCache()
    }
}
