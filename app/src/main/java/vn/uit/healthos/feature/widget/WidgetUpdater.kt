package vn.uit.healthos.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.TokenStore
import vn.uit.healthos.feature.home.HomeRepository
import vn.uit.healthos.feature.safety.SafetyPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Every home-screen widget the app ships. */
enum class WidgetKind {
    VITAL_SCORE,
    STEPS,
    HYDRATION,
    NEXT_DOSE,
    SOS,
    ASK_AI,
    CALORIES,
    ;

    companion object {
        /** Widgets that show a number derived from the step count. */
        val STEP_DEPENDENT: Set<WidgetKind> = setOf(STEPS, VITAL_SCORE)

        /** Widgets that show a number derived from today's water intake. */
        val HYDRATION_DEPENDENT: Set<WidgetKind> = setOf(HYDRATION, VITAL_SCORE)

        /** Widgets that read the cached dashboard or the user's goals. */
        val DASHBOARD_DEPENDENT: Set<WidgetKind> = setOf(VITAL_SCORE, STEPS, HYDRATION, CALORIES)
    }
}

/**
 * Re-renders widgets after the data behind them changes. Writers call [update] (suspending) or [requestUpdate]
 * (fire-and-forget); a [WidgetRefreshWorker] also refreshes everything every 30 minutes for day rollover.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun updateAll() = update(WidgetKind.entries)

    suspend fun update(kinds: Collection<WidgetKind>) {
        kinds.toSet().forEach { kind ->
            try {
                widgetFor(kind).updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A widget that fails to render must not break the write that triggered the refresh.
                Log.w(TAG, "Widget refresh failed for $kind", e)
            }
        }
    }

    suspend fun update(vararg kinds: WidgetKind) = update(kinds.toList())

    /** Refreshes [kinds] without making the caller wait, e.g. from a sensor callback. */
    fun requestUpdate(kinds: Collection<WidgetKind>) {
        scope.launch { update(kinds) }
    }

    fun requestUpdateAll() = requestUpdate(WidgetKind.entries)

    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun widgetFor(kind: WidgetKind): GlanceAppWidget = when (kind) {
        WidgetKind.VITAL_SCORE -> VitalScoreWidget()
        WidgetKind.STEPS -> StepsWidget()
        WidgetKind.HYDRATION -> HydrationWidget()
        WidgetKind.NEXT_DOSE -> NextDoseWidget()
        WidgetKind.SOS -> SosWidget()
        WidgetKind.ASK_AI -> AskAiWidget()
        WidgetKind.CALORIES -> CaloriesWidget()
    }

    private companion object {
        const val TAG = "WidgetUpdater"
        const val REFRESH_INTERVAL_MINUTES = 30L
    }
}

/** Refreshes every widget so day rollover and data written while no widget session ran show up. */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        widgetUpdater.updateAll()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "healthos_widget_refresh"
    }
}

/**
 * Refreshes widgets when state written outside the widget-aware repositories changes: sign-in/out (tokens are saved
 * after `SessionCleaner.onSignedIn` runs), the app-lock switch, goals, a new dashboard from the server, and the cached
 * emergency contacts the SOS widget counts.
 */
@Singleton
class WidgetAppStateWatcher @Inject constructor(
    private val widgetUpdater: WidgetUpdater,
    private val tokenStore: TokenStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val homeRepository: HomeRepository,
    private val safetyPreferences: SafetyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // drop(1): the first value is the state at process start, which the widgets already show.
            val session = tokenStore.tokens.map { it?.userId }.distinctUntilChanged().drop(1)
                .map { WidgetKind.entries.toSet() }
            val privacy = preferencesRepository.appLockEnabled.distinctUntilChanged().drop(1)
                .map { WidgetKind.entries.toSet() }
            val goals = preferencesRepository.preferences
                .map { it.stepGoal to it.dailyCalorieTarget }
                .distinctUntilChanged()
                .drop(1)
                .map { WidgetKind.DASHBOARD_DEPENDENT }
            val dashboard = homeRepository.observeCached().map { it?.updatedAtMs }.distinctUntilChanged().drop(1)
                .map { WidgetKind.DASHBOARD_DEPENDENT }
            // The safety store emits on every write; only a changed contact count matters to the SOS widget.
            val contacts = safetyPreferences.state.map { safetyPreferences.cachedContacts().size }
                .distinctUntilChanged()
                .drop(1)
                .map { setOf(WidgetKind.SOS) }
            merge(session, privacy, goals, dashboard, contacts).collect { kinds -> widgetUpdater.update(kinds) }
        }
    }
}
