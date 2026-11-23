package vn.uit.healthos.feature.profile

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class NullableDataEnvelope<T>(val data: T? = null)

@Serializable
data class EmergencyContactDto(
    val name: String,
    val phone: String,
    val relationship: String,
    val email: String? = null,
)

@Serializable
data class MedicalInfoDto(
    val allergies: String? = null,
    @SerialName("chronic_conditions") val chronicConditions: String? = null,
    @SerialName("current_medications") val currentMedications: String? = null,
    val notes: String? = null,
)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("onboarding_status") val onboardingStatus: String = "pending",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_type") val bloodType: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("emergency_contacts") val emergencyContacts: List<EmergencyContactDto>? = null,
    @SerialName("medical_info") val medicalInfo: MedicalInfoDto? = null,
)

@Serializable
data class UserProfileUpdateBody(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_type") val bloodType: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("emergency_contacts") val emergencyContacts: List<EmergencyContactDto>? = null,
    @SerialName("medical_info") val medicalInfo: MedicalInfoDto? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean? = null,
)

@Serializable
data class OnboardingDraftPutBody(
    val data: JsonObject,
)

@Serializable
data class OnboardingDraftDto(
    val data: JsonObject,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class HealthGoalCreateBody(
    @SerialName("target_weight_kg") val targetWeightKg: Double,
    val deadline: String? = null,
)

@Serializable
data class HealthGoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    val deadline: String? = null,
)

@Serializable
data class UserPreferenceDto(
    @SerialName("theme_mode") val themeMode: String = "system",
    @SerialName("accent_color") val accentColor: String? = null,
    val locale: String = "vi",
)

@Serializable
data class UserPreferenceUpdateBody(
    @SerialName("theme_mode") val themeMode: String? = null,
    @SerialName("accent_color") val accentColor: String? = null,
    val locale: String? = null,
)

interface ProfileApi {
    @GET("v1/users/me")
    suspend fun getMe(): DataEnvelope<UserProfileDto>

    @PATCH("v1/users/me")
    suspend fun patchMe(@Body body: UserProfileUpdateBody): DataEnvelope<UserProfileDto>

    @Multipart
    @POST("v1/users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): DataEnvelope<UserProfileDto>

    @GET("v1/users/me/onboarding-draft")
    suspend fun getOnboardingDraft(): DataEnvelope<OnboardingDraftDto>

    @PUT("v1/users/me/onboarding-draft")
    suspend fun putOnboardingDraft(@Body body: OnboardingDraftPutBody): DataEnvelope<OnboardingDraftDto>

    @DELETE("v1/users/me/onboarding-draft")
    suspend fun deleteOnboardingDraft()

    @GET("v1/health-goals")
    suspend fun getHealthGoal(): NullableDataEnvelope<HealthGoalDto>

    @POST("v1/health-goals")
    suspend fun createHealthGoal(@Body body: HealthGoalCreateBody): DataEnvelope<HealthGoalDto>

    @GET("v1/preferences/me")
    suspend fun getPreferences(): DataEnvelope<UserPreferenceDto>

    @PATCH("v1/preferences/me")
    suspend fun patchPreferences(@Body body: UserPreferenceUpdateBody): DataEnvelope<UserPreferenceDto>
}

@Module
@InstallIn(SingletonComponent::class)
object ProfileNetworkModule {
    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)
}
