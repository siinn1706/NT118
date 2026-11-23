package vn.uit.healthos.feature.meals

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import vn.uit.healthos.R
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.toUiText
import vn.uit.healthos.core.ui.UiText
import javax.inject.Inject

data class PortionOptionUi(
    val label: String,
    val calories: Double,
    val multiplier: Double,
)

data class EditableMealItem(
    val id: String,
    val name: String,
    val grams: Double,
    val calories: Double,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val ingredientId: String? = null,
)

data class AnalysisDraftUi(
    val mealId: String?,
    val photoUri: Uri?,
    val dishName: String,
    val mealType: String = "lunch",
    val caloriesText: String,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val portionOptions: List<PortionOptionUi> = emptyList(),
    val selectedPortionIndex: Int = -1,
    val items: List<EditableMealItem> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rawNutrition: NutritionResultDto? = null,
)

data class MealsUiState(
    val todayMeals: List<MealResponseDto> = emptyList(),
    val pendingMeals: List<PendingMealEntity> = emptyList(),
    val weeklyCalories: List<CalorieSummaryPointDto> = emptyList(),
    val calorieTarget: Int = 2_000,
    val loading: Boolean = false,
    val errorMessage: UiText? = null,
    // Snap & Poll
    val capturedPhotoUri: Uri? = null,
    val analyzing: Boolean = false,
    val analysisError: UiText? = null,
    val analysisDraft: AnalysisDraftUi? = null,
    /** Why saving the analysis draft failed; the draft itself is kept so the edits survive. */
    val draftError: UiText? = null,
    // Manual entry
    val manualName: String = "",
    val manualMealType: String = "lunch",
    val manualCaloriesText: String = "",
    val manualNotes: String = "",
    val manualItems: List<EditableMealItem> = emptyList(),
    val ingredientQuery: String = "",
    val ingredientResults: List<IngredientDto> = emptyList(),
    val saving: Boolean = false,
    val infoMessage: UiText? = null,
) {
    /** Pending rows to show and count: rows whose meal already reached the server are left out. */
    val visiblePendingMeals: List<PendingMealEntity>
        get() = unsyncedPendingMeals(pendingMeals, todayMeals)

    val todayTotalCalories: Double
        get() = todayMeals.sumOf { it.nutritionResult?.calories ?: 0.0 } +
            visiblePendingMeals.sumOf { it.calories }
}

