package vn.uit.healthos

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import vn.uit.healthos.feature.meals.SnapDestination
import vn.uit.healthos.feature.widget.WidgetIntents
import vn.uit.healthos.navigation.ChatRoute
import vn.uit.healthos.navigation.HomeRoute
import vn.uit.healthos.navigation.MedsRoute
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.session.AppLockManager
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.SessionCleaner
import vn.uit.healthos.core.data.session.SessionRepository
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.glass.AmbientBackground
import vn.uit.healthos.core.designsystem.glass.GlassRoot
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.HealthOsTheme
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.feature.auth.AuthRepository
import vn.uit.healthos.feature.profile.NutritionTargetRestorer
import vn.uit.healthos.navigation.AppNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var sessionCleaner: SessionCleaner

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var nutritionTargetRestorer: NutritionTargetRestorer

    /** The splash screen stays up until the preferences and the lock state are known. */
    @Volatile
    private var contentReady = false

    /** Screen a home-screen widget asked to open; handed to [AppNavHost], which navigates once the app is ready. */
    private val pendingWidgetRoute = MutableStateFlow<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) captureWidgetRoute(intent)
        enableEdgeToEdge()
        // Until the app lock setting is known, assume it is on: health data must never reach a screenshot, a screen
        // recording or the recents thumbnail during the first frames.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        splash.setKeepOnScreenCondition { !contentReady }
        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle<AppPreferences?>(initialValue = null)
            val signedInFlow = remember(sessionRepository) { sessionRepository.isSignedIn.map<Boolean, Boolean?> { it } }
            val signedIn by signedInFlow.collectAsStateWithLifecycle(initialValue = null)
            val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()
            val pendingRoute by pendingWidgetRoute.collectAsStateWithLifecycle()
            val loaded = prefs
            val gate = resolveLockGate(
                prefsLoaded = loaded != null,
                lockEnabled = loaded?.appLockEnabled == true,
                signedIn = signedIn,
                isLocked = isLocked,
                coldStartSettled = ColdStartLock.settled,
            )
            SideEffect {
                if (gate != LockGate.Unknown) contentReady = true
                if (isLocked || gate == LockGate.Open) ColdStartLock.settled = true
            }
            LaunchedEffect(loaded?.appLockEnabled) {
                val enabled = loaded?.appLockEnabled ?: return@LaunchedEffect
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            HealthOsTheme(
                themeMode = loaded?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = loaded?.dynamicColor ?: false,
                accentPreset = loaded?.accentPreset ?: AccentPreset.Default,
                backgroundStyle = loaded?.backgroundStyle ?: BackgroundStyle.Default,
                reducedTransparency = loaded?.reducedTransparency ?: false,
            ) {
                // The lock screen is drawn above GlassDialog overlays, and those overlays are not shown at all while the
                // app is not open, so a dialog left open on a screen can neither show health data over the lock screen
                // nor be used before unlocking.
                GlassRoot(
                    overlaysSuppressed = gate != LockGate.Open,
                    foreground = {
                        if (gate == LockGate.Locked) {
                            AppLockOverlay(
                                activity = this@MainActivity,
                                appLockManager = appLockManager,
                                onUnlocked = { ColdStartLock.settled = true },
                                onSignOut = { lifecycleScope.launch { authRepository.logout() } },
                            )
                        }
                    },
                ) {
                    // Composed for the first time only once the app is open, then kept (with its back stack) while a
                    // later lock covers it, hidden from accessibility services and keyboard focus.
                    var navHostComposed by remember { mutableStateOf(false) }
                    if (gate == LockGate.Open) {
                        SideEffect { navHostComposed = true }
                    }
                    if (gate == LockGate.Open || (navHostComposed && gate == LockGate.Locked)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (gate == LockGate.Open) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .clearAndSetSemantics {}
                                            .focusProperties { onEnter = { cancelFocusChange() } }
                                            .focusGroup()
                                    },
                                ),
                        ) {
                            AppNavHost(
                                sessionRepository = sessionRepository,
                                preferencesRepository = preferencesRepository,
                                appLockManager = appLockManager,
                                sessionCleaner = sessionCleaner,
                                nutritionTargetRestorer = nutritionTargetRestorer,
                                pendingRoute = pendingRoute,
                                onPendingRouteHandled = { pendingWidgetRoute.value = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureWidgetRoute(intent)
    }

    /** Turns a widget intent into a route and clears its extras so a rotation or replay does not open it again. */
    private fun captureWidgetRoute(intent: Intent?) {
        val route: Any = when (WidgetIntents.destinationFrom(intent) ?: return) {
            WidgetIntents.Destination.CHAT -> WidgetIntents.chatRouteFrom(intent) ?: ChatRoute()
            WidgetIntents.Destination.MEDS -> MedsRoute
            WidgetIntents.Destination.HOME -> HomeRoute
            WidgetIntents.Destination.MEAL_SNAP -> SnapDestination
        }
        WidgetIntents.consume(intent)
        pendingWidgetRoute.value = route
    }
}

/** Whether the app content may be shown. */
internal enum class LockGate {
    /** Preferences or session not loaded yet: show nothing but the background (the splash screen stays up). */
    Unknown,

    /** The lock screen covers the app. */
    Locked,

    /** The app is usable. */
    Open,
}

/**
 * Decides the [LockGate]. AppLockManager applies its cold-start lock asynchronously, after its own read of the same
 * preferences, so in a fresh process with app lock on and a signed-in session the app counts as locked until that
 * lock has been observed (or the user unlocked). [coldStartSettled] records that, once per process.
 */
internal fun resolveLockGate(
    prefsLoaded: Boolean,
    lockEnabled: Boolean,
    signedIn: Boolean?,
    isLocked: Boolean,
    coldStartSettled: Boolean,
): LockGate = when {
    !prefsLoaded || signedIn == null -> LockGate.Unknown
    isLocked -> LockGate.Locked
    lockEnabled && signedIn && !coldStartSettled -> LockGate.Locked
    else -> LockGate.Open
}

/**
 * Process-wide: true once the cold-start lock decision has been applied, so later activities trust isLocked. Snapshot
 * state, so setting it (after an unlock) recomposes the gate.
 */
private object ColdStartLock {
    var settled: Boolean by mutableStateOf(false)
}

/**
 * Lock screen: an opaque ambient background (nothing of the app shows through), a frosted glass card with the orb and
 * the explanation, the unlock action and a plain sign-out. It swallows every touch and the back gesture (which sends
 * the app to the background instead of navigating the hidden screens).
 */
@Composable
private fun AppLockOverlay(
    activity: FragmentActivity,
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val noCredentialMessage = stringResource(R.string.lock_no_device_credential)
    // Re-checked on every composition so removing the screen lock in Settings is picked up on return.
    val canAuthenticate = appLockManager.canAuthenticate(activity)

    val triggerUnlock = {
        errorMessage = null
        if (appLockManager.canAuthenticate(activity)) {
            appLockManager.authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = {
                    errorMessage = null
                    onUnlocked()
                },
                onError = { errorMessage = it },
            )
        } else {
            errorMessage = noCredentialMessage
        }
    }

    LaunchedEffect(Unit) {
        triggerUnlock()
    }
    BackHandler { activity.moveTaskToBack(true) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BentoCard(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VitalOrb(score = 0.8f, size = 132.dp)
                    Text(
                        text = stringResource(R.string.lock_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = errorMessage ?: stringResource(R.string.lock_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (canAuthenticate) {
                PrimaryButton(
                    text = stringResource(R.string.lock_unlock_action),
                    onClick = triggerUnlock,
                    leadingIcon = Icons.Rounded.Lock,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                )
            }
            // Without a device credential the prompt can never succeed; signing out is the way back in.
            PlainButton(text = stringResource(R.string.lock_sign_out_action), onClick = onSignOut)
        }
    }
}
