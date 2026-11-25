package vn.uit.healthos.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The last server answer behind a tab (today's meals, medication plans, the profile), kept as JSON so the tab can show
 * it at once on the next visit or launch. [stale] marks a snapshot a local write has changed: it is still shown, but the
 * next visit refetches it whatever its age.
 */
@Entity(tableName = "snapshot_cache")
data class SnapshotCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "stale", defaultValue = "0") val stale: Boolean = false,
)

@Dao
interface SnapshotCacheDao {
    @Query("SELECT * FROM snapshot_cache WHERE cache_key = :key")
    suspend fun get(key: String): SnapshotCacheEntity?

    @Upsert
    suspend fun put(entity: SnapshotCacheEntity)

    @Query("DELETE FROM snapshot_cache WHERE cache_key = :key")
    suspend fun delete(key: String)
}

@Module
@InstallIn(SingletonComponent::class)
object SnapshotCacheModule {
    @Provides
    fun provideSnapshotCacheDao(db: AppDatabase): SnapshotCacheDao = db.snapshotCacheDao()
}
