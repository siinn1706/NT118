package vn.uit.healthos.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import vn.uit.healthos.core.data.outbox.OutboxDao
import vn.uit.healthos.core.data.outbox.OutboxEntity
import vn.uit.healthos.feature.home.DashboardCacheDao
import vn.uit.healthos.feature.home.DashboardCacheEntity
import vn.uit.healthos.feature.hydration.HydrationDao
import vn.uit.healthos.feature.hydration.HydrationLogEntity
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
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun dailyStepDao(): DailyStepDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun dashboardCacheDao(): DashboardCacheDao
    abstract fun pendingMealDao(): PendingMealDao
    abstract fun doseCacheDao(): DoseCacheDao
    abstract fun userDataDao(): UserDataDao
}
