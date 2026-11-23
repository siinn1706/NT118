package vn.uit.healthos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import javax.inject.Inject
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText

enum class OtpPurpose(val apiValue: String) {
    SIGNUP("signup"),
    RESET_PASSWORD("reset_password"),
}

data class PasswordChecklist(
    val minLength: Boolean = false,
    val hasUpper: Boolean = false,
    val hasLower: Boolean = false,
    val hasDigit: Boolean = false,
    val hasSpecial: Boolean = false,
) {
    val allSatisfied: Boolean get() = minLength && hasUpper && hasLower && hasDigit && hasSpecial

    companion object {
        private val SPECIAL_REGEX = Regex("[!@#\$%^&*()_+\\-=\\[\\]{}|;:,.<>?]")

        fun evaluate(password: String): PasswordChecklist = PasswordChecklist(
            minLength = password.length in 8..128,
            hasUpper = password.any { it.isUpperCase() },
            hasLower = password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
            hasSpecial = SPECIAL_REGEX.containsMatchIn(password),
        )
    }
}

data class AuthUiState(
    // Sign In
    val identifier: String = "",
    val password: String = "",
    // Sign Up
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val signUpPassword: String = "",
    val signUpConfirmPassword: String = "",
    val emailAvailable: Boolean? = null,
    val usernameAvailable: Boolean? = null,
    // OTP
    val otpEmail: String = "",
    val otpPurpose: OtpPurpose = OtpPurpose.SIGNUP,
    val otpCode: String = "",
    val otpResendSeconds: Int = 0,
    val debugOtpHint: String? = null,
    // MFA
    val mfaChallengeId: String = "",
    val mfaCode: String = "",
    val mfaExpiresInSeconds: Int = 300,
    // Forgot Password
    val resetEmail: String = "",
    val resetNewPassword: String = "",
    val resetConfirmPassword: String = "",
    val resetOtpVerified: Boolean = false,
    // Shared status
    val loading: Boolean = false,
    val errorMessage: UiText? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val signUpPasswordChecklist: PasswordChecklist
        get() = PasswordChecklist.evaluate(signUpPassword)

    val resetPasswordChecklist: PasswordChecklist
        get() = PasswordChecklist.evaluate(resetNewPassword)
}

sealed interface AuthNavEvent {
    data object ToHome : AuthNavEvent
    data object ToOnboarding : AuthNavEvent
    data object ToMfa : AuthNavEvent
    data object ToOtp : AuthNavEvent
    data object ToResetNewPassword : AuthNavEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _events = Channel<AuthNavEvent>(Channel.BUFFERED)
    val events: Flow<AuthNavEvent> = _events.receiveAsFlow()

    private var cooldownJob: Job? = null
    private var emailCheckJob: Job? = null
    private var usernameCheckJob: Job? = null

