package vn.uit.healthos.feature.meds

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassChip
import vn.uit.healthos.core.designsystem.component.GlassTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import vn.uit.healthos.core.ui.asString

private val MED_FORMS = listOf(
    "tablet" to R.string.meds_form_tablet,
    "capsule" to R.string.meds_form_capsule,
    "liquid" to R.string.meds_form_liquid,
    "other" to R.string.meds_form_other,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedPlanFormScreen(
    state: MedsUiState,
    onUpdateForm: ((MedPlanFormState) -> MedPlanFormState) -> Unit,
    onAddDoseTime: () -> Unit,
    onRemoveDoseTime: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form

    DetailScaffold(
        title = stringResource(R.string.meds_add_plan_title),
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
                        value = form.name,
                        onValueChange = { v -> onUpdateForm { it.copy(name = v) } },
                        label = stringResource(R.string.meds_name_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassTextField(
                        value = form.strength,
                        onValueChange = { v -> onUpdateForm { it.copy(strength = v) } },
                        label = stringResource(R.string.meds_strength_label),
                        placeholder = stringResource(R.string.meds_form_dosage_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MED_FORMS.forEach { (key, resId) ->
                            GlassChip(
                                label = stringResource(resId),
                                onClick = { onUpdateForm { it.copy(form = key) } },
                                selected = form.form == key,
                            )
                        }
                    }
                    GlassTextField(
                        value = form.instructions,
                        onValueChange = { v -> onUpdateForm { it.copy(instructions = v) } },
                        label = stringResource(R.string.meds_instructions_label),
                        placeholder = stringResource(R.string.meds_form_note_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.meds_dose_times_label), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.doseTimes.forEach { time ->
                            val removable = form.doseTimes.size > 1
                            val removeLabel = stringResource(R.string.meds_remove_time_cd, time)
                            GlassChip(
                                label = time,
                                onClick = { onRemoveDoseTime(time) },
                                leadingIcon = if (removable) Icons.Rounded.Close else null,
                                modifier = if (removable) {
                                    Modifier.semantics { contentDescription = removeLabel }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassTextField(
                            value = form.newTimeInput,
                            onValueChange = { v -> onUpdateForm { it.copy(newTimeInput = v.take(5)) } },
                            label = stringResource(R.string.meds_add_time_hint),
                            modifier = Modifier.weight(1f),
                        )
                        GlassIconButton(onClick = onAddDoseTime, contentDescription = stringResource(R.string.meds_add_time_action)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    }
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTextField(
                            value = form.startDate,
                            onValueChange = { v -> onUpdateForm { it.copy(startDate = v) } },
                            label = stringResource(R.string.meds_start_date),
                            modifier = Modifier.weight(1f),
                        )
                        GlassTextField(
                            value = form.endDate,
                            onValueChange = { v -> onUpdateForm { it.copy(endDate = v) } },
                            label = stringResource(R.string.meds_end_date_optional),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    GlassTextField(
                        value = form.refillSupplyText,
                        onValueChange = { v -> onUpdateForm { it.copy(refillSupplyText = v.filter(Char::isDigit)) } },
                        label = stringResource(R.string.meds_refill_supply_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                onClick = onSubmit,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
