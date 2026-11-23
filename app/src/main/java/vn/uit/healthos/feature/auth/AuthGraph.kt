package vn.uit.healthos.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import vn.uit.healthos.navigation.AuthRoute

@Serializable private data object SignInDestination
@Serializable private data object SignUpDestination
@Serializable private data object OtpDestination
@Serializable private data object MfaDestination
@Serializable private data object ForgotPasswordDestination

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthenticatedHome: () -> Unit,
    onAuthenticatedOnboarding: () -> Unit,
) {
    navigation<AuthRoute>(startDestination = SignInDestination) {
        composable<SignInDestination> { entry ->
            val viewModel = sharedAuthViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            AuthEventEffect(viewModel, navController, onAuthenticatedHome, onAuthenticatedOnboarding)
            SignInScreen(
                state = state,
                onIdentifierChange = viewModel::onIdentifierChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSignIn = viewModel::signIn,
                onNavigateSignUp = {
                    viewModel.clearErrors()
                    navController.navigate(SignUpDestination)
                },
                onNavigateForgotPassword = {
                    viewModel.clearErrors()
                    navController.navigate(ForgotPasswordDestination)
                },
            )
        }

        composable<SignUpDestination> { entry ->
            val viewModel = sharedAuthViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            AuthEventEffect(viewModel, navController, onAuthenticatedHome, onAuthenticatedOnboarding)
            SignUpScreen(
                state = state,
                onDisplayNameChange = viewModel::onDisplayNameChange,
                onUsernameChange = viewModel::onUsernameChange,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onSignUpPasswordChange,
                onConfirmPasswordChange = viewModel::onSignUpConfirmPasswordChange,
                onSubmit = viewModel::requestSignUpOtp,
                onBack = { navController.popBackStack() },
            )
        }

        composable<OtpDestination> { entry ->
            val viewModel = sharedAuthViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            AuthEventEffect(viewModel, navController, onAuthenticatedHome, onAuthenticatedOnboarding)
            OtpScreen(
                state = state,
                onCodeChange = viewModel::onOtpCodeChange,
                onVerify = viewModel::verifyOtp,
                onResend = viewModel::resendOtp,
                onBack = { navController.popBackStack() },
            )
        }

        composable<MfaDestination> { entry ->
            val viewModel = sharedAuthViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            AuthEventEffect(viewModel, navController, onAuthenticatedHome, onAuthenticatedOnboarding)
            MfaScreen(
                state = state,
                onCodeChange = viewModel::onMfaCodeChange,
                onVerify = viewModel::verifyMfa,
                onBack = { navController.popBackStack() },
            )
        }

        composable<ForgotPasswordDestination> { entry ->
            val viewModel = sharedAuthViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            AuthEventEffect(viewModel, navController, onAuthenticatedHome, onAuthenticatedOnboarding)
            ForgotPasswordScreen(
                state = state,
                onEmailChange = viewModel::onResetEmailChange,
                onRequestOtp = viewModel::requestResetOtp,
                onNewPasswordChange = viewModel::onResetNewPasswordChange,
                onConfirmPasswordChange = viewModel::onResetConfirmPasswordChange,
                onSubmitReset = viewModel::submitResetPassword,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun sharedAuthViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
): AuthViewModel {
    val parentEntry = remember(entry) { navController.getBackStackEntry(AuthRoute) }
    return hiltViewModel(parentEntry)
}

@Composable
private fun AuthEventEffect(
    viewModel: AuthViewModel,
    navController: NavController,
    onAuthenticatedHome: () -> Unit,
    onAuthenticatedOnboarding: () -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuthNavEvent.ToHome -> onAuthenticatedHome()
                AuthNavEvent.ToOnboarding -> onAuthenticatedOnboarding()
                AuthNavEvent.ToMfa -> navController.navigate(MfaDestination)
                AuthNavEvent.ToOtp -> navController.navigate(OtpDestination)
                AuthNavEvent.ToResetNewPassword -> navController.popBackStack()
            }
        }
    }
}
