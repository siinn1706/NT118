package vn.uit.healthos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.ui.asString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignInScreen(
    state: AuthUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onNavigateSignUp: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        VitalOrb(
            score = 0.85f,
            size = 112.dp,
            contentDescription = stringResource(R.string.app_name),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.auth_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.auth_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(
                    value = state.identifier,
                    onValueChange = onIdentifierChange,
                    label = stringResource(R.string.auth_identifier_label),
                    errorText = state.fieldErrors["identifier"]?.let { authFieldErrorText(it) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                GlassTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.auth_password_label),
                    errorText = state.fieldErrors["password"]?.let { authFieldErrorText(it) },
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PlainButton(
                        text = stringResource(R.string.auth_forgot_password),
                        onClick = onNavigateForgotPassword,
                    )
                }

                state.errorMessage?.let { error ->
                    AuthErrorBanner(message = error.asString())
                }

                PrimaryButton(
                    text = stringResource(R.string.auth_sign_in_action),
                    onClick = onSignIn,
                    loading = state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // The sign-up link wraps onto its own centred line when it does not fit beside the question.
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.auth_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlainButton(
                text = stringResource(R.string.auth_sign_up_link),
                onClick = onNavigateSignUp,
            )
        }
    }
}

/** Auth errors inside the form card: a glass banner, which turns into an inset there so glass never stacks. */
@Composable
internal fun AuthErrorBanner(message: String, modifier: Modifier = Modifier) {
    GlassBanner(
        message = message,
        tone = GlassBannerTone.Error,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun authFieldErrorText(code: String): String = when (code) {
    "EMPTY" -> stringResource(R.string.auth_error_required)
    "TOO_SHORT" -> stringResource(R.string.auth_error_username_short)
    "TAKEN" -> stringResource(R.string.auth_error_already_taken)
    "INVALID_EMAIL" -> stringResource(R.string.auth_error_invalid_email)
    "WEAK_PASSWORD" -> stringResource(R.string.auth_error_weak_password)
    "MISMATCH" -> stringResource(R.string.auth_error_password_mismatch)
    "INVALID_OTP" -> stringResource(R.string.auth_error_invalid_otp)
    "INVALID_CODE" -> stringResource(R.string.auth_error_invalid_mfa)
    else -> stringResource(R.string.auth_error_invalid_value)
}
