package vn.uit.healthos.core.network

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.uit.healthos.R

class ApiErrorTextTest {

    @Test
    fun `known backend codes win over the HTTP status`() {
        assertEquals(R.string.error_invalid_credentials, ApiError("INVALID_CREDENTIALS", "Bad", 401).userMessageRes())
        assertEquals(R.string.error_too_many_tokens, ApiError("TOO_MANY_ACTIVE_TOKENS", "x", 409).userMessageRes())
    }

    @Test
    fun `unknown codes fall back by status`() {
        assertEquals(R.string.error_network, ApiError(ApiError.NETWORK, "timeout").userMessageRes())
        assertEquals(R.string.error_session_expired, ApiError("HTTP_401", "x", 401).userMessageRes())
        assertEquals(R.string.error_validation, ApiError("HTTP_422", "x", 422).userMessageRes())
        assertEquals(R.string.error_rate_limited, ApiError("HTTP_429", "x", 429).userMessageRes())
        assertEquals(R.string.error_server, ApiError("HTTP_503", "x", 503).userMessageRes())
        assertEquals(R.string.error_unknown, ApiError("WHATEVER", "x").userMessageRes())
    }
}
