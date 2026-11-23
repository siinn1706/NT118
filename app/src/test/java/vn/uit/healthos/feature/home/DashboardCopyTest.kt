package vn.uit.healthos.feature.home

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import vn.uit.healthos.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import vn.uit.healthos.core.ui.UiText

class DashboardCopyTest {

    /** Every code the backend's rule-based exercise fallback can emit (`services/dashboard.py`). */
    private val backendExerciseCodes = setOf(
        "EXERCISE_NO_DATA",
        "EXERCISE_HR_BREATHING",
        "EXERCISE_HR_ELEVATED",
        "EXERCISE_BP_YOGA",
        "EXERCISE_WALK_MORE",
        "EXERCISE_WALK_BOOST",
        "EXERCISE_SLEEP_YOGA",
        "EXERCISE_BURN_CALORIES",
        "EXERCISE_HIIT",
        "EXERCISE_STRENGTH",
        "EXERCISE_BALANCED_STRENGTH",
        "EXERCISE_BALANCED_CARDIO",
        "EXERCISE_BALANCED_FLEX",
        "EXERCISE_BALANCED_REST",
    )

    /** Every `ai_insight.insight_code` the dashboard summary can emit. */
    private val backendInsightCodes = setOf("CALORIE_LOW", "CALORIE_HIGH", "CALORIE_NORMAL", "ACTIVITY_GOOD")

    @Test
    fun everyBackendCodeHasLocalizedCopy() {
        assertEquals(backendExerciseCodes, DashboardCopy.exerciseCopy.keys)
        assertEquals(backendInsightCodes, DashboardCopy.insightCopy.keys)
    }

    @Test
    fun placeholderCountMatchesDeclaredParams() {
        val strings = loadStrings("strings_home.xml")
        DashboardCopy.exerciseCopy.forEach { (code, copy) ->
            val title = strings.getValue(resourceName(copy.title))
            val message = strings.getValue(resourceName(copy.message))
            assertEquals("$code title", 0, placeholderCount(title))
            assertEquals("$code message", copy.paramKeys.size, placeholderCount(message))
        }
        DashboardCopy.insightCopy.values.forEach { id ->
            assertNotNull(strings[resourceName(id)])
        }
    }

    @Test
    fun ruleCodeResolvesToResourceWithOrderedParams() {
        val ui = DashboardCopy.exerciseUi(
            exercise(
                title = "EXERCISE_WALK_MORE",
                params = mapOf(
                    "target" to JsonPrimitive(10000),
                    "steps" to JsonPrimitive(3200),
                    "remaining" to JsonPrimitive(6800),
                ),
            ),
        )

        assertEquals(UiText.Res(R.string.home_ex_walk_more_title), ui.title)
        assertEquals(UiText.Res(R.string.home_ex_walk_more_message, listOf("3200", "6800", "10000")), ui.message)
    }

    @Test
    fun unknownRuleCodeFallsBackToGenericCopy() {
        val ui = DashboardCopy.exerciseUi(exercise(title = "EXERCISE_SOMETHING_NEW"))

        assertEquals(UiText.Res(R.string.home_ex_generic_title), ui.title)
        assertEquals(UiText.Res(R.string.home_ex_generic_message), ui.message)
    }

    @Test
    fun knownCodeMissingParamFallsBackToGenericCopy() {
        val ui = DashboardCopy.exerciseUi(exercise(title = "EXERCISE_HIIT"))

        assertEquals(UiText.Res(R.string.home_ex_generic_message), ui.message)
    }

    @Test
    fun aiSuggestionKeepsServerProse() {
        val ui = DashboardCopy.exerciseUi(
            exercise(title = "Đi bộ sau bữa tối", message = "Đi bộ 15 phút.", source = "ai"),
        )

        assertEquals(UiText.Raw("Đi bộ sau bữa tối"), ui.title)
        assertEquals(UiText.Raw("Đi bộ 15 phút."), ui.message)
    }

    @Test
    fun insightUsesCodeThenGenericFallback() {
        assertEquals(
            UiText.Res(R.string.home_insight_calorie_low),
            DashboardCopy.insightText(DashboardAiInsightDto(text = "CALORIE_LOW", insightCode = "CALORIE_LOW")),
        )
        assertEquals(
            UiText.Res(R.string.home_insight_activity_good),
            DashboardCopy.insightText(DashboardAiInsightDto(text = "ACTIVITY_GOOD")),
        )
        assertEquals(
            UiText.Res(R.string.home_insight_generic),
            DashboardCopy.insightText(DashboardAiInsightDto(text = "SLEEP_GREAT")),
        )
    }

    @Test
    fun suggestionsAreOrderedMostUrgentFirst() {
        val ui = listOf(
            exercise(id = "p3", title = "EXERCISE_BALANCED_REST", priority = 3),
            exercise(id = "p1", title = "EXERCISE_NO_DATA", priority = 1),
            exercise(id = "p4", title = "EXERCISE_BALANCED_FLEX", priority = 4),
            exercise(id = "p2", title = "EXERCISE_BALANCED_CARDIO", priority = 2),
        ).toExerciseUi()

        assertEquals(listOf("p1", "p2", "p3"), ui.map { it.id })
    }

    private fun exercise(
        id: String = "ex",
        title: String,
        message: String = title,
        params: Map<String, JsonPrimitive>? = null,
        priority: Int = 1,
        source: String = "rule",
    ) = ExerciseSuggestionDto(
        id = id,
        title = title,
        message = message,
        messageParams = params,
        priority = priority,
        source = source,
    )

    private fun resourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun placeholderCount(value: String): Int =
        Regex("%(\\d+)\\\$s").findAll(value).map { it.groupValues[1] }.toSet().size

    private fun loadStrings(fileName: String): Map<String, String> {
        val file = listOf(
            File("src/main/res/values/$fileName"),
            File("app/src/main/res/values/$fileName"),
        ).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
}
