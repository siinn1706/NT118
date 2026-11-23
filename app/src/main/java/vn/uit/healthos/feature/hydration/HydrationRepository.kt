package vn.uit.healthos.feature.hydration

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.feature.steps.MetricIngestPayload
import vn.uit.healthos.feature.steps.OUTBOX_TYPE_METRIC_INGEST
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import vn.uit.healthos.core.time.localDayTicker
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "hydration_logs")
data class HydrationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amountMl: Int,
    val loggedAtMs: Long,
)

@Dao
interface HydrationDao {
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM hydration_logs WHERE date = :date")
    fun observeTotalForDate(date: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HydrationLogEntity)
}

@Singleton
class HydrationRepository @Inject constructor(
    private val dao: HydrationDao,
    private val outboxRepository: OutboxRepository,
) {
    /** Today's total; switches to the new day's total at local midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayMl(): Flow<Int> =
        localDayTicker().flatMapLatest { day -> dao.observeTotalForDate(day.toString()) }

    suspend fun addWater(amountMl: Int = 250) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        dao.insert(HydrationLogEntity(date = today, amountMl = amountMl, loggedAtMs = now))
        outboxRepository.enqueue(
            type = OUTBOX_TYPE_METRIC_INGEST,
            payload = MetricIngestPayload(
                externalIdPrefix = "hydration-$today-${UUID.randomUUID().toString().take(8)}",
                metricType = "hydration_ml",
                value = amountMl.toDouble(),
                unit = "ml",
                recordedAtIso = Instant.ofEpochMilli(now).toString(),
                versionMs = now,
            ),
            serializer = MetricIngestPayload.serializer(),
        )
    }
}
