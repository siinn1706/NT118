package vn.uit.healthos.feature.meds

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { v -> onUpdateForm { it.copy(name = v) } },
                        label = { Text(stringResource(R.string.meds_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.strength,
                        onValueChange = { v -> onUpdateForm { it.copy(strength = v) } },
                        label = { Text(stringResource(R.string.meds_strength_label)) },
                        placeholder = { Text(stringResource(R.string.meds_form_dosage_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MED_FORMS.forEach { (key, resId) ->
                            FilterChip(
                                selected = form.form == key,
                                onClick = { onUpdateForm { it.copy(form = key) } },
                                label = { Text(stringResource(resId)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = form.instructions,
                        onValueChange = { v -> onUpdateForm { it.copy(instructions = v) } },
                        label = { Text(stringResource(R.string.meds_instructions_label)) },
                        placeholder = { Text(stringResource(R.string.meds_form_note_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.meds_dose_times_label), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.doseTimes.forEach { time ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveDoseTime(time) },
                                label = { Text(time) },
                                trailingIcon = if (form.doseTimes.size > 1) {
                                    { Icon(Icons.Rounded.Close, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = form.newTimeInput,
                            onValueChange = { v -> onUpdateForm { it.copy(newTimeInput = v.take(5)) } },
                            label = { Text(stringResource(R.string.meds_add_time_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onAddDoseTime) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.meds_add_time_action))
                        }
                    }
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = form.startDate,
                            onValueChange = { v -> onUpdateForm { it.copy(startDate = v) } },
                            label = { Text(stringResource(R.string.meds_start_date)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.endDate,
                            onValueChange = { v -> onUpdateForm { it.copy(endDate = v) } },
                            label = { Text(stringResource(R.string.meds_end_date_optional)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = form.refillSupplyText,
                        onValueChange = { v -> onUpdateForm { it.copy(refillSupplyText = v.filter(Char::isDigit)) } },
                        label = { Text(stringResource(R.string.meds_refill_supply_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
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
