package vn.uit.healthos.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Unauthenticated refresh endpoint, called synchronously from [TokenAuthenticator]. */
interface TokenRefreshApi {
    @POST("v1/auth/refresh")
    fun refresh(@Body body: RefreshTokenBody): Call<DataEnvelope<AuthTokenDto>>
}

interface HealthCheckApi {
    @GET("v1/health")
    suspend fun health(): HealthStatus
}
