package vn.uit.healthos.core.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How long a snapshot counts as fresh: a tab visited again within this window does not refetch. */
const val FRESHNESS_WINDOW_MS = 60_000L

/** A cached value and when the server last confirmed it. [stale] means a local write changed it since. */
data class Snapshot<out T>(
    val value: T,
    val updatedAtMs: Long,
    val stale: Boolean = false,
)

/** True while the snapshot was confirmed by the server less than [windowMs] ago and no local write changed it since. */
fun Snapshot<*>?.isFresh(nowMs: Long, windowMs: Long = FRESHNESS_WINDOW_MS): Boolean =
    this != null && !stale && nowMs - updatedAtMs in 0 until windowMs

/**
 * One row of `snapshot_cache` mirrored in memory. The first reader pays for one disk read and JSON decode (off the main
 * thread); every later reader, including a screen created again after a tab switch, gets the value from memory.
 */
class SnapshotStore<T>(
    private val dao: SnapshotCacheDao,
    private val json: Json,
    private val key: String,
    private val serializer: KSerializer<T>,
) {
    private val lock = Mutex()
    private val memory = MutableStateFlow<Snapshot<T>?>(null)

    @Volatile
    private var loaded = false

    /** The snapshot, read from disk on first use, then every change. */
    val snapshots: Flow<Snapshot<T>?> = flow {
        load()
        emitAll(memory)
    }

    suspend fun current(): Snapshot<T>? {
        load()
        return memory.value
    }

    /** Stores a value the server just returned. */
    suspend fun put(value: T, updatedAtMs: Long = System.currentTimeMillis()) = lock.withLock {
        loaded = true
        save(Snapshot(value, updatedAtMs))
    }

    /**
     * Applies a write the user just made to the cached value, so it shows at once, and marks the snapshot stale so the
     * next visit fetches the server's version. Does nothing while nothing is cached.
     */
    suspend fun edit(transform: (T) -> T) = lock.withLock {
        loadLocked()
        val current = memory.value ?: return@withLock
        save(Snapshot(transform(current.value), current.updatedAtMs, stale = true))
    }

    /** Marks the snapshot stale without changing it: the server changed in a way only a refetch can show. */
    suspend fun invalidate() = lock.withLock {
        loadLocked()
        val current = memory.value ?: return@withLock
        if (!current.stale) save(current.copy(stale = true))
    }

    /** Forgets the snapshot, on disk and in memory (sign-out, account switch). */
    suspend fun clear() = lock.withLock {
        dao.delete(key)
        memory.value = null
        loaded = true
    }

    private suspend fun load() {
        if (!loaded) lock.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        memory.value = dao.get(key)?.let { decode(it) }
        loaded = true
    }

    private suspend fun save(snapshot: Snapshot<T>) {
        memory.value = snapshot
        val payload = withContext(Dispatchers.Default) { json.encodeToString(serializer, snapshot.value) }
        dao.put(SnapshotCacheEntity(key, payload, snapshot.updatedAtMs, snapshot.stale))
    }

    private suspend fun decode(row: SnapshotCacheEntity): Snapshot<T>? = withContext(Dispatchers.Default) {
        try {
            Snapshot(json.decodeFromString(serializer, row.payloadJson), row.updatedAtMs, row.stale)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
