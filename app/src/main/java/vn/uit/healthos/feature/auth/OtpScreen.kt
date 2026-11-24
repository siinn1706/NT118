package vn.uit.healthos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.ui.asString

@Composable
fun OtpScreen(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(
        title = stringResource(R.string.auth_otp_title),
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
            Text(
                text = stringResource(R.string.auth_otp_subtitle, state.otpEmail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.debugOtpHint?.let { devOtp ->
                GlassBanner(
                    message = stringResource(R.string.auth_otp_dev_hint, devOtp),
                    tone = GlassBannerTone.Info,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassTextField(
                        value = state.otpCode,
                        onValueChange = onCodeChange,
                        label = stringResource(R.string.auth_otp_code_label),
                        errorText = state.fieldErrors["otp"]?.let { authFieldErrorText(it) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onVerify() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.errorMessage?.let { AuthErrorBanner(message = it.asString()) }

                    PrimaryButton(
                        text = stringResource(R.string.auth_verify_action),
                        onClick = onVerify,
                        loading = state.loading,
                        enabled = state.otpCode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val label = if (state.otpResendSeconds > 0) {
                    stringResource(R.string.auth_resend_otp_wait, state.otpResendSeconds)
                } else {
                    stringResource(R.string.auth_resend_otp)
                }
                PlainButton(
                    text = label,
                    onClick = onResend,
                    enabled = state.otpResendSeconds == 0 && !state.loading,
                )
            }
        }
    }
}
