package vn.uit.healthos.feature.climate

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import vn.uit.healthos.core.network.BareClient
import javax.inject.Singleton

@Serializable
data class OpenMeteoForecastResponse(
    val current: ForecastCurrentDto? = null,
)

@Serializable
data class ForecastCurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperatureC: Double? = null,
    @SerialName("apparent_temperature") val apparentTempC: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
)

@Serializable
data class OpenMeteoAirQualityResponse(
    val current: AirQualityCurrentDto? = null,
)

@Serializable
data class AirQualityCurrentDto(
    val time: String? = null,
    @SerialName("us_aqi") val usAqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
)

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun currentForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,uv_index",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoForecastResponse
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun currentAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoAirQualityResponse
}

@Module
@InstallIn(SingletonComponent::class)
object ClimateNetworkModule {
    @Provides
    @Singleton
    fun provideForecastApi(@BareClient bareClient: OkHttpClient, json: Json): OpenMeteoForecastApi =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(bareClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoForecastApi::class.java)

    @Provides
    @Singleton
    fun provideAirQualityApi(@BareClient bareClient: OkHttpClient, json: Json): OpenMeteoAirQualityApi =
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .client(bareClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoAirQualityApi::class.java)
}
