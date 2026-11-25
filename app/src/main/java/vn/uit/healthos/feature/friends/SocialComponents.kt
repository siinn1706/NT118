package vn.uit.healthos.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors

/** Round initial badge standing in for a profile photo; [online] adds the presence dot. Decorative for TalkBack. */
@Composable
internal fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    online: Boolean = false,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(modifier.size(size).clearAndSetSemantics {}) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(LocalVitalColors.current.good),
            )
        }
    }
}

/** Glass pull-to-refresh indicator: a small Control-glass disc with a spinner that follows the pull. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.GlassRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, top: Dp) {
    val visible = refreshing || state.distanceFraction > 0f
    if (!visible) return
    val label = stringResource(R.string.social_refreshing)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = top)
            .graphicsLayer {
                val pull = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                alpha = pull
                translationY = (pull - 1f) * 24.dp.toPx()
            }
            .size(44.dp)
            .liquidGlass(shape = CircleShape, style = GlassStyle.Control),
        contentAlignment = Alignment.Center,
    ) {
        GlassSpinner(size = 20.dp, contentDescription = if (refreshing) label else null)
    }
}

/** Centered spinner for first loads, announced to TalkBack. */
@Composable
internal fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        GlassSpinner(size = 32.dp, contentDescription = stringResource(R.string.state_loading))
    }
}
