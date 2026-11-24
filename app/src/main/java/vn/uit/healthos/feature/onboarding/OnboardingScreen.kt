package vn.uit.healthos.feature.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onFullNameChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onSexChange: (BiologicalSex) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onBloodTypeChange: (String) -> Unit,
    onActivityChange: (ActivityLevel) -> Unit,
    onGoalDirectionChange: (GoalDirection) -> Unit,
    onTargetWeightChange: (String) -> Unit,
    onContactChange: (Int, EmergencyContactForm) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .liquidGlass(shape = GlassTokens.CardShape, style = GlassStyle.Chrome),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.step > 0) {
                        SecondaryButton(
                            text = stringResource(R.string.action_back),
                            onClick = onPrevious,
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.step < 3) {
                        PrimaryButton(
                            text = stringResource(R.string.action_continue),
                            onClick = onNext,
                            loading = state.loading,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.onboarding_complete_action),
                            onClick = onComplete,
                            loading = state.loading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_progress, state.step + 1, 4),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (state.step + 1) / 4f },
                modifier = Modifier.fillMaxWidth(),
            )

            when (state.step) {
                0 -> BodyStep(
                    state = state,
                    onFullNameChange = onFullNameChange,
                    onDateOfBirthChange = onDateOfBirthChange,
                    onSexChange = onSexChange,
                    onHeightChange = onHeightChange,
                    onWeightChange = onWeightChange,
                    onBloodTypeChange = onBloodTypeChange,
                )
                1 -> ActivityStep(
                    selected = state.activityLevel,
                    onSelect = onActivityChange,
                )
                2 -> GoalTdeeStep(
                    state = state,
                    onGoalDirectionChange = onGoalDirectionChange,
                    onTargetWeightChange = onTargetWeightChange,
                )
                else -> EmergencyContactsStep(
                    contacts = state.contacts,
                    onContactChange = onContactChange,
                    onAddContact = onAddContact,
                    onRemoveContact = onRemoveContact,
                )
            }

            state.validationError?.let { code ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = onboardingErrorText(code),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            state.errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg.asString(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyStep(
    state: OnboardingUiState,
    onFullNameChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onSexChange: (BiologicalSex) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onBloodTypeChange: (String) -> Unit,
) {
    Text(stringResource(R.string.onboarding_body_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_body_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = { Text(stringResource(R.string.onboarding_full_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.dateOfBirth,
                onValueChange = onDateOfBirthChange,
                label = { Text(stringResource(R.string.onboarding_dob_label)) },
                supportingText = { Text(stringResource(R.string.onboarding_dob_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.onboarding_gender_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiologicalSex.entries.forEach { option ->
                    FilterChip(
                        selected = state.sex == option,
                        onClick = { onSexChange(option) },
                        label = { Text(sexLabel(option)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.heightCm,
                    onValueChange = onHeightChange,
                    label = { Text(stringResource(R.string.onboarding_height_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.weightKg,
                    onValueChange = onWeightChange,
                    label = { Text(stringResource(R.string.onboarding_weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.bloodType,
                onValueChange = onBloodTypeChange,
                label = { Text(stringResource(R.string.onboarding_blood_type_optional)) },
                placeholder = { Text("O+, A-, AB+...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivityStep(
    selected: ActivityLevel,
    onSelect: (ActivityLevel) -> Unit,
) {
    Text(stringResource(R.string.onboarding_activity_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_activity_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActivityLevel.entries.forEach { level ->
        val isSelected = level == selected
        BentoCard(
            onClick = { onSelect(level) },
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                LocalVitalColors.current.cardSurface
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = isSelected, onClick = { onSelect(level) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(activityTitle(level), style = MaterialTheme.typography.titleSmall)
                    Text(
                        activityDesc(level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "×${level.factor}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun GoalTdeeStep(
    state: OnboardingUiState,
    onGoalDirectionChange: (GoalDirection) -> Unit,
    onTargetWeightChange: (String) -> Unit,
) {
    Text(stringResource(R.string.onboarding_goal_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_goal_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoalDirection.entries.forEach { dir ->
            FilterChip(
                selected = state.goalDirection == dir,
                onClick = { onGoalDirectionChange(dir) },
                label = { Text(goalLabel(dir)) },
            )
        }
    }

    OutlinedTextField(
        value = state.targetWeightKg,
        onValueChange = onTargetWeightChange,
        label = { Text(stringResource(R.string.onboarding_target_weight_kg)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    state.tdee?.let { result ->
        BentoCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.onboarding_tdee_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_tdee_target_value, result.targetCalories),
                style = MaterialTheme.typography.displaySmall.tabular(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            FormulaRow(stringResource(R.string.onboarding_tdee_bmr), "${result.bmr} kcal")
            FormulaRow(stringResource(R.string.onboarding_tdee_activity_mult), "× ${result.activityFactor}")
            FormulaRow(stringResource(R.string.onboarding_tdee_maintenance), "${result.maintenanceCalories} kcal")
            val adjPrefix = if (result.goalAdjustment > 0) "+" else ""
            FormulaRow(stringResource(R.string.onboarding_tdee_adjustment), "$adjPrefix${result.goalAdjustment} kcal")
            FormulaRow(stringResource(R.string.onboarding_tdee_bmi), "${result.bmi}")
        }
    }
}

@Composable
private fun FormulaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun EmergencyContactsStep(
    contacts: List<EmergencyContactForm>,
    onContactChange: (Int, EmergencyContactForm) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Int) -> Unit,
) {
    Text(stringResource(R.string.onboarding_emergency_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_emergency_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    contacts.forEachIndexed { index, contact ->
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.onboarding_contact_number, index + 1),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (contacts.size > 1) {
                        IconButton(onClick = { onRemoveContact(index) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = contact.name,
                    onValueChange = { onContactChange(index, contact.copy(name = it)) },
                    label = { Text(stringResource(R.string.onboarding_contact_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact.relationship,
                    onValueChange = { onContactChange(index, contact.copy(relationship = it)) },
                    label = { Text(stringResource(R.string.onboarding_contact_relationship)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact.phone,
                    onValueChange = { onContactChange(index, contact.copy(phone = it)) },
                    label = { Text(stringResource(R.string.onboarding_contact_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact.email,
                    onValueChange = { onContactChange(index, contact.copy(email = it)) },
                    label = { Text(stringResource(R.string.onboarding_contact_email_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (contacts.size < 5) {
        TextButton(onClick = onAddContact) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.onboarding_add_contact))
        }
    }
}

@Composable
private fun sexLabel(sex: BiologicalSex): String = when (sex) {
    BiologicalSex.MALE -> stringResource(R.string.gender_male)
    BiologicalSex.FEMALE -> stringResource(R.string.gender_female)
    BiologicalSex.OTHER -> stringResource(R.string.gender_other)
}

@Composable
private fun activityTitle(level: ActivityLevel): String = when (level) {
    ActivityLevel.SEDENTARY -> stringResource(R.string.activity_sedentary)
    ActivityLevel.LIGHT -> stringResource(R.string.activity_light)
    ActivityLevel.MODERATE -> stringResource(R.string.activity_moderate)
    ActivityLevel.ACTIVE -> stringResource(R.string.activity_active)
    ActivityLevel.VERY_ACTIVE -> stringResource(R.string.activity_very_active)
}

@Composable
private fun activityDesc(level: ActivityLevel): String = when (level) {
    ActivityLevel.SEDENTARY -> stringResource(R.string.activity_sedentary_desc)
    ActivityLevel.LIGHT -> stringResource(R.string.activity_light_desc)
    ActivityLevel.MODERATE -> stringResource(R.string.activity_moderate_desc)
    ActivityLevel.ACTIVE -> stringResource(R.string.activity_active_desc)
    ActivityLevel.VERY_ACTIVE -> stringResource(R.string.activity_very_active_desc)
}

@Composable
private fun goalLabel(goal: GoalDirection): String = when (goal) {
    GoalDirection.LOSE -> stringResource(R.string.goal_lose)
    GoalDirection.MAINTAIN -> stringResource(R.string.goal_maintain)
    GoalDirection.GAIN -> stringResource(R.string.goal_gain)
}

@Composable
private fun onboardingErrorText(code: String): String = when (code) {
    "NAME_REQUIRED" -> stringResource(R.string.onboarding_err_name)
    "DOB_INVALID" -> stringResource(R.string.onboarding_err_dob)
    "HEIGHT_INVALID" -> stringResource(R.string.onboarding_err_height)
    "WEIGHT_INVALID" -> stringResource(R.string.onboarding_err_weight)
    "TARGET_WEIGHT_INVALID" -> stringResource(R.string.onboarding_err_target_weight)
    "CONTACT_INCOMPLETE" -> stringResource(R.string.onboarding_err_contact)
    else -> code
}
