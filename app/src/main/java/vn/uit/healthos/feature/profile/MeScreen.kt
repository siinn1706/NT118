package vn.uit.healthos.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContactEmergency
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.shapes.Capsule
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassListRow
import vn.uit.healthos.core.designsystem.component.GlassToolbarItem
import vn.uit.healthos.core.designsystem.component.LargeTitleHeader
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.ui.BackgroundRefreshBar
import vn.uit.healthos.core.ui.SkeletonList
import vn.uit.healthos.core.ui.asString
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun MeScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPpg: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditContacts: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

    if (showLogoutConfirm) {
        GlassDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = stringResource(R.string.profile_logout_confirm_title),
            message = stringResource(R.string.profile_logout_confirm_body),
            confirmLabel = stringResource(R.string.profile_logout_action),
            onConfirm = {
                showLogoutConfirm = false
                onSignOut()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            icon = Icons.AutoMirrored.Rounded.Logout,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp + LocalBottomChromeInset.current),
    ) {
        LargeTitleHeader(
            title = stringResource(R.string.nav_me),
            modifier = Modifier.padding(horizontal = 4.dp),
            actions = {
                GlassToolbarItem(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                )
            },
        )
        BackgroundRefreshBar(
            visible = state.refreshing && !state.showSkeleton,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MeContent(
                state = state,
                onRefresh = onRefresh,
                onOpenFriends = onOpenFriends,
                onOpenLeaderboard = onOpenLeaderboard,
                onOpenSafety = onOpenSafety,
                onOpenPpg = onOpenPpg,
                onOpenSettings = onOpenSettings,
                onEditContacts = onEditContacts,
                onOpenDesignGallery = onOpenDesignGallery,
                onAskSignOut = { showLogoutConfirm = true },
            )
        }
    }
}

@Composable
private fun MeContent(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPpg: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditContacts: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onAskSignOut: () -> Unit,
) {
    val profile = state.profile
    if (state.showSkeleton) {
        SkeletonList(count = 1, itemHeight = 104.dp)
    } else {
        ProfileHeaderCard(profile = profile)
    }

    val heightCm = profile?.heightCm
    val weightKg = profile?.weightKg
    val bmiValue = if (heightCm != null && weightKg != null && heightCm > 0) {
        ((weightKg / (heightCm / 100.0).pow(2.0)) * 10.0).roundToInt() / 10.0
    } else {
        null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricTile(
            label = stringResource(R.string.profile_metric_bmi),
            value = bmiValue?.toString() ?: "—",
            caption = if (heightCm != null && weightKg != null) {
                "${heightCm.roundToInt()} cm · $weightKg kg"
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.profile_metric_calorie_goal),
            value = state.preferences.dailyCalorieTarget?.toString() ?: "—",
            unit = stringResource(R.string.unit_kcal),
            caption = state.healthGoal?.targetWeightKg?.let {
                stringResource(R.string.profile_target_weight_caption, it.toString())
            },
            iconTint = LocalVitalColors.current.energy,
            modifier = Modifier.weight(1f),
        )
    }

    SectionHeader(title = stringResource(R.string.profile_social_section))
    BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        GlassListRow(
            title = stringResource(R.string.profile_friends_title),
            subtitle = stringResource(R.string.profile_friends_subtitle),
            leadingIcon = Icons.Rounded.Group,
            iconBackground = MaterialTheme.colorScheme.primary,
            onClick = onOpenFriends,
            divider = true,
        )
        GlassListRow(
            title = stringResource(R.string.profile_leaderboard_title),
            subtitle = stringResource(R.string.profile_leaderboard_subtitle),
            leadingIcon = Icons.Rounded.Leaderboard,
            iconBackground = LocalVitalColors.current.energy,
            onClick = onOpenLeaderboard,
        )
    }

    SectionHeader(title = stringResource(R.string.profile_tools_section))
    BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        GlassListRow(
            title = stringResource(R.string.feature_safety),
            subtitle = stringResource(R.string.profile_safety_subtitle),
            leadingIcon = Icons.Rounded.Shield,
            iconBackground = MaterialTheme.colorScheme.error,
            onClick = onOpenSafety,
            divider = true,
        )
        GlassListRow(
            title = stringResource(R.string.feature_ppg),
            subtitle = stringResource(R.string.profile_ppg_subtitle),
            leadingIcon = Icons.Rounded.Favorite,
            iconBackground = MaterialTheme.colorScheme.tertiary,
            onClick = onOpenPpg,
            divider = true,
        )
        GlassListRow(
            title = stringResource(R.string.profile_settings_title),
            subtitle = stringResource(R.string.profile_settings_subtitle),
            leadingIcon = Icons.Rounded.Settings,
            iconBackground = MaterialTheme.colorScheme.outline,
            onClick = onOpenSettings,
            divider = BuildConfig.DEBUG,
        )
        if (BuildConfig.DEBUG) {
            GlassListRow(
                title = stringResource(R.string.feature_design_gallery),
                subtitle = stringResource(R.string.profile_gallery_subtitle),
                leadingIcon = Icons.Rounded.Palette,
                iconBackground = LocalVitalColors.current.energy,
                onClick = onOpenDesignGallery,
            )
        }
    }

    if (profile != null) {
        val contacts = profile.emergencyContacts.orEmpty()
        SectionHeader(
            title = stringResource(R.string.profile_emergency_contacts_section),
            actionLabel = stringResource(
                if (contacts.isEmpty()) R.string.profile_contacts_add_action else R.string.profile_contacts_edit_action,
            ),
            onAction = onEditContacts,
        )
        if (contacts.isEmpty()) {
            GlassBanner(
                message = stringResource(R.string.profile_contacts_empty),
                tone = GlassBannerTone.Warning,
                actionLabel = stringResource(R.string.profile_contacts_add_action),
                onAction = onEditContacts,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                contacts.forEachIndexed { index, contact ->
                    GlassListRow(
                        title = "${contact.name} (${contact.relationship})",
                        subtitle = listOfNotNull(contact.phone, contact.email).joinToString(" · "),
                        leadingIcon = Icons.Rounded.ContactEmergency,
                        iconBackground = MaterialTheme.colorScheme.primary,
                        onClick = onEditContacts,
                        divider = index < contacts.lastIndex,
                    )
                }
            }
        }
    }

    state.error?.let { error ->
        GlassBanner(
            message = error.asString(),
            tone = GlassBannerTone.Error,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SecondaryButton(
        text = stringResource(R.string.profile_logout_action),
        onClick = onAskSignOut,
        enabled = !state.loggingOut,
        leadingIcon = Icons.AutoMirrored.Rounded.Logout,
        destructive = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileHeaderCard(profile: UserProfileDto?) {
    val displayName = profile?.fullName ?: profile?.displayName ?: stringResource(R.string.profile_default_user)
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileAvatar(name = displayName, avatarUrl = profile?.avatarUrl, size = 72)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                profile?.username?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "@$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                profile?.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                profile?.bloodType?.takeIf { it.isNotBlank() }?.let { blood ->
                    Text(
                        text = stringResource(R.string.profile_blood_type_badge, blood),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .glassInset(Capsule(), tint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Round avatar: the profile photo, or the first letter of [name] on the accent container. */
@Composable
fun ProfileAvatar(name: String, avatarUrl: String?, size: Int, modifier: Modifier = Modifier) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "H",
                style = if (size >= 56) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
