package vn.uit.healthos.feature.ppg

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.PpgRoute

fun NavGraphBuilder.ppgGraph(onBack: () -> Unit) {
    composable<PpgRoute> {
        val viewModel: PpgViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        PpgScreen(
            state = state,
            onStart = viewModel::startMeasurement,
            onCancel = viewModel::cancelMeasurement,
            onSample = viewModel::onFrameSample,
            onCameraError = viewModel::onCameraError,
            onSave = viewModel::saveResult,
            onBack = onBack,
        )
    }
}
