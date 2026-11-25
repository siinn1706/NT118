package vn.uit.healthos.feature.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.AccentPicker
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.DetailScaffold
import vn.uit.healthos.core.designsystem.component.ErrorState
import vn.uit.healthos.core.designsystem.component.GlassBanner
import vn.uit.healthos.core.designsystem.component.GlassBannerTone
import vn.uit.healthos.core.designsystem.component.GlassChip
import vn.uit.healthos.core.designsystem.component.GlassDialog
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.GlassListRow
import vn.uit.healthos.core.designsystem.component.GlassProgressBar
import vn.uit.healthos.core.designsystem.component.GlassSegmentedControl
import vn.uit.healthos.core.designsystem.component.GlassSlider
import vn.uit.healthos.core.designsystem.component.GlassSnackbarHost
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.component.GlassSwitch
import vn.uit.healthos.core.designsystem.component.GlassTextField
import vn.uit.healthos.core.designsystem.component.GlassToolbar
import vn.uit.healthos.core.designsystem.component.GlassToolbarItem
import vn.uit.healthos.core.designsystem.component.LargeTitleHeader
import vn.uit.healthos.core.designsystem.component.MetricTile
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.component.SectionHeader
import vn.uit.healthos.core.designsystem.component.VitalOrb
import vn.uit.healthos.core.designsystem.glass.GlassRoot
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.HealthOsTheme
import vn.uit.healthos.core.designsystem.theme.LocalVitalColors
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.designsystem.theme.tabular
import vn.uit.healthos.navigation.DesignGalleryRoute

fun NavGraphBuilder.devGraph(onBack: () -> Unit) {
    composable<DesignGalleryRoute> { DesignGalleryScreen(onBack = onBack) }
}

/**
 * Debug-only catalogue of every design-system primitive, previewable in light and dark with any accent preset,
 * background style and reduced transparency, plus a backend ping. The preview theme is applied through a nested
 * GlassRoot whose aurora is painted in window coordinates, so it lines up with the app root without a seam.
 */
