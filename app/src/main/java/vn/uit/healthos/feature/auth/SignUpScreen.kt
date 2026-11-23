package vn.uit.healthos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.ui.asString

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    DetailScaffold(
        title = stringResource(R.string.auth_sign_up_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_sign_up_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text(stringResource(R.string.auth_name_label)) },
                        singleLine = true,
                        isError = state.fieldErrors.containsKey("name"),
                        supportingText = state.fieldErrors["name"]?.let { { Text(authFieldErrorText(it)) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.auth_username_label)) },
                        singleLine = true,
                        isError = state.fieldErrors.containsKey("username") || state.usernameAvailable == false,
                        supportingText = {
                            when {
                                state.fieldErrors.containsKey("username") ->
                                    Text(authFieldErrorText(state.fieldErrors.getValue("username")))
                                state.usernameAvailable == true ->
                                    Text(
                                        text = stringResource(R.string.auth_available),
                                        color = LocalVitalColors.current.good,
                                    )
                                state.usernameAvailable == false ->
                                    Text(stringResource(R.string.auth_error_already_taken))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        singleLine = true,
                        isError = state.fieldErrors.containsKey("email") || state.emailAvailable == false,
                        supportingText = {
                            when {
                                state.fieldErrors.containsKey("email") ->
                                    Text(authFieldErrorText(state.fieldErrors.getValue("email")))
                                state.emailAvailable == true ->
                                    Text(
                                        text = stringResource(R.string.auth_available),
                                        color = LocalVitalColors.current.good,
                                    )
                                state.emailAvailable == false ->
                                    Text(stringResource(R.string.auth_error_already_taken))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.signUpPassword,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.auth_password_label)) },
                        singleLine = true,
                        isError = state.fieldErrors.containsKey("signUpPassword"),
                        supportingText = state.fieldErrors["signUpPassword"]?.let {
                            { Text(authFieldErrorText(it)) }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(R.string.auth_toggle_password),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PasswordRulesView(checklist = state.signUpPasswordChecklist)

                    OutlinedTextField(
                        value = state.signUpConfirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                        singleLine = true,
                        isError = state.fieldErrors.containsKey("signUpConfirmPassword"),
                        supportingText = state.fieldErrors["signUpConfirmPassword"]?.let {
                            { Text(authFieldErrorText(it)) }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.errorMessage?.let { AuthErrorBanner(message = it.asString()) }

                    PrimaryButton(
                        text = stringResource(R.string.auth_send_otp_action),
                        onClick = onSubmit,
                        loading = state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PasswordRulesView(checklist: PasswordChecklist, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PasswordRuleRow(checklist.minLength, stringResource(R.string.auth_pw_rule_length))
        PasswordRuleRow(checklist.hasUpper, stringResource(R.string.auth_pw_rule_upper))
        PasswordRuleRow(checklist.hasLower, stringResource(R.string.auth_pw_rule_lower))
        PasswordRuleRow(checklist.hasDigit, stringResource(R.string.auth_pw_rule_digit))
        PasswordRuleRow(checklist.hasSpecial, stringResource(R.string.auth_pw_rule_special))
    }
}

@Composable
private fun PasswordRuleRow(met: Boolean, text: String) {
    val tint = if (met) LocalVitalColors.current.good else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (met) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}
