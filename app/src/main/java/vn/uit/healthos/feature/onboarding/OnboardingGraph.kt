package vn.uit.healthos.feature.onboarding

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.OnboardingRoute

fun NavGraphBuilder.onboardingGraph(
    onCompleted: () -> Unit,
) {
    composable<OnboardingRoute> {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is OnboardingEvent.Completed) onCompleted()
            }
        }

        OnboardingScreen(
            state = state,
            onFullNameChange = viewModel::onFullNameChange,
            onDateOfBirthChange = viewModel::onDateOfBirthChange,
            onSexChange = viewModel::onSexChange,
            onHeightChange = viewModel::onHeightChange,
            onWeightChange = viewModel::onWeightChange,
            onBloodTypeChange = viewModel::onBloodTypeChange,
            onActivityChange = viewModel::onActivityChange,
            onGoalDirectionChange = viewModel::onGoalDirectionChange,
            onTargetWeightChange = viewModel::onTargetWeightChange,
            onContactChange = viewModel::onContactChange,
            onAddContact = viewModel::addContact,
            onRemoveContact = viewModel::removeContact,
            onAccentChange = viewModel::onAccentChange,
            onPrevious = viewModel::previousStep,
            onNext = viewModel::nextStep,
            onComplete = viewModel::completeOnboarding,
        )
    }
}
