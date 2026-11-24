package vn.uit.healthos.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kyant.shapes.Capsule
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassChip
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassListRow
import vn.uit.healthos.core.designsystem.component.GlassSwitch
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTintMode
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import vn.uit.healthos.feature.climate.ClimateCard
import vn.uit.healthos.feature.home.charts.WeeklyTrendChart
import vn.uit.healthos.feature.hydration.HydrationCard
import vn.uit.healthos.feature.meds.DEFAULT_MED_TZID
import vn.uit.healthos.feature.meds.DoseCacheEntity
import vn.uit.healthos.feature.profile.ProfileAvatar
import vn.uit.healthos.feature.safety.SosCountdownActivity
import vn.uit.healthos.feature.safety.card.EmergencyCardActivity
import vn.uit.healthos.feature.steps.StepSensorService
import vn.uit.healthos.feature.widget.NextDoseSelector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Navigation out of Home; every tile and quick action leads somewhere. */
data class HomeNavigation(
    val onOpenMe: () -> Unit,
    val onOpenSafety: () -> Unit,
    val onOpenLeaderboard: () -> Unit,
    val onOpenMeals: () -> Unit,
    val onOpenSnap: () -> Unit,
    val onOpenPpg: () -> Unit,
    val onOpenMeds: () -> Unit,
    /** Opens the AI doctor; a non-null prompt is prefilled in the composer, never sent automatically. */
    val onOpenChat: (prompt: String?) -> Unit,
)

