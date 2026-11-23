package vn.uit.healthos.feature.meds

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class MedicationDoseSummaryDto(
    @SerialName("reminder_id") val reminderId: String,
    val time: String,
    val repeat: String = "daily",
    @SerialName("next_occurrence_at") val nextOccurrenceAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class MedicationPlanDto(
    val id: String,
    val name: String,
    @SerialName("generic_name") val genericName: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val instructions: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val status: String = "active",
    val tzid: String = "Asia/Ho_Chi_Minh",
    @SerialName("refill_supply_units") val refillSupplyUnits: Int? = null,
    @SerialName("refill_cadence_days") val refillCadenceDays: Int? = null,
    @SerialName("next_refill_estimated_at") val nextRefillEstimatedAt: String? = null,
    val notes: String? = null,
    @SerialName("dose_count") val doseCount: Int = 0,
    @SerialName("next_dose_at") val nextDoseAt: String? = null,
)

@Serializable
data class MedicationPlanDetailDto(
    val id: String,
    val name: String,
    val strength: String? = null,
    val form: String? = null,
    val instructions: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val status: String = "active",
    val tzid: String = "Asia/Ho_Chi_Minh",
    @SerialName("refill_supply_units") val refillSupplyUnits: Int? = null,
    @SerialName("refill_cadence_days") val refillCadenceDays: Int? = null,
    @SerialName("next_refill_estimated_at") val nextRefillEstimatedAt: String? = null,
    val notes: String? = null,
    @SerialName("dose_count") val doseCount: Int = 0,
    val doses: List<MedicationDoseSummaryDto> = emptyList(),
)

@Serializable
data class MedicationPlanCreateBody(
    val name: String,
    val strength: String? = null,
    val form: String? = null,
    val instructions: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val tzid: String = "Asia/Ho_Chi_Minh",
    @SerialName("dose_times") val doseTimes: List<String>,
    val repeat: String = "daily",
    @SerialName("refill_supply_units") val refillSupplyUnits: Int? = null,
    @SerialName("refill_cadence_days") val refillCadenceDays: Int? = null,
    val notes: String? = null,
)

@Serializable
data class MedicationRefillBody(
    @SerialName("supply_units") val supplyUnits: Int,
)

@Serializable
data class MedicationPauseBody(
    @SerialName("pause_reason") val pauseReason: String? = null,
)

@Serializable
data class MedicationDoseDto(
    @SerialName("occurrence_id") val occurrenceId: String,
    @SerialName("reminder_id") val reminderId: String,
    @SerialName("medication_plan_id") val medicationPlanId: String,
    @SerialName("plan_name") val planName: String,
    val strength: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String = "pending",
)

/** Windows the backend accepts for `GET /medications/{id}/adherence?period=`. */
enum class AdherencePeriod(val apiValue: String) { WEEK("7d"), MONTH("30d"), QUARTER("90d") }

@Serializable
data class AdherenceDto(
    @SerialName("period_days") val periodDays: Int = 30,
    val scheduled: Int = 0,
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    @SerialName("snoozed_pending") val snoozedPending: Int = 0,
    val pending: Int = 0,
    val percent: Int = 0,
)

@Serializable
data class ReminderOccurrenceDto(
    val id: String,
    @SerialName("reminder_id") val reminderId: String,
    val title: String,
    val type: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String,
    @SerialName("snoozed_until") val snoozedUntil: String? = null,
)

@Serializable
data class ReminderOccurrenceActionBody(
    @SerialName("occurrence_id") val occurrenceId: String? = null,
)

@Serializable
data class ReminderSnoozeBody(
    @SerialName("occurrence_id") val occurrenceId: String? = null,
    val minutes: Int,
)

interface MedsApi {
    @GET("v1/medications")
    suspend fun listPlans(@Query("status") status: String = "all"): DataEnvelope<List<MedicationPlanDto>>

    @POST("v1/medications")
    suspend fun createPlan(@Body body: MedicationPlanCreateBody): DataEnvelope<MedicationPlanDetailDto>

    @GET("v1/medications/today")
    suspend fun getTodayDoses(@Query("tzid") tzid: String = "Asia/Ho_Chi_Minh"): DataEnvelope<List<MedicationDoseDto>>

    @GET("v1/medications/{planId}")
    suspend fun getPlanDetail(@Path("planId") planId: String): DataEnvelope<MedicationPlanDetailDto>

    @POST("v1/medications/{planId}/pause")
    suspend fun pausePlan(
        @Path("planId") planId: String,
        @Body body: MedicationPauseBody = MedicationPauseBody(),
    ): DataEnvelope<MedicationPlanDto>

    @POST("v1/medications/{planId}/resume")
    suspend fun resumePlan(@Path("planId") planId: String): DataEnvelope<MedicationPlanDto>

    @POST("v1/medications/{planId}/refill")
    suspend fun refillPlan(
        @Path("planId") planId: String,
        @Body body: MedicationRefillBody,
    ): DataEnvelope<MedicationPlanDto>

    @DELETE("v1/medications/{planId}")
    suspend fun deletePlan(@Path("planId") planId: String)

    @GET("v1/medications/{planId}/adherence")
    suspend fun getAdherence(
        @Path("planId") planId: String,
        @Query("period") period: String,
    ): DataEnvelope<AdherenceDto>

    @GET("v1/reminders/occurrences")
    suspend fun listOccurrences(
        @Query("from") fromIso: String,
        @Query("to") toIso: String,
        @Query("tzid") tzid: String = "Asia/Ho_Chi_Minh",
    ): DataEnvelope<List<ReminderOccurrenceDto>>

    @POST("v1/reminders/{reminderId}/done")
    suspend fun markDoseDone(
        @Path("reminderId") reminderId: String,
        @Body body: ReminderOccurrenceActionBody,
    ): DataEnvelope<ReminderOccurrenceDto>

    @POST("v1/reminders/{reminderId}/skip")
    suspend fun skipDose(
        @Path("reminderId") reminderId: String,
        @Body body: ReminderOccurrenceActionBody,
    ): DataEnvelope<ReminderOccurrenceDto>

    @POST("v1/reminders/{reminderId}/snooze")
    suspend fun snoozeDose(
        @Path("reminderId") reminderId: String,
        @Body body: ReminderSnoozeBody,
    ): DataEnvelope<ReminderOccurrenceDto>
}

@Module
@InstallIn(SingletonComponent::class)
object MedsNetworkModule {
    @Provides
    @Singleton
    fun provideMedsApi(retrofit: Retrofit): MedsApi = retrofit.create(MedsApi::class.java)
}
