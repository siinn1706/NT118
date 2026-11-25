package vn.uit.healthos.feature.meds

import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassTextField
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.LoadingState
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular

@Composable
fun MedDetailScreen(
    state: MedsUiState,
    onTogglePause: () -> Unit,
    onRefill: (Int) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.selectedPlan
    var showRefillDialog by rememberSaveable { mutableStateOf(false) }
    var refillUnitsText by rememberSaveable { mutableStateOf("30") }

    if (showRefillDialog) {
        val refillQty = refillUnitsText.toIntOrNull()
        GlassDialog(
            onDismissRequest = { showRefillDialog = false },
            title = stringResource(R.string.meds_refill_dialog_title),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                if (refillQty != null && refillQty > 0) {
                    showRefillDialog = false
                    onRefill(refillQty)
                }
            },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmEnabled = refillQty != null && refillQty > 0,
        ) {
            GlassTextField(
                value = refillUnitsText,
                onValueChange = { refillUnitsText = it.filter(Char::isDigit) },
                label = stringResource(R.string.meds_refill_supply_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    DetailScaffold(
        title = plan?.name ?: stringResource(R.string.nav_meds),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        if (plan == null) {
            LoadingState(modifier = Modifier.padding(padding))
            return@DetailScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                plan.strength?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                plan.instructions?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.meds_schedule_summary,
                        plan.doses.joinToString(", ") { it.time }.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            state.selectedAdherence?.let { adh ->
                SectionHeader(title = stringResource(R.string.meds_adherence_section, adh.periodDays))
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${adh.percent}%",
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = if (adh.percent >= 80) LocalVitalColors.current.good else LocalVitalColors.current.watch,
                    )
                    Text(
                        text = stringResource(R.string.meds_adherence_counts, adh.taken, adh.scheduled),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    AdherenceBreakdownBar(
                        taken = adh.taken,
                        skipped = adh.skipped,
                        missed = adh.missed,
                        pending = adh.pending + adh.snoozedPending,
                    )
                }
            }

            // Side by side only while both labels fit on one line; otherwise stacked at full width.
            AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 2, horizontalSpacing = 12.dp) {
                PrimaryButton(
                    text = if (plan.status == "paused") {
                        stringResource(R.string.meds_resume_action)
                    } else {
                        stringResource(R.string.meds_pause_action)
                    },
                    onClick = onTogglePause,
                    loading = state.saving,
                )
                SecondaryButton(
                    text = stringResource(R.string.meds_refill_action),
                    onClick = { showRefillDialog = true },
                )
            }

            SecondaryButton(
                text = stringResource(R.string.meds_delete_plan_action),
                onClick = onDelete,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AdherenceBreakdownBar(
    taken: Int,
    skipped: Int,
    missed: Int,
    pending: Int,
) {
    val total = (taken + skipped + missed + pending).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        Segment(weight = taken.toFloat() / total, color = LocalVitalColors.current.good)
        Segment(weight = skipped.toFloat() / total, color = LocalVitalColors.current.watch)
        Segment(weight = missed.toFloat() / total, color = LocalVitalColors.current.alert)
        Segment(weight = pending.toFloat() / total, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
    }
    Spacer(Modifier.height(8.dp))
    // One line of three counts when they fit, otherwise a grid; the counts never run into each other.
    AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 3, horizontalSpacing = 12.dp, verticalSpacing = 4.dp) {
        Text(stringResource(R.string.meds_adherence_taken, taken), style = MaterialTheme.typography.labelSmall.tabular())
        Text(stringResource(R.string.meds_adherence_skipped, skipped), style = MaterialTheme.typography.labelSmall.tabular())
        Text(stringResource(R.string.meds_adherence_missed, missed), style = MaterialTheme.typography.labelSmall.tabular())
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(weight: Float, color: Color) {
    if (weight > 0f) {
        Box(modifier = Modifier.weight(weight).height(14.dp).background(color))
    }
}
