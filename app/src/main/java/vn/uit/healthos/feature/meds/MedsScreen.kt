package vn.uit.healthos.feature.meds

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MedsScreen(
    state: MedsUiState,
    onRefresh: () -> Unit,
    onAddPlan: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onMarkDone: (DoseCacheEntity) -> Unit,
    onSkipDose: (DoseCacheEntity) -> Unit,
    onSnoozeDose: (DoseCacheEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.of(DEFAULT_MED_TZID)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val notificationPermission = rememberNotificationPermission()

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
            Text(stringResource(R.string.nav_meds), style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                }
            }
        }

        state.loadError?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (!notificationPermission.granted) {
            NotificationPermissionCard(onEnable = notificationPermission.request)
        }

        PrimaryButton(
            text = stringResource(R.string.meds_add_plan_action),
            onClick = onAddPlan,
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )

        // Today timeline
        SectionHeader(title = stringResource(R.string.meds_today_section))
        if (state.todayDoses.isEmpty()) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.meds_today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            state.todayDoses.forEach { dose ->
                val timeStr = Instant.ofEpochMilli(dose.snoozedUntilMs ?: dose.scheduledAtMs)
                    .atZone(zone)
                    .format(timeFmt)
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(dose.planName, style = MaterialTheme.typography.titleSmall)
                            dose.strength?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DoseStatusBadge(status = dose.status)
                    }

                    if (DoseStatus.of(dose.status).isOpen) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onMarkDone(dose) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    stringResource(R.string.meds_action_taken),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            OutlinedButton(onClick = { onSnoozeDose(dose) }) {
                                Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("10p", modifier = Modifier.padding(start = 4.dp))
                            }
                            TextButton(onClick = { onSkipDose(dose) }) {
                                Text(stringResource(R.string.meds_action_skip))
                            }
                        }
                    }
                }
            }
        }

        // Medication plans
        SectionHeader(title = stringResource(R.string.meds_plans_section))
        if (state.plans.isEmpty()) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.meds_plans_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val nowMs = System.currentTimeMillis()
            state.plans.forEach { plan ->
                val adherencePct = state.planAdherence[plan.id]
                val refillDays = if (plan.status == "active") {
                    refillWarningDays(plan.refillSupplyUnits, plan.nextRefillEstimatedAt, nowMs)
                } else {
                    null
                }
                BentoCard(
                    onClick = { onOpenPlan(plan.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleSmall)
                            val sub = listOfNotNull(
                                plan.strength?.takeIf { it.isNotBlank() },
                                stringResource(R.string.meds_doses_per_day, plan.doseCount),
                                if (plan.status == "paused") stringResource(R.string.meds_status_paused) else null,
                            ).joinToString(" · ")
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (refillDays != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = LocalVitalColors.current.alert,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (refillDays > 0) {
                                            stringResource(R.string.meds_refill_warning_days, refillDays)
                                        } else {
                                            stringResource(R.string.meds_refill_warning_now)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalVitalColors.current.alert,
                                    )
                                }
                            }
                        }
                        if (adherencePct != null) {
                            Text(
                                text = "$adherencePct%",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = if (adherencePct >= 80) LocalVitalColors.current.good else LocalVitalColors.current.watch,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class NotificationPermissionState(val granted: Boolean, val request: () -> Unit)

/** Asks once for POST_NOTIFICATIONS (Android 13+) so dose alarms can show their reminder. */
@Composable
private fun rememberNotificationPermission(): NotificationPermissionState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return remember { NotificationPermissionState(granted = true, request = {}) }
    }
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return NotificationPermissionState(granted) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun NotificationPermissionCard(onEnable: () -> Unit) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = LocalVitalColors.current.alert,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.meds_notifications_disabled),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.action_enable))
            }
        }
    }
}

@Composable
private fun DoseStatusBadge(status: String) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg, labelRes) = when (DoseStatus.of(status)) {
        DoseStatus.PENDING -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, R.string.meds_status_pending)
        DoseStatus.FIRED -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, R.string.meds_status_due)
        DoseStatus.SNOOZED -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, R.string.meds_status_snoozed)
        DoseStatus.DONE -> Triple(colors.primaryContainer, colors.onPrimaryContainer, R.string.meds_status_taken)
        DoseStatus.SKIPPED -> Triple(colors.surfaceVariant, colors.onSurfaceVariant, R.string.meds_status_skipped)
        DoseStatus.MISSED -> Triple(colors.errorContainer, colors.onErrorContainer, R.string.meds_status_missed)
        DoseStatus.CANCELLED -> Triple(colors.surfaceVariant, colors.onSurfaceVariant, R.string.meds_status_cancelled)
        DoseStatus.UNKNOWN -> Triple(colors.surfaceVariant, colors.onSurfaceVariant, R.string.meds_status_unknown)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
