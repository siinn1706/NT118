package vn.uit.healthos.feature.meals

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Local mirror of an offline-created meal while it waits in the outbox to sync. */
@Entity(tableName = "pending_meals")
data class PendingMealEntity(
    @PrimaryKey val localId: String,
    val name: String,
    val calories: Double,
    val mealType: String?,
    val loggedDate: String,
    val loggedAtIso: String,
)

@Dao
interface PendingMealDao {
    @Query("SELECT * FROM pending_meals WHERE loggedDate = :date ORDER BY loggedAtIso DESC")
    fun observeForDate(date: String): Flow<List<PendingMealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingMealEntity)

    @Query("DELETE FROM pending_meals WHERE localId = :localId")
    suspend fun deleteById(localId: String)
}
