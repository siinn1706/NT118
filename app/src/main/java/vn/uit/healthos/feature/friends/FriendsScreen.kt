package vn.uit.healthos.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.ui.asString

/** Everything the friends screen can ask its ViewModel to do. */
data class FriendsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSelectSegment: (FriendsSegment) -> Unit,
    val onOpenChat: (FriendRowUi) -> Unit,
    val onRequestUnfriend: (FriendDto) -> Unit,
    val onConfirmUnfriend: () -> Unit,
    val onDismissUnfriend: () -> Unit,
    val onAccept: (FriendRequestDto) -> Unit,
    val onDecline: (FriendRequestDto) -> Unit,
    val onCancelRequest: (requestId: String, userId: String) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onSendRequest: (FriendSearchResultDto) -> Unit,
    val onDismissNotice: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(state: FriendsUiState, actions: FriendsActions, modifier: Modifier = Modifier) {
    state.confirmUnfriend?.let { friend ->
        GlassDialog(
            onDismissRequest = actions.onDismissUnfriend,
            title = stringResource(R.string.friends_unfriend_title, friend.user.displayName),
            message = stringResource(R.string.friends_unfriend_message),
            confirmLabel = stringResource(R.string.friends_unfriend),
            onConfirm = actions.onConfirmUnfriend,
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            icon = Icons.Rounded.PersonRemove,
        )
    }

    DetailScaffold(title = stringResource(R.string.friends_title), onBack = actions.onBack, modifier = modifier) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                GlassRefreshIndicator(pullState, state.refreshing, top = padding.calculateTopPadding() + 4.dp)
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = LocalBottomChromeInset.current + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "segments") {
                    val requestCount = state.incoming.size
                    GlassSegmentedControl(
                        options = listOf(
                            stringResource(R.string.friends_segment_friends),
                            if (requestCount > 0) {
                                stringResource(R.string.friends_segment_requests_count, requestCount)
                            } else {
                                stringResource(R.string.friends_segment_requests)
                            },
                            stringResource(R.string.friends_segment_search),
                        ),
                        selectedIndex = state.segment.ordinal,
                        onSelect = { actions.onSelectSegment(FriendsSegment.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.notice?.let { notice ->
                    item(key = "notice") {
                        GlassBanner(
                            message = notice.asString(),
                            tone = if (state.noticeIsError) GlassBannerTone.Error else GlassBannerTone.Success,
                            onDismiss = actions.onDismissNotice,
                        )
                    }
                }
                when (state.segment) {
                    FriendsSegment.FRIENDS -> friendsSection(state, actions)
                    FriendsSegment.REQUESTS -> requestsSection(state, actions)
                    FriendsSegment.SEARCH -> searchSection(state, actions)
                }
            }
        }
    }
}

private fun LazyListScope.friendsSection(state: FriendsUiState, actions: FriendsActions) {
    when {
        state.loading && state.friends.isEmpty() -> item(key = "loading") { LoadingBlock() }
        state.loadError != null && state.friends.isEmpty() -> item(key = "error") {
            GlassBanner(
                title = stringResource(R.string.friends_load_error_title),
                message = state.loadError.asString(),
                tone = GlassBannerTone.Error,
                actionLabel = stringResource(R.string.action_retry),
                onAction = actions.onRefresh,
            )
        }
        state.friends.isEmpty() -> item(key = "empty") {
            EmptyCard(
                title = stringResource(R.string.friends_empty_title),
                body = stringResource(R.string.friends_empty_body),
                actionLabel = stringResource(R.string.friends_empty_action),
                onAction = { actions.onSelectSegment(FriendsSegment.SEARCH) },
            )
        }
        else -> item(key = "friends") {
            BentoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                state.friends.forEachIndexed { index, row ->
                    FriendRow(
                        row = row,
                        busy = row.userId in state.busyIds,
                        onOpen = { actions.onOpenChat(row) },
                        onUnfriend = { actions.onRequestUnfriend(row.friend) },
                    )
                    if (index < state.friends.lastIndex) RowDivider()
                }
            }
        }
    }
}

