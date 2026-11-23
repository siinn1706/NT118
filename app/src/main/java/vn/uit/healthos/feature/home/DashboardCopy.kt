package vn.uit.healthos.feature.home

import androidx.annotation.StringRes
import kotlinx.serialization.json.JsonNull
import vn.uit.healthos.R
import vn.uit.healthos.core.ui.UiText

/** Text that is either server-provided prose or a localized resource with positional arguments. */
/** Localized copy for a rule-based suggestion code; [paramKeys] order matches the `%n$s` placeholders. */
data class CodeCopy(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val paramKeys: List<String> = emptyList(),
)

data class ExerciseSuggestionUi(
    val id: String,
    val title: UiText,
    val message: UiText,
    val durationMinutes: Int?,
)

/** Backend priority 1 is the most urgent (`sorted(key=priority)` server-side), so sort ascending. */
fun List<ExerciseSuggestionDto>.toExerciseUi(limit: Int = 3): List<ExerciseSuggestionUi> =
    sortedBy { it.priority }.take(limit).map(DashboardCopy::exerciseUi)

/**
 * Maps the codes emitted by the backend's rule-based dashboard fallbacks
 * (`services/dashboard.py`) to Vietnamese string resources.
 */
object DashboardCopy {

    val exerciseCopy: Map<String, CodeCopy> = mapOf(
        "EXERCISE_NO_DATA" to CodeCopy(R.string.home_ex_no_data_title, R.string.home_ex_no_data_message),
        "EXERCISE_HR_BREATHING" to CodeCopy(
            R.string.home_ex_hr_breathing_title,
            R.string.home_ex_hr_breathing_message,
            listOf("heart_rate"),
        ),
        "EXERCISE_HR_ELEVATED" to CodeCopy(
            R.string.home_ex_hr_elevated_title,
            R.string.home_ex_hr_elevated_message,
            listOf("heart_rate"),
        ),
        "EXERCISE_BP_YOGA" to CodeCopy(
            R.string.home_ex_bp_yoga_title,
            R.string.home_ex_bp_yoga_message,
            listOf("systolic"),
        ),
        "EXERCISE_WALK_MORE" to CodeCopy(
            R.string.home_ex_walk_more_title,
            R.string.home_ex_walk_more_message,
            listOf("steps", "remaining", "target"),
        ),
        "EXERCISE_WALK_BOOST" to CodeCopy(
            R.string.home_ex_walk_boost_title,
            R.string.home_ex_walk_boost_message,
            listOf("steps", "remaining"),
        ),
        "EXERCISE_SLEEP_YOGA" to CodeCopy(
            R.string.home_ex_sleep_yoga_title,
            R.string.home_ex_sleep_yoga_message,
            listOf("sleep_hours"),
        ),
        "EXERCISE_BURN_CALORIES" to CodeCopy(
            R.string.home_ex_burn_calories_title,
            R.string.home_ex_burn_calories_message,
            listOf("calories", "deficit"),
        ),
        "EXERCISE_HIIT" to CodeCopy(
            R.string.home_ex_hiit_title,
            R.string.home_ex_hiit_message,
            listOf("steps"),
        ),
        "EXERCISE_STRENGTH" to CodeCopy(
            R.string.home_ex_strength_title,
            R.string.home_ex_strength_message,
            listOf("steps"),
        ),
        "EXERCISE_BALANCED_STRENGTH" to CodeCopy(
            R.string.home_ex_balanced_strength_title,
            R.string.home_ex_balanced_strength_message,
        ),
        "EXERCISE_BALANCED_CARDIO" to CodeCopy(
            R.string.home_ex_balanced_cardio_title,
            R.string.home_ex_balanced_cardio_message,
        ),
        "EXERCISE_BALANCED_FLEX" to CodeCopy(
            R.string.home_ex_balanced_flex_title,
            R.string.home_ex_balanced_flex_message,
        ),
        "EXERCISE_BALANCED_REST" to CodeCopy(
            R.string.home_ex_balanced_rest_title,
            R.string.home_ex_balanced_rest_message,
        ),
    )

    val insightCopy: Map<String, Int> = mapOf(
        "CALORIE_LOW" to R.string.home_insight_calorie_low,
        "CALORIE_HIGH" to R.string.home_insight_calorie_high,
        "CALORIE_NORMAL" to R.string.home_insight_calorie_normal,
        "ACTIVITY_GOOD" to R.string.home_insight_activity_good,
    )

    private val genericTitle = UiText.Res(R.string.home_ex_generic_title)
    private val genericMessage = UiText.Res(R.string.home_ex_generic_message)

    /**
     * Rule-based suggestions carry a code in `title`/`message`; AI suggestions carry prose.
     * Unknown codes, or known codes missing a parameter, fall back to generic localized copy.
     */
    fun exerciseUi(dto: ExerciseSuggestionDto): ExerciseSuggestionUi {
        val (title, message) = exerciseText(dto)
        return ExerciseSuggestionUi(dto.id, title, message, dto.durationMinutes)
    }

    private fun exerciseText(dto: ExerciseSuggestionDto): Pair<UiText, UiText> {
        val copy = exerciseCopy[dto.title.trim()]
            ?: return if (dto.source == SOURCE_AI && dto.title.isNotBlank() && dto.message.isNotBlank()) {
                UiText.Raw(dto.title) to UiText.Raw(dto.message)
            } else {
                genericTitle to genericMessage
            }
        val params = dto.messageParams.orEmpty()
        val args = copy.paramKeys.map { key ->
            params[key]?.takeUnless { it is JsonNull }?.content ?: return genericTitle to genericMessage
        }
        return UiText.Res(copy.title) to UiText.Res(copy.message, args)
    }

    fun insightText(dto: DashboardAiInsightDto): UiText {
        val code = (dto.insightCode ?: dto.text).trim()
        return UiText.Res(insightCopy[code] ?: R.string.home_insight_generic)
    }

    private const val SOURCE_AI = "ai"
}
