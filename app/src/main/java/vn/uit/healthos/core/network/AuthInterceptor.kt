package vn.uit.healthos.core.network

import okhttp3.Interceptor
import okhttp3.Response
import vn.uit.healthos.core.data.session.TokenStore

/** Adds the current bearer token unless the request already carries an Authorization header. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val token = tokenStore.current()?.accessToken ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
