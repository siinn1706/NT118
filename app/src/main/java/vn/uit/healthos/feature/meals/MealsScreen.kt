package vn.uit.healthos.feature.meals

import vn.uit.healthos.core.designsystem.component.LargeTitleHeader
import vn.uit.healthos.core.designsystem.component.GlassToolbarItem
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.glass.glassInset
import com.kyant.shapes.RoundedRectangle
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.LocalBottomChromeInset
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.core.ui.asString
import kotlin.math.roundToInt

@Composable
fun MealsScreen(
    state: MealsUiState,
    onRefresh: () -> Unit,
    onOpenSnap: () -> Unit,
    onOpenManual: () -> Unit,
    onDeleteMeal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val consumed = state.todayTotalCalories.roundToInt()
    val target = state.calorieTarget.coerceAtLeast(1)
    val fraction = (consumed.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val overTarget = consumed > target
    val pendingMeals = state.visiblePendingMeals

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp + LocalBottomChromeInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LargeTitleHeader(
            title = stringResource(R.string.nav_meals),
            actions = {
                GlassToolbarItem(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    onClick = onRefresh,
                )
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.infoMessage?.let { msg ->
                GlassBanner(message = msg.asString(), modifier = Modifier.fillMaxWidth())
            }

            // Calorie Ring Card
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    val ringColor = if (overTarget) LocalVitalColors.current.alert else LocalVitalColors.current.good
                    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                style = stroke,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$consumed", style = MaterialTheme.typography.titleLarge.tabular())
                            Text(
                                stringResource(R.string.unit_kcal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.meals_today_ring_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.meals_target_kcal, target),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val diff = target - consumed
                        Text(
                            text = if (diff >= 0) {
                                stringResource(R.string.meals_remaining_kcal, diff)
                            } else {
                                stringResource(R.string.meals_over_kcal, -diff)
                            },
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = if (diff >= 0) LocalVitalColors.current.good else LocalVitalColors.current.alert,
                        )
                    }
                }
            }

            // Action Buttons: Snap Photo & Manual Entry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.meals_snap_action),
                    onClick = onOpenSnap,
                    leadingIcon = Icons.Rounded.CameraAlt,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.meals_manual_action),
                    onClick = onOpenManual,
                    leadingIcon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
            }

            // Pending offline meals
            if (pendingMeals.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.meals_pending_section))
                pendingMeals.forEach { pending ->
                    BentoCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pending.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    stringResource(R.string.meals_pending_badge),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Text(
                                "${pending.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.labelLarge.tabular(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            // Today's synced meals
            SectionHeader(title = stringResource(R.string.meals_today_section))
            if (state.todayMeals.isEmpty() && pendingMeals.isEmpty()) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.meals_empty_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.todayMeals.forEach { meal ->
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!meal.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = meal.imageUrl,
                                    contentDescription = meal.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .glassInset(
                                            RoundedRectangle(14.dp),
                                            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Restaurant,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(meal.name, style = MaterialTheme.typography.titleSmall)
                                val nr = meal.nutritionResult
                                val proteinLabel = stringResource(R.string.meals_macro_protein, nr?.proteinG?.roundToInt() ?: 0)
                                val carbsLabel = stringResource(R.string.meals_macro_carbs, nr?.carbsG?.roundToInt() ?: 0)
                                val macroSummary = buildString {
                                    nr?.calories?.let { append("${it.roundToInt()} kcal") }
                                    if (nr?.proteinG != null) append(" · ").append(proteinLabel)
                                    if (nr?.carbsG != null) append(" · ").append(carbsLabel)
                                }.ifBlank { meal.status }
                                Text(
                                    macroSummary,
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            GlassIconButton(
                                onClick = { onDeleteMeal(meal.id) },
                                contentDescription = stringResource(R.string.action_delete),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 7-day calorie bar chart
            if (state.weeklyCalories.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.meals_weekly_chart_title))
                WeeklyCaloriesBarCard(points = state.weeklyCalories, targetCalories = target)
            }
        }
    }
}

@Composable
private fun WeeklyCaloriesBarCard(
    points: List<CalorieSummaryPointDto>,
    targetCalories: Int,
) {
    val maxVal = maxOf(targetCalories.toDouble(), points.maxOfOrNull { it.totalCalories } ?: 1.0, 1.0)
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.takeLast(7).forEach { pt ->
                val ratio = (pt.totalCalories / maxVal).toFloat().coerceIn(0.06f, 1f)
                val barColor = if (pt.totalCalories > targetCalories) {
                    LocalVitalColors.current.alert
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = "${pt.totalCalories.roundToInt()}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height((76 * ratio).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(barColor),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (pt.date.length >= 10) pt.date.substring(8, 10) else pt.date,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
