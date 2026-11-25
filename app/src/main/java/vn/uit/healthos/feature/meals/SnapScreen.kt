package vn.uit.healthos.feature.meals

import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.ui.asString
import java.io.File

@Composable
fun SnapScreen(
    state: MealsUiState,
    onPhotoReady: (Uri) -> Unit,
    onCancelAnalysis: () -> Unit,
    onFallbackManual: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var captureFailed by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPhotoReady(uri)
    }

    val cancelAndLeave = {
        onCancelAnalysis()
        onBack()
    }
    BackHandler(onBack = cancelAndLeave)

    DetailScaffold(
        title = stringResource(R.string.meals_snap_title),
        onBack = cancelAndLeave,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.analyzing) {
                // The captured photo is recorded as a layer so the glass panel frosts and refracts it directly,
                // instead of a blurred copy under a scrim and an opaque card.
                val photoLayer = rememberLayerBackdrop()
                val photoUri = state.capturedPhotoUri
                photoUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(GlassTokens.CardShape)
                            .layerBackdrop(photoLayer),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .then(
                                if (photoUri != null) {
                                    Modifier.liquidGlass(
                                        GlassTokens.CardShape,
                                        GlassStyle.Material,
                                        backdrop = photoLayer,
                                    )
                                } else {
                                    Modifier.liquidGlass(GlassTokens.CardShape, GlassStyle.Material)
                                },
                            ),
                    ) {
                        CompositionLocalProvider(LocalInsideGlass provides true) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            GlassSpinner(
                                size = 44.dp,
                                contentDescription = stringResource(R.string.meals_analyzing_title),
                            )
                            Text(
                                stringResource(R.string.meals_analyzing_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.meals_analyzing_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = onCancelAnalysis,
                            )
                        }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (cameraGranted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            CameraPreviewSurface(
                                imageCapture = imageCapture,
                                lifecycleOwner = lifecycleOwner,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(GlassTokens.CardShape),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BentoCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(24.dp),
                            ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.meals_camera_permission_needed),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                PrimaryButton(
                                    text = stringResource(R.string.meals_grant_camera_action),
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                )
                            }
                            }
                        }
                    }

                    val errorText = state.analysisError?.asString()
                        ?: if (captureFailed) stringResource(R.string.meals_error_capture_failed) else null
                    if (errorText != null) {
                        GlassBanner(
                            message = errorText,
                            tone = GlassBannerTone.Error,
                            actionLabel = stringResource(R.string.meals_manual_action),
                            onAction = onFallbackManual,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Side by side only while both labels fit on one line; otherwise stacked at full width.
                    AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 2, horizontalSpacing = 12.dp) {
                        SecondaryButton(
                            text = stringResource(R.string.meals_pick_gallery),
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            leadingIcon = Icons.Rounded.PhotoLibrary,
                        )
                        if (cameraGranted) {
                            PrimaryButton(
                                text = stringResource(R.string.meals_capture_action),
                                onClick = {
                                    captureFailed = false
                                    val file = File(context.cacheDir, "meal_${System.currentTimeMillis()}.jpg")
                                    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                                    imageCapture.takePicture(
                                        opts,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                onPhotoReady(Uri.fromFile(file))
                                            }

                                            override fun onError(exc: ImageCaptureException) {
                                                Log.w("SnapScreen", "Photo capture failed", exc)
                                                captureFailed = true
                                            }
                                        },
                                    )
                                },
                                leadingIcon = Icons.Rounded.CameraAlt,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewSurface(
    imageCapture: ImageCapture,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { future.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
