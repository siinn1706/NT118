package vn.uit.healthos.feature.leaderboard

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Last leaderboard received for one period and scope, shown while offline. */
@Entity(tableName = "leaderboard_cache")
data class LeaderboardCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "fetched_at_ms") val fetchedAtMs: Long,
)

@Dao
interface LeaderboardCacheDao {
    @Query("SELECT * FROM leaderboard_cache WHERE cache_key = :key")
    suspend fun get(key: String): LeaderboardCacheEntity?

    @Upsert
    suspend fun put(entity: LeaderboardCacheEntity)
}
