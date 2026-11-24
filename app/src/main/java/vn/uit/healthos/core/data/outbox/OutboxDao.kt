package vn.uit.healthos.core.data.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Narrow store contract so [OutboxProcessor] can be unit-tested without Room. */
interface OutboxStore {
    suspend fun pending(afterId: Long, limit: Int): List<OutboxEntity>
    suspend fun delete(id: Long)
    suspend fun markFailed(id: Long, error: String)

    /** Records why the item is waiting without counting an attempt (used while the session is missing). */
    suspend fun markHeld(id: Long, error: String)
}

@Dao
interface OutboxDao : OutboxStore {
    @Insert
    suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE id > :afterId ORDER BY id LIMIT :limit")
    override suspend fun pending(afterId: Long, limit: Int): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE type = :type AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pendingOfType(type: String, afterId: Long, limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    override suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    override suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE outbox SET last_error = :error WHERE id = :id")
    override suspend fun markHeld(id: Long, error: String)

    @Query("DELETE FROM outbox WHERE dedupe_key = :key")
    suspend fun deleteByDedupeKey(key: String)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE type = :type")
    fun observeCountByType(type: String): Flow<Int>
}

/** Restricts a store to one item type, for flushing a single feature's queue on demand. */
internal class TypedOutboxStore(private val dao: OutboxDao, private val type: String) : OutboxStore by dao {
    override suspend fun pending(afterId: Long, limit: Int): List<OutboxEntity> = dao.pendingOfType(type, afterId, limit)
}
