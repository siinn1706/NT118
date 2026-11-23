package vn.uit.healthos.feature.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.data.session.SessionCleaner
import vn.uit.healthos.core.data.session.SessionRepository
import vn.uit.healthos.core.data.session.SessionTokens
import vn.uit.healthos.core.data.session.TokenStore
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.AuthTokenDto
import vn.uit.healthos.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginOutcome {
    data class SignedIn(val token: AuthTokenDto) : LoginOutcome
    data class MfaRequired(val challengeId: String, val expiresInSeconds: Int) : LoginOutcome
}

interface AuthRepository {
    suspend fun login(identifier: String, password: String): ApiResult<LoginOutcome>
    suspend fun verifyMfa(challengeId: String, code: String): ApiResult<AuthTokenDto>
    suspend fun requestSignupOtp(
        email: String,
        name: String,
        username: String,
        password: String,
    ): ApiResult<OtpRequestedDto>

    suspend fun verifySignupOtp(email: String, code: String): ApiResult<AuthTokenDto>
    suspend fun requestResetOtp(email: String): ApiResult<OtpRequestedDto>
    suspend fun verifyResetOtp(email: String, code: String): ApiResult<OtpVerifiedDto>
    suspend fun resetPassword(email: String, newPassword: String): ApiResult<AuthTokenDto>
    suspend fun checkEmailAvailable(email: String): ApiResult<Boolean>
    suspend fun checkUsernameAvailable(username: String): ApiResult<Boolean>
    suspend fun refreshSessionInfo(): ApiResult<AuthMeDto>
    suspend fun logout()
}

@Singleton
class NetworkAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val json: Json,
    private val sessionRepository: SessionRepository,
    private val tokenStore: TokenStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val sessionCleaner: SessionCleaner,
) : AuthRepository {

    override suspend fun login(identifier: String, password: String): ApiResult<LoginOutcome> {
        val result = apiCall(json) {
            api.login(LoginBody(identifier = identifier.trim(), password = password)).data
        }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val obj = result.data.jsonObject
                val isMfa = obj["mfa_required"]?.jsonPrimitive?.booleanOrNull == true
                if (isMfa) {
                    val mfa = json.decodeFromJsonElement(MfaLoginRequiredDto.serializer(), result.data)
                    ApiResult.Success(LoginOutcome.MfaRequired(mfa.challengeId, mfa.expiresInSeconds))
                } else {
                    val token = json.decodeFromJsonElement(AuthTokenDto.serializer(), result.data)
                    persistAuthToken(token)
                    ApiResult.Success(LoginOutcome.SignedIn(token))
                }
            }
        }
    }

    override suspend fun verifyMfa(challengeId: String, code: String): ApiResult<AuthTokenDto> {
        val result = apiCall(json) {
            api.loginMfa(MfaLoginVerifyBody(challengeId = challengeId, code = code.trim())).data
        }
        if (result is ApiResult.Success) {
            persistAuthToken(result.data)
        }
        return result
    }

    override suspend fun requestSignupOtp(
        email: String,
        name: String,
        username: String,
        password: String,
    ): ApiResult<OtpRequestedDto> = apiCall(json) {
        api.requestOtp(
            RequestOtpBody(
                email = email.trim().lowercase(),
                purpose = "signup",
                name = name.trim(),
                username = username.trim().lowercase(),
                password = password,
            ),
        ).data
    }

    override suspend fun verifySignupOtp(email: String, code: String): ApiResult<AuthTokenDto> {
        val result = apiCall(json) {
            val raw = api.verifyOtp(
                VerifyOtpBody(
                    email = email.trim().lowercase(),
                    purpose = "signup",
                    code = code.trim(),
                ),
            ).data
            json.decodeFromJsonElement(AuthTokenDto.serializer(), raw)
        }
        if (result is ApiResult.Success) {
            persistAuthToken(result.data)
        }
        return result
    }

    override suspend fun requestResetOtp(email: String): ApiResult<OtpRequestedDto> = apiCall(json) {
        api.requestOtp(
            RequestOtpBody(
                email = email.trim().lowercase(),
                purpose = "reset_password",
            ),
        ).data
    }

    override suspend fun verifyResetOtp(email: String, code: String): ApiResult<OtpVerifiedDto> = apiCall(json) {
        val raw = api.verifyOtp(
            VerifyOtpBody(
                email = email.trim().lowercase(),
                purpose = "reset_password",
                code = code.trim(),
            ),
        ).data
        json.decodeFromJsonElement(OtpVerifiedDto.serializer(), raw)
    }

    override suspend fun resetPassword(email: String, newPassword: String): ApiResult<AuthTokenDto> {
        val result = apiCall(json) {
            api.resetPassword(
                ResetPasswordBody(
                    email = email.trim().lowercase(),
                    newPassword = newPassword,
                ),
            ).data
        }
        if (result is ApiResult.Success) {
            persistAuthToken(result.data)
        }
        return result
    }

    override suspend fun checkEmailAvailable(email: String): ApiResult<Boolean> = apiCall(json) {
        api.checkEmail(email.trim().lowercase()).available
    }

    override suspend fun checkUsernameAvailable(username: String): ApiResult<Boolean> = apiCall(json) {
        api.checkUsername(username.trim().lowercase()).available
    }

    override suspend fun refreshSessionInfo(): ApiResult<AuthMeDto> {
        val result = apiCall(json) { api.me().data }
        if (result is ApiResult.Success) {
            preferencesRepository.setOnboardingStatus(result.data.onboardingStatus)
        }
        return result
    }

    override suspend fun logout() {
        val refresh = tokenStore.current()?.refreshToken
        runCatching { api.logout(LogoutBody(refreshToken = refresh)) }
        sessionRepository.signOut()
        sessionCleaner.onSignedOut()
    }

    private suspend fun persistAuthToken(token: AuthTokenDto) {
        sessionCleaner.onSignedIn(token.userId)
        sessionRepository.signIn(
            SessionTokens(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                userId = token.userId,
            ),
        )
        preferencesRepository.setOnboardingStatus(token.onboardingStatus)
    }
}
