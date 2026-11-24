package vn.uit.healthos.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import vn.uit.healthos.core.data.outbox.OutboxDao
import vn.uit.healthos.core.data.outbox.OutboxEntity
import vn.uit.healthos.feature.friends.DmMessageDao
import vn.uit.healthos.feature.friends.DmMessageEntity
import vn.uit.healthos.feature.home.DashboardCacheDao
import vn.uit.healthos.feature.home.DashboardCacheEntity
import vn.uit.healthos.feature.hydration.HydrationDao
import vn.uit.healthos.feature.hydration.HydrationLogEntity
import vn.uit.healthos.feature.leaderboard.LeaderboardCacheDao
import vn.uit.healthos.feature.leaderboard.LeaderboardCacheEntity
import vn.uit.healthos.feature.meals.PendingMealDao
import vn.uit.healthos.feature.meals.PendingMealEntity
import vn.uit.healthos.feature.meds.DoseCacheDao
import vn.uit.healthos.feature.meds.DoseCacheEntity
import vn.uit.healthos.feature.steps.DailyStepDao
import vn.uit.healthos.feature.steps.DailyStepEntity

/**
 * Shared database. Feature modules register their entities and DAOs here.
 */
@Database(
    entities = [
        OutboxEntity::class,
        DailyStepEntity::class,
        HydrationLogEntity::class,
        DashboardCacheEntity::class,
        PendingMealEntity::class,
        DoseCacheEntity::class,
        DmMessageEntity::class,
        LeaderboardCacheEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        // Adds outbox.owner_user_id and the dm_messages and leaderboard_cache tables in place; existing rows are kept.
        // AppDatabaseMigrationTest checks the generated statements against the exported 5.json and 6.json.
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun dailyStepDao(): DailyStepDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun dashboardCacheDao(): DashboardCacheDao
    abstract fun pendingMealDao(): PendingMealDao
    abstract fun doseCacheDao(): DoseCacheDao
    abstract fun dmMessageDao(): DmMessageDao
    abstract fun leaderboardCacheDao(): LeaderboardCacheDao
    abstract fun userDataDao(): UserDataDao
}
