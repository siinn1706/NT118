package vn.uit.healthos.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vn.uit.healthos.core.designsystem.theme.LocalReducedMotion

/** Where a glass morph is in its journey between the trigger and the platter. */
enum class GlassMorphPhase {
    /** Closed and at rest: only the trigger is visible. */
    Idle,

    /** The platter is just leaving its origin, still joined to it by the SDF neck. */
    Detaching,

    /** In flight between the trigger and the platter position. */
    Travelling,

    /** Close to its destination, springing into place. */
    Arriving,

    /** Open and at rest. */
    Settled,
}

/**
 * Drives a Liquid Glass morph between a trigger (a button) and the platter that grows out of it, with the springs of
 * [LiquidSprings]: the platter flies out along a J curve ([pathT], [sizeT], [platterScale]) while a wide trigger
 * collapses to a circle ([triggerWidthProgress]); on close the platter is pulled back into the trigger, the trigger
 * splashes back to full width and takes a small recoil ([triggerPushDx], [triggerPushDy], [triggerImpactScale]).
 * [glassProgress] feeds `glassMaterialize` and [blendRadius] feeds the SDF union so the platter is joined to the
 * trigger by a neck while it detaches or arrives.
 *
 * Every value is Animatable state: read it inside layer or draw lambdas so a running morph only redraws. With
 * [reducedMotion] the springs snap, the platter crossfades in about 150 ms and there is no recoil.
 *
 * [recoilDirection] is the unit direction the trigger is pushed in when the platter lands (usually from the platter
 * towards the trigger) and [recoilDistancePx] the length of that push at its peak.
 */
@Stable
class GlassMorphController(
    private val scope: CoroutineScope,
    reducedMotion: Boolean = false,
    recoilDistancePx: Float = 0f,
    recoilDirection: Offset = Offset(0f, 1f),
) {
    var reducedMotion: Boolean by mutableStateOf(reducedMotion)
    var recoilDistancePx: Float by mutableStateOf(recoilDistancePx)
    var recoilDirection: Offset by mutableStateOf(recoilDirection)

    /** True once [open] was asked for, false after [close] or [reset]. */
    var isOpen: Boolean by mutableStateOf(false)
        private set

    private val path = Animatable(0f, VisibilityThreshold)
    private val fade = Animatable(0f, VisibilityThreshold)
    private val triggerWidth = Animatable(1f, VisibilityThreshold)
    private val recoilDrive = Animatable(0f, RecoilThreshold)
    private val recoilDamp = Animatable(0f, RecoilThreshold)
    private var job: Job? = null

    /** Travel along the J curve, 0 at the trigger and 1 at the platter position; overshoots slightly on open. */
    val pathT: Float get() = path.value

    /** Bounds morph from trigger size (0) to platter size (1); follows [pathT] but never undershoots below 0. */
    val sizeT: Float get() = path.value.coerceAtLeast(0f)

    /** Uniform scale of the platter body: [ClosedPlatterScale] inside the trigger, 1 when open. */
    val platterScale: Float get() = ClosedPlatterScale + (1f - ClosedPlatterScale) * sizeT

    /**
     * Presence of the platter glass for `glassMaterialize`: follows the platter travel, so the glass stays dense until
     * the last quarter of the way back and dissolves while it merges into the trigger. A 150 ms crossfade with reduced
     * motion.
     */
    val glassProgress: Float
        get() = if (reducedMotion) fade.value.coerceIn(0f, 1f) else path.value.coerceIn(0f, 1f)

    /** Width of a wide trigger between its circle (0) and its full pill (1). */
    val triggerWidthProgress: Float get() = triggerWidth.value

    /** Close recoil with its peak scaled to 1 (0 at rest). */
    val recoil: Float get() = ((recoilDrive.value - recoilDamp.value) / RecoilPeak).coerceIn(-1f, 1.5f)

    /** Horizontal push of the trigger during the close recoil, in pixels. */
    val triggerPushDx: Float get() = recoilDirection.x * recoilDistancePx * recoil

    /** Vertical push of the trigger during the close recoil, in pixels. */
    val triggerPushDy: Float get() = recoilDirection.y * recoilDistancePx * recoil

    /** Squash of the trigger during the close recoil: 1 at rest, [ImpactScale] at the peak. */
    val triggerImpactScale: Float get() = 1f - (1f - ImpactScale) * recoil

    /** Smooth-union radius joining the platter to the trigger: full while they touch, gone halfway through. */
    val blendRadius: Dp get() = morphBlendRadius(path.value)

    /** Current [GlassMorphPhase]. */
    val phase: GlassMorphPhase get() = morphPhase(isOpen, path.value, path.isRunning || fade.isRunning)

    /** True while any part of the platter should be drawn. */
    val isShowing: Boolean get() = isOpen || glassProgress > 0f

    /** Point on the J curve from [from] (trigger centre) to [to] (platter centre) at the current [pathT]. */
    fun platterCenter(from: Offset, to: Offset): Offset = jCurve(from, to, path.value)

    /** Grows the platter out of the trigger. Returns the job running the morph. */
    fun open(): Job {
        isOpen = true
        job?.cancel()
        val reduced = reducedMotion
        return scope.launch {
            launch { recoilDrive.snapTo(0f) }
            launch { recoilDamp.snapTo(0f) }
            if (reduced) {
                path.snapTo(1f)
                triggerWidth.snapTo(0f)
                fade.animateTo(1f, tween(ReducedMotionFadeMs, easing = LinearEasing))
            } else {
                fade.snapTo(1f)
                launch { triggerWidth.animateTo(0f, LiquidSprings.WideTriggerCollapse.spec(VisibilityThreshold)) }
                path.animateTo(1f, LiquidSprings.OpenPlatter.spec(VisibilityThreshold))
            }
        }.also { job = it }
    }

    /** Pulls the platter back into the trigger, with the trigger recoil. Returns the job running the morph. */
    fun close(): Job {
        isOpen = false
        job?.cancel()
        val reduced = reducedMotion
        return scope.launch {
            if (reduced) {
                launch { recoilDrive.snapTo(0f) }
                launch { recoilDamp.snapTo(0f) }
                triggerWidth.snapTo(1f)
                fade.animateTo(0f, tween(ReducedMotionFadeMs, easing = LinearEasing))
                path.snapTo(0f)
            } else {
                fade.snapTo(0f)
                launch { triggerWidth.animateTo(1f, LiquidSprings.WideTriggerSplash.spec(VisibilityThreshold)) }
                launch {
                    recoilDrive.snapTo(0f)
                    recoilDrive.animateTo(1f, LiquidSprings.RecoilDrive.spec(RecoilThreshold))
                }
                launch {
                    recoilDamp.snapTo(0f)
                    recoilDamp.animateTo(1f, LiquidSprings.RecoilDamp.spec(RecoilThreshold))
                }
                path.animateTo(0f, LiquidSprings.ClosePlatter.spec(VisibilityThreshold))
            }
        }.also { job = it }
    }

    /** Stops any morph and jumps to the closed, idle state. */
    fun reset(): Job {
        isOpen = false
        job?.cancel()
        return scope.launch {
            path.snapTo(0f)
            fade.snapTo(0f)
            triggerWidth.snapTo(1f)
            recoilDrive.snapTo(0f)
            recoilDamp.snapTo(0f)
        }.also { job = it }
    }

    companion object {
        /** Platter scale when it sits inside the trigger. */
        const val ClosedPlatterScale = 0.2f

        /** Trigger squash at the recoil peak. */
        const val ImpactScale = 0.96f

        /** Largest SDF blend radius, while the platter and trigger touch. */
        val MaxBlendRadius: Dp = 28.dp

        /** Default recoil push at its peak. */
        val DefaultRecoilDistance: Dp = 10.dp

        /** Crossfade used instead of the morph when reduced motion is on. */
        const val ReducedMotionFadeMs = 150

        private const val VisibilityThreshold = 0.001f
        private const val RecoilThreshold = 0.0002f
        private val RecoilPeak = CloseRecoil.peak.toFloat()
    }
}

