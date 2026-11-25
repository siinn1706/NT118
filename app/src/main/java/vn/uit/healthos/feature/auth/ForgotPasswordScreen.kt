package vn.uit.healthos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.ui.asString

@Composable
fun ForgotPasswordScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmitReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(
        title = stringResource(R.string.auth_forgot_password_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.resetOtpVerified) {
                Text(
                    text = stringResource(R.string.auth_forgot_email_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTextField(
                            value = state.resetEmail,
                            onValueChange = onEmailChange,
                            label = stringResource(R.string.auth_email_label),
                            errorText = state.fieldErrors["resetEmail"]?.let { authFieldErrorText(it) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onRequestOtp() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.errorMessage?.let { AuthErrorBanner(message = it.asString()) }
                        PrimaryButton(
                            text = stringResource(R.string.auth_send_otp_action),
                            onClick = onRequestOtp,
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.auth_reset_new_pw_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTextField(
                            value = state.resetNewPassword,
                            onValueChange = onNewPasswordChange,
                            label = stringResource(R.string.auth_new_password_label),
                            errorText = state.fieldErrors["resetNewPassword"]?.let { authFieldErrorText(it) },
                            isPassword = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PasswordRulesView(checklist = state.resetPasswordChecklist)
                        GlassTextField(
                            value = state.resetConfirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = stringResource(R.string.auth_confirm_password_label),
                            errorText = state.fieldErrors["resetConfirmPassword"]?.let { authFieldErrorText(it) },
                            isPassword = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmitReset() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.errorMessage?.let { AuthErrorBanner(message = it.asString()) }
                        PrimaryButton(
                            text = stringResource(R.string.auth_reset_password_action),
                            onClick = onSubmitReset,
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
