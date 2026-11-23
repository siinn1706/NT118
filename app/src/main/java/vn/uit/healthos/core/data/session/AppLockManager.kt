package vn.uit.healthos.core.data.session

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

const val APP_LOCK_TIMEOUT_MS = 60_000L

/** Pure timeout check kept separate from Android lifecycle so unit tests can exercise it directly. */
fun shouldLockOnResume(
    lockEnabled: Boolean,
    isSignedIn: Boolean,
    backgroundedAtMs: Long?,
    nowMs: Long,
    timeoutMs: Long = APP_LOCK_TIMEOUT_MS,
): Boolean {
    if (!lockEnabled || !isSignedIn) return false
    if (backgroundedAtMs == null) return true
    return (nowMs - backgroundedAtMs) >= timeoutMs
}

/**
 * Locks the app behind BiometricPrompt (biometrics or device credential) on cold start when enabled
 * or on return from background after more than 60 seconds.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val tokenStore: TokenStore,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val lock = Any()
    private var lockEnabled: Boolean = false
    private var prefsLoaded: Boolean = false
    private var coldStartChecked: Boolean = false
    private var backgroundedAtMs: Long? = null
    private var registered: Boolean = false

    fun register() {
        if (registered) return
        registered = true
        scope.launch {
            preferencesRepository.appLockEnabled.collectLatest { enabled ->
                synchronized(lock) {
                    lockEnabled = enabled
                    prefsLoaded = true
                    if (!enabled) _isLocked.value = false
                    // The first value may arrive after the first onStart; the cold-start lock must not be skipped.
                    checkColdStart()
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Process start counts as a cold start: lock once, as soon as both the setting and the session are known. */
    private fun checkColdStart() {
        if (coldStartChecked || !prefsLoaded) return
        coldStartChecked = true
        if (lockEnabled && tokenStore.current() != null) _isLocked.value = true
    }

    override fun onStart(owner: LifecycleOwner) {
        synchronized(lock) {
            if (!coldStartChecked) {
                checkColdStart()
            } else if (
                shouldLockOnResume(lockEnabled, tokenStore.current() != null, backgroundedAtMs, SystemClock.elapsedRealtime())
            ) {
                _isLocked.value = true
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        synchronized(lock) { backgroundedAtMs = SystemClock.elapsedRealtime() }
    }

    fun unlock() {
        synchronized(lock) {
            _isLocked.value = false
            backgroundedAtMs = null
        }
    }

    fun clearOnSignOut() = unlock()

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(ALLOWED_AUTHENTICATORS)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // System prompt stays open and lets the user retry.
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
        prompt.authenticate(promptInfo)
    }

    companion object {
        const val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
