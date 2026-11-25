package vn.uit.healthos.feature.dev

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.component.GlassSlider
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import vn.uit.healthos.core.designsystem.motion.GlassMorphController
import vn.uit.healthos.core.designsystem.motion.GlassMorphPair
import vn.uit.healthos.core.designsystem.motion.LiquidSprings
import vn.uit.healthos.core.designsystem.motion.glassMaterialize
import vn.uit.healthos.core.designsystem.motion.rememberGlassMorphController
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion
import kotlin.math.roundToInt

/** "Chuyển động" demos: a platter morphing out of its trigger, two capsules merging through an SDF neck, materialize. */
@Composable
internal fun MotionSection() {
    Text(
        "Nút mở bảng kính từ chính nó; khi đóng, bảng thu về nút và nút giật nhẹ.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MorphDemo()
    Text(
        "Kéo hai viên kính lại gần nhau để thấy cổ nối SDF.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SdfPairDemo()
    Text(
        "Kính hiện ra và tan đi bằng khúc xạ, độ mờ và viền sáng, không bằng độ trong suốt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MaterializeDemo()
}

private val TriggerHeight = 48.dp
private val TriggerWideWidth = 132.dp
private val PlatterWidth = 220.dp
private val PlatterHeight = 152.dp

@Composable
private fun MorphDemo() {
    // The platter opens above and to the left of the trigger, so the recoil pushes the trigger down and right.
    val controller = rememberGlassMorphController(recoilDirection = Offset(0.6f, 0.8f))
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val edge = with(density) { 8.dp.toPx() }
        val triggerH = with(density) { TriggerHeight.toPx() }
        val wideW = with(density) { TriggerWideWidth.toPx() }
        val platterW = with(density) { PlatterWidth.toPx() }
        val platterH = with(density) { PlatterHeight.toPx() }
        // The trigger is anchored by its right edge, so its centre moves as it collapses to a circle.
        val triggerCenter = Offset(boxW - edge - triggerH / 2f, boxH - edge - triggerH / 2f)
        val platterCenter = Offset(edge + platterW / 2f, edge + platterH / 2f)

        if (controller.isShowing) {
            Platter(controller, triggerCenter, platterCenter, platterW, platterH)
        }
        MorphTrigger(controller, wideW, triggerH, edge)
    }
}

@Composable
private fun Platter(
    controller: GlassMorphController,
    triggerCenter: Offset,
    platterCenter: Offset,
    platterW: Float,
    platterH: Float,
) {
    val scaleBlock: GraphicsLayerScope.() -> Unit = remember(controller) {
        {
            scaleX = controller.platterScale
            scaleY = controller.platterScale
        }
    }
    Box(
        Modifier
            .offset {
                val c = controller.platterCenter(triggerCenter, platterCenter)
                IntOffset((c.x - platterW / 2f).roundToInt(), (c.y - platterH / 2f).roundToInt())
            }
            .size(PlatterWidth, PlatterHeight)
            .glassMaterialize(
                progress = { controller.glassProgress },
                shape = GlassTokens.CardShape,
                style = GlassStyle.Chrome,
                layerBlock = scaleBlock,
            )
            .padding(16.dp),
    ) {
        Column(
            Modifier.graphicsLayer {
                // Labels fade in only once the glass is dense, and leave first.
                alpha = ((controller.glassProgress - 0.5f) * 2f).coerceIn(0f, 1f)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Thêm bữa sáng", style = MaterialTheme.typography.bodyLarge)
            Text("Thêm bữa trưa", style = MaterialTheme.typography.bodyLarge)
            Text("Thêm bữa tối", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MorphTrigger(controller: GlassMorphController, wideW: Float, triggerH: Float, edge: Float) {
    val recoilBlock: GraphicsLayerScope.() -> Unit = remember(controller) {
        {
            translationX = controller.triggerPushDx
            translationY = controller.triggerPushDy
            scaleX = controller.triggerImpactScale
            scaleY = controller.triggerImpactScale
        }
    }
    Box(
        Modifier
            .layout { measurable, constraints ->
                // Width follows the collapse/splash spring in the layout phase only.
                val width = lerp(triggerH, wideW, controller.triggerWidthProgress).roundToInt().coerceAtLeast(1)
                val height = triggerH.roundToInt()
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (constraints.maxWidth - edge - width).roundToInt(),
                        (constraints.maxHeight - edge - height).roundToInt(),
                    )
                }
            }
            .liquidGlass(shape = Capsule(), style = GlassStyle.Chrome, layerBlock = recoilBlock)
            .clickable(role = Role.Button) { if (controller.isOpen) controller.close() else controller.open() }
            .semantics { contentDescription = if (controller.isOpen) "Đóng bảng" else "Mở bảng thêm bữa" },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (controller.isOpen) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = null)
            Text(
                "Thêm",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { alpha = ((controller.triggerWidthProgress - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    .layout { measurable, constraints ->
                        // The label takes no room while the trigger is a circle.
                        val placeable = measurable.measure(constraints.copy(minWidth = 0))
                        val w = (placeable.width * controller.triggerWidthProgress.coerceIn(0f, 1f)).roundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }
    }
}

private val CapsuleWidth = 112.dp
private val CapsuleHeight = 56.dp

@Composable
private fun SdfPairDemo() {
    val density = LocalDensity.current
    val capsuleW = with(density) { CapsuleWidth.toPx() }
    val capsuleH = with(density) { CapsuleHeight.toPx() }
    val maxBlendPx = with(density) { GlassMorphController.MaxBlendRadius.toPx() }
    var blend by remember { mutableFloatStateOf(1f) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val boxW = with(density) { maxWidth.toPx() }
        val top = (with(density) { maxHeight.toPx() } - capsuleH) / 2f
        val leftX = remember { mutableFloatStateOf(0f) }
        val rightX = remember(boxW) { mutableFloatStateOf(boxW - capsuleW) }
        val first = remember(top) { { Rect(leftX.floatValue, top, leftX.floatValue + capsuleW, top + capsuleH) } }
        val second = remember(top, rightX) { { Rect(rightX.floatValue, top, rightX.floatValue + capsuleW, top + capsuleH) } }
        val blendPx = remember(maxBlendPx) { { blend * maxBlendPx } }
        GlassMorphPair(
            first = first,
            second = second,
            blendRadius = blendPx,
            modifier = Modifier.fillMaxSize(),
        ) {
            DragHandle("Kéo viên trái", leftX, top, capsuleW) { it.coerceIn(0f, boxW - capsuleW) }
            DragHandle("Kéo viên phải", rightX, top, capsuleW) { it.coerceIn(0f, boxW - capsuleW) }
        }
    }
    GlassSlider(
        value = blend,
        onValueChange = { blend = it },
        contentDescription = "Bán kính nhập",
        valueText = { "${(it * GlassMorphController.MaxBlendRadius.value).roundToInt()} dp" },
    )
}

@Composable
private fun DragHandle(
    label: String,
    x: MutableFloatState,
    top: Float,
    widthPx: Float,
    clamp: (Float) -> Float,
) {
    Box(
        Modifier
            .offset { IntOffset(x.floatValue.roundToInt(), top.roundToInt()) }
            .size(CapsuleWidth, CapsuleHeight)
            .pointerInput(widthPx) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    x.floatValue = clamp(x.floatValue + dragAmount)
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("Kéo", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MaterializeDemo() {
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(true) }
    val progressProvider = remember(progress) { { progress.value } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 240.dp, height = 96.dp)
                .glassMaterialize(progress = progressProvider, shape = GlassTokens.CardShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Kính",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer { alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f) },
            )
        }
    }
    SecondaryButton(
        if (shown) "Tan đi" else "Hiện ra",
        onClick = {
            shown = !shown
            val target = if (shown) 1f else 0f
            scope.launch {
                if (reducedMotion) {
                    progress.animateTo(target, tween(GlassMorphController.ReducedMotionFadeMs, easing = LinearEasing))
                } else {
                    progress.animateTo(target, (if (shown) LiquidSprings.OpenPlatter else LiquidSprings.ClosePlatter).spec())
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    GlassSlider(
        value = progress.value,
        onValueChange = { value ->
            shown = value > 0.5f
            scope.launch { progress.snapTo(value) }
        },
        contentDescription = "Tiến độ hiện kính",
        valueText = { "${(it * 100).roundToInt()}%" },
    )
}
