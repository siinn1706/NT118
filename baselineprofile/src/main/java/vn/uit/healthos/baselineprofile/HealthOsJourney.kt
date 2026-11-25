package vn.uit.healthos.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Package of the app under test (applicationId of :app). */
const val TARGET_PACKAGE = "vn.uit.healthos"

private const val WAIT_MS = 10_000L

// Vietnamese labels from app/src/main/res/values/strings.xml. UiAutomator matches the text the user sees.
private const val SIGN_IN_TITLE = "Chào mừng trở lại" // auth_welcome_title
private const val TAB_HOME = "Tổng quan" // nav_home
private const val TAB_MEALS = "Bữa ăn" // nav_meals
private const val TAB_CHAT = "Bác sĩ AI" // nav_chat
private const val TAB_MEDS = "Thuốc" // nav_meds
private const val TAB_ME = "Tôi" // nav_me
private const val SETTINGS_ENTRY = "Cài đặt" // profile_settings_title
private const val SETTINGS_SECTION = "Hiển thị" // settings_appearance_section

/**
 * Waits for the first screen after a cold start. Returns true when the signed-in shell (bottom tabs) is showing and
 * false when the app stopped on sign-in or anything else (onboarding, app lock), where the journey cannot go further.
 */
fun MacrobenchmarkScope.awaitHomeOrSignIn(): Boolean {
    val firstScreen = Pattern.compile("${Pattern.quote(TAB_HOME)}|${Pattern.quote(SIGN_IN_TITLE)}")
    device.wait(Until.hasObject(By.text(firstScreen).pkg(TARGET_PACKAGE)), WAIT_MS)
    if (device.hasObject(By.text(SIGN_IN_TITLE).pkg(TARGET_PACKAGE))) return false
    return device.hasObject(By.text(TAB_HOME).pkg(TARGET_PACKAGE))
}

/** Walks the five tabs, scrolls Home and Me, opens Settings from Me and comes back. Requires a signed-in session. */
fun MacrobenchmarkScope.browseTabs() {
    openTab(TAB_HOME)
    scrollMainContent()

    openTab(TAB_MEALS)
    openTab(TAB_CHAT)
    openTab(TAB_MEDS)

    openTab(TAB_ME)
    scrollMainContent()
    openSettingsAndBack()

    openTab(TAB_HOME)
}

private fun MacrobenchmarkScope.openTab(label: String) {
    val selector = By.text(label).pkg(TARGET_PACKAGE)
    device.wait(Until.hasObject(selector), WAIT_MS)
    // A screen title can repeat the tab label; the tab itself is the lowest match, in the floating bar.
    val tab = device.findObjects(selector).maxByOrNull { it.visibleBounds.top }
        ?: error("Tab \"$label\" not found")
    tab.click()
    // Let the tab's first frames and data load run so their code is captured too.
    device.waitForIdle()
}

/** The tallest scrollable is the screen's main column; nested horizontal rows are shorter. */
private fun MacrobenchmarkScope.mainScrollable(): UiObject2? =
    device.findObjects(By.scrollable(true).pkg(TARGET_PACKAGE)).maxByOrNull { it.visibleBounds.height() }

private fun MacrobenchmarkScope.scrollMainContent() {
    val list = mainScrollable() ?: return
    // Keep gestures away from the floating tab bar and the system gesture areas.
    list.setGestureMargin(device.displayHeight / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openSettingsAndBack() {
    val list = mainScrollable()
    val entry = device.findObject(By.text(SETTINGS_ENTRY).pkg(TARGET_PACKAGE))
        ?: list?.apply { setGestureMargin(device.displayHeight / 5) }
            ?.scrollUntil(Direction.DOWN, Until.findObject(By.text(SETTINGS_ENTRY).pkg(TARGET_PACKAGE)))
        ?: error("Settings entry \"$SETTINGS_ENTRY\" not found on the Me tab")
    entry.click()
    device.wait(Until.hasObject(By.text(SETTINGS_SECTION).pkg(TARGET_PACKAGE)), WAIT_MS)
    mainScrollable()?.let {
        it.setGestureMargin(device.displayHeight / 5)
        it.fling(Direction.DOWN)
        device.waitForIdle()
    }
    device.pressBack()
    device.wait(Until.hasObject(By.text(TAB_ME).pkg(TARGET_PACKAGE)), WAIT_MS)
    device.waitForIdle()
}
