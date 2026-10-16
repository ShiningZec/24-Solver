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
 * The unit tests run under Robolectric, which cannot reproduce everything a real process start does —
 * manifest providers, resource loading for the device's actual configuration, the platform's own
 * strictness. A failure there is invisible to every JVM test and only shows up as a crash on launch,
 * so one test that merely opens the app and looks at the input screen is worth having.
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
