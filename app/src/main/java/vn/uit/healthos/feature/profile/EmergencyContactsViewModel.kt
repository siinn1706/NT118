package vn.uit.healthos.feature.profile

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
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import javax.inject.Inject

data class EmergencyContactsUiState(
    val contacts: List<EmergencyContactInput> = emptyList(),
    /** False until `GET /users/me` succeeds; saving before that could wipe contacts the form never showed. */
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** Field errors appear only after the first save attempt, not while the user is still typing. */
    val showFieldErrors: Boolean = false,
    val loadError: UiText? = null,
    val saveError: UiText? = null,
)

sealed interface EmergencyContactsEvent {
    data object Saved : EmergencyContactsEvent
}

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyContactsUiState())
    val state: StateFlow<EmergencyContactsUiState> = _state.asStateFlow()

    private val _events = Channel<EmergencyContactsEvent>(Channel.BUFFERED)
    val events: Flow<EmergencyContactsEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            when (val res = profileRepository.getProfile()) {
                is ApiResult.Success -> {
                    val contacts = res.data.emergencyContacts.orEmpty().map { it.toInput() }
                    _state.update {
                        it.copy(
                            contacts = contacts.ifEmpty { listOf(EmergencyContactInput()) },
                            loaded = true,
                            loading = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = res.error.toUiText()) }
            }
        }
    }

    fun updateContact(index: Int, contact: EmergencyContactInput) {
        _state.update { s ->
            if (index !in s.contacts.indices) return@update s
            s.copy(contacts = s.contacts.toMutableList().apply { set(index, contact) }, saveError = null)
        }
    }

    fun addContact() {
        _state.update { s ->
            if (s.contacts.size >= MAX_EMERGENCY_CONTACTS) s else s.copy(contacts = s.contacts + EmergencyContactInput())
        }
    }

    fun removeContact(index: Int) {
        _state.update { s ->
            if (index !in s.contacts.indices) return@update s
            s.copy(contacts = s.contacts.toMutableList().apply { removeAt(index) }, saveError = null)
        }
    }

    /** Sends only `emergency_contacts`; every other profile field is null and therefore omitted from the PATCH. */
    fun save() {
        val current = _state.value
        if (!current.loaded || current.saving) return
        val contacts = current.contacts.toContactsOrNull()
        if (contacts == null) {
            _state.update { it.copy(showFieldErrors = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, showFieldErrors = true, saveError = null) }
            when (val res = profileRepository.updateProfile(UserProfileUpdateBody(emergencyContacts = contacts))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.send(EmergencyContactsEvent.Saved)
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, saveError = res.error.toUiText()) }
            }
        }
    }
}
