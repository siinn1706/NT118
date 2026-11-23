package vn.uit.healthos.feature.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.core.ui.asString

/**
 * The backend stores chat images but only sends message text to the model, so an attached photo would be
 * silently ignored. The entry point stays hidden until the backend forwards images.
 */
private const val IMAGE_ATTACH_ENABLED = false

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

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Header + AI disclaimer
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.nav_chat), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.chat_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Red Emergency Banner
        if (state.emergencyBannerVisible) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.chat_emergency_banner_text),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:115"))
                                runCatching { context.startActivity(dial) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.chat_call_115),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(onClick = onOpenSafety) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.chat_open_sos),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (state.hasMoreHistory) {
                item(key = "load_more") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = onLoadMore, enabled = !state.loadingMore) {
                            Text(stringResource(R.string.chat_load_older))
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

        // Error / info strip
        val err = state.errorMessage?.asString()
            ?: voiceError?.let { stringResource(it) }
            ?: if (state.retryAvailable) stringResource(R.string.chat_stream_failed) else null
        if (err != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.retryAvailable && !state.isStreaming) {
                        TextButton(onClick = onRetryLast) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        } else if (state.infoMessage != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.infoMessage.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Suggested prompts + Input composer
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestedPrompts(onSelectPrompt = onSendPrompt)

                state.pendingAttachment?.let { att ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = att.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            IconButton(onClick = onClearAttachment, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (IMAGE_ATTACH_ENABLED) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.uploadingImage && !state.isStreaming,
                        ) {
                            if (state.uploadingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Rounded.AttachFile,
                                    contentDescription = stringResource(R.string.chat_attach_photo),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = onInputChange,
                        placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
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
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.chat_voice_input),
                            tint = if (isListening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.isStreaming) {
                        IconButton(onClick = onStopStreaming) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.chat_stop_stream),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSend,
                            enabled = state.inputText.isNotBlank() || state.pendingAttachment != null,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.chat_send_action),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReminderDialog(
    draft: ReminderDraftUi,
    saving: Boolean,
    onUpdate: (ReminderDraftUi) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_quick_reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onUpdate(draft.copy(title = it)) },
                    label = { Text(stringResource(R.string.chat_reminder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.time,
                    onValueChange = { onUpdate(draft.copy(time = it.take(5))) },
                    label = { Text(stringResource(R.string.chat_reminder_time_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "medicine" to R.string.reminder_type_medicine,
                        "exercise" to R.string.reminder_type_exercise,
                        "appointment" to R.string.reminder_type_appointment,
                    ).forEach { (key, resId) ->
                        FilterChip(
                            selected = draft.type == key,
                            onClick = { onUpdate(draft.copy(type = key)) },
                            label = { Text(stringResource(resId)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !saving && draft.title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
