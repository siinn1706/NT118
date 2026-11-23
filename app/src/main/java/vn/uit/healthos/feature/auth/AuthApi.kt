package vn.uit.healthos.feature.auth

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import vn.uit.healthos.core.network.AuthTokenDto
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class LoginBody(
    val identifier: String,
    val password: String,
)

@Serializable
data class MfaLoginRequiredDto(
    @SerialName("mfa_required") val mfaRequired: Boolean = true,
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
)

@Serializable
data class MfaLoginVerifyBody(
    @SerialName("challenge_id") val challengeId: String,
    val code: String,
)

@Serializable
data class RequestOtpBody(
    val email: String,
    val purpose: String,
    val name: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class OtpRequestedDto(
    val delivery: String = "email",
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 300,
    val otp: String? = null,
)

@Serializable
data class VerifyOtpBody(
    val email: String,
    val purpose: String,
    val code: String,
    val password: String? = null,
)

@Serializable
data class OtpVerifiedDto(
    val email: String,
    @SerialName("next_step") val nextStep: String,
)

@Serializable
data class ResetPasswordBody(
    val email: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class LogoutBody(
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class AvailabilityDto(
    val available: Boolean,
)

@Serializable
data class AuthMeDto(
    val id: String,
    val email: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("onboarding_status") val onboardingStatus: String = "pending",
)

interface AuthApi {
    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginBody): DataEnvelope<JsonElement>

    @POST("v1/auth/login/mfa")
    suspend fun loginMfa(@Body body: MfaLoginVerifyBody): DataEnvelope<AuthTokenDto>

    @POST("v1/auth/request-otp")
    suspend fun requestOtp(@Body body: RequestOtpBody): DataEnvelope<OtpRequestedDto>

    @POST("v1/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpBody): DataEnvelope<JsonElement>

    @POST("v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordBody): DataEnvelope<AuthTokenDto>

    @POST("v1/auth/logout")
    suspend fun logout(@Body body: LogoutBody): DataEnvelope<JsonElement>

    @GET("v1/auth/check-email")
    suspend fun checkEmail(@Query("email") email: String): AvailabilityDto

    @GET("v1/auth/check-username")
    suspend fun checkUsername(@Query("username") username: String): AvailabilityDto

    @GET("v1/auth/me")
    suspend fun me(): DataEnvelope<AuthMeDto>
}

@Module
@InstallIn(SingletonComponent::class)
object AuthNetworkModule {
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: NetworkAuthRepository): AuthRepository
}
