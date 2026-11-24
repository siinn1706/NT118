package vn.uit.healthos.feature.profile

import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.annotation.StringRes
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
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.ui.UiText
import vn.uit.healthos.core.ui.asString

@Composable
fun EmergencyContactsScreen(
    state: EmergencyContactsUiState,
    onContactChange: (Int, EmergencyContactInput) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Int) -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(
        title = stringResource(R.string.profile_emergency_contacts_section),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            !state.loaded && state.loadError != null ->
                ErrorState(message = state.loadError.asString(), onRetry = onRetryLoad, modifier = contentModifier)
            !state.loaded -> LoadingState(modifier = contentModifier)
            else -> ContactsForm(
                state = state,
                onContactChange = onContactChange,
                onAddContact = onAddContact,
                onRemoveContact = onRemoveContact,
                onSave = onSave,
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ContactsForm(
    state: EmergencyContactsUiState,
    onContactChange: (Int, EmergencyContactInput) -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: (Int) -> Unit,
    onSave: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_contacts_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.contacts.forEachIndexed { index, contact ->
            ContactCard(
                index = index,
                contact = contact,
                errors = if (state.showFieldErrors && !contact.isBlank) contact.validate() else EmergencyContactErrors(),
                enabled = !state.saving,
                onChange = { onContactChange(index, it) },
                onRemove = { onRemoveContact(index) },
            )
        }

        if (state.contacts.size < MAX_EMERGENCY_CONTACTS) {
            SecondaryButton(
                text = stringResource(R.string.profile_contacts_add_action),
                onClick = onAddContact,
                enabled = !state.saving,
                leadingIcon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.profile_contacts_max_hint, MAX_EMERGENCY_CONTACTS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.contacts.all { it.isBlank }) {
            Text(
                text = stringResource(R.string.profile_contacts_none_on_save),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.saveError?.let { ErrorBanner(it) }

        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ContactCard(
    index: Int,
    contact: EmergencyContactInput,
    errors: EmergencyContactErrors,
    enabled: Boolean,
    onChange: (EmergencyContactInput) -> Unit,
    onRemove: () -> Unit,
) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_contact_number, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                )
                GlassIconButton(
                    onClick = onRemove,
                    contentDescription = stringResource(R.string.action_delete),
                    enabled = enabled,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            ContactField(
                value = contact.name,
                onValueChange = { onChange(contact.copy(name = it)) },
                label = R.string.onboarding_contact_name,
                error = errors.name,
                enabled = enabled,
            )
            ContactField(
                value = contact.relationship,
                onValueChange = { onChange(contact.copy(relationship = it)) },
                label = R.string.onboarding_contact_relationship,
                error = errors.relationship,
                enabled = enabled,
            )
            ContactField(
                value = contact.phone,
                onValueChange = { onChange(contact.copy(phone = it)) },
                label = R.string.onboarding_contact_phone,
                error = errors.phone,
                invalidMessage = R.string.profile_contact_error_phone,
                keyboardType = KeyboardType.Phone,
                enabled = enabled,
            )
            ContactField(
                value = contact.email,
                onValueChange = { onChange(contact.copy(email = it)) },
                label = R.string.onboarding_contact_email_optional,
                error = errors.email,
                invalidMessage = R.string.profile_contact_error_email,
                keyboardType = KeyboardType.Email,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ContactField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: ContactFieldError?,
    enabled: Boolean,
    @StringRes invalidMessage: Int = R.string.profile_contact_error_required,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val errorText = error?.let {
        stringResource(
            when (it) {
                ContactFieldError.REQUIRED -> R.string.profile_contact_error_required
                ContactFieldError.TOO_LONG -> R.string.profile_contact_error_too_long
                ContactFieldError.INVALID -> invalidMessage
            },
        )
    }
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        errorText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorBanner(message: UiText) {
    GlassBanner(
        message = message.asString(),
        tone = GlassBannerTone.Error,
        modifier = Modifier.fillMaxWidth(),
    )
}
