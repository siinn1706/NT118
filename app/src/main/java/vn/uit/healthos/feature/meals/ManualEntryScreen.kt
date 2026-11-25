package vn.uit.healthos.feature.meals

import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import kotlin.math.roundToInt

private val MEAL_TYPES = listOf(
    "breakfast" to R.string.meal_type_breakfast,
    "lunch" to R.string.meal_type_lunch,
    "dinner" to R.string.meal_type_dinner,
    "snack" to R.string.meal_type_snack,
)

@Composable
fun ManualEntryScreen(
    state: MealsUiState,
    onNameChange: (String) -> Unit,
    onMealTypeChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onIngredientQueryChange: (String) -> Unit,
    onAddIngredient: (IngredientDto) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(
        title = stringResource(R.string.meals_manual_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassTextField(
                        value = state.manualName,
                        onValueChange = onNameChange,
                        label = stringResource(R.string.meals_dish_name_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassSegmentedControl(
                        options = MEAL_TYPES.map { stringResource(it.second) },
                        selectedIndex = MEAL_TYPES.indexOfFirst { it.first == state.manualMealType }.coerceAtLeast(0),
                        onSelect = { onMealTypeChange(MEAL_TYPES[it].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassTextField(
                        value = state.manualCaloriesText,
                        onValueChange = onCaloriesChange,
                        label = stringResource(R.string.meals_calories_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassTextField(
                        value = state.manualNotes,
                        onValueChange = onNotesChange,
                        label = stringResource(R.string.meals_notes_optional),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            GlassTextField(
                value = state.ingredientQuery,
                onValueChange = onIngredientQueryChange,
                label = stringResource(R.string.meals_add_ingredient_search),
                modifier = Modifier.fillMaxWidth(),
            )

            state.ingredientResults.forEach { ing ->
                BentoCard(onClick = { onAddIngredient(ing) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ing.nameVi, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ing.caloriesPer100g.roundToInt()} kcal / 100g",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.manualItems.forEach { item ->
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${item.grams.roundToInt()}g · ${item.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GlassIconButton(onClick = { onRemoveItem(item.id) }, contentDescription = stringResource(R.string.action_delete)) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                GlassBanner(
                    message = err.asString(),
                    tone = GlassBannerTone.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