/**
 * Remembers a [GlassMorphController] tied to the composition's coroutine scope; it follows [LocalReducedMotion].
 * [recoilDirection] is the unit direction the trigger is pushed in when the platter lands.
 */
@Composable
fun rememberGlassMorphController(
    recoilDirection: Offset = Offset(0f, 1f),
    recoilDistance: Dp = GlassMorphController.DefaultRecoilDistance,
): GlassMorphController {
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val distancePx = with(LocalDensity.current) { recoilDistance.toPx() }
    val controller = remember(scope) { GlassMorphController(scope, reducedMotion, distancePx, recoilDirection) }
    SideEffect {
        controller.reducedMotion = reducedMotion
        controller.recoilDistancePx = distancePx
        controller.recoilDirection = recoilDirection
    }
    return controller
}

/** Phase of a morph heading open ([opening]) or closed at travel [pathT]; [animating] is false once at rest. */
fun morphPhase(opening: Boolean, pathT: Float, animating: Boolean): GlassMorphPhase {
    if (!animating) return if (opening) GlassMorphPhase.Settled else GlassMorphPhase.Idle
    val travelled = if (opening) pathT else 1f - pathT
    return when {
        travelled < DetachEnd -> GlassMorphPhase.Detaching
        travelled < ArriveStart -> GlassMorphPhase.Travelling
        else -> GlassMorphPhase.Arriving
    }
}

/**
 * SDF blend radius at travel [pathT]: [GlassMorphController.MaxBlendRadius] while the platter still overlaps the
 * trigger, easing to 0 by half the travel so the neck stretches and snaps like a drop.
 */
fun morphBlendRadius(pathT: Float): Dp {
    val x = (pathT.coerceIn(0f, NeckBreak) / NeckBreak)
    val eased = x * x * (3f - 2f * x)
    return GlassMorphController.MaxBlendRadius * (1f - eased)
}

/**
 * J-shaped path from [from] to [to] at [t]: a quadratic curve whose control point sits beside [from] at the height of
 * [to], so the platter first rises out of the trigger and then swings across. [t] may overshoot 1.
 */
fun jCurve(from: Offset, to: Offset, t: Float): Offset {
    val control = Offset(from.x, to.y)
    val u = 1f - t
    return from * (u * u) + control * (2f * u * t) + to * (t * t)
}

private const val DetachEnd = 0.2f
private const val ArriveStart = 0.85f
private const val NeckBreak = 0.5f
