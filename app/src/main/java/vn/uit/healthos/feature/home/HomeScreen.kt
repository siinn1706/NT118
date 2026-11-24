package vn.uit.healthos.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.feature.climate.ClimateCard
import vn.uit.healthos.feature.home.charts.WeeklyTrendChart
import vn.uit.healthos.feature.hydration.HydrationCard
import vn.uit.healthos.feature.steps.StepSensorService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import vn.uit.healthos.core.ui.asString

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onAddHydrationCup: () -> Unit,
    onSelectTrend: (TrendMetric) -> Unit,
    onLocationPermissionGranted: () -> Unit,
    onTriggerStepSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var activityGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

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
        if (granted) onLocationPermissionGranted()
    }

    LaunchedEffect(activityGranted) {
        if (activityGranted) {
            StepSensorService.start(context)
        }
    }

    if (state.errorMessage != null && state.lastUpdatedMs == null) {
        ErrorState(message = state.errorMessage.asString(), onRetry = onRefresh, modifier = modifier)
        return
    }

    val scorePercent = state.vitalScore?.let { (it * 100f).roundToInt() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + LocalBottomChromeInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Greeting + refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = if (state.userName.isNotBlank()) {
                        stringResource(R.string.home_greeting_named, state.userName)
                    } else {
                        stringResource(R.string.home_greeting_default)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.lastUpdatedMs?.let { ts ->
                    val time = Instant.ofEpochMilli(ts)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
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
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
            }
        }

        if (state.isOffline || state.pendingSyncCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (state.isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (state.pendingSyncCount > 0) {
                            stringResource(R.string.home_outbox_pending, state.pendingSyncCount)
                        } else {
                            stringResource(R.string.home_offline_banner)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // Vital Orb Hero Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.vitalScore != null && scorePercent != null) {
                    VitalOrb(
                        score = state.vitalScore,
                        size = 184.dp,
                        contentDescription = stringResource(R.string.home_vital_score_cd, scorePercent),
                    ) {
                        VitalScoreLabel(value = "$scorePercent", color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    val emptyCd = stringResource(R.string.home_vital_score_empty_cd)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(184.dp)
                            .semantics { contentDescription = emptyCd },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            VitalScoreLabel(
                                value = stringResource(R.string.home_vital_score_empty_value),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        state.vitalScore == null -> stringResource(R.string.home_vital_state_empty)
                        state.vitalScore >= 0.7f -> stringResource(R.string.home_vital_state_good)
                        state.vitalScore >= 0.5f -> stringResource(R.string.home_vital_state_watch)
                        else -> stringResource(R.string.home_vital_state_alert)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!activityGranted) {
            BentoCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.home_steps_permission_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val perms = buildList {
                                add(Manifest.permission.ACTIVITY_RECOGNITION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            activityLauncher.launch(perms)
                        },
                    ) {
                        Text(stringResource(R.string.action_enable))
                    }
                }
            }
        }

        // Bento Row 1: Steps + Calories
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = stringResource(R.string.home_metric_steps),
                value = state.stepsToday.toString(),
                unit = stringResource(R.string.unit_steps),
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                caption = stringResource(R.string.home_steps_goal_caption, state.stepGoal),
                onClick = onTriggerStepSync,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.home_metric_calories),
                value = state.caloriesToday?.roundToInt()?.toString() ?: "—",
                unit = stringResource(R.string.unit_kcal),
                icon = Icons.Rounded.LocalFireDepartment,
                iconTint = LocalVitalColors.current.energy,
                caption = state.calorieTarget?.let {
                    stringResource(R.string.home_calories_goal_caption, it)
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Resting HR tile if available
        state.restingHeartRate?.let { hr ->
            MetricTile(
                label = stringResource(R.string.home_metric_hr),
                value = hr.roundToInt().toString(),
                unit = stringResource(R.string.unit_bpm),
                icon = Icons.Rounded.Favorite,
                iconTint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Hydration
        HydrationCard(
            currentMl = state.hydrationMl,
            targetMl = state.climateAdvice.totalHydrationTargetMl,
            heatBonusMl = state.climateAdvice.heatBonusMl,
            onAddCup = onAddHydrationCup,
        )

        // Climate Coach
        if (state.climateConditions != null) {
            ClimateCard(
                conditions = state.climateConditions,
                advice = state.climateAdvice,
            )
        } else if (!locationGranted) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.climate_permission_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    ) {
                        Text(stringResource(R.string.action_enable))
                    }
                }
            }
        }

        // AI Advice
        val adviceTitle = state.aiAdvice?.title?.takeIf { it.isNotBlank() }
        val adviceBody = state.aiAdvice?.body?.takeIf { it.isNotBlank() } ?: state.aiInsightFallback?.asString()
        if (adviceBody != null) {
            SectionHeader(title = stringResource(R.string.home_ai_advice_section))
            BentoCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = adviceTitle ?: stringResource(R.string.home_ai_advice_default_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = adviceBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Exercise Suggestions (top 3)
        if (state.exercises.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.home_exercises_section))
            state.exercises.forEach { ex ->
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.title.asString(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                ex.message.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ex.durationMinutes?.let { mins ->
                            Text(
                                text = stringResource(R.string.unit_minutes_short, mins),
                                style = MaterialTheme.typography.labelLarge.tabular(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // 7-day trend chart (Vico)
        WeeklyTrendChart(
            selectedMetric = state.selectedTrend,
            stepPoints = state.stepTrend,
            heartRatePoints = state.heartRateTrend,
            onSelectMetric = onSelectTrend,
        )
    }
}

@Composable
private fun VitalScoreLabel(value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium.tabular(),
            color = color,
        )
        Text(
            text = stringResource(R.string.home_vital_score_label),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
