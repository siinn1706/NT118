package vn.uit.healthos.feature.safety

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.feature.profile.EmergencyContactDto
import vn.uit.healthos.feature.profile.ProfileRepository
import javax.inject.Inject

data class SafetyUiState(
    val local: SafetyLocalState = SafetyLocalState(),
    val contacts: List<EmergencyContactDto> = emptyList(),
    val isPublished: Boolean = false,
    val activeTokenCount: Int = 0,
    val loading: Boolean = false,
    val mintingToken: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val sosApi: SosApi,
    private val profileRepository: ProfileRepository,
    private val safetyPreferences: SafetyPreferences,
    private val sosController: SosController,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(SafetyUiState(loading = true))
    val state: StateFlow<SafetyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            safetyPreferences.state.collectLatest { local ->
                _state.update { it.copy(local = local) }
            }
        }
        refresh()
    }

    /**
     * Shows the offline contact cache at once, then refreshes it from `GET /users/me` (which updates the cache).
     * A stored QR token the server no longer lists as active is dropped so the card never shows a dead link.
     */
    fun refresh() {
        viewModelScope.launch {
            val cached = safetyPreferences.cachedContacts()
            _state.update { it.copy(loading = true, error = null, contacts = cached) }
            val profile = (profileRepository.getProfile() as? ApiResult.Success)?.data
            val stored = safetyPreferences.qrToken()
            val owner = (fetchOwnerView() as? ApiResult.Success)?.data
            if (owner != null) {
                safetyPreferences.saveCachedCard(owner.card)
                if (stored != null && !QrTokenPolicy.isReusable(stored, owner.tokens, System.currentTimeMillis())) {
                    safetyPreferences.clearQrToken(stored)
                }
            }
            _state.update { s ->
                s.copy(
                    contacts = if (profile != null) profile.emergencyContacts.orEmpty() else s.contacts,
                    isPublished = owner?.isPublished ?: s.isPublished,
                    activeTokenCount = owner?.activeTokenCount ?: s.activeTokenCount,
                    loading = false,
                )
            }
        }
    }

    fun toggleFallDetection(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            safetyPreferences.setFallDetectionEnabled(enabled)
            if (enabled) {
                FallDetectionService.start(context)
            } else {
                // A fall SOS still counting down or sending must keep a foreground host once monitoring stops.
                if (sosController.event.value?.isActive == true) SosAlertService.host(context)
                FallDetectionService.stop(context)
            }
        }
    }

    fun toggleTestMode(enabled: Boolean) {
        viewModelScope.launch {
            safetyPreferences.setTestModeEnabled(enabled)
        }
    }

    /** Makes sure a working QR is on the device: reuses the stored token while it is valid, mints only otherwise. */
    fun ensureEmergencyQrToken() {
        if (_state.value.mintingToken) return
        viewModelScope.launch {
            _state.update { it.copy(mintingToken = true, error = null) }
            val error = provideQrToken()
            _state.update { it.copy(mintingToken = false, error = error) }
        }
    }

    /** Returns the message to show, or null when the device now holds a usable QR token. */
    private suspend fun provideQrToken(): UiText? {
        val stored = safetyPreferences.qrToken()
        val owner = when (val res = fetchOwnerView()) {
            is ApiResult.Success -> res.data
            is ApiResult.Failure -> {
                val offlineReuse = QrTokenPolicy.isReusable(stored, null, System.currentTimeMillis())
                return if (offlineReuse) null else res.error.toUiText()
            }
        }
        _state.update { it.copy(isPublished = owner.isPublished, activeTokenCount = owner.activeTokenCount) }

        // An unpublished card answers every scan with "not found", so publishing comes before reuse or mint.
        if (!owner.isPublished) {
            when (val res = apiCall(json) { sosApi.patchEmergencyProfile(EmergencyProfileUpdateBody(isPublished = true)).data }) {
                is ApiResult.Success -> _state.update { it.copy(isPublished = true) }
                is ApiResult.Failure -> return res.error.toUiText()
            }
        }
        if (QrTokenPolicy.isReusable(stored, owner.tokens, System.currentTimeMillis())) return null

        var minted = mintToken()
        var activeCount = owner.activeTokenCount
        if (minted is ApiResult.Failure && minted.error.code == TOO_MANY_ACTIVE_TOKENS) {
            val orphan = QrTokenPolicy.revokeCandidate(owner.tokens, keepId = stored?.id)
                ?: return minted.error.toUiText()
            when (val revoked = apiCall(json) { sosApi.revokeToken(orphan).data }) {
                is ApiResult.Success -> activeCount = revoked.data.count { it.isActive }
                is ApiResult.Failure -> return revoked.error.toUiText()
            }
            minted = mintToken()
        }
        return when (minted) {
            is ApiResult.Success -> {
                val token = minted.data
                safetyPreferences.saveQrToken(
                    StoredQrToken(
                        id = token.tokenMeta.id,
                        shareUrl = token.shareUrl,
                        expiresAtMs = QrTokenPolicy.parseInstantMs(token.tokenMeta.expiresAt),
                    ),
                )
                _state.update { it.copy(activeTokenCount = activeCount + 1) }
                null
            }
            is ApiResult.Failure -> minted.error.toUiText()
        }
    }

    private suspend fun fetchOwnerView(): ApiResult<EmergencyProfileOwnerViewDto> =
        apiCall(json) { sosApi.getEmergencyProfile().data }

    private suspend fun mintToken(): ApiResult<EmergencyShareTokenWithSecretDto> = apiCall(json) {
        sosApi.mintToken(
            EmergencyShareTokenCreateBody(
                label = QrTokenPolicy.APP_TOKEN_LABEL,
                acknowledgeLowCompleteness = true,
            ),
        ).data
    }

    private companion object {
        const val TOO_MANY_ACTIVE_TOKENS = "TOO_MANY_ACTIVE_TOKENS"
    }
}
