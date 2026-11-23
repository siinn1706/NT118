package vn.uit.healthos.feature.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.R
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import java.time.LocalDate
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

data class MedsUiState(
    val todayDoses: List<DoseCacheEntity> = emptyList(),
    val plans: List<MedicationPlanDto> = emptyList(),
    val planAdherence: Map<String, Int> = emptyMap(),
    val selectedPlan: MedicationPlanDetailDto? = null,
    val selectedAdherence: AdherenceDto? = null,
    val form: MedPlanFormState = MedPlanFormState(),
    val loading: Boolean = false,
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

    private val _state = MutableStateFlow(MedsUiState(loading = true))
    val state: StateFlow<MedsUiState> = _state.asStateFlow()

    private val _events = Channel<MedsNavEvent>(Channel.BUFFERED)
    val events: Flow<MedsNavEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeTodayDoses().collectLatest { doses ->
                _state.update { it.copy(todayDoses = doses) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null, errorMessage = null) }
            repository.refreshAndReschedule()
            when (val plansRes = repository.listPlans()) {
                is ApiResult.Success -> {
                    val plans = plansRes.data.filter { it.status != "cancelled" }
                    _state.update { it.copy(plans = plans, loading = false) }
                    plans.take(10).forEach { plan ->
                        launch {
                            val adh = (repository.getAdherence(plan.id) as? ApiResult.Success)?.data
                            if (adh != null) {
                                _state.update { s ->
                                    s.copy(planAdherence = s.planAdherence + (plan.id to adh.percent))
                                }
                            }
                        }
                    }
                }
                is ApiResult.Failure -> _state.update { s ->
                    s.copy(
                        loading = false,
                        loadError = if (s.todayDoses.isEmpty() && s.plans.isEmpty()) plansRes.error.toUiText() else null,
                    )
                }
            }
        }
    }

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
            _state.update { it.copy(loading = true, errorMessage = null) }
            val detail = (repository.getPlanDetail(planId) as? ApiResult.Success)?.data
            val adh = (repository.getAdherence(planId) as? ApiResult.Success)?.data
            _state.update { it.copy(selectedPlan = detail, selectedAdherence = adh, loading = false) }
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
                refresh()
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
                refresh()
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
                refresh()
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
                    refresh()
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
    }
}
