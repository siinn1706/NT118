package vn.uit.healthos.feature.ppg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import vn.uit.healthos.R
import vn.uit.healthos.core.ui.UiText

enum class PpgPhase { GUIDE, MEASURING, RESULT }

data class PpgUiState(
    val phase: PpgPhase = PpgPhase.GUIDE,
    val quality: PpgQuality = PpgQuality.NO_FINGER,
    val liveBpm: Int? = null,
    val finalBpm: Int? = null,
    val confidence: Float = 0f,
    val progress: Float = 0f,
    val elapsedSeconds: Float = 0f,
    val waveform: List<Float> = emptyList(),
    val saved: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class PpgViewModel @Inject constructor(
    private val repository: PpgRepository,
) : ViewModel() {

    /** Guards [processor] and [sessionStartMs]: frames arrive on the camera analyzer thread, commands on main. */
    private val lock = Any()
    private val processor = PpgSignalProcessor()
    private var sessionStartMs: Long? = null

    private val _state = MutableStateFlow(PpgUiState())
    val state: StateFlow<PpgUiState> = _state.asStateFlow()

    fun startMeasurement() {
        synchronized(lock) {
            resetSession()
            _state.value = PpgUiState(phase = PpgPhase.MEASURING, quality = PpgQuality.NO_FINGER)
        }
    }

    fun cancelMeasurement() {
        synchronized(lock) {
            resetSession()
            _state.value = PpgUiState(phase = PpgPhase.GUIDE)
        }
    }

    fun onCameraError(message: String) {
        synchronized(lock) {
            resetSession()
            _state.update { it.copy(phase = PpgPhase.GUIDE, errorMessage = UiText.Raw(message)) }
        }
    }

    /**
     * Called for every camera frame. The result is the BPM of the current window only, once the finger has covered
     * the lens without interruption for [TARGET_SECONDS]; at [MAX_SESSION_SECONDS] a current good reading is accepted,
     * otherwise the session fails.
     */
    fun onFrameSample(sample: PpgSample) {
        synchronized(lock) {
            if (_state.value.phase != PpgPhase.MEASURING) return
            val start = sessionStartMs ?: sample.timestampMs.also { sessionStartMs = it }
            val totalElapsed = ((sample.timestampMs - start) / 1_000.0).coerceAtLeast(0.0).toFloat()

            val snap = processor.addSample(sample)
            val goodBpm = if (snap.quality == PpgQuality.GOOD) snap.bpm else null
            val reachedTarget = snap.elapsedSeconds >= TARGET_SECONDS
            val reachedCap = totalElapsed >= MAX_SESSION_SECONDS

            when {
                goodBpm != null && (reachedTarget || reachedCap) -> {
                    resetSession()
                    _state.update {
                        it.copy(
                            phase = PpgPhase.RESULT,
                            quality = PpgQuality.GOOD,
                            finalBpm = goodBpm,
                            liveBpm = goodBpm,
                            confidence = snap.confidence,
                            progress = 1f,
                            elapsedSeconds = snap.elapsedSeconds,
                            waveform = snap.waveform,
                            saved = false,
                        )
                    }
                }
                reachedCap -> {
                    resetSession()
                    _state.update {
                        it.copy(
                            phase = PpgPhase.GUIDE,
                            quality = PpgQuality.LOW_QUALITY,
                            liveBpm = null,
                            errorMessage = UiText.Res(R.string.ppg_signal_timeout),
                        )
                    }
                }
                else -> _state.update {
                    it.copy(
                        quality = snap.quality,
                        liveBpm = goodBpm,
                        confidence = snap.confidence,
                        progress = (snap.elapsedSeconds / TARGET_SECONDS).coerceIn(0f, 1f),
                        elapsedSeconds = snap.elapsedSeconds,
                        waveform = snap.waveform,
                    )
                }
            }
        }
    }

    private fun resetSession() {
        processor.reset()
        sessionStartMs = null
    }

    fun saveResult() {
        val bpm = _state.value.finalBpm ?: return
        if (_state.value.saving || _state.value.saved) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            repository.saveHeartRate(bpm)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        const val TARGET_SECONDS = 20f
        const val MAX_SESSION_SECONDS = 30f
    }
}
