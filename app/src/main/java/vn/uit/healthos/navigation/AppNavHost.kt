package vn.uit.healthos.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import vn.uit.healthos.core.designsystem.glass.GlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import vn.uit.healthos.core.designsystem.component.GlassSnackbarHost
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.core.designsystem.glass.ScrollEdge
import vn.uit.healthos.core.designsystem.glass.ScrollEdgeEffect
import vn.uit.healthos.feature.auth.authGraph
import vn.uit.healthos.feature.chat.chatGraph
import vn.uit.healthos.feature.dev.devGraph
import vn.uit.healthos.feature.friends.friendsGraph
import vn.uit.healthos.feature.leaderboard.leaderboardGraph
import vn.uit.healthos.feature.home.HomeNavigation
import vn.uit.healthos.feature.home.homeGraph
import vn.uit.healthos.feature.meals.SnapDestination
import vn.uit.healthos.feature.meals.mealsGraph
import vn.uit.healthos.feature.meds.medsGraph
import vn.uit.healthos.feature.onboarding.onboardingGraph
import vn.uit.healthos.feature.ppg.ppgGraph
import vn.uit.healthos.feature.profile.EmergencyContactsRoute
import vn.uit.healthos.feature.profile.NutritionTargetRestorer
import vn.uit.healthos.feature.profile.profileGraph
import vn.uit.healthos.feature.safety.safetyGraph

/** How far above the tab bar the bottom scroll edge effect fades out. */
private val BottomEdgeFade = 24.dp

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
    pendingRoute: Any? = null,
    onPendingRouteHandled: () -> Unit = {},
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

    // Opens a tab the way the bottom bar does; a chat with a prompt is a fresh entry so the prompt is applied.
    val navigateTab: (Any) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = target !is ChatRoute || target.prompt == null
        }
    }
    val homeNavigation = remember(navController) {
        HomeNavigation(
            onOpenMe = { navigateTab(MeRoute) },
            onOpenSafety = { navController.navigate(SafetyRoute) },
            onOpenLeaderboard = { navController.navigate(LeaderboardRoute) },
            onOpenMeals = { navigateTab(MealsRoute) },
            onOpenSnap = {
                navigateTab(MealsRoute)
                navController.navigate(SnapDestination)
            },
            onOpenPpg = { navController.navigate(PpgRoute) },
            onOpenMeds = { navigateTab(MedsRoute) },
            onOpenChat = { prompt -> navigateTab(ChatRoute(prompt = prompt)) },
        )
    }

    // A home-screen widget asked for a screen: open it once the session is ready (the app lock overlay still covers it).
    LaunchedEffect(pendingRoute, bootstrap) {
        val target = pendingRoute ?: return@LaunchedEffect
        if (bootstrap != SessionBootstrapState.READY) return@LaunchedEffect
        val topLevel = if (target is SnapDestination) MealsRoute else target
        navController.navigate(topLevel) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = target !is ChatRoute
        }
        if (target is SnapDestination) navController.navigate(SnapDestination)
        onPendingRouteHandled()
    }

    val glass = LocalGlassBackdrops.current
    val screensLayer = rememberLayerBackdrop()
    val chromeBackdrop: Backdrop? = glass?.let { rememberCombinedBackdrop(it.background, screensLayer) }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomChromeInset = if (showBottomBar) TabBarFootprint + navBarBottom + 8.dp else 0.dp

    val chromeBackdrops = remember(glass, chromeBackdrop) {
        glass?.let { GlassBackdrops(it.background, chromeBackdrop ?: it.chrome) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = {
            // Snackbars float above the screens, so they refract them like the tab bar does.
            CompositionLocalProvider(LocalGlassBackdrops provides chromeBackdrops) {
                GlassSnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = bottomChromeInset))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Screens are recorded as a layer so the floating tab bar can refract whatever scrolls beneath it.
            Box(Modifier.fillMaxSize().then(if (glass != null) Modifier.layerBackdrop(screensLayer) else Modifier)) {
                CompositionLocalProvider(LocalBottomChromeInset provides bottomChromeInset) {
                    NavHost(
                        navController = navController,
                        startDestination = initialDestination,
                        modifier = Modifier
                            .padding(
                                top = padding.calculateTopPadding(),
                                // Tab screens scroll under the floating bar and pad themselves with LocalBottomChromeInset.
                                bottom = if (showBottomBar) 0.dp else padding.calculateBottomPadding(),
                            )
                            .consumeWindowInsets(padding),
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
                        homeGraph(navigation = homeNavigation)
                        mealsGraph(navController = navController)
                        chatGraph(onOpenSafety = { navController.navigate(SafetyRoute) })
                        medsGraph(navController = navController)
                        profileGraph(
                            navController = navController,
                            appLockManager = appLockManager,
                            onOpenFriends = { navController.navigate(FriendsRoute) },
                            onOpenLeaderboard = { navController.navigate(LeaderboardRoute) },
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
                        friendsGraph(navController = navController)
                        leaderboardGraph(onBack = back, onOpenSettings = { navController.navigate(SettingsRoute) })
                    }
                }
            }
            if (showBottomBar) {
                // Scroll edge effect: content scrolling under the tab bar is blurred and faded out behind it. It sits
                // between the screens and the bar, so the bar still refracts the raw content.
                ScrollEdgeEffect(
                    edge = ScrollEdge.Bottom,
                    backdrop = chromeBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomChromeInset + BottomEdgeFade),
                )
                CompositionLocalProvider(LocalGlassBackdrops provides chromeBackdrops) {
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
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
