package vn.uit.healthos.feature.safety

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.feature.profile.OnEmergencyContactsSaved
import vn.uit.healthos.navigation.SafetyRoute

fun NavGraphBuilder.safetyGraph(onBack: () -> Unit, onEditContacts: () -> Unit) {
    composable<SafetyRoute> { entry ->
        val viewModel: SafetyViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        entry.OnEmergencyContactsSaved(viewModel::refresh)

        SafetyScreen(
            state = state,
            onToggleFallDetection = { enabled -> viewModel.toggleFallDetection(context, enabled) },
            onToggleTestMode = viewModel::toggleTestMode,
            onMintQrToken = viewModel::ensureEmergencyQrToken,
            onEditContacts = onEditContacts,
            onBack = onBack,
        )
    }
}
