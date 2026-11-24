package vn.uit.healthos.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.core.designsystem.glass.GlassOverlayEntry
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.LocalContainerRadius
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.LocalGlassOverlayHost
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.concentricRadius
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion

private val DialogRadius = 34.dp
private val DialogPadding = 24.dp
private val DialogShape = RoundedRectangle(DialogRadius)

/** Frosted like a card, with Liquid Glass refraction at the rim and a deep shadow so it floats over everything. */
private val DialogGlass = GlassStyle.Material.copy(
    lensHeight = 20.dp,
    lensAmount = 28.dp,
    depthEffect = true,
    shadowRadius = 32.dp,
)

/**
 * Alert dialog on Liquid Glass, shown while it is in the composition (`if (show) GlassDialog(...)`, like AlertDialog).
 *
 * It is drawn in the same window as an overlay of the nearest GlassRoot (a separate dialog window could not sample the
 * app's backdrop): a scrim, then a frosted glass panel with refracting edges that scales in. The panel holds an
 * optional [icon], the [title], an optional [message], optional extra [content] (for example a GlassTextField) and the
 * buttons: [dismissLabel] (optional, left) and [confirmLabel] (right, prominent; error-coloured when [destructive]).
 *
 * Behaves as a modal dialog: system back and a tap on the scrim (if [dismissOnScrimClick]) call [onDismissRequest];
 * everything behind it is hidden from accessibility services; the panel announces [title] as its pane title and
 * takes keyboard focus; it avoids the keyboard and system bars. Outside a GlassRoot it falls back to a regular dialog
 * window with an opaque panel.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    icon: ImageVector? = null,
    dismissOnScrimClick: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val host = LocalGlassOverlayHost.current
    val panel: @Composable () -> Unit = {
        DialogPanel(title, confirmLabel, onConfirm, modifier, message, dismissLabel, onDismiss, destructive, confirmEnabled, icon, content)
    }
    if (host == null) {
        Dialog(onDismissRequest = onDismissRequest) { panel() }
        return
    }
    val reducedMotion = LocalReducedMotion.current
    val overlay: @Composable AnimatedVisibilityScope.() -> Unit = {
        DialogOverlay(onDismissRequest, dismissOnScrimClick, reducedMotion, panel)
    }
    val entry = remember { GlassOverlayEntry(overlay) }
    SideEffect { entry.content = overlay }
    DisposableEffect(host, entry) {
        host.show(entry)
        onDispose { host.hide(entry) }
    }
}

@Composable
private fun AnimatedVisibilityScope.DialogOverlay(
    onDismissRequest: () -> Unit,
    dismissOnScrimClick: Boolean,
    reducedMotion: Boolean,
    panel: @Composable () -> Unit,
) {
    // While fading out the dialog no longer reacts, so a second tap cannot fire an action twice.
    val active = transition.targetState == EnterExitState.Visible
    val dark = LocalGlassColors.current.base.luminance() < 0.5f
    BackHandler(enabled = active) { onDismissRequest() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.48f else 0.28f))
            .pointerInput(active, dismissOnScrimClick) {
                // The scrim swallows every touch so nothing behind the dialog reacts.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (up != null && active && dismissOnScrimClick) onDismissRequest()
                }
            }
            .clearAndSetSemantics {},
    )
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.animateEnterExit(
                enter = if (reducedMotion) EnterTransition.None else scaleIn(initialScale = 0.9f),
                exit = if (reducedMotion) ExitTransition.None else scaleOut(targetScale = 0.96f),
            ),
        ) {
            panel()
        }
    }
}

@Composable
private fun DialogPanel(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier,
    message: String?,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    destructive: Boolean,
    confirmEnabled: Boolean,
    icon: ImageVector?,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
    CompositionLocalProvider(
        LocalContentColor provides scheme.onSurface,
        LocalInsideGlass provides true,
        LocalContainerRadius provides concentricRadius(DialogRadius, DialogPadding),
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .liquidGlass(DialogShape, DialogGlass)
                .semantics { paneTitle = title }
                .focusRequester(focusRequester)
                .focusable()
                .verticalScroll(rememberScrollState())
                .padding(DialogPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (destructive) scheme.error else scheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (content != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (dismissLabel != null) {
                    SecondaryButton(text = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
                }
                PrimaryButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    destructive = destructive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
