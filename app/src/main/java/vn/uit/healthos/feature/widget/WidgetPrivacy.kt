package vn.uit.healthos.feature.widget

/**
 * What a home-screen widget may reveal. Widgets sit outside the app lock, so health numbers only show when someone is
 * signed in and the app lock is off.
 */
enum class WidgetAccess { SIGNED_OUT, LOCKED, VISIBLE }

/** Gated widget data: the value is only carried when it may be shown. */
sealed interface WidgetContent<out T> {
    data object SignedOut : WidgetContent<Nothing>
    data object Locked : WidgetContent<Nothing>
    data class Visible<T>(val value: T) : WidgetContent<T>
}

object WidgetPrivacy {
    fun decide(signedIn: Boolean, appLockEnabled: Boolean): WidgetAccess = when {
        !signedIn -> WidgetAccess.SIGNED_OUT
        appLockEnabled -> WidgetAccess.LOCKED
        else -> WidgetAccess.VISIBLE
    }

    /** Builds the content for [access]; [value] is only evaluated when the data may be shown. */
    inline fun <T> gate(access: WidgetAccess, value: () -> T): WidgetContent<T> = when (access) {
        WidgetAccess.SIGNED_OUT -> WidgetContent.SignedOut
        WidgetAccess.LOCKED -> WidgetContent.Locked
        WidgetAccess.VISIBLE -> WidgetContent.Visible(value())
    }
}
