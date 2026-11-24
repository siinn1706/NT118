package vn.uit.healthos.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import vn.uit.healthos.R
import vn.uit.healthos.feature.home.VitalLevel
import java.text.NumberFormat
import java.util.Locale

/**
 * Widget palette, tuned for the translucent glass panel in both themes. Text colours keep at least 4.5:1 against
 * the panel even when a dark (light theme) or bright (dark theme) wallpaper shows through it.
 */
internal object WidgetColors {
    val onPanel: ColorProvider = ColorProvider(day = Color(0xFF1C1C1E), night = Color(0xFFF5F5F7))
    val onPanelMuted: ColorProvider = ColorProvider(day = Color(0xFF3A3A3C), night = Color(0xFFC7C7CC))
    val accent: ColorProvider = ColorProvider(day = Color(0xFF0B5F58), night = Color(0xFF5EEAD4))
    val onAccentPill: ColorProvider = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val onChip: ColorProvider = ColorProvider(day = Color(0xFF0B5F58), night = Color(0xFFCCFBF1))
    val sosText: ColorProvider = ColorProvider(day = Color(0xFFBE123C), night = Color(0xFFFDA4AF))
    val onSos: ColorProvider = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val energy: ColorProvider = ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFCD34D))
    val track: ColorProvider = ColorProvider(day = Color(0x1F000000), night = Color(0x33FFFFFF))

    // Ring bitmaps are drawn once per render, so they use mid-tone colours readable on either theme.
    const val RING_TRACK = 0x40808080
    const val RING_GOOD = 0xFF14B8A6.toInt()
    const val RING_WATCH = 0xFFF59E0B.toInt()
    const val RING_ALERT = 0xFFF43F5E.toInt()
    const val RING_WATER = 0xFF38BDF8.toInt()
    const val RING_ENERGY = 0xFFF59E0B.toInt()

    fun ringColorFor(level: VitalLevel?): Int = when (level) {
        VitalLevel.GOOD -> RING_GOOD
        VitalLevel.WATCH -> RING_WATCH
        VitalLevel.ALERT -> RING_ALERT
        null -> RING_TRACK
    }
}

/** Fraction of [target] reached, clamped to 0..1; 0 when there is no target. */
internal fun progressOf(value: Double, target: Double): Float =
    if (target <= 0.0) 0f else (value / target).coerceIn(0.0, 1.0).toFloat()

private val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")

/** Groups thousands the Vietnamese way (`12.345`). */
internal fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(VIETNAMESE).format(value)

internal fun formatCount(value: Int): String = formatCount(value.toLong())

/** Glass panel filling the widget; on Android 12+ it is clipped to the launcher's widget corner radius. */
@Composable
internal fun GlassPanel(
    onClick: Action?,
    modifier: GlanceModifier = GlanceModifier,
    padding: Dp = 12.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    var panel = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_glass_bg))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        panel = panel.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    }
    if (onClick != null) panel = panel.clickable(onClick)
    Box(modifier = panel.then(modifier).padding(padding), contentAlignment = contentAlignment) {
        content()
    }
}

/** Opens the app on [destination]. */
@Composable
internal fun openAppAction(destination: WidgetIntents.Destination, prompt: String? = null): Action =
    actionStartActivity(WidgetIntents.open(LocalContext.current, destination, prompt))

/** Lock or sign-in message shown instead of the numbers; the whole widget opens the app. */
@Composable
internal fun PrivacyNotice(content: WidgetContent<*>, compact: Boolean) {
    val context = LocalContext.current
    val (title, body) = when (content) {
        WidgetContent.Locked -> R.string.widget_locked_title to R.string.widget_locked_body
        else -> R.string.widget_signed_out_title to R.string.widget_signed_out_body
    }
    GlassPanel(
        onClick = openAppAction(WidgetIntents.Destination.HOME),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetIcon(R.drawable.widget_ic_lock, WidgetColors.accent, size = 18.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = context.getString(title),
                    style = TextStyle(color = WidgetColors.onPanel, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetIcon(R.drawable.widget_ic_lock, WidgetColors.accent, size = 24.dp)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = context.getString(title),
                    style = TextStyle(
                        color = WidgetColors.onPanel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = context.getString(body),
                    style = TextStyle(color = WidgetColors.onPanelMuted, fontSize = 12.sp, textAlign = TextAlign.Center),
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
internal fun WidgetIcon(@DrawableRes icon: Int, tint: ColorProvider, size: Dp = 16.dp) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Small icon + caption row used as a widget header. */
@Composable
internal fun WidgetHeader(@DrawableRes icon: Int, label: String, tint: ColorProvider = WidgetColors.accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(icon, tint, size = 14.dp)
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(color = WidgetColors.onPanelMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
internal fun ValueText(text: String, fontSize: TextUnit = 22.sp, color: ColorProvider = WidgetColors.onPanel) {
    Text(text = text, style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
internal fun CaptionText(text: String, maxLines: Int = 1, color: ColorProvider = WidgetColors.onPanelMuted) {
    Text(text = text, style = TextStyle(color = color, fontSize = 12.sp), maxLines = maxLines)
}

@Composable
internal fun ProgressBar(progress: Float, color: ColorProvider = WidgetColors.accent) {
    LinearProgressIndicator(
        progress = progress,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = color,
        backgroundColor = WidgetColors.track,
    )
}

enum class PillStyle(@param:DrawableRes val background: Int) {
    ACCENT(R.drawable.widget_pill_accent_bg),
    CHIP(R.drawable.widget_chip_bg),
    SOS(R.drawable.widget_pill_sos_bg),
}

/** Capsule button; an empty [text] gives an icon-only button, so pass a [contentDescription] for it. */
@Composable
internal fun PillButton(
    text: String,
    onClick: Action,
    style: PillStyle,
    modifier: GlanceModifier = GlanceModifier,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = null,
    maxLines: Int = 1,
) {
    val content = when (style) {
        PillStyle.ACCENT -> WidgetColors.onAccentPill
        PillStyle.CHIP -> WidgetColors.onChip
        PillStyle.SOS -> WidgetColors.onSos
    }
    var pill = modifier
        .background(ImageProvider(style.background))
        .padding(horizontal = 12.dp, vertical = 7.dp)
        .clickable(onClick)
    if (contentDescription != null) pill = pill.semantics { this.contentDescription = contentDescription }
    Row(
        modifier = pill,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) WidgetIcon(icon, content, size = 16.dp)
        if (icon != null && text.isNotEmpty()) Spacer(GlanceModifier.width(4.dp))
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = TextStyle(
                    color = content,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = maxLines,
            )
        }
    }
}

/** Progress ring with a centred value, drawn as a bitmap because Glance has no determinate circular indicator. */
@Composable
internal fun ProgressRing(
    progress: Float,
    ringColor: Int,
    size: Dp,
    contentDescription: String?,
    center: @Composable () -> Unit,
) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(RingBitmap.render(progress, ringColor)),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(size),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

internal object RingBitmap {
    private const val SIZE_PX = 192
    private const val STROKE_PX = 18f

    fun render(progress: Float, color: Int, track: Int = WidgetColors.RING_TRACK): Bitmap {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE_PX
            strokeCap = Paint.Cap.ROUND
        }
        val inset = STROKE_PX / 2f + 1f
        val bounds = RectF(inset, inset, SIZE_PX - inset, SIZE_PX - inset)
        paint.color = track
        canvas.drawArc(bounds, 0f, 360f, false, paint)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            paint.color = color
            canvas.drawArc(bounds, -90f, sweep, false, paint)
        }
        return bitmap
    }
}
