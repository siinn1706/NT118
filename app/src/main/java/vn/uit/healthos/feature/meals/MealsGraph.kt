package vn.uit.healthos.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.uit.healthos.navigation.MealsRoute

@Serializable private data object SnapDestination
@Serializable private data object AnalysisResultDestination
@Serializable private data object ManualEntryDestination

fun NavGraphBuilder.mealsGraph(navController: NavController) {
    composable<MealsRoute> { entry ->
        val viewModel: MealsViewModel = hiltViewModel(entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MealsEventEffect(viewModel, navController)
        MealsScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onOpenSnap = { navController.navigate(SnapDestination) },
            onOpenManual = { navController.navigate(ManualEntryDestination) },
            onDeleteMeal = viewModel::deleteMeal,
        )
    }

    composable<SnapDestination> { entry ->
        val viewModel = sharedMealsViewModel(navController, entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        MealsEventEffect(viewModel, navController)
        SnapScreen(
            state = state,
            onPhotoReady = { uri -> viewModel.analyzePhoto(context, uri) },
            onCancelAnalysis = viewModel::cancelAnalysis,
            onFallbackManual = {
                navController.popBackStack()
                navController.navigate(ManualEntryDestination)
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable<AnalysisResultDestination> { entry ->
        val viewModel = sharedMealsViewModel(navController, entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MealsEventEffect(viewModel, navController)
        AnalysisResultScreen(
            state = state,
            onDishNameChange = viewModel::updateDraftName,
            onCaloriesChange = viewModel::updateDraftCalories,
            onSelectPortion = viewModel::selectPortionOption,
            onRemoveItem = viewModel::removeDraftItem,
            onIngredientQueryChange = viewModel::onIngredientQueryChange,
            onAddIngredient = viewModel::addIngredientToDraft,
            onSave = viewModel::saveAnalysisDraft,
            onDiscard = {
                viewModel.discardAnalysisDraft()
                navController.popBackStack(MealsRoute, inclusive = false)
            },
        )
    }

    composable<ManualEntryDestination> { entry ->
        val viewModel = sharedMealsViewModel(navController, entry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        MealsEventEffect(viewModel, navController)
        ManualEntryScreen(
            state = state,
            onNameChange = viewModel::onManualNameChange,
            onMealTypeChange = viewModel::onManualMealTypeChange,
            onCaloriesChange = viewModel::onManualCaloriesChange,
            onNotesChange = viewModel::onManualNotesChange,
            onIngredientQueryChange = viewModel::onIngredientQueryChange,
            onAddIngredient = viewModel::addIngredientToManual,
            onRemoveItem = viewModel::removeManualItem,
            onSave = viewModel::saveManualMeal,
            onBack = { navController.popBackStack() },
        )
    }
}

@Composable
private fun sharedMealsViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
): MealsViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(MealsRoute) }
    return hiltViewModel(parent)
}

@Composable
private fun MealsEventEffect(
    viewModel: MealsViewModel,
    navController: NavController,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MealsNavEvent.ToAnalysisResult -> navController.navigate(AnalysisResultDestination)
                MealsNavEvent.BackToList -> navController.popBackStack(MealsRoute, inclusive = false)
            }
        }
    }
}
