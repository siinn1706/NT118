package vn.uit.healthos.feature.onboarding

import vn.uit.healthos.core.designsystem.theme.AccentPreset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.feature.profile.ProfileRepository
import vn.uit.healthos.feature.profile.UserProfileUpdateBody
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.profile.EmergencyContactInput
import vn.uit.healthos.feature.profile.toContactsOrNull

data class EmergencyContactForm(
    val name: String = "",
    val relationship: String = "",
    val phone: String = "",
    val email: String = "",
)

/** The onboarding steps in order; the last one (accent) is optional and never blocks completion. */
object OnboardingSteps {
    const val BODY = 0
    const val ACTIVITY = 1
    const val GOAL = 2
    const val CONTACTS = 3
    const val APPEARANCE = 4
    const val COUNT = 5
    const val LAST = COUNT - 1

    fun next(step: Int): Int = (step + 1).coerceAtMost(LAST)

    fun previous(step: Int): Int = (step - 1).coerceAtLeast(BODY)

    fun isLast(step: Int): Boolean = step >= LAST
}

data class OnboardingUiState(
    val step: Int = OnboardingSteps.BODY,
    val fullName: String = "",
    val dateOfBirth: String = "",
    val sex: BiologicalSex = BiologicalSex.MALE,
    val heightCm: String = "",
    val weightKg: String = "",
    val bloodType: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalDirection: GoalDirection = GoalDirection.MAINTAIN,
    val targetWeightKg: String = "",
    val contacts: List<EmergencyContactForm> = listOf(EmergencyContactForm()),
    val accentPreset: AccentPreset = AccentPreset.Default,
    val dynamicColor: Boolean = false,
    val loading: Boolean = false,
    val savingDraft: Boolean = false,
    val errorMessage: UiText? = null,
    val validationError: String? = null,
) {
    val ageYears: Int?
        get() = runCatching {
            val dob = LocalDate.parse(dateOfBirth.trim())
            val years = Period.between(dob, LocalDate.now()).years
            if (years in 5..120) years else null
        }.getOrNull()

    val tdee: TdeeResult?
        get() {
            val h = heightCm.toDoubleOrNull()?.takeIf { it in 50.0..300.0 } ?: return null
            val w = weightKg.toDoubleOrNull()?.takeIf { it in 20.0..500.0 } ?: return null
            val age = ageYears ?: return null
            return TdeeCalculator.calculate(
                sex = sex,
                weightKg = w,
                heightCm = h,
                ageYears = age,
                activity = activityLevel,
                goal = goalDirection,
            )
        }
}

