package vn.uit.healthos.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiResultTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `core error envelope is mapped to ApiError`() = runTest {
        val result = apiCall(json) {
            throw httpError(
                422,
                """{"error":{"code":"VALIDATION_ERROR","message":"Invalid","field_errors":{"email":"required"}}}""",
            )
        }

        val error = (result as ApiResult.Failure).error
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals("Invalid", error.message)
        assertEquals(422, error.httpStatus)
        assertEquals(mapOf("email" to "required"), error.fieldErrors)
        assertEquals(false, error.isRetryable)
    }

    @Test
    fun `non-envelope error body falls back to http code`() = runTest {
        val error = (apiCall(json) { throw httpError(502, "<html>bad gateway</html>") } as ApiResult.Failure).error

        assertEquals("HTTP_502", error.code)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `io failure is a retryable network error`() = runTest {
        val error = (apiCall(json) { throw IOException("timeout") } as ApiResult.Failure).error

        assertTrue(error.isNetwork)
        assertTrue(error.isRetryable)
    }
}
