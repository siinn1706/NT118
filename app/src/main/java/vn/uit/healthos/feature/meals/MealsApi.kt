package vn.uit.healthos.feature.meals

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class NutritionResultDto(
    @SerialName("dish_name") val dishName: String? = null,
    @SerialName("serving_type") val servingType: String? = null,
    @SerialName("meal_type") val mealType: String? = null,
    val calories: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("calorie_min") val calorieMin: Double? = null,
    @SerialName("calorie_max") val calorieMax: Double? = null,
    val confidence: Double? = null,
    val source: String? = null,
    @SerialName("portion_estimate") val portionEstimate: String? = null,
    @SerialName("portion_options") val portionOptions: List<JsonObject>? = null,
    val warnings: List<String>? = null,
    val notes: String? = null,
    val ingredients: List<JsonObject>? = null,
)

@Serializable
data class MealResponseDto(
    val id: String,
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("job_id") val jobId: String? = null,
    val status: String = "manual",
    @SerialName("nutrition_result") val nutritionResult: NutritionResultDto? = null,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MealListEnvelope(
    val data: List<MealResponseDto> = emptyList(),
)

@Serializable
data class MealCreateIngredientBody(
    @SerialName("ingredient_name") val ingredientName: String,
    @SerialName("ingredient_id") val ingredientId: String? = null,
    val grams: Double = 100.0,
    @SerialName("manual_calories") val manualCalories: Double? = null,
    val calories: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
)

@Serializable
data class MealCreateBody(
    val name: String,
    val notes: String? = null,
    @SerialName("logged_at") val loggedAt: String? = null,
    @SerialName("meal_type") val mealType: String? = null,
    val ingredients: List<MealCreateIngredientBody> = emptyList(),
)

@Serializable
data class MealUpdateBody(
    val name: String? = null,
    @SerialName("logged_at") val loggedAt: String? = null,
    @SerialName("nutrition_result") val nutritionResult: NutritionResultDto? = null,
)

@Serializable
data class MealAnalysisErrorDto(
    val code: String = "UNKNOWN",
    val message: String = "Analysis failed",
)

/** Bare response returned by `POST /v1/meals/analyze-photo` and `GET /v1/meals/analyze-photo/{job_id}`. */
@Serializable
data class AnalysisJobResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("meal_id") val mealId: String? = null,
    @SerialName("nutrition_result") val nutritionResult: NutritionResultDto? = null,
    val result: NutritionResultDto? = null,
    val error: MealAnalysisErrorDto? = null,
)

/** Calorie total for one local day (`yyyy-MM-dd`). */
@Serializable
data class CalorieSummaryPointDto(
    val date: String,
    @SerialName("total_calories") val totalCalories: Double,
)

@Serializable
data class IngredientDto(
    val id: String,
    val slug: String = "",
    @SerialName("name_vi") val nameVi: String,
    @SerialName("name_en") val nameEn: String = "",
    val category: String = "",
    @SerialName("calories_per_100g") val caloriesPer100g: Double = 0.0,
    @SerialName("protein_per_100g") val proteinPer100g: Double = 0.0,
    @SerialName("carbs_per_100g") val carbsPer100g: Double = 0.0,
    @SerialName("fat_per_100g") val fatPer100g: Double = 0.0,
    @SerialName("unit_hint") val unitHint: String = "g",
)

@Serializable
data class IngredientListEnvelope(
    val data: List<IngredientDto> = emptyList(),
)

interface MealsApi {
    @GET("v1/meals")
    suspend fun listMeals(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): MealListEnvelope

    @POST("v1/meals")
    suspend fun createMeal(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Body body: MealCreateBody,
    ): DataEnvelope<MealResponseDto>

    @PATCH("v1/meals/{mealId}")
    suspend fun updateMeal(
        @Path("mealId") mealId: String,
        @Body body: MealUpdateBody,
    ): DataEnvelope<MealResponseDto>

    @DELETE("v1/meals/{mealId}")
    suspend fun deleteMeal(@Path("mealId") mealId: String)

    @Multipart
    @POST("v1/meals/analyze-photo")
    suspend fun analyzePhoto(
        @Part image: MultipartBody.Part,
        @Part("name") name: RequestBody? = null,
    ): AnalysisJobResponseDto

    @GET("v1/meals/analyze-photo/{jobId}")
    suspend fun getAnalysisStatus(@Path("jobId") jobId: String): AnalysisJobResponseDto

    @GET("v1/nutrition/ingredients")
    suspend fun searchIngredients(
        @Query("q") query: String? = null,
        @Query("per_page") perPage: Int = 20,
    ): IngredientListEnvelope
}

@Module
@InstallIn(SingletonComponent::class)
object MealsNetworkModule {
    @Provides
    @Singleton
    fun provideMealsApi(retrofit: Retrofit): MealsApi = retrofit.create(MealsApi::class.java)
}
