package vn.uit.healthos.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.core.data.session.SessionEvents
import vn.uit.healthos.core.data.session.TokenStore
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client without auth handling, used for the refresh call itself. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BareClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    @BareClient
    fun provideBareClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    fun provideAuthClient(
        @BareClient bare: OkHttpClient,
        tokenStore: TokenStore,
        refreshApi: TokenRefreshApi,
        sessionEvents: SessionEvents,
    ): OkHttpClient = bare.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(TokenAuthenticator(tokenStore, refreshApi, sessionEvents))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides
    @Singleton
    fun provideTokenRefreshApi(@BareClient bare: OkHttpClient, json: Json): TokenRefreshApi =
        retrofit(bare, json).create(TokenRefreshApi::class.java)

    @Provides
    @Singleton
    fun provideHealthCheckApi(retrofit: Retrofit): HealthCheckApi = retrofit.create(HealthCheckApi::class.java)

    @Provides
    @Singleton
    fun provideSseClient(client: OkHttpClient): SseClient =
        SseClient(client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build())

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.HEALTHOS_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
}
