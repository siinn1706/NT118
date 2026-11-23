package vn.uit.healthos.feature.auth

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.AuthTokenDto
import vn.uit.healthos.R
import vn.uit.healthos.core.ui.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signInWithCompletedOnboardingEmitsToHome() = runTest {
        val repo = FakeAuthRepository(
            loginResult = ApiResult.Success(
                LoginOutcome.SignedIn(sampleToken(onboardingStatus = "completed")),
            ),
        )
        val vm = AuthViewModel(repo)
        vm.onIdentifierChange("alice@example.com")
        vm.onPasswordChange("Secret!123")

        vm.events.test {
            vm.signIn()
            advanceUntilIdle()
            assertEquals(AuthNavEvent.ToHome, awaitItem())
            assertFalse(vm.state.value.loading)
        }
    }

    @Test
    fun signInWithPendingOnboardingEmitsToOnboarding() = runTest {
        val repo = FakeAuthRepository(
            loginResult = ApiResult.Success(
                LoginOutcome.SignedIn(sampleToken(onboardingStatus = "pending")),
            ),
        )
        val vm = AuthViewModel(repo)
        vm.onIdentifierChange("newuser@example.com")
        vm.onPasswordChange("Secret!123")

        vm.events.test {
            vm.signIn()
            advanceUntilIdle()
            assertEquals(AuthNavEvent.ToOnboarding, awaitItem())
        }
    }

    @Test
    fun signInMfaBranchNavigatesToMfaAndCompletesAfterCode() = runTest {
        val repo = FakeAuthRepository(
            loginResult = ApiResult.Success(
                LoginOutcome.MfaRequired(challengeId = "ch-42", expiresInSeconds = 300),
            ),
            mfaResult = ApiResult.Success(sampleToken(onboardingStatus = "completed")),
        )
        val vm = AuthViewModel(repo)
        vm.onIdentifierChange("mfa@example.com")
        vm.onPasswordChange("Secret!123")

        vm.events.test {
            vm.signIn()
            advanceUntilIdle()
            assertEquals(AuthNavEvent.ToMfa, awaitItem())
            assertEquals("ch-42", vm.state.value.mfaChallengeId)

            vm.onMfaCodeChange("123456")
            vm.verifyMfa()
            advanceUntilIdle()
            assertEquals(AuthNavEvent.ToHome, awaitItem())
        }
    }

    @Test
    fun signInFailureSurfacesErrorMessage() = runTest {
        val repo = FakeAuthRepository(
            loginResult = ApiResult.Failure(ApiError("INVALID_CREDENTIALS", "Sai thông tin đăng nhập", 401)),
        )
        val vm = AuthViewModel(repo)
        vm.onIdentifierChange("alice@example.com")
        vm.onPasswordChange("wrong")

        vm.signIn()
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(UiText.Res(R.string.error_invalid_credentials), vm.state.value.errorMessage)
    }

    @Test
    fun signInRejectsBlankInputsWithoutCallingRepository() = runTest {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)

        vm.signIn()
        advanceUntilIdle()

        assertTrue(vm.state.value.fieldErrors.containsKey("identifier"))
        assertTrue(vm.state.value.fieldErrors.containsKey("password"))
        assertEquals(0, repo.loginCalls)
    }

    private fun sampleToken(onboardingStatus: String) = AuthTokenDto(
        accessToken = "acc-1",
        refreshToken = "ref-1",
        userId = "u-1",
        email = "user@example.com",
        displayName = "Test User",
        onboardingStatus = onboardingStatus,
    )

    private class FakeAuthRepository(
        private val loginResult: ApiResult<LoginOutcome> = ApiResult.Failure(ApiError("UNSET", "unset")),
        private val mfaResult: ApiResult<AuthTokenDto> = ApiResult.Failure(ApiError("UNSET", "unset")),
    ) : AuthRepository {
        var loginCalls = 0

        override suspend fun login(identifier: String, password: String): ApiResult<LoginOutcome> {
            loginCalls++
            return loginResult
        }

        override suspend fun verifyMfa(challengeId: String, code: String): ApiResult<AuthTokenDto> = mfaResult
        override suspend fun requestSignupOtp(email: String, name: String, username: String, password: String) =
            ApiResult.Success(OtpRequestedDto(otp = "654321"))

        override suspend fun verifySignupOtp(email: String, code: String) = mfaResult
        override suspend fun requestResetOtp(email: String) = ApiResult.Success(OtpRequestedDto())
        override suspend fun verifyResetOtp(email: String, code: String) =
            ApiResult.Success(OtpVerifiedDto(email = email, nextStep = "reset_password"))

        override suspend fun resetPassword(email: String, newPassword: String) = mfaResult
        override suspend fun checkEmailAvailable(email: String) = ApiResult.Success(true)
        override suspend fun checkUsernameAvailable(username: String) = ApiResult.Success(true)
        override suspend fun refreshSessionInfo() = ApiResult.Failure(ApiError("UNSET", "unset"))
        override suspend fun logout() = Unit
    }
}
