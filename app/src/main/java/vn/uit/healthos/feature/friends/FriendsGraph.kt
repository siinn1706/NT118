package vn.uit.healthos.feature.friends

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.uit.healthos.navigation.FriendsRoute

/**
 * A direct conversation pushed from a friend row. Property names double as the [DirectChatViewModel] saved-state
 * keys, so the ViewModel reads them without depending on navigation.
 */
@Serializable
internal data class DirectChatDestination(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
)

/** Friends list ([FriendsRoute], a pushed screen) and the direct chats opened from it. */
fun NavGraphBuilder.friendsGraph(navController: NavController) {
    composable<FriendsRoute> {
        val viewModel: FriendsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is FriendsEvent.OpenChat -> navController.navigate(
                        DirectChatDestination(event.conversationId, event.peerId, event.peerName),
                    ) { launchSingleTop = true }
                }
            }
        }
        FriendsScreen(
            state = state,
            actions = FriendsActions(
                onBack = { navController.navigateUp() },
                onRefresh = viewModel::refresh,
                onSelectSegment = viewModel::selectSegment,
                onOpenChat = viewModel::openChat,
                onRequestUnfriend = viewModel::requestUnfriend,
                onConfirmUnfriend = viewModel::confirmUnfriend,
                onDismissUnfriend = viewModel::dismissUnfriend,
                onAccept = viewModel::accept,
                onDecline = viewModel::decline,
                onCancelRequest = viewModel::cancel,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::searchNow,
                onSendRequest = viewModel::sendRequest,
                onDismissNotice = viewModel::dismissNotice,
            ),
        )
    }
    composable<DirectChatDestination> {
        val viewModel: DirectChatViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        DirectChatScreen(
            state = state,
            onBack = { navController.navigateUp() },
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onLoadOlder = viewModel::loadOlder,
            onRetry = viewModel::retry,
            onReload = viewModel::reload,
        )
    }
}