/** Actions that change Home's own data or layout. */
data class HomeActions(
    val onRefresh: () -> Unit,
    val onAddHydrationCup: () -> Unit,
    val onSelectTrend: (TrendMetric) -> Unit,
    val onLocationPermissionGranted: () -> Unit,
    val onTriggerStepSync: () -> Unit,
    val onMarkDoseTaken: (DoseCacheEntity) -> Unit,
    val onEditLayout: (Boolean) -> Unit,
    val onToggleTile: (HomeTile) -> Unit,
    val onMoveTile: (HomeTile, Int) -> Unit,
    val onResetLayout: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var activityGranted by remember { mutableStateOf(context.isGranted(Manifest.permission.ACTIVITY_RECOGNITION)) }
    var locationGranted by remember { mutableStateOf(context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
            activityGranted = true
            StepSensorService.start(context)
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted = granted
        if (granted) actions.onLocationPermissionGranted()
    }

    LaunchedEffect(activityGranted) {
        if (activityGranted) StepSensorService.start(context)
    }

    if (state.errorMessage != null && state.lastUpdatedMs == null) {
        ErrorState(message = state.errorMessage.asString(), onRetry = actions.onRefresh, modifier = modifier)
        return
    }

    val setupItems = buildList {
        if (!activityGranted) {
            add(
                SetupItem(Icons.AutoMirrored.Rounded.DirectionsWalk, R.string.home_steps_permission_prompt) {
                    val perms = buildList {
                        add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    activityLauncher.launch(perms)
                },
            )
        }
        if (!locationGranted) {
            add(
                SetupItem(Icons.Rounded.LocationOn, R.string.climate_permission_prompt) {
                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp + LocalBottomChromeInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeHeader(
                state = state,
                onOpenMe = navigation.onOpenMe,
                onOpenSafety = navigation.onOpenSafety,
                onLongPressSos = { context.startActivity(SosCountdownActivity.manualSosIntent(context)) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            QuickActionsRow(
                onSnap = navigation.onOpenSnap,
                onAddCup = actions.onAddHydrationCup,
                onMeasureHr = navigation.onOpenPpg,
                onAskAi = { navigation.onOpenChat(null) },
                onEmergencyCard = { context.startActivity(Intent(context, EmergencyCardActivity::class.java)) },
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOffline || state.pendingSyncCount > 0) {
                    GlassBanner(
                        message = if (state.pendingSyncCount > 0) {
                            stringResource(R.string.home_outbox_pending, state.pendingSyncCount)
                        } else {
                            stringResource(R.string.home_offline_banner)
                        },
                        icon = if (state.isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Sync,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (setupItems.isNotEmpty()) SetupCard(items = setupItems)

                if (state.editingLayout) {
                    LayoutEditor(
                        layout = state.layout,
                        onToggle = actions.onToggleTile,
                        onMove = actions.onMoveTile,
                        onReset = actions.onResetLayout,
                        onDone = { actions.onEditLayout(false) },
                    )
                } else {
                    BentoGrid(state = state, actions = actions, navigation = navigation)
                    PlainButton(
                        text = stringResource(R.string.home_edit_layout),
                        onClick = { actions.onEditLayout(true) },
                        leadingIcon = Icons.Rounded.Tune,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeHeader(
    state: HomeUiState,
    onOpenMe: () -> Unit,
    onOpenSafety: () -> Unit,
    onLongPressSos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = state.profileName.ifBlank { state.userName }
    val openMeLabel = stringResource(R.string.home_open_me_cd)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClickLabel = openMeLabel, role = Role.Button, onClick = onOpenMe)
                .semantics { contentDescription = openMeLabel },
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(name = name.ifBlank { "H" }, avatarUrl = state.avatarUrl, size = 44)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (name.isNotBlank()) {
                    stringResource(R.string.home_greeting_named, name)
                } else {
                    stringResource(R.string.home_greeting_default)
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            state.lastUpdatedMs?.let { ts ->
                val time = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(TimeFormat)
                Text(
                    text = if (state.isOffline) {
                        stringResource(R.string.home_offline_cached, time)
                    } else {
                        stringResource(R.string.home_last_updated, time)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SosPill(onClick = onOpenSafety, onLongClick = onLongPressSos)
    }
}

/**
 * Always-visible red glass SOS capsule. A tap opens Safety; a long press starts a manual SOS through
 * [SosCountdownActivity], whose countdown can still be cancelled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SosPill(onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.home_sos_pill_cd)
    val clickLabel = stringResource(R.string.home_sos_click_label)
    val longClickLabel = stringResource(R.string.home_sos_long_click_label)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .liquidGlass(Capsule(), GlassStyle.Control, tint = scheme.error, tintMode = GlassTintMode.Prominent)
            .combinedClickable(
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
                role = Role.Button,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Sos, contentDescription = null, tint = scheme.onError, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.home_sos_pill), style = MaterialTheme.typography.labelLarge, color = scheme.onError)
    }
}

@Composable
private fun QuickActionsRow(
    onSnap: () -> Unit,
    onAddCup: () -> Unit,
    onMeasureHr: () -> Unit,
    onAskAi: () -> Unit,
    onEmergencyCard: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassChip(stringResource(R.string.home_quick_snap), onSnap, leadingIcon = Icons.Rounded.CameraAlt)
        GlassChip(stringResource(R.string.home_quick_water), onAddCup, leadingIcon = Icons.Rounded.WaterDrop)
        GlassChip(stringResource(R.string.home_quick_ppg), onMeasureHr, leadingIcon = Icons.Rounded.Favorite)
        GlassChip(stringResource(R.string.home_quick_ask_ai), onAskAi, leadingIcon = Icons.AutoMirrored.Rounded.Chat)
        GlassChip(stringResource(R.string.home_quick_card), onEmergencyCard, leadingIcon = Icons.Rounded.MedicalServices)
    }
}

private class SetupItem(val icon: ImageVector, val text: Int, val onEnable: () -> Unit)

/** Every missing permission in one card, instead of a separate prompt card per feature. */
@Composable
private fun SetupCard(items: List<SetupItem>) {
    BentoCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.home_setup_title, items.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(item.icon, contentDescription = null)
                Text(
                    text = stringResource(item.text),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                PlainButton(text = stringResource(R.string.action_enable), onClick = item.onEnable)
            }
        }
    }
}

@Composable
private fun BentoGrid(state: HomeUiState, actions: HomeActions, navigation: HomeNavigation) {
    val tiles = state.layout.visible.filter { tile -> tile.hasContent(state) }
    HomeLayout.rows(tiles).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { tile ->
                Box(Modifier.weight(1f)) {
                    HomeTileContent(tile = tile, state = state, actions = actions, navigation = navigation)
                }
            }
        }
    }
}

/** Tiles without data to show are left out of the grid rather than drawn empty. */
private fun HomeTile.hasContent(state: HomeUiState): Boolean = when (this) {
    HomeTile.AI_TIP -> state.aiAdvice?.body?.isNotBlank() == true || state.aiInsightFallback != null
    HomeTile.CLIMATE -> state.climateConditions != null
    HomeTile.EXERCISES -> state.exercises.isNotEmpty()
    else -> true
}

@Composable
private fun HomeTileContent(tile: HomeTile, state: HomeUiState, actions: HomeActions, navigation: HomeNavigation) {
    when (tile) {
        HomeTile.VITAL -> VitalTile(state.vitalScore)
        HomeTile.STEPS -> MetricTile(
            label = stringResource(R.string.home_metric_steps),
            value = state.stepsToday.toString(),
            unit = stringResource(R.string.unit_steps),
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            caption = stringResource(R.string.home_steps_goal_caption, state.stepGoal),
            onClick = {
                // Today's count is pushed first so the leaderboard ranks the latest steps.
                actions.onTriggerStepSync()
                navigation.onOpenLeaderboard()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        HomeTile.CALORIES -> MetricTile(
            label = stringResource(R.string.home_metric_calories),
            value = state.caloriesToday?.roundToInt()?.toString() ?: "—",
            unit = stringResource(R.string.unit_kcal),
            icon = Icons.Rounded.LocalFireDepartment,
            iconTint = LocalVitalColors.current.energy,
            caption = state.calorieTarget?.let { stringResource(R.string.home_calories_goal_caption, it) },
            onClick = navigation.onOpenMeals,
            modifier = Modifier.fillMaxWidth(),
        )
        HomeTile.HEART_RATE -> MetricTile(
            label = stringResource(R.string.home_metric_hr),
            value = state.restingHeartRate?.roundToInt()?.toString() ?: "—",
            unit = stringResource(R.string.unit_bpm),
            icon = Icons.Rounded.Favorite,
            iconTint = MaterialTheme.colorScheme.tertiary,
            caption = stringResource(R.string.home_hr_measure_caption),
            onClick = navigation.onOpenPpg,
            modifier = Modifier.fillMaxWidth(),
        )
        HomeTile.WATER -> HydrationCard(
            currentMl = state.hydrationMl,
            targetMl = state.climateAdvice.totalHydrationTargetMl,
            heatBonusMl = state.climateAdvice.heatBonusMl,
            onAddCup = actions.onAddHydrationCup,
        )
        HomeTile.NEXT_DOSE -> NextDoseTile(
            dose = state.nextDose,
            onTaken = actions.onMarkDoseTaken,
            onOpenMeds = navigation.onOpenMeds,
        )
        HomeTile.AI_TIP -> AiTipTile(state = state, onAsk = navigation.onOpenChat)
        HomeTile.CLIMATE -> state.climateConditions?.let { ClimateCard(conditions = it, advice = state.climateAdvice) }
        HomeTile.EXERCISES -> ExercisesTile(exercises = state.exercises, onAsk = navigation.onOpenChat)
        HomeTile.WEEKLY_CHART -> WeeklyTrendChart(
            selectedMetric = state.selectedTrend,
            stepPoints = state.stepTrend,
            heartRatePoints = state.heartRateTrend,
            onSelectMetric = actions.onSelectTrend,
        )
    }
}

@Composable
private fun VitalTile(score: Float?) {
    val percent = score?.let { (it * 100f).roundToInt() }
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (score != null && percent != null) {
                VitalOrb(
                    score = score,
                    size = 104.dp,
                    contentDescription = stringResource(R.string.home_vital_score_cd, percent),
                ) {
                    Text(
                        text = "$percent",
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                val emptyCd = stringResource(R.string.home_vital_score_empty_cd)
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .glassInset(CircleShape)
                        .semantics { contentDescription = emptyCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_vital_score_empty_value),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = when {
                    score == null -> stringResource(R.string.home_vital_short_empty)
                    score >= 0.7f -> stringResource(R.string.home_vital_short_good)
                    score >= 0.5f -> stringResource(R.string.home_vital_short_watch)
                    else -> stringResource(R.string.home_vital_short_alert)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextDoseTile(dose: DoseCacheEntity?, onTaken: (DoseCacheEntity) -> Unit, onOpenMeds: () -> Unit) {
    BentoCard(onClick = onOpenMeds, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.home_next_dose_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (dose == null) {
            Text(
                text = stringResource(R.string.home_next_dose_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@BentoCard
        }
        val dueMs = NextDoseSelector.dueAtMs(dose)
        val time = Instant.ofEpochMilli(dueMs).atZone(ZoneId.of(DEFAULT_MED_TZID)).format(TimeFormat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (dueMs < System.currentTimeMillis()) {
                        stringResource(R.string.home_next_dose_overdue, time)
                    } else {
                        time
                    },
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(dose.planName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                dose.strength?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PrimaryButton(
                text = stringResource(R.string.meds_action_taken),
                onClick = { onTaken(dose) },
                leadingIcon = Icons.Rounded.Check,
            )
        }
    }
}

@Composable
private fun AiTipTile(state: HomeUiState, onAsk: (String?) -> Unit) {
    val title = state.aiAdvice?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_ai_advice_default_title)
    val body = state.aiAdvice?.body?.takeIf { it.isNotBlank() } ?: state.aiInsightFallback?.asString() ?: return
    val prompt = stringResource(R.string.home_ai_tip_prompt, body).take(MAX_PROMPT_CHARS)
    BentoCard(
        onClick = { onAsk(prompt) },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_ai_tip_ask),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExercisesTile(exercises: List<ExerciseSuggestionUi>, onAsk: (String?) -> Unit) {
    BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        exercises.forEachIndexed { index, ex ->
            val title = ex.title.asString()
            val message = ex.message.asString()
            val prompt = stringResource(R.string.home_exercise_prompt, title, message).take(MAX_PROMPT_CHARS)
            GlassListRow(
                title = title,
                subtitle = message,
                leadingIcon = Icons.Rounded.FitnessCenter,
                iconBackground = MaterialTheme.colorScheme.primary,
                value = ex.durationMinutes?.let { stringResource(R.string.unit_minutes_short, it) },
                onClick = { onAsk(prompt) },
                divider = index < exercises.lastIndex,
            )
        }
    }
}

/** "Sửa bố cục": show or hide each tile and move it up or down; changes are saved as they are made. */
@Composable
private fun LayoutEditor(
    layout: HomeLayout,
    onToggle: (HomeTile) -> Unit,
    onMove: (HomeTile, Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.home_edit_layout),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(R.string.home_edit_layout_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        layout.order.forEachIndexed { index, tile ->
            val label = stringResource(tile.label)
            val shown = !layout.isHidden(tile)
            GlassListRow(
                title = label,
                subtitle = stringResource(if (shown) R.string.home_tile_shown else R.string.home_tile_hidden),
                leadingIcon = Icons.Rounded.Dashboard,
                iconBackground = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(
                            onClick = { onMove(tile, -1) },
                            contentDescription = stringResource(R.string.home_tile_move_up, label),
                            enabled = index > 0,
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) }
                        GlassIconButton(
                            onClick = { onMove(tile, 1) },
                            contentDescription = stringResource(R.string.home_tile_move_down, label),
                            enabled = index < layout.order.lastIndex,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                        GlassSwitch(checked = shown, onCheckedChange = null)
                    }
                },
                onClick = { onToggle(tile) },
                showChevron = false,
                divider = index < layout.order.lastIndex,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(
            text = stringResource(R.string.home_edit_layout_reset),
            onClick = onReset,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.home_edit_layout_done),
            onClick = onDone,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun android.content.Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Same cap the Ask AI widget applies to a prefilled prompt. */
private const val MAX_PROMPT_CHARS = 500
