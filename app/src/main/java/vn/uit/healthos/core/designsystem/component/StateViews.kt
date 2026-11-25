package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors

/**
 * Section title with an optional text action. The action sits at the end of the title line when both fit, and wraps
 * under the title (aligned with it) when the title would otherwise have to break.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // The action stays beside the title only while the title fits on one line; otherwise it moves under it.
    AdaptiveRow(modifier = modifier.fillMaxWidth(), minFlexibleFraction = 1f, verticalSpacing = 0.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            PlainButton(text = actionLabel, onClick = onAction, contentPadding = 0.dp)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            message ?: stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

/** Temporary screen body for features that are not built yet. */
@Composable
fun FeaturePlaceholder(title: String, phase: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Construction,
            contentDescription = null,
            tint = LocalVitalColors.current.energy,
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.placeholder_coming_in_phase, phase),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
