package vn.uit.healthos.feature.meals

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.manualName,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.meals_dish_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MEAL_TYPES.forEach { (key, resId) ->
                            FilterChip(
                                selected = state.manualMealType == key,
                                onClick = { onMealTypeChange(key) },
                                label = { Text(stringResource(resId)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.manualCaloriesText,
                        onValueChange = onCaloriesChange,
                        label = { Text(stringResource(R.string.meals_calories_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.manualNotes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(R.string.meals_notes_optional)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = state.ingredientQuery,
                onValueChange = onIngredientQueryChange,
                label = { Text(stringResource(R.string.meals_add_ingredient_search)) },
                singleLine = true,
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
                        IconButton(onClick = { onRemoveItem(item.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = err.asString(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
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