@Composable
fun DesignGalleryScreen(onBack: () -> Unit, viewModel: DesignGalleryViewModel = hiltViewModel()) {
    var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var accent by remember { mutableStateOf(AccentPreset.Default) }
    var background by remember { mutableStateOf(BackgroundStyle.Default) }
    var reducedTransparency by remember { mutableStateOf(false) }
    val ping by viewModel.ping.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HealthOsTheme(
        themeMode = mode,
        accentPreset = accent,
        backgroundStyle = background,
        reducedTransparency = reducedTransparency,
    ) {
        GlassRoot {
            DetailScaffold(title = "Design gallery", onBack = onBack) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppearanceSection(
                        mode = mode,
                        onMode = { mode = it },
                        accent = accent,
                        onAccent = { accent = it },
                        background = background,
                        onBackground = { background = it },
                        reducedTransparency = reducedTransparency,
                        onReducedTransparency = { reducedTransparency = it },
                    )
                    SectionHeader("Accent presets (light / dark)")
                    AccentMatrix()
                    SectionHeader("Large title header")
                    LargeTitleHeader(
                        title = "Hôm nay",
                        subtitle = "Thứ Ba, 24 tháng 11",
                    ) {
                        GlassToolbarItem(Icons.Rounded.Search, "Tìm kiếm", onClick = {})
                        GlassToolbarItem(Icons.Rounded.Notifications, "Thông báo", onClick = {})
                    }
                    OrbSection()
                    SectionHeader("Metric tiles")
                    // Two columns while every tile fits its half unbroken, one full-width column otherwise.
                    AdaptiveColumns(maxColumns = 2, horizontalSpacing = 12.dp, equalHeight = true) {
                        MetricTile("Bước chân", "6 482", unit = "bước", icon = Icons.AutoMirrored.Rounded.DirectionsWalk, caption = "65% mục tiêu")
                        MetricTile("Calo", "1 240", unit = "kcal", icon = Icons.Rounded.LocalFireDepartment, iconTint = LocalVitalColors.current.energy)
                        MetricTile("Nước", "1.5", unit = "L", icon = Icons.Rounded.WaterDrop)
                        MetricTile("Nhịp tim", "72", unit = "bpm", icon = Icons.Rounded.Favorite, iconTint = MaterialTheme.colorScheme.tertiary)
                    }
                    SectionHeader("Chuyển động")
                    MotionSection()
                    SectionHeader("Buttons on the backdrop")
                    ButtonsSection()
                    SectionHeader("Buttons inside a card (inset)")
                    BentoCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("Lưu thay đổi", onClick = {}, modifier = Modifier.fillMaxWidth())
                            SecondaryButton("Để sau", onClick = {}, modifier = Modifier.fillMaxWidth())
                            GlassChip("Bộ lọc", onClick = {}, selected = true)
                        }
                    }
                    SectionHeader("Chips")
                    ChipsSection()
                    SectionHeader("Text fields")
                    TextFieldsSection()
                    SectionHeader("Controls")
                    ControlsSection()
                    SectionHeader("Progress")
                    ProgressSection()
                    SectionHeader("Banners")
                    GlassBanner("Đã đồng bộ dữ liệu từ Health Connect.", tone = GlassBannerTone.Info)
                    GlassBanner("Bữa ăn đã được ghi nhận.", tone = GlassBannerTone.Success, title = "Hoàn tất")
                    GlassBanner(
                        "Bạn chưa uống thuốc buổi sáng.",
                        tone = GlassBannerTone.Warning,
                        actionLabel = "Xem lịch",
                        onAction = {},
                    )
                    GlassBanner("Không kết nối được máy chủ.", tone = GlassBannerTone.Error, onDismiss = {})
                    SectionHeader("List rows")
                    ListSection()
                    SectionHeader("Dialog and snackbar")
                    OverlaysSection(snackbarHostState)
                    SectionHeader("Colour roles", actionLabel = "Action", onAction = {})
                    ColorSwatches()
                    SectionHeader("Typography")
                    TypographySamples()
                    SectionHeader("Backend")
                    BackendPing(viewModel.baseUrl, ping, viewModel::pingBackend)
                    SectionHeader("Error state")
                    BentoCard { ErrorState("Không kết nối được máy chủ.", onRetry = {}) }
                }
            }
            GlassSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun AppearanceSection(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    accent: AccentPreset,
    onAccent: (AccentPreset) -> Unit,
    background: BackgroundStyle,
    onBackground: (BackgroundStyle) -> Unit,
    reducedTransparency: Boolean,
    onReducedTransparency: (Boolean) -> Unit,
) {
    GlassSegmentedControl(
        options = listOf("Hệ thống", "Sáng", "Tối"),
        selectedIndex = mode.ordinal,
        onSelect = { onMode(ThemeMode.entries[it]) },
    )
    GlassSegmentedControl(
        options = BackgroundStyle.entries.map { stringResource(it.label) },
        selectedIndex = background.ordinal,
        onSelect = { onBackground(BackgroundStyle.entries[it]) },
    )
    BentoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        GlassListRow(
            title = "Màu nhấn",
            subtitle = stringResource(accent.label),
            leadingIcon = Icons.Rounded.Palette,
            iconBackground = MaterialTheme.colorScheme.primary,
            divider = true,
        )
        AccentPicker(selected = accent, onSelect = onAccent, modifier = Modifier.padding(horizontal = 12.dp))
        GlassListRow(
            title = "Giảm trong suốt",
            subtitle = "Bề mặt đặc thay cho kính",
            onClick = { onReducedTransparency(!reducedTransparency) },
            toggled = reducedTransparency,
            trailing = { GlassSwitch(checked = reducedTransparency, onCheckedChange = null) },
        )
    }
}

