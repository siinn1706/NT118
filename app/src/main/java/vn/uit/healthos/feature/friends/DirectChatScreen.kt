package vn.uit.healthos.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.ui.asString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DirectChatScreen(
    state: DirectChatUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onRetry: (DmMessageUi) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(title = state.peerName, onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            ChatStatusHeader(state, onReload)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.messages.isEmpty() && state.loading -> LoadingBlock(Modifier.align(Alignment.Center))
                    state.messages.isEmpty() -> Text(
                        text = stringResource(R.string.dm_empty, state.peerName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> MessageList(state, onLoadOlder, onRetry)
                }
            }
            Composer(
                input = state.input,
                peerName = state.peerName,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun ChatStatusHeader(state: DirectChatUiState, onReload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val status = when {
            state.peerTyping -> stringResource(R.string.dm_typing, state.peerName)
            state.peerOnline == true -> stringResource(R.string.friends_online)
            state.peerOnline == false -> stringResource(R.string.friends_offline)
            else -> null
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!state.connected) {
            GlassBanner(message = stringResource(R.string.dm_offline_banner), tone = GlassBannerTone.Warning)
        }
        state.loadError?.let { error ->
            GlassBanner(
                message = error.asString(),
                tone = GlassBannerTone.Error,
                actionLabel = stringResource(R.string.action_retry),
                onAction = onReload,
            )
        }
    }
}

@Composable
private fun MessageList(state: DirectChatUiState, onLoadOlder: () -> Unit, onRetry: (DmMessageUi) -> Unit) {
    val listState = rememberLazyListState()
    val newestFirst = remember(state.messages) { state.messages.asReversed() }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

    // Follow new messages while the newest one is on screen.
    val newestId = newestFirst.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    // Load the next older page when the oldest loaded message comes into view.
    val nearOldest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_OLDER_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearOldest }.distinctUntilChanged().filter { it }.collect { onLoadOlder() }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        items(newestFirst, key = { it.id }) { message ->
            DmBubble(
                message = message,
                peerName = state.peerName,
                time = timeFormat.format(Instant.ofEpochMilli(message.createdAtMs)),
                onRetry = { onRetry(message) },
            )
        }
        if (state.hasMore) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingOlder) {
                        GlassSpinner(size = 24.dp, contentDescription = stringResource(R.string.state_loading))
                    } else {
                        PlainButton(text = stringResource(R.string.dm_load_older), onClick = onLoadOlder)
                    }
                }
            }
        }
    }
}

@Composable
private fun DmBubble(message: DmMessageUi, peerName: String, time: String, onRetry: () -> Unit) {
    val mine = message.isMine
    val failed = message.status == DmStatus.FAILED
    val scheme = MaterialTheme.colorScheme
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val statusText = when (message.status) {
        DmStatus.PENDING -> stringResource(R.string.dm_status_pending)
        DmStatus.FAILED -> stringResource(R.string.dm_status_failed)
        else -> null
    }
    val spoken = buildString {
        append(
            if (mine) {
                stringResource(R.string.dm_message_mine, time, message.content)
            } else {
                stringResource(R.string.dm_message_peer, peerName, time, message.content)
            },
        )
        if (statusText != null) append(". ").append(statusText)
    }
    val retryLabel = stringResource(R.string.action_retry)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                if (failed) onClick(label = retryLabel) { onRetry(); true }
            },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .liquidGlass(
                    shape = shape,
                    style = if (mine) GlassStyle.Control else GlassStyle.Material,
                    tint = if (mine) scheme.primary.copy(alpha = 0.94f) else Color.Unspecified,
                )
                .then(if (failed) Modifier.clickable(role = Role.Button, onClick = onRetry) else Modifier),
        ) {
            CompositionLocalProvider(LocalContentColor provides if (mine) scheme.onPrimary else scheme.onSurface) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        Text(
            text = if (statusText != null) "$time · $statusText" else time,
            style = MaterialTheme.typography.labelSmall,
            color = if (failed) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Floating Liquid Glass composer, like the AI chat's: an inset text field and a prominent send button. */
@Composable
private fun Composer(input: String, peerName: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val canSend = input.isNotBlank()
    val fieldLabel = stringResource(R.string.dm_input_label)
    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp + LocalBottomChromeInset.current)
            .liquidGlass(shape = GlassTokens.CardShape, style = GlassStyle.Chrome),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = GlassTokens.MinTouchTarget)
                    .semantics { contentDescription = fieldLabel },
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .glassInset(RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) {
                            // One line: a wrapping hint makes the empty field look taller than what it holds.
                            Text(
                                text = stringResource(R.string.dm_input_placeholder, peerName),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(GlassTokens.MinTouchTarget)
                    .clip(CircleShape)
                    .background(if (canSend) scheme.primary else scheme.onSurface.copy(alpha = 0.12f))
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.dm_send),
                    tint = if (canSend) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private const val LOAD_OLDER_THRESHOLD = 3
