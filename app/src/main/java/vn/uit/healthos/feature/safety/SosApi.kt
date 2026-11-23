package vn.uit.healthos.feature.safety

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class PublicEmergencyContactDto(
    val name: String,
    val relationship: String? = null,
    val phone: String,
)

@Serializable
data class PublicEmergencyCardDto(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("age_years") val ageYears: Int? = null,
    val gender: String? = null,
    @SerialName("blood_type") val bloodType: String? = null,
    @SerialName("nkda_confirmed") val nkdaConfirmed: Boolean = false,
    val allergies: List<String> = emptyList(),
    @SerialName("chronic_conditions") val chronicConditions: List<String> = emptyList(),
    @SerialName("emergency_contacts") val emergencyContacts: List<PublicEmergencyContactDto> = emptyList(),
)

@Serializable
data class EmergencyShareTokenDto(
    val id: String,
    val label: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("access_count") val accessCount: Int = 0,
)

@Serializable
data class EmergencyShareTokenWithSecretDto(
    @SerialName("token_meta") val tokenMeta: EmergencyShareTokenDto,
    val token: String,
    @SerialName("share_url") val shareUrl: String,
    @SerialName("qr_png_data_url") val qrPngDataUrl: String = "",
)

@Serializable
data class EmergencyShareTokenCreateBody(
    val label: String,
    @SerialName("acknowledge_low_completeness") val acknowledgeLowCompleteness: Boolean = true,
)

@Serializable
data class EmergencyProfileOwnerViewDto(
    @SerialName("is_published") val isPublished: Boolean = false,
    val card: PublicEmergencyCardDto = PublicEmergencyCardDto(),
    @SerialName("completeness_score") val completenessScore: Int = 0,
    val tokens: List<EmergencyShareTokenDto> = emptyList(),
    @SerialName("active_token_count") val activeTokenCount: Int = 0,
)

@Serializable
data class EmergencyProfileUpdateBody(
    @SerialName("is_published") val isPublished: Boolean? = null,
)

@Serializable
data class SosEmailRequestBody(
    val trigger: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("accuracy_m") val accuracyM: Float? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("client_event_id") val clientEventId: String,
)

@Serializable
data class SosEmailResultDto(
    val emailed: Int = 0,
    @SerialName("skipped_no_email") val skippedNoEmail: Int = 0,
)

interface SosApi {
    @GET("v1/users/me/emergency-profile")
    suspend fun getEmergencyProfile(): DataEnvelope<EmergencyProfileOwnerViewDto>

    @PATCH("v1/users/me/emergency-profile")
    suspend fun patchEmergencyProfile(@Body body: EmergencyProfileUpdateBody): DataEnvelope<EmergencyProfileOwnerViewDto>

    @POST("v1/users/me/emergency-profile/tokens")
    suspend fun mintToken(@Body body: EmergencyShareTokenCreateBody): DataEnvelope<EmergencyShareTokenWithSecretDto>

    @GET("v1/users/me/emergency-profile/tokens")
    suspend fun listTokens(): DataEnvelope<List<EmergencyShareTokenDto>>

    @DELETE("v1/users/me/emergency-profile/tokens/{tokenId}")
    suspend fun revokeToken(@Path("tokenId") tokenId: String): DataEnvelope<List<EmergencyShareTokenDto>>

    @POST("v1/emergency/sos")
    suspend fun sendSosEmail(@Body body: SosEmailRequestBody): DataEnvelope<SosEmailResultDto>
}

@Module
@InstallIn(SingletonComponent::class)
object SosNetworkModule {
    @Provides
    @Singleton
    fun provideSosApi(retrofit: Retrofit): SosApi = retrofit.create(SosApi::class.java)
}
