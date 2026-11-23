package vn.uit.healthos.feature.home

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dashboard_cache")
data class DashboardCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val summaryJson: String?,
    val aiAdviceJson: String?,
    val exercisesJson: String?,
    val vitalsJson: String?,
    val updatedAtMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface DashboardCacheDao {
    @Query("SELECT * FROM dashboard_cache WHERE id = 1 LIMIT 1")
    fun observe(): Flow<DashboardCacheEntity?>

    @Query("SELECT * FROM dashboard_cache WHERE id = 1 LIMIT 1")
    suspend fun get(): DashboardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardCacheEntity)

    @Query("DELETE FROM dashboard_cache")
    suspend fun clear()
}
