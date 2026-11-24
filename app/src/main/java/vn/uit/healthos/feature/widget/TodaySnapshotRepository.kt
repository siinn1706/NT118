package vn.uit.healthos.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.TokenStore
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.time.localDayTicker
import vn.uit.healthos.feature.climate.ClimateCoach
import vn.uit.healthos.feature.home.CachedDashboard
import vn.uit.healthos.feature.home.HomeRepository
import vn.uit.healthos.feature.home.TodayMetrics
import vn.uit.healthos.feature.hydration.HydrationRepository
import vn.uit.healthos.feature.steps.StepCounterRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "healthos_widgets")

/**
 * Today's metrics for widgets, built with the same [TodayMetrics.compute] the Home screen uses and gated by
 * [WidgetPrivacy]. Everything it reads is local (Room and DataStore), so widgets render offline.
 */
@Singleton
class TodaySnapshotRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val homeRepository: HomeRepository,
    private val stepRepository: StepCounterRepository,
    private val hydrationRepository: HydrationRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val tokenStore: TokenStore,
) {
    private val store: DataStore<Preferences> get() = context.widgetDataStore

    /** Whether widgets may show health data right now. */
    fun observeAccess(): Flow<WidgetAccess> = combine(
        tokenStore.tokens.map { it != null },
        preferencesRepository.appLockEnabled,
    ) { signedIn, lockEnabled -> WidgetPrivacy.decide(signedIn, lockEnabled) }
        .distinctUntilChanged()

    suspend fun access(): WidgetAccess = observeAccess().first()

    /** Today's metrics, re-emitted on every local write, dashboard refresh and at local midnight. */
    fun observeToday(): Flow<WidgetContent<TodayMetrics>> {
        val metrics = combine(
            homeRepository.observeCached(),
            localDayTicker(),
            stepRepository.observeTodaySteps(),
            hydrationRepository.observeTodayMl(),
            preferencesRepository.preferences,
        ) { cached, today, steps, hydrationMl, prefs -> MetricInputs(cached, today, steps, hydrationMl, prefs) }
        return combine(metrics, observeHydrationTarget(), observeAccess()) { inputs, targetMl, access ->
            WidgetPrivacy.gate(access) {
                TodayMetrics.compute(
                    cached = inputs.cached,
                    today = inputs.today,
                    localStepsToday = inputs.steps,
                    localHydrationMl = inputs.hydrationMl,
                    prefs = inputs.prefs,
                    hydrationTargetMl = targetMl,
                )
            }
        }.distinctUntilChanged()
    }

    suspend fun today(): WidgetContent<TodayMetrics> = observeToday().first()

    /**
     * Remembers the climate- and weight-adjusted water goal Home last computed, so widgets show the same target
     * without fetching weather themselves. Returns true when the stored value changed.
     */
    suspend fun rememberHydrationTarget(targetMl: Int): Boolean {
        if (targetMl <= 0) return false
        var changed = false
        store.edit { prefs ->
            if (prefs[KEY_HYDRATION_TARGET] != targetMl) {
                prefs[KEY_HYDRATION_TARGET] = targetMl
                changed = true
            }
        }
        return changed
    }

    private fun observeHydrationTarget(): Flow<Int> =
        store.data.map { it[KEY_HYDRATION_TARGET] ?: DEFAULT_HYDRATION_TARGET_ML }.distinctUntilChanged()

    internal suspend fun clear() {
        store.edit { it.clear() }
    }

    private data class MetricInputs(
        val cached: CachedDashboard?,
        val today: LocalDate,
        val steps: Int,
        val hydrationMl: Int,
        val prefs: AppPreferences,
    )

    private companion object {
        val KEY_HYDRATION_TARGET = intPreferencesKey("hydration_target_ml")
        val DEFAULT_HYDRATION_TARGET_ML = ClimateCoach.evaluate(null, null).totalHydrationTargetMl
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WidgetDataModule {
    /** The remembered water goal derives from the account's weight, so it goes with the account. */
    @Provides
    @IntoSet
    fun provideWidgetDataCleanup(repository: TodaySnapshotRepository): UserDataCleanup =
        UserDataCleanup { repository.clear() }
}
