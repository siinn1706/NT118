package vn.uit.healthos.feature.onboarding

import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassProgressBar
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.AccentPicker
import vn.uit.healthos.core.designsystem.glass.ScrollEdge
import vn.uit.healthos.core.designsystem.glass.ScrollEdgeEffect
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import vn.uit.healthos.core.designsystem.component.GlassTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
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
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
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
    onAccentChange: (AccentPreset) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeight = with(density) { barHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = barHeight + 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_progress, state.step + 1, OnboardingSteps.COUNT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            GlassProgressBar(
                progress = { (state.step + 1) / OnboardingSteps.COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                height = 8.dp,
            )

            when (state.step) {
                OnboardingSteps.BODY -> BodyStep(
                    state = state,
                    onFullNameChange = onFullNameChange,
                    onDateOfBirthChange = onDateOfBirthChange,
                    onSexChange = onSexChange,
                    onHeightChange = onHeightChange,
                    onWeightChange = onWeightChange,
                    onBloodTypeChange = onBloodTypeChange,
                )
                OnboardingSteps.ACTIVITY -> ActivityStep(
                    selected = state.activityLevel,
                    onSelect = onActivityChange,
                )
                OnboardingSteps.GOAL -> GoalTdeeStep(
                    state = state,
                    onGoalDirectionChange = onGoalDirectionChange,
                    onTargetWeightChange = onTargetWeightChange,
                )
                OnboardingSteps.CONTACTS -> EmergencyContactsStep(
                    contacts = state.contacts,
                    onContactChange = onContactChange,
                    onAddContact = onAddContact,
                    onRemoveContact = onRemoveContact,
                )
                else -> AppearanceStep(
                    selected = state.accentPreset,
                    dynamicColor = state.dynamicColor,
                    onSelect = onAccentChange,
                )
            }

            state.validationError?.let { code ->
                GlassBanner(
                    message = onboardingErrorText(code),
                    tone = GlassBannerTone.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.errorMessage?.let { msg ->
                GlassBanner(
                    message = msg.asString(),
                    tone = GlassBannerTone.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ScrollEdgeEffect(
            edge = ScrollEdge.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight + 24.dp),
        )

        // Two separate glass capsules float on the background (iOS 26 bottom toolbar), never glass inside a glass slab.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeightPx = it.height }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step > OnboardingSteps.BODY) {
                SecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = onPrevious,
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!OnboardingSteps.isLast(state.step)) {
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
            GlassTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = stringResource(R.string.onboarding_full_name),
                modifier = Modifier.fillMaxWidth(),
            )
            GlassTextField(
                value = state.dateOfBirth,
                onValueChange = onDateOfBirthChange,
                label = stringResource(R.string.onboarding_dob_label),
                supportingText = stringResource(R.string.onboarding_dob_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.onboarding_gender_label), style = MaterialTheme.typography.labelLarge)
            GlassSegmentedControl(
                options = BiologicalSex.entries.map { sexLabel(it) },
                selectedIndex = BiologicalSex.entries.indexOf(state.sex).coerceAtLeast(0),
                onSelect = { onSexChange(BiologicalSex.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(
                    value = state.heightCm,
                    onValueChange = onHeightChange,
                    label = stringResource(R.string.onboarding_height_cm),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                GlassTextField(
                    value = state.weightKg,
                    onValueChange = onWeightChange,
                    label = stringResource(R.string.onboarding_weight_kg),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            GlassTextField(
                value = state.bloodType,
                onValueChange = onBloodTypeChange,
                label = stringResource(R.string.onboarding_blood_type_optional),
                placeholder = "O+, A-, AB+...",
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
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.selected = isSelected },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

    GlassSegmentedControl(
        options = GoalDirection.entries.map { goalLabel(it) },
        selectedIndex = GoalDirection.entries.indexOf(state.goalDirection).coerceAtLeast(0),
        onSelect = { onGoalDirectionChange(GoalDirection.entries[it]) },
        modifier = Modifier.fillMaxWidth(),
    )

    GlassTextField(
        value = state.targetWeightKg,
        onValueChange = onTargetWeightChange,
        label = stringResource(R.string.onboarding_target_weight_kg),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        GlassIconButton(
                            onClick = { onRemoveContact(index) },
                            contentDescription = stringResource(R.string.action_delete),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                GlassTextField(
                    value = contact.name,
                    onValueChange = { onContactChange(index, contact.copy(name = it)) },
                    label = stringResource(R.string.onboarding_contact_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassTextField(
                    value = contact.relationship,
                    onValueChange = { onContactChange(index, contact.copy(relationship = it)) },
                    label = stringResource(R.string.onboarding_contact_relationship),
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassTextField(
                    value = contact.phone,
                    onValueChange = { onContactChange(index, contact.copy(phone = it)) },
                    label = stringResource(R.string.onboarding_contact_phone),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassTextField(
                    value = contact.email,
                    onValueChange = { onContactChange(index, contact.copy(email = it)) },
                    label = stringResource(R.string.onboarding_contact_email_optional),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (contacts.size < 5) {
        SecondaryButton(
            text = stringResource(R.string.onboarding_add_contact),
            onClick = onAddContact,
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppearanceStep(
    selected: AccentPreset,
    dynamicColor: Boolean,
    onSelect: (AccentPreset) -> Unit,
) {
    Text(
        stringResource(R.string.onboarding_appearance_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(R.string.onboarding_appearance_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_accent_label), style = MaterialTheme.typography.titleSmall)
            AccentPicker(selected = selected, onSelect = onSelect, enabled = !dynamicColor)
            if (dynamicColor) {
                Text(
                    text = stringResource(R.string.settings_accent_dynamic_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
