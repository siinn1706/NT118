package vn.uit.healthos.feature.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.CacheFirstLoader
import vn.uit.healthos.core.ui.CacheFirstState
import vn.uit.healthos.core.ui.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MedPlanFormState(
    val name: String = "",
    val strength: String = "",
    val form: String = "tablet",
    val instructions: String = "",
    val doseTimes: List<String> = listOf("08:00"),
    val newTimeInput: String = "20:00",
    val startDate: String = LocalDate.now().toString(),
    val endDate: String = "",
    val refillSupplyText: String = "30",
    val notes: String = "",
)

/** A dose of today's timeline with its time already formatted. */
data class DoseRowUi(val dose: DoseCacheEntity, val timeLabel: String)

/** A plan card: the plan, its adherence percent when known and the days of supply left when a refill is due. */
data class PlanRowUi(val plan: MedicationPlanDto, val adherencePercent: Int?, val refillWarningDays: Int?)

data class MedsUiState(
    val todayDoses: List<DoseRowUi> = emptyList(),
    val plans: List<PlanRowUi> = emptyList(),
    val selectedPlan: MedicationPlanDetailDto? = null,
    val selectedAdherence: AdherenceDto? = null,
    val form: MedPlanFormState = MedPlanFormState(),
    /** First ever load with no plans cached: placeholders instead of "no plans". */
    val showSkeleton: Boolean = false,
    /** A background refresh over the cached plans is running. */
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    /** Why the list could not load; shown only while there is nothing cached to display. */
    val loadError: UiText? = null,
    /** Validation or save failure for the plan form. */
    val errorMessage: UiText? = null,
)

sealed interface MedsNavEvent {
    data object BackToList : MedsNavEvent
}

