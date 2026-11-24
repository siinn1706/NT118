package vn.uit.healthos.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import vn.uit.healthos.core.data.outbox.OutboxRetention

/**
 * Wipes rows that belong to the signed-in account. `daily_steps` stays here because it holds the sensor baseline;
 * the step repository clears it (and the baseline) itself when a different account signs in. Outbox items of the
 * types in [OutboxRetention] survive, so an SOS email still waiting for the network is not cancelled by signing out.
 */
@Dao
abstract class UserDataDao {
    @Query("DELETE FROM outbox WHERE type NOT IN (:keepTypes)")
    protected abstract suspend fun clearOutboxExcept(keepTypes: List<String>)

    @Query("DELETE FROM dashboard_cache")
    protected abstract suspend fun clearDashboardCache()

    @Query("DELETE FROM hydration_logs")
    protected abstract suspend fun clearHydrationLogs()

    @Query("DELETE FROM pending_meals")
    protected abstract suspend fun clearPendingMeals()

    @Query("DELETE FROM dose_cache")
    protected abstract suspend fun clearDoseCache()

    @Query("DELETE FROM dm_messages")
    protected abstract suspend fun clearDirectMessages()

    @Query("DELETE FROM leaderboard_cache")
    protected abstract suspend fun clearLeaderboardCache()

    @Transaction
    open suspend fun clearAll() {
        clearOutboxExcept(OutboxRetention.SESSION_INDEPENDENT_TYPES)
        clearDashboardCache()
        clearHydrationLogs()
        clearPendingMeals()
        clearDoseCache()
        clearDirectMessages()
        clearLeaderboardCache()
    }
}
