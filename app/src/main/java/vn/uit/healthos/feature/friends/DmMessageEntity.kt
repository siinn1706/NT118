package vn.uit.healthos.feature.friends

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Delivery state of a cached direct message. */
object DmStatus {
    const val SENT = "sent"
    const val PENDING = "pending"
    const val FAILED = "failed"
}

/**
 * A direct message kept on the phone so recent history reads offline. Own messages are written before the server has
 * them: [id] is then `local:<clientMessageId>` and is swapped for the server id once the send is acknowledged.
 */
@Entity(
    tableName = "dm_messages",
    indices = [
        Index(value = ["conversation_id", "created_at_ms"]),
        Index(value = ["client_message_id"]),
    ],
)
data class DmMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sender_id") val senderId: String?,
    val content: String,
    @ColumnInfo(name = "client_message_id") val clientMessageId: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    val status: String,
) {
    companion object {
        fun localId(clientMessageId: String): String = "local:$clientMessageId"
    }
}

@Dao
abstract class DmMessageDao {
    @Query("SELECT * FROM dm_messages WHERE conversation_id = :conversationId ORDER BY created_at_ms, id")
    abstract fun observeConversation(conversationId: String): Flow<List<DmMessageEntity>>

    @Query("SELECT * FROM dm_messages WHERE client_message_id = :clientMessageId LIMIT 1")
    abstract suspend fun byClientId(clientMessageId: String): DmMessageEntity?

    @Upsert
    abstract suspend fun upsert(rows: List<DmMessageEntity>)

    @Query("DELETE FROM dm_messages WHERE client_message_id = :clientMessageId AND id != :keepId")
    protected abstract suspend fun deleteOtherCopies(clientMessageId: String, keepId: String)

    @Query("UPDATE dm_messages SET status = :status WHERE client_message_id = :clientMessageId AND status != 'sent'")
    abstract suspend fun setLocalStatus(clientMessageId: String, status: String)

    /**
     * Keeps the newest [keep] messages of a conversation. Messages that are not on the server yet are always kept.
     */
    @Query(
        """
        DELETE FROM dm_messages WHERE conversation_id = :conversationId AND status = 'sent' AND id NOT IN (
            SELECT id FROM dm_messages WHERE conversation_id = :conversationId
            ORDER BY created_at_ms DESC LIMIT :keep
        )
        """,
    )
    abstract suspend fun trim(conversationId: String, keep: Int)

    /** Stores server copies; an own message replaces its local placeholder (matched by client message id). */
    @Transaction
    open suspend fun putServerMessages(rows: List<DmMessageEntity>) {
        rows.forEach { row -> row.clientMessageId?.let { deleteOtherCopies(it, row.id) } }
        upsert(rows)
    }
}
