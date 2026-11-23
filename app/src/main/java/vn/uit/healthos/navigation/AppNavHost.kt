package vn.uit.healthos.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.combine
import vn.uit.healthos.R
import vn.uit.healthos.core.data.session.AppLockManager
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.SessionCleaner
import vn.uit.healthos.core.data.session.SessionRepository
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.feature.auth.authGraph
import vn.uit.healthos.feature.chat.chatGraph
import vn.uit.healthos.feature.dev.devGraph
import vn.uit.healthos.feature.home.homeGraph
import vn.uit.healthos.feature.meals.mealsGraph
import vn.uit.healthos.feature.meds.medsGraph
import vn.uit.healthos.feature.onboarding.onboardingGraph
import vn.uit.healthos.feature.ppg.ppgGraph
import vn.uit.healthos.feature.profile.EmergencyContactsRoute
import vn.uit.healthos.feature.profile.NutritionTargetRestorer
import vn.uit.healthos.feature.profile.profileGraph
import vn.uit.healthos.feature.safety.safetyGraph

private enum class SessionBootstrapState { LOADING, SIGNED_OUT, NEEDS_ONBOARDING, READY }

/**
 * Root navigation. Each feature contributes one `xxxGraph()` call here; features own their screens.
 */
@Composable
fun AppNavHost(
    sessionRepository: SessionRepository,
    preferencesRepository: AppPreferencesRepository,
    appLockManager: AppLockManager,
    sessionCleaner: SessionCleaner,
    nutritionTargetRestorer: NutritionTargetRestorer,
    modifier: Modifier = Modifier,
) {
    val bootstrapFlow = remember(sessionRepository, preferencesRepository) {
        combine(sessionRepository.isSignedIn, preferencesRepository.onboardingStatus) { signedIn, status ->
            when {
                !signedIn -> SessionBootstrapState.SIGNED_OUT
                status.equals("completed", ignoreCase = true) -> SessionBootstrapState.READY
                status == null -> SessionBootstrapState.READY
                else -> SessionBootstrapState.NEEDS_ONBOARDING
            }
        }
    }
    val bootstrap by bootstrapFlow.collectAsStateWithLifecycle(initialValue = SessionBootstrapState.LOADING)

    if (bootstrap == SessionBootstrapState.LOADING) {
        LoadingState(modifier = modifier)
        return
    }

    val initialDestination: Any = remember {
        when (bootstrap) {
            SessionBootstrapState.SIGNED_OUT -> AuthRoute
            SessionBootstrapState.NEEDS_ONBOARDING -> OnboardingRoute
            else -> HomeRoute
        }
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionExpiredMessage = stringResource(R.string.error_session_expired)

    LaunchedEffect(sessionRepository) {
        sessionRepository.expired.collect {
            sessionCleaner.onSessionExpired()
            navController.navigate(AuthRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            snackbarHostState.showSnackbar(sessionExpiredMessage)
        }
    }

    // Any sign-out (Settings, the lock screen, a failed refresh) returns to sign-in from wherever the user is.
    LaunchedEffect(sessionRepository) {
        sessionRepository.isSignedIn.collect { signedIn ->
            val current = navController.currentDestination ?: return@collect
            if (!signedIn && current.hierarchy.none { it.hasRoute(AuthRoute::class) }) {
                navController.navigate(AuthRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(bootstrap) {
        if (bootstrap == SessionBootstrapState.READY) nutritionTargetRestorer.restoreIfMissing()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    val navigateRoot: (Any) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    currentDestination = currentDestination,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            // Home is the root of every signed-in stack; the graph's start may be the auth screen.
                            popUpTo(HomeRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = initialDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val back: () -> Unit = { navController.popBackStack() }
            authGraph(
                navController = navController,
                onAuthenticatedHome = { navigateRoot(HomeRoute) },
                onAuthenticatedOnboarding = { navigateRoot(OnboardingRoute) },
            )
            onboardingGraph(
                onCompleted = { navigateRoot(HomeRoute) },
            )
            homeGraph()
            mealsGraph(navController = navController)
            chatGraph(onOpenSafety = { navController.navigate(SafetyRoute) })
            medsGraph(navController = navController)
            profileGraph(
                navController = navController,
                appLockManager = appLockManager,
                onOpenSafety = { navController.navigate(SafetyRoute) },
                onOpenPpg = { navController.navigate(PpgRoute) },
                onOpenDesignGallery = { navController.navigate(DesignGalleryRoute) },
                onSignedOut = { navigateRoot(AuthRoute) },
            )
            safetyGraph(
                onBack = back,
                onEditContacts = { navController.navigate(EmergencyContactsRoute) },
            )
            ppgGraph(onBack = back)
            devGraph(onBack = back)
        }
    }
}
