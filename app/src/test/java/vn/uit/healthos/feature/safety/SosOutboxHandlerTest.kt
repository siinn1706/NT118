package vn.uit.healthos.feature.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.network.ApiError

class SosOutboxHandlerTest {

    @Test
    fun missingEndpointStaysQueued() {
        val notFound = ApiError(code = "HTTP_404", message = "Not Found", httpStatus = 404)
        assertFalse(notFound.isRetryable)

        val mapped = retryWhileEndpointMissing(notFound)
        assertTrue(mapped.isRetryable)
        assertEquals(404, mapped.httpStatus)
    }

    @Test
    fun otherErrorsPassThroughUnchanged() {
        val badRequest = ApiError(code = "VALIDATION_ERROR", message = "bad", httpStatus = 422)
        assertSame(badRequest, retryWhileEndpointMissing(badRequest))
        assertFalse(retryWhileEndpointMissing(badRequest).isRetryable)

        val serverError = ApiError(code = "HTTP_503", message = "down", httpStatus = 503)
        assertSame(serverError, retryWhileEndpointMissing(serverError))
    }
}
