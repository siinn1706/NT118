package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.glass.rememberPressScale

private val TopBarHeight = 56.dp

/**
 * Scaffold for pushed (non-tab) screens in the iOS 26 style: a floating Liquid Glass back button and an inline title
 * directly on the ambient backdrop. The screen content is recorded as a layer so the back button refracts it.
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

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = { GlassTopBar(title = title, onBack = onBack, backdrop = chrome) },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (parent != null) Modifier.layerBackdrop(screenLayer) else Modifier)) {
            content(padding)
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
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
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

/** 44dp circular Liquid Glass button for toolbar actions (back, close, more). */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrops.current?.chrome,
    content: @Composable () -> Unit,
) {
    val shape = Capsule()
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, pressedScale = 0.9f)
    Box(
        modifier = modifier
            .size(GlassTokens.NavButtonSize)
            .liquidGlass(
                shape = shape,
                style = GlassStyle.Chrome,
                backdrop = backdrop,
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

