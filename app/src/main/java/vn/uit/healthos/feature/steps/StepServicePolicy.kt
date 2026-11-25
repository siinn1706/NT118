package vn.uit.healthos.feature.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** Pure rules for when the step service runs and when it refreshes the home-screen widgets. */
object StepServicePolicy {
    /** Widgets follow the live count, but at most every [WIDGET_REFRESH_INTERVAL_MS] or [WIDGET_REFRESH_STEPS]. */
    const val WIDGET_REFRESH_INTERVAL_MS = 5 * 60_000L
    const val WIDGET_REFRESH_STEPS = 200

    /**
     * The `health` foreground service type requires ACTIVITY_RECOGNITION on Android 14+, and counting only makes
     * sense for a signed-in account, since steps taken while signed out belong to nobody.
     */
    fun shouldRun(activityRecognitionGranted: Boolean, signedIn: Boolean): Boolean =
        activityRecognitionGranted && signedIn

    /**
     * The phone's step count restarts at zero when the account signing in is not the one that owns the local data.
     * That includes the first sign-in (no owner yet), since anything counted before it belongs to nobody.
     */
    fun shouldResetStepsOnSignIn(previousOwner: String?, userId: String): Boolean = previousOwner != userId

    fun shouldRefreshWidgets(lastRefreshAtMs: Long?, lastRefreshSteps: Int, nowMs: Long, steps: Int): Boolean {
        if (lastRefreshAtMs == null) return true
        // A lower count means a new day started (or the account changed): show it at once.
        if (steps < lastRefreshSteps) return true
        return nowMs - lastRefreshAtMs >= WIDGET_REFRESH_INTERVAL_MS ||
            abs(steps - lastRefreshSteps) >= WIDGET_REFRESH_STEPS
    }

    fun hasActivityRecognition(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
