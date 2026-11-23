package vn.uit.healthos.feature.ppg

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import vn.uit.healthos.core.ui.asString

@Composable
fun PpgScreen(
    state: PpgUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSample: (PpgSample) -> Unit,
    onCameraError: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (granted) onStart()
    }

    if (state.phase == PpgPhase.MEASURING && cameraGranted) {
        PpgCameraBinding(
            fingerDetected = state.quality != PpgQuality.NO_FINGER,
            onSample = onSample,
            onError = onCameraError,
        )
    }

    DetailScaffold(
        title = stringResource(R.string.feature_ppg),
        onBack = {
            onCancel()
            onBack()
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.phase) {
                PpgPhase.GUIDE -> {
                    VitalOrb(score = 0.85f, size = 148.dp) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.ppg_guide_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ppg_guide_step_1), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.ppg_guide_step_2), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.ppg_guide_step_3), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    state.errorMessage?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = err.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    PrimaryButton(
                        text = stringResource(R.string.ppg_start_action),
                        onClick = {
                            if (cameraGranted) {
                                onStart()
                            } else {
                                permLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        leadingIcon = Icons.Rounded.Favorite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PpgPhase.MEASURING -> {
                    val statusText = when (state.quality) {
                        PpgQuality.NO_FINGER -> stringResource(R.string.ppg_status_no_finger)
                        PpgQuality.COLLECTING -> stringResource(R.string.ppg_status_collecting)
                        PpgQuality.LOW_QUALITY -> stringResource(R.string.ppg_status_reposition)
                        PpgQuality.GOOD -> stringResource(R.string.ppg_status_good)
                    }

                    VitalOrb(
                        score = when (state.quality) {
                            PpgQuality.GOOD -> 0.85f
                            PpgQuality.COLLECTING -> 0.6f
                            else -> 0.25f
                        },
                        size = 164.dp,
                        breathsPerMinute = (state.liveBpm?.div(4f) ?: 15f).coerceIn(6f, 28f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state.quality == PpgQuality.GOOD && state.liveBpm != null) {
                                    "${state.liveBpm}"
                                } else {
                                    "—"
                                },
                                style = MaterialTheme.typography.displayMedium.tabular(),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = stringResource(R.string.unit_bpm),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = if (state.quality == PpgQuality.LOW_QUALITY || state.quality == PpgQuality.NO_FINGER) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.ppg_progress_seconds,
                            state.elapsedSeconds.roundToInt(),
                            PpgViewModel.TARGET_SECONDS.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    PpgWaveformCard(waveform = state.waveform)

                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PpgPhase.RESULT -> {
                    val bpm = state.finalBpm ?: 0
                    val confidencePct = (state.confidence * 100f).roundToInt()

                    VitalOrb(score = 0.85f, size = 164.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$bpm",
                                style = MaterialTheme.typography.displayMedium.tabular(),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = stringResource(R.string.unit_bpm),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.ppg_result_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.ppg_confidence_label, confidencePct),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = LocalVitalColors.current.good,
                    )

                    if (state.waveform.isNotEmpty()) {
                        PpgWaveformCard(waveform = state.waveform)
                    }

                    if (state.saved) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.ppg_saved_confirmation),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.ppg_save_action),
                            onClick = onSave,
                            loading = state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SecondaryButton(
                        text = stringResource(R.string.ppg_measure_again),
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ppg_medical_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PpgWaveformCard(waveform: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ppg_waveform_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                if (waveform.size >= 2) {
                    val path = Path()
                    val stepX = size.width / (waveform.size - 1).coerceAtLeast(1)
                    val amp = size.height * 0.42f
                    waveform.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = midY - v * amp
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

/**
 * Binds the back camera with the torch on while composed. Auto-exposure runs while the finger is being placed and is
 * locked shortly after the finger covers the lens, so exposure changes cannot masquerade as pulse.
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun PpgCameraBinding(
    fingerDetected: Boolean,
    onSample: (PpgSample) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSample by rememberUpdatedState(onSample)
    val currentOnError by rememberUpdatedState(onError)
    val cameraErrorText = stringResource(R.string.ppg_camera_error)
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var analysis: ImageAnalysis? = null
        val listener = Runnable {
            if (disposed) return@Runnable
            runCatching {
                val provider = future.get()
                val builder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                Camera2Interop.Extender(builder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                val useCase = builder.build().apply {
                    setAnalyzer(executor, PpgAnalyzer { sample -> currentOnSample(sample) })
                }
                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCase,
                )
                bound.cameraControl.enableTorch(true)
                analysis = useCase
                camera = bound
            }.onFailure {
                currentOnError(cameraErrorText)
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching { camera?.cameraControl?.enableTorch(false) }
            camera = null
            analysis?.let { useCase ->
                useCase.clearAnalyzer()
                if (future.isDone) runCatching { future.get().unbind(useCase) }
            }
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, fingerDetected) {
        val bound = camera ?: return@LaunchedEffect
        if (fingerDetected) delay(AE_SETTLE_MS)
        runCatching {
            Camera2CameraControl.from(bound.cameraControl).setCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, fingerDetected)
                    .build(),
            )
        }
    }
}

private const val AE_SETTLE_MS = 1_000L
