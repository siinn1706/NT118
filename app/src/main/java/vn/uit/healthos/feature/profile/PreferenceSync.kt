package vn.uit.healthos.feature.profile

import androidx.annotation.StringRes
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.ThemeMode

/** Who may see the user on the step leaderboard (`user_preferences.leaderboard_visibility`). */
enum class LeaderboardVisibility(val apiValue: String, @param:StringRes val label: Int) {
    OFF("off", R.string.settings_leaderboard_off),
    FRIENDS("friends", R.string.settings_leaderboard_friends),
    GLOBAL("global", R.string.settings_leaderboard_global),
    ;

    companion object {
        val Default = FRIENDS

        fun fromApi(value: String?): LeaderboardVisibility? =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Preference values changed on this device that the server has not confirmed yet, in their API encoding. A
 * non-null field is "dirty": the server value for it must not overwrite the local one until a PATCH succeeded.
 */
data class PendingPreferences(
    val themeMode: String? = null,
    val accentColor: String? = null,
    val background: String? = null,
    val leaderboardVisibility: String? = null,
) {
    val isEmpty: Boolean
        get() = themeMode == null && accentColor == null && background == null && leaderboardVisibility == null

    /** Fields of [other] win where they are set. */
    fun plus(other: PendingPreferences) = PendingPreferences(
        themeMode = other.themeMode ?: themeMode,
        accentColor = other.accentColor ?: accentColor,
        background = other.background ?: background,
        leaderboardVisibility = other.leaderboardVisibility ?: leaderboardVisibility,
    )
}

/** Server values that may replace the local ones; null means "keep what the device has". */
data class AdoptedPreferences(
    val themeMode: ThemeMode? = null,
    val accentPreset: AccentPreset? = null,
    val backgroundStyle: BackgroundStyle? = null,
    val leaderboardVisibility: LeaderboardVisibility? = null,
)

/** Pure rules for keeping local appearance and privacy choices in step with `/v1/preferences/me`. */
object PreferenceSyncPolicy {

    /**
     * What to take from [server]: every field except those still [pending] locally. An unset server accent (null)
     * never resets the local choice.
     */
    fun adopt(server: UserPreferenceDto, pending: PendingPreferences): AdoptedPreferences = AdoptedPreferences(
        themeMode = if (pending.themeMode == null) themeFromApi(server.themeMode) else null,
        accentPreset = if (pending.accentColor == null) server.accentColor?.let(AccentPreset::fromHex) else null,
        backgroundStyle = if (pending.background == null) backgroundFromApi(server.appearance?.background) else null,
        leaderboardVisibility = if (pending.leaderboardVisibility == null) {
            LeaderboardVisibility.fromApi(server.leaderboardVisibility)
        } else {
            null
        },
    )

    /** The PATCH body that sends every pending field, or null when nothing is pending. */
    fun patchBody(pending: PendingPreferences): UserPreferenceUpdateBody? {
        if (pending.isEmpty) return null
        return UserPreferenceUpdateBody(
            themeMode = pending.themeMode,
            accentColor = pending.accentColor,
            appearance = pending.background?.let { AppearanceDto(background = it) },
            leaderboardVisibility = pending.leaderboardVisibility,
        )
    }

    /**
     * What stays pending after [sent] was accepted while [current] is pending now: a field changed again during the
     * request keeps its newer value; a field that still holds the sent value is confirmed and cleared.
     */
    fun remainingAfterSent(current: PendingPreferences, sent: PendingPreferences) = PendingPreferences(
        themeMode = current.themeMode.takeUnless { it == sent.themeMode },
        accentColor = current.accentColor.takeUnless { it == sent.accentColor },
        background = current.background.takeUnless { it == sent.background },
        leaderboardVisibility = current.leaderboardVisibility.takeUnless { it == sent.leaderboardVisibility },
    )

    fun themeToApi(mode: ThemeMode): String = when (mode) {
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
        ThemeMode.SYSTEM -> "system"
    }

    fun themeFromApi(value: String?): ThemeMode? = when (value?.trim()?.lowercase()) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        "system" -> ThemeMode.SYSTEM
        else -> null
    }

    fun backgroundToApi(style: BackgroundStyle): String = style.name.lowercase()

    fun backgroundFromApi(value: String?): BackgroundStyle? =
        BackgroundStyle.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
}
