package vn.uit.healthos.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R

/** A thin bar shown while a tab refreshes in the background over content it already shows. */
@Composable
fun BackgroundRefreshBar(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val label = stringResource(R.string.state_loading)
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .semantics { contentDescription = label },
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        )
    }
}

/** Grey blocks standing in for cards during a tab's first ever load, instead of a spinner. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 3,
    itemHeight: Dp = 72.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val label = stringResource(R.string.state_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    // Read in the draw phase so the pulse never recomposes the list.
                    .graphicsLayer { alpha = pulse.value }
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
        }
    }
}
