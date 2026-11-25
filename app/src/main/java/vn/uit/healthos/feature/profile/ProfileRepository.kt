package vn.uit.healthos.feature.profile

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import vn.uit.healthos.core.data.db.Snapshot
import vn.uit.healthos.core.data.db.SnapshotCacheDao
import vn.uit.healthos.core.data.db.SnapshotStore
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.UserDataCleanup
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.core.network.map
import vn.uit.healthos.feature.safety.SafetyPreferences
import vn.uit.healthos.feature.widget.WidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in user's profile and health goal as the server last returned them. */
@Serializable
data class ProfileCache(
    val profile: UserProfileDto,
    val healthGoal: HealthGoalDto? = null,
)

@Singleton
class ProfileRepository @Inject constructor(
    private val api: ProfileApi,
    private val json: Json,
    private val preferencesRepository: AppPreferencesRepository,
    private val safetyPreferences: SafetyPreferences,
    private val syncStore: PreferenceSyncStore,
    private val outboxRepository: OutboxRepository,
    private val widgetUpdater: WidgetUpdater,
    snapshotCacheDao: SnapshotCacheDao,
) {
    private val profileStore = SnapshotStore(snapshotCacheDao, json, PROFILE_CACHE_KEY, ProfileCache.serializer())

    /** The last known profile, at once from memory or disk; every profile the server returns updates it. */
    val cachedProfile: Flow<Snapshot<ProfileCache>?> get() = profileStore.snapshots

    suspend fun getProfile(): ApiResult<UserProfileDto> {
        val result = apiCall(json) { api.getMe().data }
        if (result is ApiResult.Success) cacheProfile(result.data)
        return result
    }

    suspend fun updateProfile(body: UserProfileUpdateBody): ApiResult<UserProfileDto> {
        val result = apiCall(json) { api.patchMe(body).data }
        if (result is ApiResult.Success) cacheProfile(result.data)
        return result
    }

    /** Refreshes what the Me tab shows: the profile, the health goal and the synced preferences. */
    suspend fun refreshProfile(): ApiResult<Unit> {
        val profile = getProfile()
        getHealthGoal()
        syncPreferencesFromServer()
        return profile.map { }
    }

    /** Forgets the cached profile; the next visit loads the signed-in account's own. */
    suspend fun clearCache() = profileStore.clear()

    /** Keeps the offline SOS contact list and the Me tab's cache in step with every profile the server returns. */
    private suspend fun cacheProfile(profile: UserProfileDto) {
        preferencesRepository.setOnboardingStatus(profile.onboardingStatus)
        safetyPreferences.saveProfileCache(profile.fullName ?: profile.displayName, profile.emergencyContacts.orEmpty())
        profileStore.put(ProfileCache(profile, profileStore.current()?.value?.healthGoal))
    }

    /** Stores a health goal the server returned next to the cached profile, keeping the profile's age. */
    private suspend fun cacheHealthGoal(goal: HealthGoalDto?) {
        val current = profileStore.current() ?: return
        profileStore.put(current.value.copy(healthGoal = goal), current.updatedAtMs)
    }

    suspend fun getOnboardingDraft(): ApiResult<OnboardingDraftDto> = apiCall(json) {
        api.getOnboardingDraft().data
    }

    suspend fun saveOnboardingDraft(data: JsonObject): ApiResult<OnboardingDraftDto> = apiCall(json) {
        api.putOnboardingDraft(OnboardingDraftPutBody(data)).data
    }

    suspend fun deleteOnboardingDraft(): ApiResult<Unit> = apiCall(json) {
        api.deleteOnboardingDraft()
    }

    suspend fun getHealthGoal(): ApiResult<HealthGoalDto?> {
        val result = apiCall(json) { api.getHealthGoal().data }
        if (result is ApiResult.Success) cacheHealthGoal(result.data)
        return result
    }

    suspend fun saveHealthGoal(targetWeightKg: Double, deadline: String? = null): ApiResult<HealthGoalDto> {
        val result = apiCall(json) {
            api.createHealthGoal(HealthGoalCreateBody(targetWeightKg = targetWeightKg, deadline = deadline)).data
        }
        if (result is ApiResult.Success) cacheHealthGoal(result.data)
        return result
    }

    /**
     * Pulls `/v1/preferences/me` and adopts every value that has no local change waiting to be sent; values changed
     * on this device stay until their PATCH succeeds, so a stale server copy never undoes a fresh local choice.
     * Pending changes are (re)queued, in case an earlier attempt was dropped.
     */
    suspend fun syncPreferencesFromServer(): ApiResult<UserPreferenceDto> {
        val pendingBefore = syncStore.pending()
        val result = apiCall(json) { api.getPreferences().data }
        if (result is ApiResult.Success) {
            val pending = pendingBefore.plus(syncStore.pending())
            val adopted = PreferenceSyncPolicy.adopt(result.data, pending)
            val local = preferencesRepository.current()
            adopted.themeMode?.let { preferencesRepository.setThemeMode(it) }
            adopted.leaderboardVisibility?.let { syncStore.setLeaderboardVisibility(it) }
            var widgetsChanged = false
            adopted.accentPreset?.takeIf { it != local.accentPreset }?.let {
                preferencesRepository.setAccentPreset(it)
                widgetsChanged = true
            }
            adopted.backgroundStyle?.takeIf { it != local.backgroundStyle }?.let {
                preferencesRepository.setBackgroundStyle(it)
                widgetsChanged = true
            }
            if (widgetsChanged) widgetUpdater.updateAll()
            if (!pending.isEmpty) queuePreferencePatch()
        }
        return result
    }

    val leaderboardVisibility: Flow<LeaderboardVisibility> = syncStore.leaderboardVisibility

    suspend fun setThemeMode(mode: ThemeMode) {
        preferencesRepository.setThemeMode(mode)
        markAndQueue(PendingPreferences(themeMode = PreferenceSyncPolicy.themeToApi(mode)))
    }

    /** Applies the accent at once, syncs it as `accent_color`, and re-renders the widgets in the new colour. */
    suspend fun setAccentPreset(preset: AccentPreset) {
        preferencesRepository.setAccentPreset(preset)
        markAndQueue(PendingPreferences(accentColor = preset.hex))
        widgetUpdater.updateAll()
    }

    suspend fun setBackgroundStyle(style: BackgroundStyle) {
        preferencesRepository.setBackgroundStyle(style)
        markAndQueue(PendingPreferences(background = PreferenceSyncPolicy.backgroundToApi(style)))
        widgetUpdater.updateAll()
    }

    suspend fun setLeaderboardVisibility(visibility: LeaderboardVisibility) {
        syncStore.setLeaderboardVisibility(visibility)
        markAndQueue(PendingPreferences(leaderboardVisibility = visibility.apiValue))
    }

    private suspend fun markAndQueue(change: PendingPreferences) {
        syncStore.markPending(change)
        queuePreferencePatch()
    }

    /** One queued PATCH at a time; it reads the latest pending values when it runs and retries while offline. */
    private suspend fun queuePreferencePatch() {
        outboxRepository.enqueue(
            type = OUTBOX_TYPE_PREFERENCES_PATCH,
            payload = PreferencesPatchPayload(),
            serializer = PreferencesPatchPayload.serializer(),
            dedupeKey = OUTBOX_TYPE_PREFERENCES_PATCH,
        )
    }

    private companion object {
        const val PROFILE_CACHE_KEY = "profile"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ProfileCacheModule {
    @Provides
    @IntoSet
    fun provideProfileCacheCleanup(repository: ProfileRepository): UserDataCleanup =
        UserDataCleanup { repository.clearCache() }
}
