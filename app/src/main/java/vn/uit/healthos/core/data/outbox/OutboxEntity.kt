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
    /**
     * The account that queued the item. It is only ever sent while that account is signed in, so an item kept across
     * a sign-out (see [OutboxRetention]) can never be delivered under someone else's session. Null for items queued
     * while signed out and for rows written before the column existed; those go out under whoever signs in.
     */
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String? = null,
)
