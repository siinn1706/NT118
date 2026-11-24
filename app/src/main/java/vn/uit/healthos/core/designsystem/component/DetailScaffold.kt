package vn.uit.healthos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.ScrollEdge
import vn.uit.healthos.core.designsystem.glass.ScrollEdgeEffect
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import vn.uit.healthos.core.designsystem.theme.VbMotion

private val TopBarHeight = 56.dp
private val EdgeFade = 24.dp

/**
 * Scaffold for pushed (non-tab) screens in the iOS 26 style: a floating Liquid Glass back button and an inline title
 * directly on the ambient backdrop. The screen content is recorded as a layer so the back button refracts it, and a
 * scroll edge effect (blur and fade) appears under the bar as soon as the content has scrolled, keeping the title
 * legible. Apply the [PaddingValues] as the scroll container's content padding to let content scroll under the bar.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val parent = LocalGlassBackdrops.current
    val screenLayer = rememberLayerBackdrop()
    val chrome: Backdrop? = parent?.let { rememberCombinedBackdrop(it.background, screenLayer) }

    // Distance scrolled from the top, tracked from nested scroll so it works for any scrolling child.
    val scrolled = remember { mutableFloatStateOf(0f) }
    val scrollTracker = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                scrolled.floatValue = (scrolled.floatValue - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    val contentUnderBar by remember { derivedStateOf { scrolled.floatValue > 0.5f } }
    val edgeAlpha = animateFloatAsState(
        targetValue = if (contentUnderBar) 1f else 0f,
        animationSpec = VbMotion.orSnap(LocalReducedMotion.current, VbMotion.enter()),
        label = "scrollEdge",
    )

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = { GlassTopBar(title = title, onBack = onBack, backdrop = chrome) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (parent != null) Modifier.layerBackdrop(screenLayer) else Modifier)
                    .nestedScroll(scrollTracker),
            ) {
                content(padding)
            }
            ScrollEdgeEffect(
                edge = ScrollEdge.Top,
                backdrop = chrome,
                visibility = { edgeAlpha.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(padding.calculateTopPadding() + EdgeFade),
            )
        }
    }
}

@Composable
private fun GlassTopBar(title: String, onBack: () -> Unit, backdrop: Backdrop?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TopBarHeight),
    ) {
        GlassIconButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.action_back),
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = null,
                modifier = Modifier.size(20.dp).padding(start = 4.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 68.dp)
                .semantics { heading() },
        )
    }
}
