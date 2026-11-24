package vn.uit.healthos.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.HomeRoute

fun NavGraphBuilder.homeGraph(navigation: HomeNavigation) {
    composable<HomeRoute> {
        val viewModel: HomeViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val actions = remember(viewModel) {
            HomeActions(
                onRefresh = viewModel::refresh,
                onAddHydrationCup = viewModel::onAddHydrationCup,
                onSelectTrend = viewModel::onSelectTrend,
                onLocationPermissionGranted = viewModel::onLocationPermissionGranted,
                onTriggerStepSync = viewModel::triggerStepSync,
                onMarkDoseTaken = viewModel::onMarkDoseTaken,
                onEditLayout = viewModel::setEditingLayout,
                onToggleTile = viewModel::toggleTile,
                onMoveTile = viewModel::moveTile,
                onResetLayout = viewModel::resetLayout,
            )
        }
        HomeScreen(state = state, actions = actions, navigation = navigation)
    }
}