sealed interface OnboardingEvent {
    data object Completed : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    init {
        loadInitialData()
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _state.update { it.copy(accentPreset = prefs.accentPreset, dynamicColor = prefs.dynamicColor) }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val profile = (profileRepository.getProfile() as? ApiResult.Success)?.data
            if (profile != null) {
                _state.update { s ->
                    s.copy(
                        fullName = profile.fullName ?: profile.displayName,
                        dateOfBirth = profile.dateOfBirth ?: s.dateOfBirth,
                        sex = BiologicalSex.entries.firstOrNull { it.apiValue == profile.gender } ?: s.sex,
                        heightCm = profile.heightCm?.toInt()?.toString() ?: s.heightCm,
                        weightKg = profile.weightKg?.toString() ?: s.weightKg,
                        targetWeightKg = profile.weightKg?.toString() ?: s.targetWeightKg,
                        bloodType = profile.bloodType.orEmpty(),
                        contacts = profile.emergencyContacts?.takeIf { it.isNotEmpty() }?.map {
                            EmergencyContactForm(
                                name = it.name,
                                relationship = it.relationship,
                                phone = it.phone,
                                email = it.email.orEmpty(),
                            )
                        } ?: s.contacts,
                    )
                }
            }
            val draft = (profileRepository.getOnboardingDraft() as? ApiResult.Success)?.data
            if (draft != null) {
                applyDraft(draft.data)
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun onFullNameChange(v: String) = _state.update { it.copy(fullName = v, validationError = null, errorMessage = null) }
    fun onDateOfBirthChange(v: String) = _state.update { it.copy(dateOfBirth = v, validationError = null, errorMessage = null) }
    fun onSexChange(v: BiologicalSex) = _state.update { it.copy(sex = v) }
    fun onHeightChange(v: String) = _state.update { it.copy(heightCm = v.filter { c -> c.isDigit() || c == '.' }, validationError = null) }
    fun onWeightChange(v: String) = _state.update { it.copy(weightKg = v.filter { c -> c.isDigit() || c == '.' }, validationError = null) }
    fun onBloodTypeChange(v: String) = _state.update { it.copy(bloodType = v.uppercase().take(4)) }
    fun onActivityChange(v: ActivityLevel) = _state.update { it.copy(activityLevel = v) }
    fun onGoalDirectionChange(v: GoalDirection) = _state.update { it.copy(goalDirection = v) }
    /** Applies the accent at once (the whole app recolours as a preview) and syncs it like Settings does. */
    fun onAccentChange(preset: AccentPreset) {
        _state.update { it.copy(accentPreset = preset) }
        viewModelScope.launch { profileRepository.setAccentPreset(preset) }
    }

    fun onTargetWeightChange(v: String) = _state.update { it.copy(targetWeightKg = v.filter { c -> c.isDigit() || c == '.' }, validationError = null) }

    fun onContactChange(index: Int, updated: EmergencyContactForm) {
        _state.update { s ->
            val list = s.contacts.toMutableList()
            if (index in list.indices) list[index] = updated
            s.copy(contacts = list, validationError = null, errorMessage = null)
        }
    }

    fun addContact() {
        _state.update { s ->
            if (s.contacts.size >= 5) s else s.copy(contacts = s.contacts + EmergencyContactForm())
        }
    }

    fun removeContact(index: Int) {
        _state.update { s ->
            if (s.contacts.size <= 1 || index !in s.contacts.indices) {
                s
            } else {
                s.copy(contacts = s.contacts.toMutableList().apply { removeAt(index) })
            }
        }
    }

    fun previousStep() {
        _state.update { it.copy(step = OnboardingSteps.previous(it.step), validationError = null, errorMessage = null) }
    }

    fun nextStep() {
        val current = _state.value
        val error = validateStep(current)
        if (error != null) {
            _state.update { it.copy(validationError = error) }
            return
        }
        val next = OnboardingSteps.next(current.step)
        _state.update {
            // The goal step starts from the weight just entered rather than an invented number.
            it.copy(step = next, targetWeightKg = it.targetWeightKg.ifBlank { it.weightKg }, validationError = null, errorMessage = null)
        }
        persistDraftAsync()
    }

    fun completeOnboarding() {
        val s = _state.value
        val error = validateStep(s)
        if (error != null) {
            _state.update { it.copy(validationError = error) }
            return
        }
        val validContacts = s.contacts.map { it.toInput() }.toContactsOrNull() ?: run {
            _state.update { it.copy(validationError = "CONTACT_INCOMPLETE") }
            return
        }
        val tdee = s.tdee
        val targetWeight = s.targetWeightKg.toDoubleOrNull()?.coerceIn(30.0, 200.0)
            ?: s.weightKg.toDoubleOrNull()?.coerceIn(30.0, 200.0)

        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val profileBody = UserProfileUpdateBody(
                fullName = s.fullName.trim(),
                dateOfBirth = s.dateOfBirth.trim(),
                gender = s.sex.apiValue,
                bloodType = s.bloodType.trim().ifBlank { null },
                heightCm = s.heightCm.toDoubleOrNull(),
                weightKg = s.weightKg.toDoubleOrNull(),
                emergencyContacts = validContacts,
                onboardingCompleted = true,
            )
            when (val res = profileRepository.updateProfile(profileBody)) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, errorMessage = res.error.toUiText()) }
                    return@launch
                }
                is ApiResult.Success -> Unit
            }

            if (targetWeight != null) {
                profileRepository.saveHealthGoal(targetWeightKg = targetWeight)
            }
            if (tdee != null) {
                preferencesRepository.setNutritionTargets(
                    dailyCalorieTarget = tdee.targetCalories,
                    targetWeightKg = targetWeight?.toFloat(),
                    activityLevel = s.activityLevel.key,
                    goalDirection = s.goalDirection.key,
                )
            }
            preferencesRepository.setOnboardingStatus("completed")
            profileRepository.deleteOnboardingDraft()
            _state.update { it.copy(loading = false) }
            _events.send(OnboardingEvent.Completed)
        }
    }

    private fun validateStep(s: OnboardingUiState): String? = when (s.step) {
        OnboardingSteps.BODY -> when {
            s.fullName.isBlank() -> "NAME_REQUIRED"
            s.ageYears == null -> "DOB_INVALID"
            s.heightCm.toDoubleOrNull()?.let { it in 50.0..300.0 } != true -> "HEIGHT_INVALID"
            s.weightKg.toDoubleOrNull()?.let { it in 20.0..500.0 } != true -> "WEIGHT_INVALID"
            else -> null
        }
        OnboardingSteps.GOAL -> when {
            s.targetWeightKg.toDoubleOrNull()?.let { it in 30.0..200.0 } != true -> "TARGET_WEIGHT_INVALID"
            else -> null
        }
        OnboardingSteps.CONTACTS -> if (s.contacts.map { it.toInput() }.toContactsOrNull() == null) "CONTACT_INCOMPLETE" else null
        else -> null
    }

    private fun persistDraftAsync() {
        val s = _state.value
        val draftJson = buildJsonObject {
            put("step", JsonPrimitive(s.step))
            put("full_name", JsonPrimitive(s.fullName))
            put("date_of_birth", JsonPrimitive(s.dateOfBirth))
            put("gender", JsonPrimitive(s.sex.apiValue))
            put("height_cm", JsonPrimitive(s.heightCm))
            put("weight_kg", JsonPrimitive(s.weightKg))
            put("blood_type", JsonPrimitive(s.bloodType))
            put("activity_level", JsonPrimitive(s.activityLevel.key))
            put("goal_direction", JsonPrimitive(s.goalDirection.key))
            put("target_weight_kg", JsonPrimitive(s.targetWeightKg))
            put(
                "emergency_contacts",
                buildJsonArray {
                    s.contacts.forEach { c ->
                        add(
                            buildJsonObject {
                                put("name", JsonPrimitive(c.name))
                                put("relationship", JsonPrimitive(c.relationship))
                                put("phone", JsonPrimitive(c.phone))
                                put("email", JsonPrimitive(c.email))
                            },
                        )
                    }
                },
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(savingDraft = true) }
            profileRepository.saveOnboardingDraft(draftJson)
            _state.update { it.copy(savingDraft = false) }
        }
    }

    private fun applyDraft(data: JsonObject) {
        _state.update { s ->
            val contactsArray: JsonArray? = runCatching { data["emergency_contacts"]?.jsonArray }.getOrNull()
            val restoredContacts = contactsArray?.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    EmergencyContactForm(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        relationship = obj["relationship"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        phone = obj["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        email = obj["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }.getOrNull()
            }?.takeIf { it.isNotEmpty() }

            s.copy(
                fullName = data["full_name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: s.fullName,
                dateOfBirth = data["date_of_birth"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: s.dateOfBirth,
                sex = BiologicalSex.entries.firstOrNull {
                    it.apiValue == data["gender"]?.jsonPrimitive?.contentOrNull
                } ?: s.sex,
                heightCm = data["height_cm"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: s.heightCm,
                weightKg = data["weight_kg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: s.weightKg,
                bloodType = data["blood_type"]?.jsonPrimitive?.contentOrNull ?: s.bloodType,
                activityLevel = ActivityLevel.entries.firstOrNull {
                    it.key == data["activity_level"]?.jsonPrimitive?.contentOrNull
                } ?: s.activityLevel,
                goalDirection = GoalDirection.entries.firstOrNull {
                    it.key == data["goal_direction"]?.jsonPrimitive?.contentOrNull
                } ?: s.goalDirection,
                targetWeightKg = data["target_weight_kg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                    ?: s.targetWeightKg,
                contacts = restoredContacts ?: s.contacts,
            )
        }
    }
}

/** Onboarding shares the contact rules of the contacts editor, so both send the same shape to the server. */
private fun EmergencyContactForm.toInput() = EmergencyContactInput(name, relationship, phone, email)
