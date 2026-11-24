package vn.uit.healthos.feature.profile

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
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.auth.AuthRepository
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfileDto? = null,
    val healthGoal: HealthGoalDto? = null,
    val preferences: AppPreferences = AppPreferences(),
    val leaderboardVisibility: LeaderboardVisibility = LeaderboardVisibility.Default,
    val loading: Boolean = false,
    val loggingOut: Boolean = false,
    val error: UiText? = null,
)

sealed interface ProfileEvent {
    data object SignedOut : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(loading = true))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collectLatest { prefs ->
                _state.update { it.copy(preferences = prefs) }
            }
        }
        viewModelScope.launch {
            profileRepository.leaderboardVisibility.collectLatest { visibility ->
                _state.update { it.copy(leaderboardVisibility = visibility) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val res = profileRepository.getProfile()) {
                is ApiResult.Success -> _state.update { it.copy(profile = res.data) }
                is ApiResult.Failure -> _state.update { it.copy(error = res.error.toUiText()) }
            }
            val goal = (profileRepository.getHealthGoal() as? ApiResult.Success)?.data
            profileRepository.syncPreferencesFromServer()
            _state.update { it.copy(healthGoal = goal, loading = false) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            profileRepository.setThemeMode(mode)
        }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch {
            profileRepository.setAccentPreset(preset)
        }
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch {
            profileRepository.setBackgroundStyle(style)
        }
    }

    fun setReducedTransparency(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReducedTransparency(enabled)
        }
    }

    fun setLeaderboardVisibility(visibility: LeaderboardVisibility) {
        viewModelScope.launch {
            profileRepository.setLeaderboardVisibility(visibility)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAppLockEnabled(enabled)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(loggingOut = true) }
            authRepository.logout()
            _state.update { it.copy(loggingOut = false) }
            _events.send(ProfileEvent.SignedOut)
        }
    }
}
