package vn.uit.healthos.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Core API success envelope: `{ "data": ... }`. */
@Serializable
data class DataEnvelope<T>(val data: T)

/** Core API error envelope: `{ "error": { code, message, details, field_errors } }`. */
@Serializable
data class ErrorEnvelope(val error: ErrorDetail)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: String? = null,
    @SerialName("field_errors") val fieldErrors: Map<String, String>? = null,
)

@Serializable
data class RefreshTokenBody(@SerialName("refresh_token") val refreshToken: String)

/** Subset of Core `AuthToken` the client relies on. */
@Serializable
data class AuthTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("user_id") val userId: String,
    val email: String? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("onboarding_status") val onboardingStatus: String = "pending",
)

@Serializable
data class HealthStatus(val status: String)
