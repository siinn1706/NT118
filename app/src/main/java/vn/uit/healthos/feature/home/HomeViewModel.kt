package vn.uit.healthos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.climate.ClimateAdvice
import vn.uit.healthos.feature.climate.ClimateCoach
import vn.uit.healthos.feature.climate.ClimateConditions
import vn.uit.healthos.feature.climate.ClimateRepository
import vn.uit.healthos.feature.hydration.HydrationRepository
import vn.uit.healthos.core.time.localDayTicker
import vn.uit.healthos.feature.profile.ProfileRepository
import vn.uit.healthos.feature.steps.DailyStepEntity
import vn.uit.healthos.feature.steps.StepCounterRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val lastUpdatedMs: Long? = null,
    val isRefreshing: Boolean = false,
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
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isRefreshing = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val climateFlow = MutableStateFlow<ClimateConditions?>(null)
    private val profileWeightFlow = MutableStateFlow<Double?>(null)

    init {
        observeSources()
        refresh()
    }

    private fun observeSources() {
        viewModelScope.launch {
            val baseFlow = combine(
                homeRepository.observeCached(),
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
            }.collect { computed ->
                _state.update { current ->
                    computed.copy(
                        selectedTrend = current.selectedTrend,
                        isRefreshing = current.isRefreshing,
                        isOffline = current.isOffline,
                        errorMessage = current.errorMessage,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = homeRepository.refresh()
            val profile = (profileRepository.getProfile() as? ApiResult.Success)?.data
            if (profile?.weightKg != null) {
                profileWeightFlow.value = profile.weightKg
            }
            climateFlow.value = climateRepository.getConditions(forceRefresh = false)
            _state.update { s ->
                when (result) {
                    is ApiResult.Success -> s.copy(isRefreshing = false, isOffline = false, errorMessage = null)
                    is ApiResult.Failure -> s.copy(
                        isRefreshing = false,
                        isOffline = result.error.isNetwork,
                        errorMessage = if (s.lastUpdatedMs == null) result.error.toUiText() else null,
                    )
                }
            }
        }
    }

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

    fun onSelectTrend(metric: TrendMetric) {
        _state.update { it.copy(selectedTrend = metric) }
    }

    fun triggerStepSync() {
        viewModelScope.launch {
            stepRepository.enqueueTodaySync()
        }
    }

    private fun buildUiState(
        base: BaseSnapshot,
        climate: ClimateConditions?,
        weightKg: Double?,
        pendingCount: Int,
    ): HomeUiState {
        val summary = base.cached?.summary
        val fetchedOn = base.cached?.let {
            Instant.ofEpochMilli(it.updatedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val kpis = (summary?.toKpis() ?: DashboardKpis()).let { all ->
            if (fetchedOn == null) all else all.forDay(fetchedOn = fetchedOn, today = base.today)
        }
        val todayIso = base.today.toString()
        val sensorSteps = base.localStepDays.firstOrNull { it.date == todayIso }?.steps ?: 0
        val stepsToday = maxOf(sensorSteps, kpis.steps ?: 0)
        val stepGoal = kpis.stepTarget ?: base.prefs.stepGoal

        val calories = kpis.caloriesIn
        val calorieTarget = base.prefs.dailyCalorieTarget ?: kpis.calorieTarget

        val hydrationMl = maxOf(base.localHydration, kpis.hydrationMl ?: 0)

        val hr = kpis.heartRateBpm
            ?: base.cached?.vitals?.lastOrNull { it.heartRate != null }?.heartRate

        val climateAdvice = ClimateCoach.evaluate(climate, weightKg)
        val score = VitalScore.compute(
            steps = stepsToday.takeIf { it > 0 || kpis.steps != null },
            stepGoal = stepGoal,
            caloriesConsumed = calories,
            calorieTarget = calorieTarget,
            hydrationMl = hydrationMl.takeIf { it > 0 || kpis.hydrationMl != null },
            hydrationTargetMl = climateAdvice.totalHydrationTargetMl,
            restingHeartRateBpm = hr,
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
            vitalScore = score,
            stepsToday = stepsToday,
            stepGoal = stepGoal,
            caloriesToday = calories,
            calorieTarget = calorieTarget,
            restingHeartRate = hr,
            hydrationMl = hydrationMl,
            climateConditions = climate,
            climateAdvice = climateAdvice,
            aiAdvice = base.cached?.aiAdvice,
            aiInsightFallback = summary?.aiInsight?.let(DashboardCopy::insightText),
            exercises = base.cached?.exercises.orEmpty().toExerciseUi(),
            stepTrend = stepTrend,
            heartRateTrend = hrTrend,
            pendingSyncCount = pendingCount,
            lastUpdatedMs = base.cached?.updatedAtMs,
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
}
