package vn.uit.healthos.feature.profile

import android.appwidget.AppWidgetManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.AccentPicker
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassListRow
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.GlassSwitch
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.glass.GlassRoot
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.feature.safety.card.EmergencyCardWidgetReceiver
import vn.uit.healthos.feature.widget.AskAiWidgetReceiver
import vn.uit.healthos.feature.widget.CaloriesWidgetReceiver
import vn.uit.healthos.feature.widget.HydrationWidgetReceiver
import vn.uit.healthos.feature.widget.NextDoseWidgetReceiver
import vn.uit.healthos.feature.widget.StepsWidgetReceiver
import vn.uit.healthos.feature.widget.VitalScoreWidgetReceiver

@Composable
fun SettingsScreen(
    state: ProfileUiState,
    canUseBiometrics: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onBackgroundStyleChange: (BackgroundStyle) -> Unit,
    onReducedTransparencyChange: (Boolean) -> Unit,
    onLeaderboardVisibilityChange: (LeaderboardVisibility) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = dynamicAvailable && prefs.dynamicColor
    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }

    if (showWidgetPicker) {
        AddWidgetDialog(onDismiss = { showWidgetPicker = false })
    }

    DetailScaffold(
        title = stringResource(R.string.profile_settings_title),
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
            SectionHeader(title = stringResource(R.string.settings_appearance_section))

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingLabel(R.string.settings_theme_label)
                    GlassSegmentedControl(
                        options = ThemeMode.entries.map { themeLabel(it) },
                        selectedIndex = ThemeMode.entries.indexOf(prefs.themeMode),
                        onSelect = { onThemeModeChange(ThemeMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SettingLabel(R.string.settings_accent_label)
                    AccentPicker(
                        selected = prefs.accentPreset,
                        onSelect = onAccentChange,
                        enabled = !dynamicOn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (dynamicOn) {
                        Text(
                            text = stringResource(R.string.settings_accent_dynamic_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingLabel(R.string.settings_background_label)
                    GlassSegmentedControl(
                        options = BackgroundStyle.entries.map { stringResource(it.label) },
                        selectedIndex = BackgroundStyle.entries.indexOf(prefs.backgroundStyle),
                        onSelect = { onBackgroundStyleChange(BackgroundStyle.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BackgroundPreview(style = prefs.backgroundStyle)
                }
            }

            BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                if (dynamicAvailable) {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        icon = Icons.Rounded.AutoAwesome,
                        checked = prefs.dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                        divider = true,
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.settings_reduced_transparency_title),
                    subtitle = stringResource(R.string.settings_reduced_transparency_subtitle),
                    icon = Icons.Rounded.Contrast,
                    checked = prefs.reducedTransparency,
                    onCheckedChange = onReducedTransparencyChange,
                )
            }

            SectionHeader(title = stringResource(R.string.settings_privacy_section))
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_leaderboard_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.settings_leaderboard_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassSegmentedControl(
                        options = LeaderboardVisibility.entries.map { stringResource(it.label) },
                        selectedIndex = LeaderboardVisibility.entries.indexOf(state.leaderboardVisibility),
                        onSelect = { onLeaderboardVisibilityChange(LeaderboardVisibility.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.settings_security_section))
            BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SwitchRow(
                    title = stringResource(R.string.settings_app_lock_title),
                    subtitle = if (canUseBiometrics) {
                        stringResource(R.string.settings_app_lock_subtitle)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
                    },
                    icon = Icons.Rounded.Fingerprint,
                    checked = prefs.appLockEnabled && canUseBiometrics,
                    enabled = canUseBiometrics,
                    onCheckedChange = onAppLockChange,
                )
            }

            SectionHeader(title = stringResource(R.string.settings_widgets_section))
            BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                GlassListRow(
                    title = stringResource(R.string.settings_add_widget_title),
                    subtitle = stringResource(R.string.settings_add_widget_subtitle),
                    leadingIcon = Icons.Rounded.Widgets,
                    iconBackground = MaterialTheme.colorScheme.primary,
                    onClick = { showWidgetPicker = true },
                )
            }

            if (BuildConfig.DEBUG) {
                SectionHeader(title = stringResource(R.string.settings_about_section))
                BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    GlassListRow(
                        title = stringResource(R.string.settings_server_url_label),
                        subtitle = BuildConfig.HEALTHOS_BASE_URL,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall)
}

/** A list row whose whole surface toggles the switch, so the 56dp row is the touch target. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    divider: Boolean = false,
) {
    GlassListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        iconBackground = MaterialTheme.colorScheme.primary,
        trailing = { GlassSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        onClick = { onCheckedChange(!checked) },
        showChevron = false,
        enabled = enabled,
        divider = divider,
    )
}

/**
 * A small window onto the selected background style: its own [GlassRoot] paints the aurora (or the plain base) and
 * a sample glass card floats on it, the way every screen will look.
 */
@Composable
private fun BackgroundPreview(style: BackgroundStyle) {
    val description = stringResource(R.string.settings_background_preview_cd, stringResource(style.label))
    val shape = remember { RoundedRectangle(18.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .semantics { contentDescription = description },
    ) {
        GlassRoot(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalInsideGlass provides false) {
                BentoCard(modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.settings_background_preview_sample),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

private data class PinnableWidget(
    @param:StringRes val name: Int,
    val icon: ImageVector,
    val receiver: Class<out GlanceAppWidgetReceiver>,
)

private val PinnableWidgets = listOf(
    PinnableWidget(R.string.widget_vital_name, Icons.Rounded.Favorite, VitalScoreWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_steps_name, Icons.AutoMirrored.Rounded.DirectionsWalk, StepsWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_hydration_name, Icons.Rounded.WaterDrop, HydrationWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_dose_name, Icons.Rounded.Medication, NextDoseWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_sos_name, Icons.Rounded.Sos, EmergencyCardWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_ask_name, Icons.AutoMirrored.Rounded.Chat, AskAiWidgetReceiver::class.java),
    PinnableWidget(R.string.widget_calories_name, Icons.Rounded.LocalFireDepartment, CaloriesWidgetReceiver::class.java),
)

/** Lists the home-screen widgets; tapping one asks the launcher to pin it (the launcher shows its own confirmation). */
@Composable
private fun AddWidgetDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supported = remember(context) { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    var failed by remember { mutableStateOf(false) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_add_widget_title),
        message = stringResource(
            if (supported) R.string.settings_add_widget_hint else R.string.settings_add_widget_unsupported,
        ),
        confirmLabel = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        icon = Icons.Rounded.Widgets,
    ) {
        if (supported) {
            PinnableWidgets.forEachIndexed { index, widget ->
                GlassListRow(
                    title = stringResource(widget.name),
                    leadingIcon = widget.icon,
                    iconBackground = MaterialTheme.colorScheme.primary,
                    divider = index < PinnableWidgets.lastIndex,
                    onClick = {
                        scope.launch {
                            val requested = runCatching {
                                GlanceAppWidgetManager(context).requestPinGlanceAppWidget(widget.receiver)
                            }.getOrDefault(false)
                            failed = !requested
                            if (requested) onDismiss()
                        }
                    },
                )
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.settings_add_widget_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}
