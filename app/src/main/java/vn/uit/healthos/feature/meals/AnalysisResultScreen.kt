package vn.uit.healthos.feature.meals

import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.GlassChip
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassTextField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisResultScreen(
    state: MealsUiState,
    onDishNameChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onSelectPortion: (Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onIngredientQueryChange: (String) -> Unit,
    onAddIngredient: (IngredientDto) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.analysisDraft
    BackHandler(onBack = onDiscard)
    DetailScaffold(
        title = stringResource(R.string.meals_result_title),
        onBack = onDiscard,
        modifier = modifier,
    ) { padding ->
        if (draft == null) {
            ErrorState(
                message = stringResource(R.string.meals_result_empty),
                modifier = Modifier.padding(padding),
            )
            return@DetailScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            draft.photoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = draft.dishName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassTextField(
                        value = draft.dishName,
                        onValueChange = onDishNameChange,
                        label = stringResource(R.string.meals_dish_name_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassTextField(
                        value = draft.caloriesText,
                        onValueChange = onCaloriesChange,
                        label = stringResource(R.string.meals_calories_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Three macros on one line when they fit, otherwise a grid; they never run into each other.
                    AdaptiveColumns(
                        modifier = Modifier.fillMaxWidth(),
                        maxColumns = 3,
                        horizontalSpacing = 12.dp,
                        verticalSpacing = 4.dp,
                    ) {
                        Text(
                            stringResource(R.string.meals_macro_protein, draft.proteinG.roundToInt()),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.meals_macro_carbs, draft.carbsG.roundToInt()),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.meals_macro_fat, draft.fatG.roundToInt()),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (draft.portionOptions.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.meals_portion_section))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.portionOptions.forEachIndexed { idx, opt ->
                        GlassChip(
                            label = "${opt.label} (${opt.calories.roundToInt()} kcal)",
                            onClick = { onSelectPortion(idx) },
                            selected = draft.selectedPortionIndex == idx,
                        )
                    }
                }
            }

            if (draft.items.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.meals_ingredients_section))
                draft.items.forEach { item ->
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
            }

            // Add ingredient search
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

            if (draft.warnings.isNotEmpty()) {
                GlassBanner(
                    message = draft.warnings.joinToString("\n"),
                    tone = GlassBannerTone.Warning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.draftError?.let { err ->
                GlassBanner(
                    message = err.asString(),
                    tone = GlassBannerTone.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Side by side only while both labels fit on one line; otherwise stacked at full width.
            AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 2, horizontalSpacing = 12.dp) {
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDiscard,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSave,
                    loading = state.saving,
                )
            }
        }
    }
}
