package vn.uit.healthos.feature.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.LargeTitleHeader
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.LocalGlassBackdrops
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.ScrollEdge
import vn.uit.healthos.core.designsystem.glass.ScrollEdgeEffect
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.ui.asString

/**
 * The backend stores chat images but only sends message text to the model, so an attached photo would be
 * silently ignored. The entry point stays hidden until the backend forwards images.
 */
private const val IMAGE_ATTACH_ENABLED = false

/** How far the bottom scroll edge reaches above the floating composer. */
private val ComposerEdgeFade = 24.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onRetryLast: () -> Unit,
    onLoadMore: () -> Unit,
    onAttachImage: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onOpenQuickReminder: (String) -> Unit,
    onUpdateReminderDraft: (ReminderDraftUi) -> Unit,
    onDismissReminderDraft: () -> Unit,
    onSaveReminder: () -> Unit,
    onOpenSafety: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var isListening by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<Int?>(null) }
    // Dictation appends to what was already typed instead of replacing it.
    var voiceBase by remember { mutableStateOf("") }

    val voiceOutput = remember(context) { VoiceOutput(context) }
    val voiceInput = remember(context) {
        VoiceInput(
            context = context,
            onPartialText = { partial -> onInputChange(voiceBase + partial) },
            onFinalText = { finalText -> onInputChange(voiceBase + finalText) },
            onListeningChanged = { listening -> isListening = listening },
            onError = { err -> voiceError = err },
        )
    }

    // The reply stream belongs to the ViewModel, so leaving the composition (rotation, tab switch) does not stop it.
    DisposableEffect(Unit) {
        onDispose {
            voiceInput.cancel()
            voiceOutput.shutdown()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceBase = state.inputText.trimEnd().let { if (it.isEmpty()) it else "$it " }
            voiceInput.startListening()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onAttachImage(uri)
    }

    ChatAutoScrollEffect(listState = listState, messages = state.messages)

    state.reminderDraft?.let { draft ->
        QuickReminderDialog(
            draft = draft,
            saving = state.savingReminder,
            onUpdate = onUpdateReminderDraft,
            onDismiss = onDismissReminderDraft,
            onSave = onSaveReminder,
        )
    }

    if (state.loading && state.messages.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }
    if (state.conversationId == null && state.errorMessage != null) {
        ErrorState(message = state.errorMessage.asString(), onRetry = onReload, modifier = modifier)
        return
    }

    val glass = LocalGlassBackdrops.current
    val messagesLayer = rememberLayerBackdrop()
    val composerBackdrop: Backdrop? = glass?.let { rememberCombinedBackdrop(it.background, messagesLayer) }
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val composerHeight = with(density) { composerHeightPx.toDp() }
    val bottomInset = if (WindowInsets.isImeVisible) 0.dp else LocalBottomChromeInset.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LargeTitleHeader(
            title = stringResource(R.string.nav_chat),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.chat_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        if (state.emergencyBannerVisible) {
            EmergencyBanner(
                onCall115 = {
                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:115"))
                    runCatching { context.startActivity(dial) }
                },
                onOpenSafety = onOpenSafety,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The messages are recorded as a layer so the floating composer refracts what scrolls beneath it.
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = composerHeight + bottomInset + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glass != null) Modifier.layerBackdrop(messagesLayer) else Modifier),
            ) {
                if (state.hasMoreHistory) {
                    item(key = "load_more") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) {
                                GlassSpinner(contentDescription = stringResource(R.string.chat_loading_older))
                            } else {
                                SecondaryButton(
                                    text = stringResource(R.string.chat_load_older),
                                    onClick = onLoadMore,
                                )
                            }
                        }
                    }
                }

                if (state.messages.isEmpty()) {
                    item(key = "welcome_empty") {
                        Text(
                            text = stringResource(R.string.chat_empty_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                        )
                    }
                }

                items(state.messages, key = { it.id }) { msg ->
                    val speaking = speakingMessageId == msg.id
                    MessageBubble(
                        message = msg,
                        isSpeaking = speaking,
                        onToggleSpeak = {
                            if (speaking) {
                                voiceOutput.stop()
                                speakingMessageId = null
                            } else {
                                voiceOutput.speak(msg.content)
                                speakingMessageId = msg.id
                            }
                        },
                        onCreateReminder = { onOpenQuickReminder(msg.content) },
                    )
                }
            }

            ScrollEdgeEffect(
                edge = ScrollEdge.Top,
                backdrop = composerBackdrop,
                visibility = { if (listState.canScrollBackward) 1f else 0f },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(ComposerEdgeFade),
            )
            ScrollEdgeEffect(
                edge = ScrollEdge.Bottom,
                backdrop = composerBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(composerHeight + bottomInset + ComposerEdgeFade),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { composerHeightPx = it.height }
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val err = state.errorMessage?.asString()
                    ?: voiceError?.let { stringResource(it) }
                    ?: if (state.retryAvailable) stringResource(R.string.chat_stream_failed) else null
                if (err != null) {
                    GlassBanner(
                        message = err,
                        tone = GlassBannerTone.Error,
                        actionLabel = if (state.retryAvailable && !state.isStreaming) {
                            stringResource(R.string.action_retry)
                        } else {
                            null
                        },
                        onAction = if (state.retryAvailable && !state.isStreaming) onRetryLast else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.infoMessage != null) {
                    GlassBanner(
                        message = state.infoMessage.asString(),
                        tone = GlassBannerTone.Success,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Composer(
                    state = state,
                    isListening = isListening,
                    backdrop = composerBackdrop,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onSendPrompt = onSendPrompt,
                    onStopStreaming = onStopStreaming,
                    onClearAttachment = onClearAttachment,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onToggleVoice = {
                        voiceError = null
                        if (isListening) {
                            voiceInput.stopListening()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                voiceBase = state.inputText.trimEnd().let { if (it.isEmpty()) it else "$it " }
                                voiceInput.startListening()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Keeps the list where the reader is: loading older history never jumps to the end, a sent message always shows,
 * and a reply (and its streaming text) is followed only while the reader is already at the bottom.
 */
@Composable
private fun ChatAutoScrollEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    messages: List<ChatMessageUi>,
) {
    var followingBottom by remember { mutableStateOf(true) }
    var previous by remember { mutableStateOf<ChatAutoScroll.Snapshot?>(null) }

    LaunchedEffect(listState) {
        // Only the reader's own scrolling changes whether the list follows new content.
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling) followingBottom = !canScrollForward
            }
    }

    val snapshot = ChatAutoScroll.snapshotOf(messages)
    LaunchedEffect(snapshot) {
        val scroll = ChatAutoScroll.shouldScrollToEnd(previous, snapshot, followingBottom)
        previous = snapshot
        if (scroll) {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                listState.scrollToItem(lastIndex)
                // A tall last bubble (a long or growing reply) is shown down to its end.
                listState.scrollBy(Float.MAX_VALUE / 4)
            }
            followingBottom = true
        }
    }
}

@Composable
private fun EmergencyBanner(onCall115: () -> Unit, onOpenSafety: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassBanner(
            message = stringResource(R.string.chat_emergency_banner_text),
            tone = GlassBannerTone.Error,
            actionLabel = stringResource(R.string.chat_call_115),
            onAction = onCall115,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.chat_open_sos),
            onClick = onOpenSafety,
            leadingIcon = Icons.Rounded.Shield,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Floating Liquid Glass composer. The glass panel is the text field (iOS Messages style), so the field itself is
 * borderless; controls inside are insets ([LocalInsideGlass]).
 */
@Composable
private fun Composer(
    state: ChatUiState,
    isListening: Boolean,
    backdrop: Backdrop?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onClearAttachment: () -> Unit,
    onPickPhoto: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassTokens.CardShape, style = GlassStyle.Chrome, backdrop = backdrop),
    ) {
        CompositionLocalProvider(LocalInsideGlass provides true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestedPrompts(onSelectPrompt = onSendPrompt)

                state.pendingAttachment?.let { att ->
                    Row(
                        modifier = Modifier
                            .glassInset(Capsule(), tint = scheme.secondaryContainer.copy(alpha = 0.5f))
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = att.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSecondaryContainer,
                        )
                        GlassIconButton(
                            onClick = onClearAttachment,
                            contentDescription = stringResource(R.string.action_delete),
                        ) { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (IMAGE_ATTACH_ENABLED) {
                        GlassIconButton(
                            onClick = onPickPhoto,
                            contentDescription = stringResource(R.string.chat_attach_photo),
                            enabled = !state.uploadingImage && !state.isStreaming,
                        ) {
                            if (state.uploadingImage) {
                                GlassSpinner(size = 20.dp)
                            } else {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                            }
                        }
                    }

                    ComposerField(
                        value = state.inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                    )

                    GlassIconButton(
                        onClick = onToggleVoice,
                        contentDescription = stringResource(R.string.chat_voice_input),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (isListening) scheme.tertiary else scheme.onSurfaceVariant,
                        )
                    }

                    if (state.isStreaming) {
                        GlassIconButton(
                            onClick = onStopStreaming,
                            contentDescription = stringResource(R.string.chat_stop_stream),
                        ) { Icon(Icons.Rounded.Stop, contentDescription = null, tint = scheme.error) }
                    } else {
                        SendButton(
                            enabled = state.inputText.isNotBlank() || state.pendingAttachment != null,
                            onClick = onSend,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.chat_input_label)
    val placeholder = stringResource(R.string.chat_input_placeholder)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        maxLines = 4,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        modifier = modifier
            .heightIn(min = GlassTokens.MinTouchTarget)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

/** Solid accent circle (not glass, so it never stacks on the composer glass) with a 48dp target. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.chat_send_action)
    Box(
        modifier = Modifier
            .size(GlassTokens.MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                tint = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val ReminderTypes = listOf(
    "medicine" to R.string.reminder_type_medicine,
    "exercise" to R.string.reminder_type_exercise,
    "appointment" to R.string.reminder_type_appointment,
)

@Composable
private fun QuickReminderDialog(
    draft: ReminderDraftUi,
    saving: Boolean,
    onUpdate: (ReminderDraftUi) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_quick_reminder_title),
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = onSave,
        dismissLabel = stringResource(R.string.action_cancel),
        confirmEnabled = !saving && draft.title.isNotBlank(),
    ) {
        GlassTextField(
            value = draft.title,
            onValueChange = { onUpdate(draft.copy(title = it)) },
            label = stringResource(R.string.chat_reminder_name_label),
        )
        GlassTextField(
            value = draft.time,
            onValueChange = { onUpdate(draft.copy(time = it.take(5))) },
            label = stringResource(R.string.chat_reminder_time_label),
        )
        Text(
            text = stringResource(R.string.chat_reminder_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
            options = ReminderTypes.map { stringResource(it.second) },
            selectedIndex = ReminderTypes.indexOfFirst { it.first == draft.type }.coerceAtLeast(0),
            onSelect = { onUpdate(draft.copy(type = ReminderTypes[it].first)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
