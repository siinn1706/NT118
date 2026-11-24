package vn.uit.healthos.core.data.session

import android.util.Log
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import vn.uit.healthos.core.data.db.UserDataDao
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.feature.steps.StepServicePolicy
import vn.uit.healthos.feature.steps.StepSession
import vn.uit.healthos.feature.widget.WidgetKind
import vn.uit.healthos.feature.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A feature's hook for wiping state it keeps outside Room (alarms, DataStore, cached ids).
 * Register with `@Binds @IntoSet`.
 */
fun interface UserDataCleanup {
    suspend fun clear()
}

/**
 * Decides what local state survives a session change:
 * - explicit sign-out wipes the account's local data but keeps its nutrition targets for the next sign-in;
 * - session expiry keeps everything, since the same user usually signs straight back in;
 * - a different account signing in wipes everything left by the previous one, targets included, and restarts the
 *   phone's step count at zero so the previous account's steps are never synced as the new one's.
 *
 * Widgets are refreshed after each change so they never keep showing the previous session's numbers.
 */
@Singleton
class SessionCleaner @Inject constructor(
    private val preferences: AppPreferencesRepository,
    private val appLockManager: AppLockManager,
    private val userDataDao: UserDataDao,
    private val outboxRepository: OutboxRepository,
    private val stepSession: StepSession,
    private val widgetUpdater: WidgetUpdater,
    private val cleanups: Set<@JvmSuppressWildcards UserDataCleanup>,
) {
    suspend fun onSignedIn(userId: String) {
        val owner = preferences.localDataOwner()
        if (owner != null && owner != userId) {
            clearUserData()
            preferences.clearNutritionTargets()
        }
        if (StepServicePolicy.shouldResetStepsOnSignIn(previousOwner = owner, userId = userId)) {
            stepSession.onAccountChanged()
        }
        preferences.setLocalDataOwner(userId)
        // The session tokens are saved right after this returns; WidgetAppStateWatcher refreshes the widgets again
        // once they are, so the signed-in state shows up.
        widgetUpdater.requestUpdateAll()
    }

    suspend fun onSignedOut() {
        stepSession.onSignedOut()
        clearUserData()
        onSessionExpired()
    }

    suspend fun onSessionExpired() {
        preferences.clearSessionScoped()
        appLockManager.clearOnSignOut()
        widgetUpdater.update(WidgetKind.entries)
    }

    private suspend fun clearUserData() {
        outboxRepository.cancelSync()
        userDataDao.clearAll()
        cleanups.forEach { cleanup ->
            try {
                cleanup.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "User data cleanup failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "SessionCleaner"
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface UserDataCleanupModule {
    @Multibinds
    fun userDataCleanups(): Set<UserDataCleanup>
}