private fun LazyListScope.requestsSection(state: FriendsUiState, actions: FriendsActions) {
    if (state.loading && state.incoming.isEmpty() && state.outgoing.isEmpty()) {
        item(key = "loading") { LoadingBlock() }
        return
    }
    if (state.incoming.isEmpty() && state.outgoing.isEmpty()) {
        item(key = "requests-empty") {
            EmptyCard(title = stringResource(R.string.friends_requests_empty), body = null)
        }
        return
    }
    if (state.incoming.isNotEmpty()) {
        item(key = "incoming-header") { SectionTitle(stringResource(R.string.friends_requests_incoming)) }
        item(key = "incoming") {
            BentoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                state.incoming.forEachIndexed { index, request ->
                    val busy = request.id in state.busyIds
                    RequestRow(request) {
                        PrimaryButton(
                            text = stringResource(R.string.friends_accept),
                            onClick = { actions.onAccept(request) },
                            enabled = !busy,
                            loading = busy,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = stringResource(R.string.friends_decline),
                            onClick = { actions.onDecline(request) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (index < state.incoming.lastIndex) RowDivider()
                }
            }
        }
    }
    if (state.outgoing.isNotEmpty()) {
        item(key = "outgoing-header") { SectionTitle(stringResource(R.string.friends_requests_outgoing)) }
        item(key = "outgoing") {
            BentoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                state.outgoing.forEachIndexed { index, request ->
                    val busy = request.id in state.busyIds
                    RequestRow(request) {
                        PlainButton(
                            text = stringResource(R.string.friends_cancel_request),
                            onClick = { actions.onCancelRequest(request.id, request.user.id) },
                            enabled = !busy,
                            destructive = true,
                        )
                    }
                    if (index < state.outgoing.lastIndex) RowDivider()
                }
            }
        }
    }
}

private fun LazyListScope.searchSection(state: FriendsUiState, actions: FriendsActions) {
    item(key = "search-field") {
        GlassTextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            label = stringResource(R.string.friends_search_label),
            placeholder = stringResource(R.string.friends_search_placeholder),
            supportingText = stringResource(R.string.friends_search_hint),
            errorText = state.searchError?.asString(),
            leadingIcon = Icons.Rounded.Search,
            trailing = if (state.query.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .size(GlassTokens.MinTouchTarget)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.friends_search_clear)) {
                                actions.onQueryChange("")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.friends_search_clear),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when {
        state.searching && state.searchResults.isEmpty() -> item(key = "searching") { LoadingBlock() }
        state.searchedQuery != null && state.searchResults.isEmpty() && state.searchError == null -> item(key = "no-results") {
            EmptyCard(title = stringResource(R.string.friends_search_empty, state.searchedQuery), body = null)
        }
        state.searchResults.isNotEmpty() -> item(key = "results") {
            BentoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                state.searchResults.forEachIndexed { index, result ->
                    SearchResultRow(
                        result = result,
                        busy = result.id in state.busyIds || (result.requestId != null && result.requestId in state.busyIds),
                        actions = actions,
                    )
                    if (index < state.searchResults.lastIndex) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun FriendRow(row: FriendRowUi, busy: Boolean, onOpen: () -> Unit, onUnfriend: () -> Unit) {
    val name = row.friend.user.displayName
    val unfriendLabel = stringResource(R.string.friends_unfriend)
    val preview = when {
        row.lastMessage == null -> stringResource(R.string.friends_no_messages)
        row.lastMessageMine -> stringResource(R.string.friends_last_message_mine, row.lastMessage)
        else -> row.lastMessage
    }
    val presence = stringResource(if (row.isOnline) R.string.friends_online else R.string.friends_offline)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(
                enabled = !busy,
                role = Role.Button,
                onClickLabel = stringResource(R.string.friends_open_chat, name),
                onClick = onOpen,
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(unfriendLabel) { onUnfriend(); true })
            }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialAvatar(name = name, online = row.isOnline)
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$presence · $preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (row.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            GlassSpinner(size = 20.dp)
        } else if (row.unread > 0) {
            UnreadBadge(row.unread)
        }
        Box(
            modifier = Modifier
                .size(GlassTokens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(enabled = !busy, role = Role.Button, onClickLabel = unfriendLabel, onClick = onUnfriend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PersonRemove,
                contentDescription = stringResource(R.string.friends_unfriend_named, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val label = pluralStringResource(R.plurals.friends_unread, count, count)
    Box(
        modifier = Modifier
            .widthIn(min = 24.dp)
            .heightIn(min = 24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { this.contentDescription = label }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun RequestRow(request: FriendRequestDto, buttons: @Composable RowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(name = request.user.displayName, online = request.user.isOnline)
            Column(Modifier.weight(1f)) {
                Text(
                    request.user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.friends_username, request.user.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = buttons,
        )
    }
}

@Composable
private fun SearchResultRow(result: FriendSearchResultDto, busy: Boolean, actions: FriendsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(name = result.displayName)
            Column(Modifier.weight(1f)) {
                Text(
                    result.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.friends_username, result.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (result.friendshipStatus) {
            FriendsViewModel.FRIENDS -> StatusLabel(stringResource(R.string.friends_already_label))
            FriendsViewModel.OUTGOING -> {
                val requestId = result.requestId
                if (requestId != null) {
                    PlainButton(
                        text = stringResource(R.string.friends_cancel_request),
                        onClick = { actions.onCancelRequest(requestId, result.id) },
                        enabled = !busy,
                        destructive = true,
                    )
                } else {
                    StatusLabel(stringResource(R.string.friends_request_sent_label))
                }
            }
            FriendsViewModel.INCOMING -> PlainButton(
                text = stringResource(R.string.friends_view_request),
                onClick = { actions.onSelectSegment(FriendsSegment.REQUESTS) },
            )
            else -> PrimaryButton(
                text = stringResource(R.string.friends_add),
                onClick = { actions.onSendRequest(result) },
                enabled = !busy,
                loading = busy,
            )
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 8.dp, top = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EmptyCard(title: String, body: String?, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && onAction != null) {
                PrimaryButton(text = actionLabel, onClick = onAction, leadingIcon = Icons.Rounded.Search)
            }
        }
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = GlassTokens.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

private const val MAX_BADGE = 99
