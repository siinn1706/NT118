package vn.uit.healthos.feature.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.HealthCheckApi
import vn.uit.healthos.core.network.apiCall
import javax.inject.Inject

sealed interface PingState {
    data object Idle : PingState
    data object Loading : PingState
    data class Ok(val status: String, val millis: Long) : PingState
    data class Failed(val message: String) : PingState
}

@HiltViewModel
class DesignGalleryViewModel @Inject constructor(
    private val healthCheckApi: HealthCheckApi,
    private val json: Json,
) : ViewModel() {

    val baseUrl: String = BuildConfig.HEALTHOS_BASE_URL

    private val _ping = MutableStateFlow<PingState>(PingState.Idle)
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    fun pingBackend() {
        viewModelScope.launch {
            _ping.value = PingState.Loading
            val started = System.nanoTime()
            _ping.value = when (val result = apiCall(json) { healthCheckApi.health() }) {
                is ApiResult.Success -> PingState.Ok(result.data.status, (System.nanoTime() - started) / 1_000_000)
                is ApiResult.Failure -> PingState.Failed("${result.error.code}: ${result.error.message}")
            }
        }
    }
}
