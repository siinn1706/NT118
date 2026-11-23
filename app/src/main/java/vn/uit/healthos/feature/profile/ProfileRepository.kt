package vn.uit.healthos.feature.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import vn.uit.healthos.feature.safety.SafetyPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: ProfileApi,
    private val json: Json,
    private val preferencesRepository: AppPreferencesRepository,
    private val safetyPreferences: SafetyPreferences,
) {
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

    /** Keeps the offline SOS contact list in step with every profile the server returns. */
    private suspend fun cacheProfile(profile: UserProfileDto) {
        preferencesRepository.setOnboardingStatus(profile.onboardingStatus)
        safetyPreferences.saveProfileCache(profile.fullName ?: profile.displayName, profile.emergencyContacts.orEmpty())
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

    suspend fun getHealthGoal(): ApiResult<HealthGoalDto?> = apiCall(json) {
        api.getHealthGoal().data
    }

    suspend fun saveHealthGoal(targetWeightKg: Double, deadline: String? = null): ApiResult<HealthGoalDto> =
        apiCall(json) {
            api.createHealthGoal(HealthGoalCreateBody(targetWeightKg = targetWeightKg, deadline = deadline)).data
        }

    suspend fun syncPreferencesFromServer(): ApiResult<UserPreferenceDto> {
        val result = apiCall(json) { api.getPreferences().data }
        if (result is ApiResult.Success) {
            preferencesRepository.setThemeMode(result.data.themeMode.toThemeMode())
        }
        return result
    }

    suspend fun setThemeMode(mode: ThemeMode): ApiResult<UserPreferenceDto> {
        preferencesRepository.setThemeMode(mode)
        return apiCall(json) {
            api.patchPreferences(UserPreferenceUpdateBody(themeMode = mode.toApiValue())).data
        }
    }

    private fun String.toThemeMode(): ThemeMode = when (lowercase()) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    private fun ThemeMode.toApiValue(): String = when (this) {
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
        ThemeMode.SYSTEM -> "system"
    }
}