sealed interface MealsNavEvent {
    data object ToAnalysisResult : MealsNavEvent
    data object BackToList : MealsNavEvent
}

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealsRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MealsUiState(loading = true))
    val state: StateFlow<MealsUiState> = _state.asStateFlow()

    private val _events = Channel<MealsNavEvent>(Channel.BUFFERED)
    val events: Flow<MealsNavEvent> = _events.receiveAsFlow()

    private var analysisJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.dailyCalorieTarget.collectLatest { target ->
                if (target != null) _state.update { it.copy(calorieTarget = target) }
            }
        }
        viewModelScope.launch {
            repository.observeTodayPendingMeals().collectLatest { pending ->
                val previousIds = _state.value.pendingMeals.map { it.localId }
                _state.update { it.copy(pendingMeals = pending) }
                // A row that left the queue was either synced or rejected; reload so the day shows the server's view.
                if (!pending.map { it.localId }.containsAll(previousIds)) refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val res = repository.getMealsOverview()
            _state.update { s ->
                when (res) {
                    is ApiResult.Success -> s.copy(
                        todayMeals = res.data.todayMeals,
                        weeklyCalories = res.data.weeklyCalories,
                        loading = false,
                    )
                    is ApiResult.Failure -> s.copy(
                        loading = false,
                        errorMessage = res.error.toUiText().takeIf { s.todayMeals.isEmpty() && s.pendingMeals.isEmpty() },
                    )
                }
            }
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch {
            if (repository.deleteMeal(mealId) is ApiResult.Success) {
                _state.update { s -> s.copy(todayMeals = s.todayMeals.filterNot { it.id == mealId }) }
            }
        }
    }

    fun analyzePhoto(context: Context, uri: Uri) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    capturedPhotoUri = uri,
                    analyzing = true,
                    analysisError = null,
                    analysisDraft = null,
                )
            }
            val bytes = ImageDownscaler.downscaleToJpegBytes(context, uri)
            if (bytes == null) {
                _state.update { it.copy(analyzing = false, analysisError = UiText.Res(R.string.meals_error_photo_unreadable)) }
                return@launch
            }
            // The upload creates a server meal. Let it finish even if the user cancels, so that meal can be deleted.
            val jobRes = withContext(NonCancellable) { repository.uploadMealPhoto(bytes) }
            val started = when (jobRes) {
                is ApiResult.Success -> jobRes.data
                is ApiResult.Failure -> {
                    if (isActive) _state.update { it.copy(analyzing = false, analysisError = jobRes.error.toUiText()) }
                    return@launch
                }
            }
            val polled = try {
                ensureActive()
                repository.pollAnalysis(started.jobId)
            } catch (e: CancellationException) {
                discardServerMeal(started.mealId)
                throw e
            }
            when (polled) {
                is MealPollOutcome.Analyzed -> {
                    val nr = polled.response.nutritionResult ?: polled.response.result
                    val draft = buildDraftFromResult(
                        mealId = polled.response.mealId ?: started.mealId,
                        photoUri = uri,
                        nr = nr,
                    )
                    _state.update { it.copy(analyzing = false, analysisDraft = draft, draftError = null) }
                    _events.send(MealsNavEvent.ToAnalysisResult)
                }
                is MealPollOutcome.Failed -> {
                    discardServerMeal(started.mealId)
                    val error = polled.error?.toUiText() ?: UiText.Res(R.string.meals_error_analysis_failed)
                    _state.update { it.copy(analyzing = false, analysisError = error) }
                }
                MealPollOutcome.Timeout -> {
                    discardServerMeal(started.mealId)
                    _state.update { it.copy(analyzing = false, analysisError = UiText.Res(R.string.meals_error_analysis_timeout)) }
                }
            }
        }
    }

    /** Stops the running analysis; its coroutine deletes the server meal the upload created. */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _state.update { it.copy(analyzing = false, analysisError = null) }
    }

    /** Deletes an abandoned photo meal, finishing even when the calling coroutine is being cancelled. */
    private suspend fun discardServerMeal(mealId: String?) {
        if (mealId == null) return
        withContext(NonCancellable) { repository.discardMeal(mealId) }
    }

    fun updateDraftName(name: String) {
        _state.update { s -> s.copy(analysisDraft = s.analysisDraft?.copy(dishName = name)) }
    }

    fun updateDraftCalories(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }
        _state.update { s ->
            s.copy(analysisDraft = s.analysisDraft?.copy(caloriesText = cleaned, selectedPortionIndex = -1))
        }
    }

    fun selectPortionOption(index: Int) {
        _state.update { s ->
            val draft = s.analysisDraft ?: return@update s
            val opt = draft.portionOptions.getOrNull(index) ?: return@update s
            s.copy(
                analysisDraft = draft.copy(
                    selectedPortionIndex = index,
                    caloriesText = opt.calories.toInt().toString(),
                ),
            )
        }
    }

    fun removeDraftItem(id: String) {
        _state.update { s ->
            val draft = s.analysisDraft ?: return@update s
            val next = draft.items.filterNot { it.id == id }
            val nextKcal = if (next.isNotEmpty()) next.sumOf { it.calories }.toInt().toString() else draft.caloriesText
            s.copy(analysisDraft = draft.copy(items = next, caloriesText = nextKcal))
        }
    }

    fun addIngredientToDraft(ing: IngredientDto) {
        _state.update { s ->
            val draft = s.analysisDraft ?: return@update s
            val item = EditableMealItem(
                id = "${ing.id}-${System.nanoTime()}",
                name = ing.nameVi,
                grams = 100.0,
                calories = ing.caloriesPer100g,
                proteinG = ing.proteinPer100g,
                carbsG = ing.carbsPer100g,
                fatG = ing.fatPer100g,
                ingredientId = ing.id,
            )
            val next = draft.items + item
            val currentKcal = draft.caloriesText.toDoubleOrNull() ?: 0.0
            s.copy(
                analysisDraft = draft.copy(
                    items = next,
                    caloriesText = (currentKcal + ing.caloriesPer100g).toInt().toString(),
                    proteinG = draft.proteinG + ing.proteinPer100g,
                    carbsG = draft.carbsG + ing.carbsPer100g,
                    fatG = draft.fatG + ing.fatPer100g,
                ),
                ingredientQuery = "",
                ingredientResults = emptyList(),
            )
        }
    }

    fun saveAnalysisDraft() {
        val draft = _state.value.analysisDraft ?: return
        val mealId = draft.mealId
        val calories = draft.caloriesText.toDoubleOrNull() ?: 0.0
        val name = draft.dishName.trim().ifBlank { "Bữa ăn đã chụp" }
        val updatedIngredients = draft.items.map { item ->
            buildJsonObject {
                put("name", JsonPrimitive(item.name))
                put("grams", JsonPrimitive(item.grams))
                put("kcal", JsonPrimitive(item.calories))
                put("protein_g", JsonPrimitive(item.proteinG))
                put("carbs_g", JsonPrimitive(item.carbsG))
                put("fat_g", JsonPrimitive(item.fatG))
            }
        }
        val nutrition = (draft.rawNutrition ?: NutritionResultDto()).copy(
            dishName = name,
            mealType = draft.mealType,
            calories = calories,
            proteinG = draft.proteinG,
            carbsG = draft.carbsG,
            fatG = draft.fatG,
            ingredients = updatedIngredients.takeIf { it.isNotEmpty() } ?: draft.rawNutrition?.ingredients,
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, draftError = null, infoMessage = null) }
            var queuedOffline = false
            val error: UiText? = if (mealId != null) {
                when (val res = repository.updateAnalyzedMeal(mealId, MealUpdateBody(name = name, nutritionResult = nutrition))) {
                    is ApiResult.Success -> null
                    is ApiResult.Failure -> res.error.toUiText()
                }
            } else {
                val res = repository.createManualMeal(
                    MealCreateBody(
                        name = name,
                        mealType = draft.mealType,
                        ingredients = listOf(
                            MealCreateIngredientBody(
                                ingredientName = name,
                                grams = 100.0,
                                manualCalories = calories,
                                calories = calories,
                            ),
                        ),
                    ),
                )
                when (res) {
                    is ManualMealSaveResult.Synced -> null
                    ManualMealSaveResult.QueuedOffline -> {
                        queuedOffline = true
                        null
                    }
                    is ManualMealSaveResult.Failed -> res.error.toUiText()
                }
            }
            if (error != null) {
                // Keep the draft so the user's edits stay on screen and can be saved again.
                _state.update { it.copy(saving = false, draftError = error) }
                return@launch
            }
            _state.update {
                it.copy(
                    saving = false,
                    analysisDraft = null,
                    infoMessage = if (queuedOffline) UiText.Res(R.string.meals_info_saved_offline) else null,
                )
            }
            refresh()
            _events.send(MealsNavEvent.BackToList)
        }
    }

    fun discardAnalysisDraft() {
        val mealId = _state.value.analysisDraft?.mealId
        _state.update { it.copy(analysisDraft = null, draftError = null) }
        if (mealId != null) {
            viewModelScope.launch { discardServerMeal(mealId) }
        }
    }

    // Manual entry actions
    fun onManualNameChange(v: String) = _state.update { it.copy(manualName = v, errorMessage = null) }
    fun onManualMealTypeChange(v: String) = _state.update { it.copy(manualMealType = v) }
    fun onManualCaloriesChange(v: String) = _state.update { it.copy(manualCaloriesText = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onManualNotesChange(v: String) = _state.update { it.copy(manualNotes = v) }

    fun onIngredientQueryChange(q: String) {
        _state.update { it.copy(ingredientQuery = q) }
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(ingredientResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val res = repository.searchIngredients(q)
            if (res is ApiResult.Success) {
                _state.update { it.copy(ingredientResults = res.data) }
            }
        }
    }

    fun addIngredientToManual(ing: IngredientDto) {
        val item = EditableMealItem(
            id = "${ing.id}-${System.nanoTime()}",
            name = ing.nameVi,
            grams = 100.0,
            calories = ing.caloriesPer100g,
            proteinG = ing.proteinPer100g,
            carbsG = ing.carbsPer100g,
            fatG = ing.fatPer100g,
            ingredientId = ing.id,
        )
        _state.update { s ->
            val next = s.manualItems + item
            s.copy(
                manualItems = next,
                manualName = s.manualName.ifBlank { ing.nameVi },
                manualCaloriesText = next.sumOf { it.calories }.toInt().toString(),
                ingredientQuery = "",
                ingredientResults = emptyList(),
            )
        }
    }

    fun removeManualItem(id: String) {
        _state.update { s ->
            val next = s.manualItems.filterNot { it.id == id }
            s.copy(
                manualItems = next,
                manualCaloriesText = if (next.isNotEmpty()) next.sumOf { it.calories }.toInt().toString() else s.manualCaloriesText,
            )
        }
    }

    fun saveManualMeal() {
        val s = _state.value
        val name = s.manualName.trim()
        val kcal = s.manualCaloriesText.toDoubleOrNull() ?: s.manualItems.sumOf { it.calories }
        if (name.isBlank()) {
            _state.update { it.copy(errorMessage = UiText.Res(R.string.meals_error_name_required)) }
            return
        }
        val ingredients = if (s.manualItems.isNotEmpty()) {
            s.manualItems.map { item ->
                MealCreateIngredientBody(
                    ingredientName = item.name,
                    ingredientId = item.ingredientId,
                    grams = item.grams,
                    manualCalories = item.calories,
                    calories = item.calories,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                )
            }
        } else {
            listOf(
                MealCreateIngredientBody(
                    ingredientName = name,
                    grams = 100.0,
                    manualCalories = kcal,
                    calories = kcal,
                ),
            )
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, errorMessage = null, infoMessage = null) }
            when (
                val res = repository.createManualMeal(
                    MealCreateBody(
                        name = name,
                        notes = s.manualNotes.trim().ifBlank { null },
                        mealType = s.manualMealType,
                        ingredients = ingredients,
                    ),
                )
            ) {
                is ManualMealSaveResult.Synced -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            manualName = "",
                            manualCaloriesText = "",
                            manualNotes = "",
                            manualItems = emptyList(),
                        )
                    }
                    refresh()
                    _events.send(MealsNavEvent.BackToList)
                }
                ManualMealSaveResult.QueuedOffline -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            manualName = "",
                            manualCaloriesText = "",
                            manualNotes = "",
                            manualItems = emptyList(),
                            infoMessage = UiText.Res(R.string.meals_info_saved_offline),
                        )
                    }
                    _events.send(MealsNavEvent.BackToList)
                }
                is ManualMealSaveResult.Failed -> {
                    _state.update { it.copy(saving = false, errorMessage = res.error.toUiText()) }
                }
            }
        }
    }

    private fun buildDraftFromResult(
        mealId: String?,
        photoUri: Uri?,
        nr: NutritionResultDto?,
    ): AnalysisDraftUi {
        val baseCalories = nr?.calories ?: 0.0
        val parsedPortions = nr?.portionOptions.orEmpty().mapIndexedNotNull { idx, obj ->
            val label = obj["label"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["size"]?.jsonPrimitive?.contentOrNull
                ?: return@mapIndexedNotNull null
            val mult = obj["multiplier"]?.jsonPrimitive?.doubleOrNull
                ?: obj["factor"]?.jsonPrimitive?.doubleOrNull
                ?: 1.0
            val kcal = obj["calories"]?.jsonPrimitive?.doubleOrNull
                ?: obj["kcal"]?.jsonPrimitive?.doubleOrNull
                ?: (baseCalories * mult)
            PortionOptionUi(label = label, calories = kcal, multiplier = mult)
        }
        val parsedItems = nr?.ingredients.orEmpty().mapIndexedNotNull { idx, obj ->
            val name = obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["ingredient_name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapIndexedNotNull null
            val grams = obj["grams"]?.jsonPrimitive?.doubleOrNull ?: 100.0
            val kcal = obj["kcal"]?.jsonPrimitive?.doubleOrNull
                ?: obj["calories"]?.jsonPrimitive?.doubleOrNull
                ?: 0.0
            EditableMealItem(
                id = "ai-$idx",
                name = name,
                grams = grams,
                calories = kcal,
                proteinG = obj["protein_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                carbsG = obj["carbs_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                fatG = obj["fat_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }
        return AnalysisDraftUi(
            mealId = mealId,
            photoUri = photoUri,
            dishName = nr?.dishName.orEmpty().ifBlank { "Món ăn nhận diện" },
            mealType = nr?.mealType ?: "lunch",
            caloriesText = baseCalories.toInt().toString(),
            proteinG = nr?.proteinG ?: 0.0,
            carbsG = nr?.carbsG ?: 0.0,
            fatG = nr?.fatG ?: 0.0,
            portionOptions = parsedPortions,
            items = parsedItems,
            warnings = nr?.warnings.orEmpty(),
            rawNutrition = nr,
        )
    }
}
