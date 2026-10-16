package com.a24solver.wakusei

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: the app must actually start.
 *
 * The unit tests run under Robolectric, which cannot reproduce everything that happens on a real
 * device during process start. The launch crash this guards against is a concrete example:
 * `play-services-ads` merges `MobileAdsInitProvider` into the manifest, and that provider runs
 * inside `ActivityThread.handleBindApplication` — *before* `Application.onCreate` — where it aborts
 * the process when the AdMob app ID is missing. No JVM test can see that; launching the activity on
 * a device or emulator can.
 *
 * Run with `./gradlew :app:connectedDebugAndroidTest` and a device attached.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @Test
    fun theAppStartsOnTheInputScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.findViewById<android.view.View>(R.id.button_solve) != null) {
                    "the input screen was not inflated"
                }
            }
            onView(withId(R.id.text_target)).check(matches(isDisplayed()))
            onView(withId(R.id.text_target)).check(matches(withText("TARGET: 24")))
            onView(withId(R.id.button1)).check(matches(isDisplayed()))
            onView(withId(R.id.button_solve)).check(matches(isDisplayed()))
        }
    }
}
