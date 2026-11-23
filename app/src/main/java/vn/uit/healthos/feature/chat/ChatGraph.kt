package vn.uit.healthos.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.navigation.ChatRoute

fun NavGraphBuilder.chatGraph(
    onOpenSafety: () -> Unit,
) {
    composable<ChatRoute> {
        val viewModel: ChatViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current

        ChatScreen(
            state = state,
            onInputChange = viewModel::onInputChange,
            onSend = { viewModel.sendPrompt() },
            onSendPrompt = { prompt -> viewModel.sendPrompt(overrideText = prompt) },
            onStopStreaming = viewModel::stopStreaming,
            onRetryLast = viewModel::retryLastFailed,
            onLoadMore = viewModel::loadMoreHistory,
            onAttachImage = { uri -> viewModel.attachPhoto(context, uri) },
            onClearAttachment = viewModel::clearAttachment,
            onOpenQuickReminder = viewModel::openQuickReminder,
            onUpdateReminderDraft = viewModel::updateReminderDraft,
            onDismissReminderDraft = viewModel::dismissReminderDraft,
            onSaveReminder = viewModel::saveQuickReminder,
            onOpenSafety = onOpenSafety,
            onReload = viewModel::loadConversation,
        )
    }
}
