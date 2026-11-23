package vn.uit.healthos.feature.home

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.HomeRoute

fun NavGraphBuilder.homeGraph() {
    composable<HomeRoute> {
        val viewModel: HomeViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onAddHydrationCup = viewModel::onAddHydrationCup,
            onSelectTrend = viewModel::onSelectTrend,
            onLocationPermissionGranted = viewModel::onLocationPermissionGranted,
            onTriggerStepSync = viewModel::triggerStepSync,
        )
    }
}
