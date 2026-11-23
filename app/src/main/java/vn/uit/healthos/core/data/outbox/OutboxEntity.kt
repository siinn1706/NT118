package vn.uit.healthos.core.data.outbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A write made while offline (or not yet confirmed), replayed in order by [OutboxWorker]. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    /** Items sharing a key supersede each other: enqueueing a new one drops the older pending ones. */
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String? = null,
)
