package vn.uit.healthos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.AppLockManager
import vn.uit.healthos.feature.widget.WidgetAppStateWatcher
import vn.uit.healthos.feature.widget.WidgetUpdater
import javax.inject.Inject

@HiltAndroidApp
class HealthOsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var outboxRepository: OutboxRepository

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var widgetAppStateWatcher: WidgetAppStateWatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appLockManager.register()
        // Drain anything left in the outbox from a previous session once the network is available.
        outboxRepository.scheduleSync()
        // Widgets use updatePeriodMillis=0; this keeps them current across midnight and server refreshes.
        widgetUpdater.schedulePeriodicRefresh()
        widgetAppStateWatcher.start()
    }
}
