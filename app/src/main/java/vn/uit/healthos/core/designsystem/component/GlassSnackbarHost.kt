package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.liquidGlass

private val SnackbarShape = RoundedRectangle(24.dp)

/**
 * Drop-in replacement for Material's SnackbarHost that renders each message as a floating Chrome glass pill (see
 * [GlassSnackbar]). Queueing, timing, swipe-free dismissal and TalkBack announcements come from SnackbarHost.
 */
@Composable
fun GlassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> GlassSnackbar(data) }
}

/**
 * One snackbar on Chrome glass: the message, the optional action as a tinted [PlainButton] and, when the visuals ask
 * for it, a close button. At most 600dp wide, at least 52dp tall, centred above the bottom chrome.
 */
@Composable
fun GlassSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val visuals = data.visuals
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalInsideGlass provides true,
    ) {
        Row(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .liquidGlass(SnackbarShape, GlassStyle.Chrome)
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            )
            val action = visuals.actionLabel
            if (action != null) PlainButton(text = action, onClick = data::performAction)
            if (visuals.withDismissAction) {
                GlassIconButton(onClick = data::dismiss, contentDescription = stringResource(R.string.action_close)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