/** Every accent preset in both appearances, with its onPrimary label, to eyeball the tuned pairs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentMatrix() {
    BentoCard(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentPreset.entries.forEach { preset ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(preset.light, preset.dark).forEach { colors ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(colors.primary, MaterialTheme.shapes.small),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Aa", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text(preset.hex, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ButtonsSection() {
    PrimaryButton("Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
    PrimaryButton("Đang tải", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
    PrimaryButton("Xóa dữ liệu", onClick = {}, destructive = true, leadingIcon = Icons.Rounded.Delete, modifier = Modifier.fillMaxWidth())
    PrimaryButton("Không khả dụng", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    SecondaryButton("Secondary", onClick = {}, modifier = Modifier.fillMaxWidth())
    SecondaryButton("Hủy đăng ký", onClick = {}, destructive = true, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlainButton("Plain", onClick = {})
        PlainButton("Xóa", onClick = {}, destructive = true)
        GlassIconButton(onClick = {}, contentDescription = "Thêm") {
            Icon(Icons.Rounded.Add, contentDescription = null)
        }
        GlassToolbar {
            GlassToolbarItem(Icons.Rounded.Share, "Chia sẻ", onClick = {})
            GlassToolbarItem(Icons.Rounded.MoreHoriz, "Thêm tùy chọn", onClick = {})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection() {
    var selected by remember { mutableStateOf(setOf("Sáng")) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Sáng", "Trưa", "Tối").forEach { label ->
            GlassChip(
                label = label,
                selected = label in selected,
                onClick = { selected = if (label in selected) selected - label else selected + label },
            )
        }
        GlassChip("Thêm bữa", onClick = {}, leadingIcon = Icons.Rounded.Add)
    }
}

@Composable
private fun TextFieldsSection() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("72,5") }
    GlassTextField(
        value = email,
        onValueChange = { email = it },
        label = "Email",
        placeholder = "ban@vidu.com",
        leadingIcon = Icons.Rounded.Email,
        supportingText = "Trên nền kính",
    )
    BentoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextField(value = password, onValueChange = { password = it }, label = "Mật khẩu", isPassword = true)
            GlassTextField(
                value = weight,
                onValueChange = { weight = it },
                label = "Cân nặng (kg)",
                errorText = if (weight.isBlank()) "Vui lòng nhập cân nặng" else "Giá trị không hợp lệ",
            )
        }
    }
}

@Composable
private fun ControlsSection() {
    var segment by remember { mutableIntStateOf(1) }
    var switchOn by remember { mutableStateOf(true) }
    var level by remember { mutableFloatStateOf(0.4f) }
    var stepped by remember { mutableFloatStateOf(3f) }
    GlassSegmentedControl(options = listOf("Ngày", "Tuần", "Tháng"), selectedIndex = segment, onSelect = { segment = it })
    BentoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassSegmentedControl(options = listOf("kg", "lb"), selectedIndex = segment % 2, onSelect = { segment = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nhắc uống nước", Modifier.weight(1f))
                GlassSwitch(checked = switchOn, onCheckedChange = { switchOn = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Không khả dụng", Modifier.weight(1f))
                GlassSwitch(checked = false, onCheckedChange = {}, enabled = false)
            }
            GlassSlider(
                value = level,
                onValueChange = { level = it },
                contentDescription = "Độ sáng",
                valueText = { "${(it * 100).toInt()}%" },
            )
            GlassSlider(
                value = stepped,
                onValueChange = { stepped = it },
                valueRange = 1f..5f,
                steps = 3,
                contentDescription = "Mức vận động",
                valueText = { "Mức ${it.toInt()}" },
            )
        }
    }
}

@Composable
private fun ProgressSection() {
    BentoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassProgressBar(progress = { 0.65f })
            GlassProgressBar()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSpinner()
                GlassSpinner(size = 36.dp, color = MaterialTheme.colorScheme.primary, contentDescription = "Đang tải")
            }
        }
    }
}

@Composable
private fun ListSection() {
    var notifications by remember { mutableStateOf(true) }
    BentoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        GlassListRow(
            title = "Thông báo",
            leadingIcon = Icons.Rounded.Notifications,
            iconBackground = Color(0xFFFF3B30),
            onClick = { notifications = !notifications },
            toggled = notifications,
            trailing = { GlassSwitch(checked = notifications, onCheckedChange = null) },
            divider = true,
        )
        GlassListRow(
            title = "Mục tiêu bước chân",
            value = "8 000",
            leadingIcon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            iconBackground = Color(0xFF34C759),
            onClick = {},
            divider = true,
        )
        GlassListRow(title = "Xóa dữ liệu cục bộ", subtitle = "Không thể hoàn tác", destructive = true, onClick = {})
    }
}

@Composable
private fun OverlaysSection(snackbarHostState: SnackbarHostState) {
    var showDialog by remember { mutableStateOf(false) }
    var showDestructive by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    SecondaryButton("Mở hộp thoại", onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth())
    SecondaryButton("Hộp thoại xóa", onClick = { showDestructive = true }, destructive = true, modifier = Modifier.fillMaxWidth())
    SecondaryButton(
        "Hiện snackbar",
        onClick = { scope.launch { snackbarHostState.showSnackbar("Đã lưu bữa ăn", actionLabel = "Hoàn tác", withDismissAction = true) } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (showDialog) {
        GlassDialog(
            onDismissRequest = { showDialog = false },
            title = "Thêm ghi chú",
            message = "Ghi chú sẽ được lưu cùng bữa ăn.",
            confirmLabel = "Lưu",
            onConfirm = { showDialog = false },
            dismissLabel = "Hủy",
            confirmEnabled = note.isNotBlank(),
        ) {
            GlassTextField(value = note, onValueChange = { note = it }, label = "Ghi chú")
        }
    }
    if (showDestructive) {
        GlassDialog(
            onDismissRequest = { showDestructive = false },
            title = "Xóa toàn bộ dữ liệu?",
            message = "Dữ liệu trên thiết bị này sẽ bị xóa và không thể khôi phục.",
            confirmLabel = "Xóa",
            onConfirm = { showDestructive = false },
            dismissLabel = "Hủy",
            destructive = true,
            icon = Icons.Rounded.Delete,
        )
    }
}

@Composable
private fun OrbSection() {
    var score by remember { mutableFloatStateOf(0.8f) }
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
        GlassSlider(value = score, onValueChange = { score = it }, contentDescription = "Điểm sức khỏe")
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
