package vn.uit.healthos.feature.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import vn.uit.healthos.feature.friends.GlassRefreshIndicator
import vn.uit.healthos.feature.friends.InitialAvatar
import vn.uit.healthos.feature.friends.LoadingBlock
import vn.uit.healthos.feature.friends.RowDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBack: () -> Unit,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSelectScope: (LeaderboardScope) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = state.board?.me
    val bottomInset = LocalBottomChromeInset.current
    DetailScaffold(title = stringResource(R.string.leaderboard_title), onBack = onBack, modifier = modifier) { padding ->
        val pullState = rememberPullToRefreshState()
        Box(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
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
                        // Room for the pinned "you" row.
                        bottom = bottomInset + 24.dp + if (me != null) PinnedRowSpace else 0.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "period") {
                        GlassSegmentedControl(
                            options = listOf(
                                stringResource(R.string.leaderboard_period_today),
                                stringResource(R.string.leaderboard_period_week),
                            ),
                            selectedIndex = state.period.ordinal,
                            onSelect = { onSelectPeriod(LeaderboardPeriod.entries[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "scope") {
                        GlassSegmentedControl(
                            options = listOf(
                                stringResource(R.string.leaderboard_scope_friends),
                                stringResource(R.string.leaderboard_scope_global),
                            ),
                            selectedIndex = state.scope.ordinal,
                            onSelect = { onSelectScope(LeaderboardScope.entries[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    visibilityHint(state, onOpenSettings)
                    boardContent(state, onRefresh, onDismissError)
                }
            }
            if (me != null) {
                PinnedMeRow(
                    entry = me,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 12.dp),
                )
            }
        }
    }
}

private fun LazyListScope.visibilityHint(state: LeaderboardUiState, onOpenSettings: () -> Unit) {
    when {
        state.hiddenFromBoards -> item(key = "hidden") {
            GlassBanner(
                title = stringResource(R.string.leaderboard_visibility_off_title),
                message = stringResource(R.string.leaderboard_visibility_off),
                tone = GlassBannerTone.Warning,
                icon = Icons.Rounded.VisibilityOff,
                actionLabel = stringResource(R.string.leaderboard_open_settings),
                onAction = onOpenSettings,
            )
        }
        state.missingFromGlobal -> item(key = "not-global") {
            GlassBanner(
                message = stringResource(R.string.leaderboard_visibility_not_global),
                tone = GlassBannerTone.Info,
                actionLabel = stringResource(R.string.leaderboard_open_settings),
                onAction = onOpenSettings,
            )
        }
    }
}

private fun LazyListScope.boardContent(state: LeaderboardUiState, onRefresh: () -> Unit, onDismissError: () -> Unit) {
    val board = state.board
    if (board == null) {
        when {
            state.loading -> item(key = "loading") { LoadingBlock() }
            state.error != null -> item(key = "error") {
                GlassBanner(
                    message = state.error.asString(),
                    tone = GlassBannerTone.Error,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRefresh,
                )
            }
        }
        return
    }
    item(key = "updated") { UpdatedLine(state, board) }
    state.error?.let { error ->
        item(key = "stale-error") {
            GlassBanner(message = error.asString(), tone = GlassBannerTone.Error, onDismiss = onDismissError)
        }
    }
    if (board.entries.isEmpty()) {
        item(key = "empty") {
            BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(
                            if (state.scope == LeaderboardScope.FRIENDS) {
                                R.string.leaderboard_empty_friends
                            } else {
                                R.string.leaderboard_empty_global
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }
    item(key = "podium") { Podium(state.podium) }
    if (state.rest.isNotEmpty()) {
        item(key = "rest") {
            BentoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                state.rest.forEachIndexed { index, entry ->
                    RankRow(entry)
                    if (index < state.rest.lastIndex) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun UpdatedLine(state: LeaderboardUiState, board: LeaderboardDto) {
    val updated = state.updatedAtMs?.let { formatUpdatedAt(it) }
    val lines = buildList {
        if (state.period == LeaderboardPeriod.WEEK) {
            add(stringResource(R.string.leaderboard_range_week, formatDay(board.rangeStart), formatDay(board.rangeEnd)))
        }
        if (updated != null) {
            add(
                stringResource(
                    if (state.fromCache) R.string.leaderboard_updated_cached else R.string.leaderboard_updated,
                    updated,
                ),
            )
        }
    }
    if (lines.isEmpty()) return
    Text(
        text = lines.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Top three as glass medals, second, first and third from left to right like a podium. */
@Composable
private fun Podium(entries: List<LeaderboardEntryDto>) {
    val order = listOfNotNull(entries.getOrNull(1), entries.getOrNull(0), entries.getOrNull(2))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { entry -> PodiumPlace(entry, Modifier.weight(1f)) }
    }
}

@Composable
private fun PodiumPlace(entry: LeaderboardEntryDto, modifier: Modifier = Modifier) {
    val medal = medalColor(entry.rank)
    val size: Dp = if (entry.rank == 1) 88.dp else 72.dp
    val name = if (entry.isMe) stringResource(R.string.leaderboard_you) else entry.displayName
    val steps = formatSteps(entry.steps)
    val spoken = stringResource(R.string.leaderboard_entry, entry.rank, name, steps)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .liquidGlass(
                    shape = CircleShape,
                    style = GlassStyle.Control,
                    tint = medal,
                    tintMode = GlassTintMode.Prominent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.rank.toString(),
                style = (if (entry.rank == 1) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall)
                    .tabular(),
                fontWeight = FontWeight.Bold,
                color = MedalInk,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.leaderboard_steps, steps),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RankRow(entry: LeaderboardEntryDto) {
    val name = if (entry.isMe) stringResource(R.string.leaderboard_you) else entry.displayName
    val steps = formatSteps(entry.steps)
    val spoken = stringResource(R.string.leaderboard_entry, entry.rank, name, steps)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.leaderboard_rank, entry.rank),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        InitialAvatar(name = entry.displayName, size = 40.dp)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
            color = if (entry.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.leaderboard_steps, steps),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The user's own rank, pinned above the bottom edge on a Chrome glass bar so it stays visible while scrolling. */
@Composable
private fun PinnedMeRow(entry: LeaderboardEntryDto, modifier: Modifier = Modifier) {
    val steps = formatSteps(entry.steps)
    val spoken = stringResource(R.string.leaderboard_you_row, entry.rank, steps)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassTokens.CardShape, style = GlassStyle.Chrome)
            .heightIn(min = 64.dp)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.leaderboard_rank, entry.rank),
            style = MaterialTheme.typography.titleLarge.tabular(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.leaderboard_you),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.leaderboard_steps, steps),
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun medalColor(rank: Int): Color = when (rank) {
    1 -> Gold
    2 -> Silver
    else -> Bronze
}

private val Gold = Color(0xFFF5C542)
private val Silver = Color(0xFFC9D1D9)
private val Bronze = Color(0xFFE0A46E)

/** Dark ink on the light medal tints keeps the rank above 7:1 contrast in both themes. */
private val MedalInk = Color(0xFF1C1B1F)
private val PinnedRowSpace = 84.dp
