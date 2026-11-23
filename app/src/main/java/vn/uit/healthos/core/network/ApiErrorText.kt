package vn.uit.healthos.core.network

import androidx.annotation.StringRes
import vn.uit.healthos.R
import vn.uit.healthos.core.ui.UiText

/** Vietnamese message for an [ApiError]; never shows the server's English text or a raw code. */
fun ApiError.toUiText(): UiText = UiText.Res(userMessageRes())

@StringRes
fun ApiError.userMessageRes(): Int = codeMessages[code] ?: when {
    isNetwork -> R.string.error_network
    code == ApiError.PARSE -> R.string.error_unexpected_response
    httpStatus == 401 -> R.string.error_session_expired
    httpStatus == 403 -> R.string.error_forbidden
    httpStatus == 404 -> R.string.error_not_found
    httpStatus == 409 -> R.string.error_conflict
    httpStatus == 413 -> R.string.error_payload_too_large
    httpStatus == 400 || httpStatus == 422 -> R.string.error_validation
    httpStatus == 429 -> R.string.error_rate_limited
    httpStatus != null && httpStatus >= 500 -> R.string.error_server
    else -> R.string.error_unknown
}

private val codeMessages: Map<String, Int> = mapOf(
    "INVALID_CREDENTIALS" to R.string.error_invalid_credentials,
    "ACCOUNT_LOCKED" to R.string.error_account_locked,
    "ACCOUNT_BANNED" to R.string.error_account_banned,
    "ACCOUNT_PENDING_DELETION" to R.string.error_account_pending_deletion,
    "ACCOUNT_NOT_FOUND_EMAIL" to R.string.error_account_not_found_email,
    "EMAIL_TAKEN" to R.string.error_email_taken,
    "USERNAME_TAKEN" to R.string.error_username_taken,
    "PASSWORD_BREACHED" to R.string.error_password_breached,
    "OTP_INVALID" to R.string.error_otp_invalid,
    "OTP_LOCKED" to R.string.error_otp_locked,
    "OTP_REQUIRED" to R.string.error_otp_required,
    "INVALID_TOTP_CODE" to R.string.error_mfa_invalid,
    "MFA_CHALLENGE_INVALID" to R.string.error_mfa_expired,
    "MFA_CHALLENGE_LOCKED" to R.string.error_mfa_locked,
    "SESSION_EXPIRED" to R.string.error_session_expired,
    "AUTH_INVALID_TOKEN" to R.string.error_session_expired,
    "AUTH_TOKEN_REVOKED" to R.string.error_session_expired,
    "AUTH_REQUIRED" to R.string.error_session_expired,
    "RATE_LIMITED" to R.string.error_rate_limited,
    "RATE_LIMIT_EXCEEDED" to R.string.error_rate_limited,
    "PAYLOAD_TOO_LARGE" to R.string.error_payload_too_large,
    "UNSUPPORTED_MEDIA_TYPE" to R.string.error_unsupported_media,
    "EMPTY_FILE" to R.string.error_unsupported_media,
    "TOO_MANY_ACTIVE_TOKENS" to R.string.error_too_many_tokens,
    "VALIDATION_ERROR" to R.string.error_validation,
    "VALIDATION_FAILED" to R.string.error_validation,
    "NOT_FOUND" to R.string.error_not_found,
    "SERVICE_UNAVAILABLE" to R.string.error_server,
    "UPSTREAM_ERROR" to R.string.error_server,
)
