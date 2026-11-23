package vn.uit.healthos.feature.home

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class DashboardAlertDto(
    val id: String,
    val type: String,
    val message: String,
)

@Serializable
data class KpiValueDto(
    val current: Double? = null,
    val target: Double? = null,
)

@Serializable
data class DashboardGoalDto(
    val id: String,
    val key: String,
    val current: Double? = null,
    val target: Double? = null,
    val unit: String = "",
)

@Serializable
data class DashboardAiInsightDto(
    val text: String,
    val category: String? = null,
    @SerialName("insight_code") val insightCode: String? = null,
)

@Serializable
data class DashboardSummaryDto(
    @SerialName("user_name") val userName: String = "",
    val alerts: List<DashboardAlertDto> = emptyList(),
    val kpis: Map<String, KpiValueDto> = emptyMap(),
    val goals: List<DashboardGoalDto> = emptyList(),
    @SerialName("ai_insight") val aiInsight: DashboardAiInsightDto? = null,
)

@Serializable
data class DashboardAiAdviceDto(
    val id: String = "",
    val status: String = "ready",
    val category: String = "general",
    val priority: String = "medium",
    val title: String = "",
    val body: String = "",
    val source: String = "rule",
    val disclaimer: String = "",
)

@Serializable
data class ExerciseSuggestionDto(
    val id: String,
    val type: String = "tip",
    val icon: String = "",
    val title: String,
    val message: String,
    @SerialName("message_params") val messageParams: Map<String, JsonPrimitive>? = null,
    val priority: Int = 0,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val intensity: String = "medium",
    val category: String = "cardio",
    val source: String = "rule",
)

@Serializable
data class ExtendedVitalPointDto(
    val date: String,
    @SerialName("heart_rate") val heartRate: Double? = null,
    val systolic: Double? = null,
    val diastolic: Double? = null,
    val steps: Double? = null,
    @SerialName("sleep_minutes") val sleepMinutes: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
)

@Serializable
data class MetricAggregatePointDto(
    val date: String,
    @SerialName("avg_value") val avgValue: Double,
    @SerialName("min_value") val minValue: Double,
    @SerialName("max_value") val maxValue: Double,
    val count: Int,
)

interface DashboardApi {
    @GET("v1/dashboard/summary")
    suspend fun getSummary(): DataEnvelope<DashboardSummaryDto>

    @GET("v1/dashboard/ai-advice")
    suspend fun getAiAdvice(
        @Query("locale") locale: String = "vi",
        @Query("surface") surface: String = "mobile",
    ): DataEnvelope<DashboardAiAdviceDto>

    @GET("v1/dashboard/exercise-suggestions")
    suspend fun getExerciseSuggestions(): DataEnvelope<List<ExerciseSuggestionDto>>

    @GET("v1/dashboard/vitals-extended")
    suspend fun getVitalsExtended(@Query("days") days: Int = 7): DataEnvelope<List<ExtendedVitalPointDto>>

    @GET("v1/health-metrics/aggregated")
    suspend fun getAggregatedMetrics(
        @Query("metric_type") metricType: String,
        @Query("period") period: String = "daily",
    ): DataEnvelope<List<MetricAggregatePointDto>>
}

@Module
@InstallIn(SingletonComponent::class)
object DashboardNetworkModule {
    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)
}
