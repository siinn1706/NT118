package vn.uit.healthos.feature.safety

import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.GlassSwitch
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactEmergency
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.ui.asString
import vn.uit.healthos.feature.safety.card.EmergencyCardActivity
import vn.uit.healthos.feature.safety.card.QrGenerator

@Composable
fun SafetyScreen(
    state: SafetyUiState,
    onToggleFallDetection: (Boolean) -> Unit,
    onToggleTestMode: (Boolean) -> Unit,
    onMintQrToken: () -> Unit,
    onEditContacts: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val qrBitmap = remember(state.local.shareUrl) {
        state.local.shareUrl?.let { QrGenerator.generate(it, sizePx = 380) }
    }

    // Re-read permission state whenever the screen resumes (e.g. back from system settings) or a request returns.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionRefresh++
        onPauseOrDispose { }
    }
    val canFullScreen = remember(context, permissionRefresh) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }
    val smsMissing = remember(context, permissionRefresh) { !context.isGranted(Manifest.permission.SEND_SMS) }
    val locationMissing = remember(context, permissionRefresh) {
        !context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    var fallNeedsNotifications by rememberSaveable { mutableStateOf(false) }

    // A manual SOS is armed whatever the answer: without SMS it still emails, without location it omits the map link.
    val sosPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRefresh++
        context.startActivity(SosCountdownActivity.manualSosIntent(context))
    }

    // Fall detection needs notifications, otherwise a false alarm could not be cancelled; SMS/location only degrade it.
    val enableFallDetection = {
        val canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fallNeedsNotifications = !canNotify
        if (canNotify) onToggleFallDetection(true)
    }
    val fallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRefresh++
        enableFallDetection()
    }

    DetailScaffold(
        title = stringResource(R.string.feature_safety),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.contacts.isEmpty() && !state.loading) {
                BentoCard(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.profile_contacts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = stringResource(R.string.profile_contacts_add_action),
                        onClick = onEditContacts,
                        leadingIcon = Icons.Rounded.PersonAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Big Manual SOS Button
            BentoCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.safety_manual_sos_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.safety_manual_sos_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(12.dp))
                if (smsMissing || locationMissing) {
                    if (smsMissing) {
                        Text(
                            text = stringResource(R.string.safety_sos_perm_sms_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (locationMissing) {
                        Text(
                            text = stringResource(R.string.safety_sos_perm_location_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    PlainButton(
                        text = stringResource(R.string.safety_sos_perm_open_settings),
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                PrimaryButton(
                    text = stringResource(R.string.safety_trigger_sos_action),
                    onClick = {
                        val missing = context.missingPermissions(sosPermissions())
                        if (missing.isEmpty()) {
                            context.startActivity(SosCountdownActivity.manualSosIntent(context))
                        } else {
                            sosPermissionLauncher.launch(missing)
                        }
                    },
                    leadingIcon = Icons.Rounded.Sos,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
            }

            if (!canFullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                BentoCard(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.safety_fullscreen_perm_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        PlainButton(
                            text = stringResource(R.string.action_enable),
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                runCatching { context.startActivity(intent) }
                            },
                        )
                    }
                }
            }

            // Fall detection toggles
            SectionHeader(title = stringResource(R.string.safety_fall_section))
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val onFallToggle: (Boolean) -> Unit = { enable ->
                        if (enable) {
                            val missing = context.missingPermissions(
                                sosPermissions() + Manifest.permission.ACTIVITY_RECOGNITION,
                            )
                            if (missing.isEmpty()) {
                                enableFallDetection()
                            } else {
                                fallPermissionLauncher.launch(missing)
                            }
                        } else {
                            onToggleFallDetection(false)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = GlassTokens.MinTouchTarget)
                            .toggleable(
                                value = state.local.fallDetectionEnabled,
                                role = Role.Switch,
                                onValueChange = onFallToggle,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.safety_fall_toggle_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.safety_fall_toggle_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GlassSwitch(checked = state.local.fallDetectionEnabled, onCheckedChange = null)
                    }

                    if (fallNeedsNotifications && !state.local.fallDetectionEnabled) {
                        Text(
                            text = stringResource(R.string.safety_fall_needs_notifications),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = GlassTokens.MinTouchTarget)
                            .toggleable(
                                value = state.local.testModeEnabled,
                                role = Role.Switch,
                                onValueChange = onToggleTestMode,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.safety_test_mode_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.safety_test_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GlassSwitch(checked = state.local.testModeEnabled, onCheckedChange = null)
                    }
                }
            }

            // Emergency Card & QR
            SectionHeader(title = stringResource(R.string.safety_card_title))
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.safety_card_qr_cd),
                            modifier = Modifier.size(180.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.safety_card_no_qr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Side by side only while both labels fit on one line; otherwise stacked at full width.
                    AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 2, horizontalSpacing = 10.dp) {
                        PrimaryButton(
                            text = stringResource(R.string.safety_mint_qr_action),
                            onClick = onMintQrToken,
                            loading = state.mintingToken,
                            leadingIcon = Icons.Rounded.QrCode2,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.safety_open_card_action),
                            onClick = {
                                context.startActivity(Intent(context, EmergencyCardActivity::class.java))
                            },
                        )
                    }
                }
            }

            // Contacts
            SectionHeader(
                title = stringResource(R.string.profile_emergency_contacts_section),
                actionLabel = stringResource(
                    if (state.contacts.isEmpty()) {
                        R.string.profile_contacts_add_action
                    } else {
                        R.string.profile_contacts_edit_action
                    },
                ),
                onAction = onEditContacts,
            )
            BentoCard(onClick = onEditContacts, modifier = Modifier.fillMaxWidth()) {
                if (state.contacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_contacts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.contacts.forEach { contact ->
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

            state.error?.let { err ->
                GlassBanner(
                    message = err.asString(),
                    tone = GlassBannerTone.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Runtime permissions an SOS uses: SMS to contacts, location for the map link, notifications for the alert. */
private fun sosPermissions(): Array<String> = buildList {
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.missingPermissions(permissions: Array<String>): Array<String> =
    permissions.filterNot { isGranted(it) }.toTypedArray()
