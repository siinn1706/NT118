package vn.uit.healthos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.db.Snapshot
import vn.uit.healthos.core.data.db.isFresh
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.CacheFirstLoader
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.climate.ClimateAdvice
import vn.uit.healthos.feature.climate.ClimateCoach
import vn.uit.healthos.feature.climate.ClimateConditions
import vn.uit.healthos.feature.climate.ClimateRepository
import vn.uit.healthos.feature.meds.DEFAULT_MED_TZID
import vn.uit.healthos.feature.meds.DoseActionReceiver
import vn.uit.healthos.feature.meds.DoseCacheEntity
import vn.uit.healthos.feature.meds.MedsRepository
import vn.uit.healthos.feature.hydration.HydrationRepository
import vn.uit.healthos.core.time.localDayTicker
import vn.uit.healthos.feature.profile.ProfileRepository
import vn.uit.healthos.feature.steps.DailyStepEntity
import vn.uit.healthos.feature.steps.StepCounterRepository
import vn.uit.healthos.feature.widget.NextDoseSelector
import vn.uit.healthos.feature.widget.TodaySnapshotRepository
import vn.uit.healthos.feature.widget.WidgetKind
import vn.uit.healthos.feature.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class TrendMetric { STEPS, HEART_RATE }

data class TrendDayPoint(
    val date: String,
    val label: String,
    val value: Double,
)

