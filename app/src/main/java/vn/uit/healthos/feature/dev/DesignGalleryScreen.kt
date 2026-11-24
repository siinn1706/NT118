package vn.uit.healthos.feature.dev

import vn.uit.healthos.core.designsystem.glass.GlassRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.theme.HealthOsTheme
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.navigation.DesignGalleryRoute

fun NavGraphBuilder.devGraph(onBack: () -> Unit) {
    composable<DesignGalleryRoute> { DesignGalleryScreen(onBack = onBack) }
}

/** Debug-only catalogue of every design-system component, previewable in light and dark, plus a backend ping. */
@Composable
fun DesignGalleryScreen(onBack: () -> Unit, viewModel: DesignGalleryViewModel = hiltViewModel()) {
    var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    val ping by viewModel.ping.collectAsStateWithLifecycle()

    HealthOsTheme(themeMode = mode) {
        GlassRoot {
            DetailScaffold(title = "Design gallery", onBack = onBack) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { option ->
                            FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option.name) })
                        }
                    }
                    OrbSection()
                    SectionHeader("Metric tiles")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile("Bước chân", "6 482", Modifier.weight(1f), unit = "bước", icon = Icons.AutoMirrored.Rounded.DirectionsWalk, caption = "65% mục tiêu")
                        MetricTile("Calo", "1 240", Modifier.weight(1f), unit = "kcal", icon = Icons.Rounded.LocalFireDepartment, iconTint = LocalVitalColors.current.energy)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile("Nước", "1.5", Modifier.weight(1f), unit = "L", icon = Icons.Rounded.WaterDrop)
                        MetricTile("Nhịp tim", "72", Modifier.weight(1f), unit = "bpm", icon = Icons.Rounded.Favorite, iconTint = MaterialTheme.colorScheme.tertiary)
                    }
                    SectionHeader("Colour roles", actionLabel = "Action", onAction = {})
                    ColorSwatches()
                    SectionHeader("Buttons")
                    PrimaryButton("Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
                    PrimaryButton("Loading", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Secondary", onClick = {}, modifier = Modifier.fillMaxWidth())
                    SectionHeader("Typography")
                    TypographySamples()
                    SectionHeader("Backend")
                    BackendPing(viewModel.baseUrl, ping, viewModel::pingBackend)
                    SectionHeader("Error state")
                    BentoCard { ErrorState("Không kết nối được máy chủ.", onRetry = {}) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbSection() {
    val sliderState = rememberSliderState(value = 0.8f)
    val score = sliderState.value
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VitalOrb(score = score, contentDescription = "Điểm sức khỏe ${(score * 100).toInt()}") {
                Text(
                    "${(score * 100).toInt()}",
                    style = MaterialTheme.typography.displayMedium.tabular(),
                    color = Color.White,
                )
            }
        }
        Slider(state = sliderState, onValueChange = { sliderState.value = it }, onValueChangeFinished = null)
    }
}

@Composable
private fun ColorSwatches() {
    val c = MaterialTheme.colorScheme
    val v = LocalVitalColors.current
    val swatches = listOf(
        "primary" to c.primary, "secondary" to c.secondary, "tertiary" to c.tertiary, "error" to c.error,
        "good" to v.good, "watch" to v.watch, "alert" to v.alert, "energy" to v.energy,
    )
    swatches.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (name, color) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).background(color, MaterialTheme.shapes.medium))
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TypographySamples() {
    val t = MaterialTheme.typography
    Text("Sức khỏe của bạn", style = t.displaySmall)
    Text("Tiêu đề lớn — Title Large", style = t.titleLarge)
    Text("Nội dung chính đọc thoải mái, hỗ trợ đầy đủ dấu tiếng Việt: ằ ẵ ặ ổ ỡ ự.", style = t.bodyLarge)
    Text("Nhãn nhỏ · Label", style = t.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BackendPing(baseUrl: String, state: PingState, onPing: () -> Unit) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Text(baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when (state) {
                PingState.Idle -> "Chưa kiểm tra"
                PingState.Loading -> "Đang gọi GET /v1/health…"
                is PingState.Ok -> "OK: ${state.status} (${state.millis} ms)"
                is PingState.Failed -> "Lỗi: ${state.message}"
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PrimaryButton("Ping backend", onClick = onPing, loading = state == PingState.Loading)
    }
}
