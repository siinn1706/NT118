package vn.uit.healthos.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import vn.uit.healthos.core.data.session.SessionEvents
import vn.uit.healthos.core.data.session.SessionTokens
import vn.uit.healthos.core.data.session.TokenStore

/**
 * Refreshes the access token once on 401 and retries the request.
 * Concurrent 401s share a single refresh: whoever enters the lock second sees the token already
 * changed and simply retries with it.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: TokenRefreshApi,
    private val sessionEvents: SessionEvents,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val failedToken = response.request.header(AuthInterceptor.AUTHORIZATION)
            ?.removePrefix("Bearer ")
            ?: return null

        synchronized(lock) {
            val current = tokenStore.current() ?: return null
            if (current.accessToken != failedToken) return response.request.withBearer(current.accessToken)

            val refreshToken = current.refreshToken ?: return expire()
            // A network failure during refresh keeps the session; only an explicit rejection signs out.
            val refreshResponse = runCatching { refreshApi.refresh(RefreshTokenBody(refreshToken)).execute() }
                .getOrNull()
                ?: return null
            if (refreshResponse.code() == 401 || refreshResponse.code() == 403) return expire()
            val refreshed = refreshResponse.takeIf { it.isSuccessful }?.body()?.data ?: return null

            val next = SessionTokens(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: refreshToken,
                userId = refreshed.userId,
            )
            runBlocking { tokenStore.save(next) }
            return response.request.withBearer(next.accessToken)
        }
    }

    private fun expire(): Request? {
        runBlocking { tokenStore.clear() }
        sessionEvents.notifyExpired()
        return null
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header(AuthInterceptor.AUTHORIZATION, "Bearer $token").build()
}