@HiltViewModel
class MedsViewModel @Inject constructor(
    private val repository: MedsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedsUiState())
    val state: StateFlow<MedsUiState> = _state.asStateFlow()

    private val _events = Channel<MedsNavEvent>(Channel.BUFFERED)
    val events: Flow<MedsNavEvent> = _events.receiveAsFlow()

    private val loader = CacheFirstLoader(viewModelScope, repository.cachedPlans, fetch = repository::refreshPlans)

    init {
        viewModelScope.launch {
            combine(repository.observeTodayDoses(), loader.state) { doses, load -> buildList(doses, load) }
                // Formatting times and projecting refills for every row stays off the main thread.
                .flowOn(Dispatchers.Default)
                .collect { lists ->
                    _state.update {
                        it.copy(
                            todayDoses = lists.doses,
                            plans = lists.plans,
                            showSkeleton = lists.showSkeleton,
                            refreshing = lists.refreshing,
                            loadError = lists.loadError,
                        )
                    }
                }
        }
    }

    /** The tab became visible: refetch only when the cached plans are missing, changed locally or older than a minute. */
    fun onVisible() = loader.refreshIfStale()

    /** The refresh button. */
    fun refresh() = loader.refresh()

    private fun buildList(doses: List<DoseCacheEntity>, load: CacheFirstState<MedsPlansCache>): MedsLists {
        val nowMs = System.currentTimeMillis()
        val cache = load.data
        val doseRows = doses.map { dose ->
            DoseRowUi(dose, TIME_FORMAT.format(Instant.ofEpochMilli(dose.snoozedUntilMs ?: dose.scheduledAtMs).atZone(ZONE)))
        }
        val planRows = cache?.plans.orEmpty().map { plan ->
            PlanRowUi(
                plan = plan,
                adherencePercent = cache?.adherence?.get(plan.id),
                refillWarningDays = if (plan.status == "active") {
                    refillWarningDays(plan.refillSupplyUnits, plan.nextRefillEstimatedAt, nowMs)
                } else {
                    null
                },
            )
        }
        return MedsLists(
            doses = doseRows,
            plans = planRows,
            showSkeleton = load.showSkeleton,
            refreshing = load.refreshing,
            loadError = load.error?.takeIf { cache == null && doses.isEmpty() }?.toUiText(),
        )
    }

    private data class MedsLists(
        val doses: List<DoseRowUi>,
        val plans: List<PlanRowUi>,
        val showSkeleton: Boolean,
        val refreshing: Boolean,
        val loadError: UiText?,
    )

    fun markDoseDone(dose: DoseCacheEntity) {
        viewModelScope.launch { repository.recordDoseAction(dose, DoseActionReceiver.ACTION_DONE) }
    }

    fun skipDose(dose: DoseCacheEntity) {
        viewModelScope.launch { repository.recordDoseAction(dose, DoseActionReceiver.ACTION_SKIP) }
    }

    fun snoozeDose(dose: DoseCacheEntity) {
        viewModelScope.launch { repository.recordDoseAction(dose, DoseActionReceiver.ACTION_SNOOZE) }
    }

    fun loadPlanDetail(planId: String) {
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null) }
            val detail = (repository.getPlanDetail(planId) as? ApiResult.Success)?.data
            val adh = (repository.getAdherence(planId) as? ApiResult.Success)?.data
            _state.update { it.copy(selectedPlan = detail, selectedAdherence = adh) }
        }
    }

    fun togglePauseSelected() {
        val plan = _state.value.selectedPlan ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val res = if (plan.status == "paused") {
                repository.resumePlan(plan.id)
            } else {
                repository.pausePlan(plan.id, plan.doses.map { it.reminderId })
            }
            if (res is ApiResult.Success) {
                loadPlanDetail(plan.id)
                loader.refresh(userRequested = false)
            }
            _state.update { it.copy(saving = false) }
        }
    }

    fun refillSelected(units: Int) {
        val plan = _state.value.selectedPlan ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            if (repository.refillPlan(plan.id, units) is ApiResult.Success) {
                loadPlanDetail(plan.id)
                loader.refresh(userRequested = false)
            }
            _state.update { it.copy(saving = false) }
        }
    }

    fun deleteSelected() {
        val plan = _state.value.selectedPlan ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            if (repository.deletePlan(plan.id, plan.doses.map { it.reminderId }) is ApiResult.Success) {
                _state.update { it.copy(saving = false, selectedPlan = null) }
                loader.refresh(userRequested = false)
                _events.send(MedsNavEvent.BackToList)
            } else {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun updateForm(transform: (MedPlanFormState) -> MedPlanFormState) {
        _state.update { it.copy(form = transform(it.form), errorMessage = null) }
    }

    fun addDoseTime() {
        val raw = _state.value.form.newTimeInput.trim()
        if (!TIME_REGEX.matches(raw)) {
            _state.update { it.copy(errorMessage = UiText.Res(R.string.meds_error_time_format)) }
            return
        }
        _state.update { s ->
            if (s.form.doseTimes.size >= 8 || raw in s.form.doseTimes) {
                s
            } else {
                s.copy(form = s.form.copy(doseTimes = (s.form.doseTimes + raw).sorted()))
            }
        }
    }

    fun removeDoseTime(time: String) {
        _state.update { s ->
            if (s.form.doseTimes.size <= 1) {
                s
            } else {
                s.copy(form = s.form.copy(doseTimes = s.form.doseTimes - time))
            }
        }
    }

    fun submitNewPlan() {
        val f = _state.value.form
        if (f.name.isBlank()) {
            _state.update { it.copy(errorMessage = UiText.Res(R.string.meds_error_name_required)) }
            return
        }
        if (f.doseTimes.isEmpty()) {
            _state.update { it.copy(errorMessage = UiText.Res(R.string.meds_error_dose_time_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, errorMessage = null) }
            val body = MedicationPlanCreateBody(
                name = f.name.trim(),
                strength = f.strength.trim().ifBlank { null },
                form = f.form,
                instructions = f.instructions.trim().ifBlank { null },
                startDate = f.startDate.trim().ifBlank { null },
                endDate = f.endDate.trim().ifBlank { null },
                tzid = DEFAULT_MED_TZID,
                doseTimes = f.doseTimes,
                repeat = "daily",
                refillSupplyUnits = f.refillSupplyText.toIntOrNull(),
                notes = f.notes.trim().ifBlank { null },
            )
            when (val res = repository.createPlan(body)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, form = MedPlanFormState()) }
                    loader.refresh(userRequested = false)
                    _events.send(MedsNavEvent.BackToList)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(saving = false, errorMessage = res.error.toUiText()) }
                }
            }
        }
    }

    private companion object {
        val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val ZONE: ZoneId = ZoneId.of(DEFAULT_MED_TZID)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
