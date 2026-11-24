package vn.uit.healthos.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.ThemeMode

class PreferenceSyncPolicyTest {

    private val server = UserPreferenceDto(
        themeMode = "dark",
        accentColor = AccentPreset.VIOLET.hex,
        appearance = AppearanceDto(background = "solid"),
        leaderboardVisibility = "global",
    )

    @Test
    fun `adopts every server value when nothing is pending`() {
        val adopted = PreferenceSyncPolicy.adopt(server, PendingPreferences())

        assertEquals(ThemeMode.DARK, adopted.themeMode)
        assertEquals(AccentPreset.VIOLET, adopted.accentPreset)
        assertEquals(BackgroundStyle.SOLID, adopted.backgroundStyle)
        assertEquals(LeaderboardVisibility.GLOBAL, adopted.leaderboardVisibility)
    }

    @Test
    fun `a pending local change is never overwritten by the server copy`() {
        val pending = PendingPreferences(accentColor = AccentPreset.ROSE.hex, themeMode = "light")

        val adopted = PreferenceSyncPolicy.adopt(server, pending)

        assertNull(adopted.accentPreset)
        assertNull(adopted.themeMode)
        assertEquals(BackgroundStyle.SOLID, adopted.backgroundStyle)
        assertEquals(LeaderboardVisibility.GLOBAL, adopted.leaderboardVisibility)
    }

    @Test
    fun `an unset or unknown server value keeps the local one`() {
        val adopted = PreferenceSyncPolicy.adopt(
            UserPreferenceDto(themeMode = "sepia", accentColor = null, appearance = null, leaderboardVisibility = "?"),
            PendingPreferences(),
        )

        assertNull(adopted.themeMode)
        assertNull(adopted.accentPreset)
        assertNull(adopted.backgroundStyle)
        assertNull(adopted.leaderboardVisibility)
    }

    @Test
    fun `patch body carries only the pending fields`() {
        assertNull(PreferenceSyncPolicy.patchBody(PendingPreferences()))

        val body = PreferenceSyncPolicy.patchBody(PendingPreferences(background = "soft", leaderboardVisibility = "off"))!!

        assertNull(body.themeMode)
        assertNull(body.accentColor)
        assertEquals("soft", body.appearance?.background)
        assertEquals("off", body.leaderboardVisibility)
    }

    @Test
    fun `a field changed again while the request was in flight stays pending`() {
        val sent = PendingPreferences(accentColor = "#111111", themeMode = "dark")
        val current = PendingPreferences(accentColor = "#222222", themeMode = "dark", background = "soft")

        val remaining = PreferenceSyncPolicy.remainingAfterSent(current, sent)

        assertEquals(PendingPreferences(accentColor = "#222222", background = "soft"), remaining)
        assertTrue(PreferenceSyncPolicy.remainingAfterSent(sent, sent).isEmpty)
    }

    @Test
    fun `api encodings round trip`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, PreferenceSyncPolicy.themeFromApi(PreferenceSyncPolicy.themeToApi(mode)))
        }
        BackgroundStyle.entries.forEach { style ->
            assertEquals(style, PreferenceSyncPolicy.backgroundFromApi(PreferenceSyncPolicy.backgroundToApi(style)))
        }
        assertEquals("aurora", PreferenceSyncPolicy.backgroundToApi(BackgroundStyle.AURORA))
        LeaderboardVisibility.entries.forEach { visibility ->
            assertEquals(visibility, LeaderboardVisibility.fromApi(visibility.apiValue))
        }
    }
}
