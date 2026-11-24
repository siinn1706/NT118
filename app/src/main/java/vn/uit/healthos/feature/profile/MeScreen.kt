package vn.uit.healthos.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContactEmergency
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.ui.asString
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun MeScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPpg: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditContacts: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.profile_logout_confirm_title)) },
            text = { Text(stringResource(R.string.profile_logout_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onSignOut()
                    },
                ) {
                    Text(stringResource(R.string.profile_logout_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + LocalBottomChromeInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.nav_me), style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.profile_settings_title))
                }
            }
        }

        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val displayName = profile?.fullName ?: profile?.displayName ?: stringResource(R.string.profile_default_user)
                if (!profile?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.uppercase() ?: "H",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge)
                    profile?.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    profile?.bloodType?.takeIf { it.isNotBlank() }?.let { blood ->
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = stringResource(R.string.profile_blood_type_badge, blood),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
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
                    "${heightCm.roundToInt()} cm · ${weightKg} kg"
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

        SectionHeader(title = stringResource(R.string.profile_tools_section))

        ProfileActionRow(
            title = stringResource(R.string.feature_safety),
            subtitle = stringResource(R.string.profile_safety_subtitle),
            icon = Icons.Rounded.Shield,
            iconTint = MaterialTheme.colorScheme.tertiary,
            onClick = onOpenSafety,
        )
        ProfileActionRow(
            title = stringResource(R.string.feature_ppg),
            subtitle = stringResource(R.string.profile_ppg_subtitle),
            icon = Icons.Rounded.Favorite,
            iconTint = MaterialTheme.colorScheme.tertiary,
            onClick = onOpenPpg,
        )
        ProfileActionRow(
            title = stringResource(R.string.profile_settings_title),
            subtitle = stringResource(R.string.profile_settings_subtitle),
            icon = Icons.Rounded.Settings,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onOpenSettings,
        )
        if (BuildConfig.DEBUG) {
            ProfileActionRow(
                title = stringResource(R.string.feature_design_gallery),
                subtitle = stringResource(R.string.profile_gallery_subtitle),
                icon = Icons.Rounded.Palette,
                iconTint = LocalVitalColors.current.energy,
                onClick = onOpenDesignGallery,
            )
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
            BentoCard(onClick = onEditContacts, modifier = Modifier.fillMaxWidth()) {
                if (contacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_contacts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    contacts.forEach { contact ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContactEmergency,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${contact.name} (${contact.relationship})",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(contact.phone, contact.email).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        SecondaryButton(
            text = stringResource(R.string.profile_logout_action),
            onClick = { showLogoutConfirm = true },
            enabled = !state.loggingOut,
            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    BentoCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