    fun onIdentifierChange(value: String) {
        _state.update { it.copy(identifier = value, errorMessage = null, fieldErrors = it.fieldErrors - "identifier") }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessage = null, fieldErrors = it.fieldErrors - "password") }
    }

    fun onDisplayNameChange(value: String) {
        _state.update { it.copy(displayName = value, errorMessage = null, fieldErrors = it.fieldErrors - "name") }
    }

    fun onUsernameChange(value: String) {
        _state.update {
            it.copy(
                username = value,
                usernameAvailable = null,
                errorMessage = null,
                fieldErrors = it.fieldErrors - "username",
            )
        }
        usernameCheckJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.length >= 3) {
            usernameCheckJob = viewModelScope.launch {
                delay(400)
                when (val res = repository.checkUsernameAvailable(trimmed)) {
                    is ApiResult.Success -> _state.update { s ->
                        if (s.username.trim() == trimmed) s.copy(usernameAvailable = res.data) else s
                    }
                    is ApiResult.Failure -> Unit
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        _state.update {
            it.copy(
                email = value,
                emailAvailable = null,
                errorMessage = null,
                fieldErrors = it.fieldErrors - "email",
            )
        }
        emailCheckJob?.cancel()
        val trimmed = value.trim()
        if (isValidEmail(trimmed)) {
            emailCheckJob = viewModelScope.launch {
                delay(400)
                when (val res = repository.checkEmailAvailable(trimmed)) {
                    is ApiResult.Success -> _state.update { s ->
                        if (s.email.trim() == trimmed) s.copy(emailAvailable = res.data) else s
                    }
                    is ApiResult.Failure -> Unit
                }
            }
        }
    }

    fun onSignUpPasswordChange(value: String) {
        _state.update {
            it.copy(signUpPassword = value, errorMessage = null, fieldErrors = it.fieldErrors - "signUpPassword")
        }
    }

    fun onSignUpConfirmPasswordChange(value: String) {
        _state.update {
            it.copy(
                signUpConfirmPassword = value,
                errorMessage = null,
                fieldErrors = it.fieldErrors - "signUpConfirmPassword",
            )
        }
    }

    fun onOtpCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(otpCode = digits, errorMessage = null, fieldErrors = it.fieldErrors - "otp") }
    }

    fun onMfaCodeChange(value: String) {
        _state.update { it.copy(mfaCode = value.take(64), errorMessage = null, fieldErrors = it.fieldErrors - "mfa") }
    }

    fun onResetEmailChange(value: String) {
        _state.update { it.copy(resetEmail = value, errorMessage = null, fieldErrors = it.fieldErrors - "resetEmail") }
    }

    fun onResetNewPasswordChange(value: String) {
        _state.update {
            it.copy(resetNewPassword = value, errorMessage = null, fieldErrors = it.fieldErrors - "resetNewPassword")
        }
    }

    fun onResetConfirmPasswordChange(value: String) {
        _state.update {
            it.copy(
                resetConfirmPassword = value,
                errorMessage = null,
                fieldErrors = it.fieldErrors - "resetConfirmPassword",
            )
        }
    }

    fun clearErrors() {
        _state.update { it.copy(errorMessage = null, fieldErrors = emptyMap()) }
    }

    fun signIn() {
        val s = _state.value
        val errors = mutableMapOf<String, String>()
        if (s.identifier.isBlank()) errors["identifier"] = "EMPTY"
        if (s.password.isEmpty()) errors["password"] = "EMPTY"
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (val result = repository.login(s.identifier, s.password)) {
                is ApiResult.Success -> {
                    when (val outcome = result.data) {
                        is LoginOutcome.SignedIn -> {
                            _state.update { it.copy(loading = false, password = "") }
                            emitPostAuthRoute(outcome.token.onboardingStatus)
                        }
                        is LoginOutcome.MfaRequired -> {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    mfaChallengeId = outcome.challengeId,
                                    mfaExpiresInSeconds = outcome.expiresInSeconds,
                                    mfaCode = "",
                                )
                            }
                            _events.send(AuthNavEvent.ToMfa)
                        }
                    }
                }
                is ApiResult.Failure -> applyFailure(result.error)
            }
        }
    }

    fun verifyMfa() {
        val s = _state.value
        if (s.mfaCode.trim().length < 6) {
            _state.update { it.copy(fieldErrors = mapOf("mfa" to "INVALID_CODE")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (val result = repository.verifyMfa(s.mfaChallengeId, s.mfaCode)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, mfaCode = "", password = "") }
                    emitPostAuthRoute(result.data.onboardingStatus)
                }
                is ApiResult.Failure -> applyFailure(result.error)
            }
        }
    }

    fun requestSignUpOtp() {
        val s = _state.value
        val errors = mutableMapOf<String, String>()
        if (s.displayName.isBlank()) errors["name"] = "EMPTY"
        if (s.username.trim().length < 3) errors["username"] = "TOO_SHORT"
        if (s.usernameAvailable == false) errors["username"] = "TAKEN"
        if (!isValidEmail(s.email.trim())) errors["email"] = "INVALID_EMAIL"
        if (s.emailAvailable == false) errors["email"] = "TAKEN"
        if (!s.signUpPasswordChecklist.allSatisfied) errors["signUpPassword"] = "WEAK_PASSWORD"
        if (s.signUpConfirmPassword != s.signUpPassword) errors["signUpConfirmPassword"] = "MISMATCH"
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (
                val result = repository.requestSignupOtp(
                    email = s.email,
                    name = s.displayName,
                    username = s.username,
                    password = s.signUpPassword,
                )
            ) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            otpEmail = s.email.trim().lowercase(),
                            otpPurpose = OtpPurpose.SIGNUP,
                            otpCode = "",
                            debugOtpHint = result.data.otp,
                        )
                    }
                    startResendCooldown()
                    _events.send(AuthNavEvent.ToOtp)
                }
                is ApiResult.Failure -> applyFailure(result.error)
            }
        }
    }

    fun requestResetOtp() {
        val email = _state.value.resetEmail.trim()
        if (!isValidEmail(email)) {
            _state.update { it.copy(fieldErrors = mapOf("resetEmail" to "INVALID_EMAIL")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (val result = repository.requestResetOtp(email)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            otpEmail = email.lowercase(),
                            otpPurpose = OtpPurpose.RESET_PASSWORD,
                            otpCode = "",
                            resetOtpVerified = false,
                            debugOtpHint = result.data.otp,
                        )
                    }
                    startResendCooldown()
                    _events.send(AuthNavEvent.ToOtp)
                }
                is ApiResult.Failure -> applyFailure(result.error)
            }
        }
    }

    fun resendOtp() {
        val s = _state.value
        if (s.otpResendSeconds > 0 || s.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val result = when (s.otpPurpose) {
                OtpPurpose.SIGNUP -> repository.requestSignupOtp(
                    email = s.otpEmail,
                    name = s.displayName,
                    username = s.username,
                    password = s.signUpPassword,
                )
                OtpPurpose.RESET_PASSWORD -> repository.requestResetOtp(s.otpEmail)
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, debugOtpHint = result.data.otp) }
                    startResendCooldown()
                }
                is ApiResult.Failure -> applyFailure(result.error)
            }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        if (s.otpCode.length != 6) {
            _state.update { it.copy(fieldErrors = mapOf("otp" to "INVALID_OTP")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (s.otpPurpose) {
                OtpPurpose.SIGNUP -> {
                    when (val res = repository.verifySignupOtp(s.otpEmail, s.otpCode)) {
                        is ApiResult.Success -> {
                            _state.update { it.copy(loading = false, otpCode = "", signUpPassword = "") }
                            emitPostAuthRoute(res.data.onboardingStatus)
                        }
                        is ApiResult.Failure -> applyFailure(res.error)
                    }
                }
                OtpPurpose.RESET_PASSWORD -> {
                    when (val res = repository.verifyResetOtp(s.otpEmail, s.otpCode)) {
                        is ApiResult.Success -> {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    otpCode = "",
                                    resetOtpVerified = true,
                                    resetNewPassword = "",
                                    resetConfirmPassword = "",
                                )
                            }
                            _events.send(AuthNavEvent.ToResetNewPassword)
                        }
                        is ApiResult.Failure -> applyFailure(res.error)
                    }
                }
            }
        }
    }

    fun submitResetPassword() {
        val s = _state.value
        val errors = mutableMapOf<String, String>()
        if (!s.resetPasswordChecklist.allSatisfied) errors["resetNewPassword"] = "WEAK_PASSWORD"
        if (s.resetConfirmPassword != s.resetNewPassword) errors["resetConfirmPassword"] = "MISMATCH"
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, fieldErrors = emptyMap()) }
            when (val res = repository.resetPassword(s.otpEmail, s.resetNewPassword)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, resetNewPassword = "", resetConfirmPassword = "") }
                    emitPostAuthRoute(res.data.onboardingStatus)
                }
                is ApiResult.Failure -> applyFailure(res.error)
            }
        }
    }

    private suspend fun emitPostAuthRoute(onboardingStatus: String) {
        if (onboardingStatus.equals("completed", ignoreCase = true)) {
            _events.send(AuthNavEvent.ToHome)
        } else {
            _events.send(AuthNavEvent.ToOnboarding)
        }
    }

    private fun applyFailure(error: ApiError) {
        _state.update {
            it.copy(
                loading = false,
                errorMessage = error.toUiText(),
                // Server field messages are English prose; the screens only need to know which field failed.
                fieldErrors = error.fieldErrors.mapValues { "INVALID" },
            )
        }
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _state.update { it.copy(otpResendSeconds = sec) }
                if (sec > 0) delay(1_000)
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        val dot = email.lastIndexOf('.')
        return at > 0 && dot > at + 1 && dot < email.length - 1 && !email.contains(' ')
    }
}