data class HomeUiState(
    val userName: String = "",
    val vitalScore: Float? = null,
    val stepsToday: Int = 0,
    val stepGoal: Int = 8_000,
    val caloriesToday: Double? = null,
    val calorieTarget: Int? = null,
    val restingHeartRate: Double? = null,
    val hydrationMl: Int = 0,
    val climateConditions: ClimateConditions? = null,
    val climateAdvice: ClimateAdvice = ClimateCoach.evaluate(null, null),
    val aiAdvice: DashboardAiAdviceDto? = null,
    val aiInsightFallback: UiText? = null,
    val exercises: List<ExerciseSuggestionUi> = emptyList(),
    val selectedTrend: TrendMetric = TrendMetric.STEPS,
    val stepTrend: List<TrendDayPoint> = emptyList(),
    val heartRateTrend: List<TrendDayPoint> = emptyList(),
    val pendingSyncCount: Int = 0,
    val profileName: String = "",
    val avatarUrl: String? = null,
    val nextDose: DoseCacheEntity? = null,
    /** [nextDose]'s due time as HH:mm in the medication time zone. */
    val nextDoseTime: String? = null,
    val nextDoseOverdue: Boolean = false,
    val layout: HomeLayout = HomeLayout(),
    val editingLayout: Boolean = false,
    val lastUpdatedMs: Long? = null,
    /** [lastUpdatedMs] as HH:mm on this device. */
    val lastUpdatedTime: String? = null,
    /** A refresh is running in the background over the cached dashboard. */
    val isRefreshing: Boolean = false,
    /** The running refresh came from pull-to-refresh, so its indicator shows. */
    val isPullRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val stepRepository: StepCounterRepository,
    private val hydrationRepository: HydrationRepository,
    private val climateRepository: ClimateRepository,
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val outboxRepository: OutboxRepository,
    private val todaySnapshotRepository: TodaySnapshotRepository,
    private val widgetUpdater: WidgetUpdater,
    private val medsRepository: MedsRepository,
    private val layoutStore: HomeLayoutStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val climateFlow = MutableStateFlow<ClimateConditions?>(null)
    private val profileWeightFlow = MutableStateFlow<Double?>(null)

    private val loader = CacheFirstLoader(
        scope = viewModelScope,
        cache = homeRepository.observeCached().map { cached -> cached?.let { Snapshot(it, it.updatedAtMs) } },
        fetch = ::fetchDashboard,
    )

    init {
        observeSources()
        observeLoad()
        observeProfile()
        observeNextDose()
        observeLayout()
        // The climate repository keeps conditions for an hour, so this is usually served from memory.
        viewModelScope.launch { climateFlow.value = climateRepository.getConditions(forceRefresh = false) }
    }

    /** The tab became visible: refetch only when the cached dashboard is missing or older than a minute. */
    fun onVisible() = loader.refreshIfStale()

    private suspend fun fetchDashboard(): ApiResult<Unit> {
        val result = homeRepository.refresh()
        // The profile has its own cache; fetch it only when that is not fresh either.
        if (!profileRepository.cachedProfile.first().isFresh(System.currentTimeMillis())) profileRepository.getProfile()
        climateFlow.value = climateRepository.getConditions(forceRefresh = false)
        return result
    }

    private fun observeLoad() {
        viewModelScope.launch {
            loader.state.collect { load ->
                _state.update {
                    it.copy(
                        isRefreshing = load.refreshing,
                        isPullRefreshing = load.userRefreshing,
                        isOffline = load.error?.isNetwork == true,
                        errorMessage = load.error?.takeIf { load.data == null }?.toUiText(),
                    )
                }
            }
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            profileRepository.cachedProfile.collect { snapshot ->
                val profile = snapshot?.value?.profile ?: return@collect
                profile.weightKg?.let { profileWeightFlow.value = it }
                _state.update {
                    it.copy(profileName = profile.fullName ?: profile.displayName, avatarUrl = profile.avatarUrl)
                }
            }
        }
    }

    private fun observeNextDose() {
        val zone = ZoneId.of(DEFAULT_MED_TZID)
        viewModelScope.launch {
            combine(medsRepository.observeTodayDoses(), minuteTicker()) { doses, nowMs ->
                val dose = NextDoseSelector.select(doses, nowMs, zone).next
                val dueMs = dose?.let(NextDoseSelector::dueAtMs)
                NextDoseUi(
                    dose = dose,
                    time = dueMs?.let { TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(zone)) },
                    overdue = dueMs != null && dueMs < nowMs,
                )
            }.distinctUntilChanged().flowOn(Dispatchers.Default).collect { next ->
                _state.update { it.copy(nextDose = next.dose, nextDoseTime = next.time, nextDoseOverdue = next.overdue) }
            }
        }
    }

    private data class NextDoseUi(val dose: DoseCacheEntity?, val time: String?, val overdue: Boolean)

    private fun observeLayout() {
        viewModelScope.launch {
            layoutStore.layout.collect { layout ->
                _state.update { it.copy(layout = layout) }
            }
        }
    }

    /** Emits the current time now and then once a minute, so an overdue dose leaves the "next dose" tile in time. */
    private fun minuteTicker() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    private fun observeSources() {
        viewModelScope.launch {
            val baseFlow = combine(
                loader.state.map { it.data }.distinctUntilChanged(),
                localDayTicker(),
                stepRepository.observeRecentDays(7),
                hydrationRepository.observeTodayMl(),
                preferencesRepository.preferences,
            ) { cached, today, localStepDays, localHydration, prefs ->
                BaseSnapshot(cached, today, localStepDays, localHydration, prefs)
            }

            combine(
                baseFlow,
                climateFlow,
                profileWeightFlow,
                outboxRepository.pendingCount,
            ) { base, climate, weightKg, pendingCount ->
                buildUiState(base, climate, weightKg, pendingCount)
            }
                // Building the trends and KPIs from the cached dashboard stays off the main thread.
                .flowOn(Dispatchers.Default)
                .collect { computed ->
                _state.update { current ->
                    computed.copy(
                        profileName = current.profileName,
                        avatarUrl = current.avatarUrl,
                        nextDose = current.nextDose,
                        nextDoseTime = current.nextDoseTime,
                        nextDoseOverdue = current.nextDoseOverdue,
                        layout = current.layout,
                        editingLayout = current.editingLayout,
                        selectedTrend = current.selectedTrend,
                        isRefreshing = current.isRefreshing,
                        isPullRefreshing = current.isPullRefreshing,
                        isOffline = current.isOffline,
                        errorMessage = current.errorMessage,
                    )
                }
                shareHydrationTarget(computed)
            }
        }
    }

    /** Pull-to-refresh and retry. */
    fun refresh() = loader.refresh()

    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            climateFlow.value = climateRepository.getConditions(forceRefresh = true)
        }
    }

    fun onAddHydrationCup() {
        viewModelScope.launch {
            hydrationRepository.addWater(250)
        }
    }

    fun onMarkDoseTaken(dose: DoseCacheEntity) {
        viewModelScope.launch {
            medsRepository.recordDoseAction(dose, DoseActionReceiver.ACTION_DONE)
        }
    }

    fun setEditingLayout(editing: Boolean) {
        _state.update { it.copy(editingLayout = editing) }
    }

    fun toggleTile(tile: HomeTile) = saveLayout(_state.value.layout.toggled(tile))

    fun moveTile(tile: HomeTile, delta: Int) = saveLayout(_state.value.layout.moved(tile, delta))

    fun resetLayout() {
        _state.update { it.copy(layout = HomeLayout()) }
        viewModelScope.launch { layoutStore.reset() }
    }

    private fun saveLayout(layout: HomeLayout) {
        // Shown at once; the store write follows and echoes the same layout back.
        _state.update { it.copy(layout = layout) }
        viewModelScope.launch { layoutStore.save(layout) }
    }

    fun onSelectTrend(metric: TrendMetric) {
        _state.update { it.copy(selectedTrend = metric) }
    }

    fun triggerStepSync() {
        viewModelScope.launch {
            stepRepository.enqueueTodaySync()
        }
    }

    /**
     * Widgets cannot fetch the weather themselves, so the climate- and weight-adjusted water goal is remembered for
     * them; once it is known (climate or weight loaded), a changed goal refreshes the widgets that show it.
     */
    private suspend fun shareHydrationTarget(state: HomeUiState) {
        if (climateFlow.value == null && profileWeightFlow.value == null) return
        if (todaySnapshotRepository.rememberHydrationTarget(state.climateAdvice.totalHydrationTargetMl)) {
            widgetUpdater.update(WidgetKind.HYDRATION_DEPENDENT)
        }
    }

    private fun buildUiState(
        base: BaseSnapshot,
        climate: ClimateConditions?,
        weightKg: Double?,
        pendingCount: Int,
    ): HomeUiState {
        val summary = base.cached?.summary
        val todayIso = base.today.toString()
        val climateAdvice = ClimateCoach.evaluate(climate, weightKg)
        val metrics = TodayMetrics.compute(
            cached = base.cached,
            today = base.today,
            localStepsToday = base.localStepDays.firstOrNull { it.date == todayIso }?.steps ?: 0,
            localHydrationMl = base.localHydration,
            prefs = base.prefs,
            hydrationTargetMl = climateAdvice.totalHydrationTargetMl,
        )

        val serverVitals = base.cached?.vitals.orEmpty()
        val stepMap = LinkedHashMap<String, Double>()
        serverVitals.forEach { pt ->
            if (pt.steps != null) stepMap[pt.date] = pt.steps
        }
        base.localStepDays.reversed().forEach { local ->
            val current = stepMap[local.date] ?: 0.0
            stepMap[local.date] = maxOf(current, local.steps.toDouble())
        }
        val stepTrend = stepMap.entries.toList().takeLast(7).map { (date, value) ->
            TrendDayPoint(date = date, label = shortDateLabel(date), value = value)
        }
        val hrTrend = serverVitals
            .filter { it.heartRate != null }
            .takeLast(7)
            .map { pt -> TrendDayPoint(date = pt.date, label = shortDateLabel(pt.date), value = pt.heartRate ?: 0.0) }

        return HomeUiState(
            userName = summary?.userName.orEmpty(),
            vitalScore = metrics.vitalScore,
            stepsToday = metrics.stepsToday,
            stepGoal = metrics.stepGoal,
            caloriesToday = metrics.caloriesIn,
            calorieTarget = metrics.calorieTarget,
            restingHeartRate = metrics.restingHeartRate,
            hydrationMl = metrics.hydrationMl,
            climateConditions = climate,
            climateAdvice = climateAdvice,
            aiAdvice = base.cached?.aiAdvice,
            aiInsightFallback = summary?.aiInsight?.let(DashboardCopy::insightText),
            exercises = base.cached?.exercises.orEmpty().toExerciseUi(),
            stepTrend = stepTrend,
            heartRateTrend = hrTrend,
            pendingSyncCount = pendingCount,
            lastUpdatedMs = base.cached?.updatedAtMs,
            lastUpdatedTime = base.cached?.updatedAtMs?.let {
                TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
            },
        )
    }

    private fun shortDateLabel(isoDate: String): String =
        if (isoDate.length >= 10) "${isoDate.substring(8, 10)}/${isoDate.substring(5, 7)}" else isoDate

    private data class BaseSnapshot(
        val cached: CachedDashboard?,
        val today: LocalDate,
        val localStepDays: List<DailyStepEntity>,
        val localHydration: Int,
        val prefs: AppPreferences,
    )

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
