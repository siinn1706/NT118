package vn.uit.healthos.feature.meds

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dose_cache")
data class DoseCacheEntity(
    @PrimaryKey val occurrenceId: String,
    val reminderId: String,
    val planId: String,
    val planName: String,
    val strength: String?,
    val scheduledAtMs: Long,
    /** Backend occurrence status, see [DoseStatus]. */
    val status: String,
    val snoozedUntilMs: Long? = null,
)

@Dao
interface DoseCacheDao {
    @Query("SELECT * FROM dose_cache WHERE scheduledAtMs >= :startMs AND scheduledAtMs < :endMs ORDER BY scheduledAtMs ASC")
    fun observeInWindow(startMs: Long, endMs: Long): Flow<List<DoseCacheEntity>>

    @Query("SELECT * FROM dose_cache WHERE scheduledAtMs >= :startMs AND scheduledAtMs < :endMs")
    suspend fun getInWindow(startMs: Long, endMs: Long): List<DoseCacheEntity>

    @Query(
        "SELECT * FROM dose_cache WHERE status IN ('pending', 'snoozed') " +
            "AND COALESCE(snoozedUntilMs, scheduledAtMs) >= :fromMs ORDER BY scheduledAtMs ASC",
    )
    suspend fun upcomingPending(fromMs: Long): List<DoseCacheEntity>

    @Query("SELECT * FROM dose_cache WHERE planId = :planId OR reminderId IN (:reminderIds)")
    suspend fun getForPlan(planId: String, reminderIds: List<String>): List<DoseCacheEntity>

    @Query("DELETE FROM dose_cache WHERE occurrenceId IN (:occurrenceIds)")
    suspend fun deleteByIds(occurrenceIds: List<String>)

    @Query("SELECT * FROM dose_cache WHERE occurrenceId = :occurrenceId LIMIT 1")
    suspend fun getById(occurrenceId: String): DoseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DoseCacheEntity>)

    @Query("UPDATE dose_cache SET status = :status, snoozedUntilMs = :snoozedUntilMs WHERE occurrenceId = :occurrenceId")
    suspend fun updateStatus(occurrenceId: String, status: String, snoozedUntilMs: Long? = null)

    @Query("DELETE FROM dose_cache WHERE scheduledAtMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)

    @Query("DELETE FROM dose_cache")
    suspend fun clearAll()
}
