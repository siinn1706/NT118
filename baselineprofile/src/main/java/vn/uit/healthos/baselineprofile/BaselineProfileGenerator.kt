package vn.uit.healthos.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile: cold start, then (when a session exists) the five tabs, scrolling Home and Me,
 * and Settings. Sign in on the device first; on the sign-in screen the profile covers startup only.
 *
 * Run with the phone connected: ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        if (awaitHomeOrSignIn()) browseTabs()
    }
}
