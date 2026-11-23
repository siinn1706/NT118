package vn.uit.healthos.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

data class ApiError(
    val code: String,
    val message: String,
    val httpStatus: Int? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isNetwork: Boolean get() = code == NETWORK
    val isRetryable: Boolean get() = isNetwork || (httpStatus != null && (httpStatus >= 500 || httpStatus == 429))

    companion object {
        const val NETWORK = "NETWORK_ERROR"
        const val PARSE = "PARSE_ERROR"
        const val UNKNOWN = "UNKNOWN_ERROR"
    }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

/** Runs a Retrofit call and maps every failure to [ApiError] using the Core error envelope. */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(e.toApiError(json))
} catch (e: IOException) {
    ApiResult.Failure(ApiError(ApiError.NETWORK, e.message ?: "Network error"))
} catch (e: SerializationException) {
    ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Unexpected response"))
}

fun HttpException.toApiError(json: Json): ApiError {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val detail = raw?.let { runCatching { json.decodeFromString(ErrorEnvelope.serializer(), it).error }.getOrNull() }
    return ApiError(
        code = detail?.code ?: "HTTP_${code()}",
        message = detail?.message ?: message(),
        httpStatus = code(),
        fieldErrors = detail?.fieldErrors.orEmpty(),
    )
}
