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
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.CacheFirstLoader
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.auth.AuthRepository
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfileDto? = null,
    val healthGoal: HealthGoalDto? = null,
    val preferences: AppPreferences = AppPreferences(),
    val leaderboardVisibility: LeaderboardVisibility = LeaderboardVisibility.Default,
    /** First ever load with no profile cached: a placeholder instead of the default name. */
    val showSkeleton: Boolean = false,
    /** A background refresh over the cached profile is running. */
    val refreshing: Boolean = false,
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

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val loader = CacheFirstLoader(viewModelScope, profileRepository.cachedProfile, profileRepository::refreshProfile)

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
        viewModelScope.launch {
            loader.state.collect { load ->
                _state.update {
                    it.copy(
                        profile = load.data?.profile,
                        healthGoal = load.data?.healthGoal,
                        showSkeleton = load.showSkeleton,
                        refreshing = load.refreshing,
                        // With a cached profile on screen a failed refresh stays quiet; the data is still valid.
                        error = load.error?.takeIf { load.data == null }?.toUiText(),
                    )
                }
            }
        }
    }

    /** The screen became visible: refetch only when the cached profile is missing or older than a minute. */
    fun onVisible() = loader.refreshIfStale()

    /** The refresh button, retry, and after the emergency contacts were edited. */
    fun refresh() = loader.refresh()

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
