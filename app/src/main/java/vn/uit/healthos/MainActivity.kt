package vn.uit.healthos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import vn.uit.healthos.core.data.session.AppLockManager
import vn.uit.healthos.core.data.session.SessionCleaner
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.SessionRepository
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.theme.HealthOsTheme
import vn.uit.healthos.feature.profile.NutritionTargetRestorer
import vn.uit.healthos.feature.auth.AuthRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences(),
            )
            val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()
            // With app lock on, health data stays out of screenshots, screen recordings and the recents thumbnail.
            LaunchedEffect(prefs.appLockEnabled) {
                if (prefs.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            HealthOsTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        sessionRepository = sessionRepository,
                        preferencesRepository = preferencesRepository,
                        appLockManager = appLockManager,
                        sessionCleaner = sessionCleaner,
                        nutritionTargetRestorer = nutritionTargetRestorer,
                    )
                    if (isLocked) {
                        AppLockOverlay(
                            activity = this@MainActivity,
                            appLockManager = appLockManager,
                            onSignOut = { lifecycleScope.launch { authRepository.logout() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockOverlay(
    activity: FragmentActivity,
    appLockManager: AppLockManager,
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
                onSuccess = { errorMessage = null },
                onError = { errorMessage = it },
            )
        } else {
            errorMessage = noCredentialMessage
        }
    }

    LaunchedEffect(Unit) {
        triggerUnlock()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VitalOrb(score = 0.8f, size = 140.dp)
            Text(
                text = stringResource(R.string.lock_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
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
            )
            if (canAuthenticate) {
                PrimaryButton(
                    text = stringResource(R.string.lock_unlock_action),
                    onClick = triggerUnlock,
                    leadingIcon = Icons.Rounded.Lock,
                )
            }
            // Without a device credential the prompt can never succeed; signing out is the way back in.
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.lock_sign_out_action)) }
        }
    }
}
