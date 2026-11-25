package vn.uit.healthos.feature.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import vn.uit.healthos.navigation.MedsRoute

@Serializable private data object MedPlanFormDestination
@Serializable private data class MedDetailDestination(val planId: String)

fun NavGraphBuilder.medsGraph(navController: NavController) {
    composable<MedsRoute> { entry ->
        val viewModel: MedsViewModel = hiltViewModel(entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MedsEventEffect(viewModel, navController)
        LifecycleResumeEffect(viewModel) {
            viewModel.onVisible()
            onPauseOrDispose { }
        }
        MedsScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onAddPlan = { navController.navigate(MedPlanFormDestination) },
            onOpenPlan = { planId -> navController.navigate(MedDetailDestination(planId)) },
            onMarkDone = viewModel::markDoseDone,
            onSkipDose = viewModel::skipDose,
            onSnoozeDose = viewModel::snoozeDose,
        )
    }

    composable<MedPlanFormDestination> { entry ->
        val viewModel = sharedMedsViewModel(navController, entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MedsEventEffect(viewModel, navController)
        MedPlanFormScreen(
            state = state,
            onUpdateForm = viewModel::updateForm,
            onAddDoseTime = viewModel::addDoseTime,
            onRemoveDoseTime = viewModel::removeDoseTime,
            onSubmit = viewModel::submitNewPlan,
            onBack = { navController.popBackStack() },
        )
    }

    composable<MedDetailDestination> { entry ->
        val route = entry.toRoute<MedDetailDestination>()
        val viewModel = sharedMedsViewModel(navController, entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MedsEventEffect(viewModel, navController)
        LaunchedEffect(route.planId) {
            viewModel.loadPlanDetail(route.planId)
        }
        MedDetailScreen(
            state = state,
            onTogglePause = viewModel::togglePauseSelected,
            onRefill = viewModel::refillSelected,
            onDelete = viewModel::deleteSelected,
            onBack = { navController.popBackStack() },
        )
    }
}

@Composable
private fun sharedMedsViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
): MedsViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(MedsRoute) }
    return hiltViewModel(parent)
}

@Composable
private fun MedsEventEffect(viewModel: MedsViewModel, navController: NavController) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is MedsNavEvent.BackToList) {
                navController.popBackStack(MedsRoute, inclusive = false)
            }
        }
    }
}
