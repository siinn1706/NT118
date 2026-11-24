package vn.uit.healthos.feature.leaderboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.LeaderboardRoute

/** Step leaderboard ([LeaderboardRoute], a pushed screen). [onOpenSettings] leads to the visibility setting. */
fun NavGraphBuilder.leaderboardGraph(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    composable<LeaderboardRoute> {
        val viewModel: LeaderboardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // The first resume is the initial load; later ones are returns from Settings, where visibility may change.
        var resumedBefore by rememberSaveable { mutableStateOf(false) }
        LifecycleResumeEffect(viewModel) {
            if (resumedBefore) viewModel.refreshVisibility() else resumedBefore = true
            onPauseOrDispose { }
        }
        LeaderboardScreen(
            state = state,
            onBack = onBack,
            onSelectPeriod = viewModel::selectPeriod,
            onSelectScope = viewModel::selectScope,
            onRefresh = viewModel::refresh,
            onDismissError = viewModel::dismissError,
            onOpenSettings = onOpenSettings,
        )
    }
}
