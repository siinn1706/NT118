package vn.uit.healthos.core.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
)

/** Persists the signed-in session. Implementations must keep tokens encrypted at rest. */
interface TokenStore {
    val tokens: Flow<SessionTokens?>

    /** Latest tokens without suspending; safe to call from OkHttp threads. */
    fun current(): SessionTokens?

    suspend fun save(tokens: SessionTokens)

    suspend fun clear()
}
