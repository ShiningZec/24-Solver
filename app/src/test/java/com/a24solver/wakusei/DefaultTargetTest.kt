package com.a24solver.wakusei

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.a24solver.wakusei.settings.DefaultTargetPreset
import com.a24solver.wakusei.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The default target, and the way into the settings screen.
 *
 * The setting decides what a fresh input screen starts on and where a long press on CLEAR returns
 * to; it must never touch the target the user is working with right now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultTargetTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var navController: NavController
    private lateinit var root: View

    private val digitIds = mapOf(
        0 to R.id.button0, 1 to R.id.button1, 2 to R.id.button2, 3 to R.id.button3,
        4 to R.id.button4, 5 to R.id.button5, 6 to R.id.button6, 7 to R.id.button7,
        8 to R.id.button8, 9 to R.id.button9
    )

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun settings() = Settings(RuntimeEnvironment.getApplication())

    /**
     * Holds the key long enough to count as a long press. A real touch stream is dispatched so the
     * app's own gesture detection runs — the platform's long click is not what the app uses.
     */
    private fun longClick(id: Int) {
        val view = root.findViewById<View>(id)
        val holdMs = Settings(root.context).longPressMs + 100L
        val down = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(holdMs))
        view.dispatchTouchEvent(
            MotionEvent.obtain(down, down + holdMs, MotionEvent.ACTION_UP, 1f, 1f, 0)
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun start() {
        markHelpSeen(RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val fm = controller.get().supportFragmentManager
        fm.executePendingTransactions()
        val navHost = fm.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHost.navController
        navHost.childFragmentManager.executePendingTransactions()
        root = (navHost.childFragmentManager.fragments.first() as FirstFragment).requireView()
    }

    private fun text(id: Int): String = root.findViewById<android.widget.TextView>(id).text.toString()

    /**
     * Picks up the input screen's view again after it was rebuilt.
     *
     * Navigating to the settings screen and back destroys and re-creates the view while the fragment
     * instance stays, so a held [root] would keep pointing at the detached one — and every assertion
     * on it would read the screen as it was *before* the navigation.
     */
    private fun rebind() {
        val navHost = controller.get().supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHost.childFragmentManager.executePendingTransactions()
        root = (navHost.childFragmentManager.fragments
            .filterIsInstance<FirstFragment>()
            .first()).requireView()
    }

    private fun click(id: Int) {
        root.findViewById<View>(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun type(vararg digits: Int) = digits.forEach { click(digitIds.getValue(it)) }

    private fun clickSettingsButton(): Boolean {
        val toolbar = controller.get().findViewById<Toolbar>(R.id.toolbar)
        val item = requireNotNull(toolbar.menu.findItem(R.id.action_settings)) {
            "the toolbar has no settings button"
        }
        return controller.get().onOptionsItemSelected(item)
    }

    // ------------------------------------------------------------ default target

    @Test
    fun aFreshScreenStartsOnTheConfiguredDefault() {
        settings().defaultTargetPreset = DefaultTargetPreset.SIXTY

        start()

        assertEquals("TARGET: 60", text(R.id.text_target))
    }

    @Test
    fun theDefaultCanBeNegative() {
        settings().defaultTargetPreset = DefaultTargetPreset.MINUS_24

        start()

        assertEquals("TARGET: -24", text(R.id.text_target))
    }

    @Test
    fun clearLongPressReturnsToTheConfiguredDefault() {
        settings().defaultTargetPreset = DefaultTargetPreset.MINUS_10
        start()
        assertEquals("TARGET: -10", text(R.id.text_target))

        click(R.id.button_target)
        type(7)
        click(R.id.button_next)
        assertEquals("TARGET: 7", text(R.id.text_target))

        longClick(R.id.button_clear)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a CLEAR long press goes back to the configured default", "TARGET: -10", text(R.id.text_target))
        assertEquals(
            root.context.getString(R.string.msg_cleared_all),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun changingTheSettingLeavesTheTargetInUseAlone() {
        start()
        click(R.id.button_target)
        type(7)
        click(R.id.button_next)
        assertEquals("TARGET: 7", text(R.id.text_target))

        // someone changes the default while the input screen is alive
        settings().defaultTargetPreset = DefaultTargetPreset.SIXTY
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the target in use is the user's, not the setting's", "TARGET: 7", text(R.id.text_target))
    }

    // ------------------------------------------------------------ settings entry

    @Test
    fun theToolbarButtonOpensTheSettingsScreen() {
        start()
        assertEquals(R.id.FirstFragment, navController.currentDestination?.id)

        assertTrue("the menu click should be handled", clickSettingsButton())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(R.id.SettingsFragment, navController.currentDestination?.id)
        assertNotNull(
            "the settings screen should be on screen",
            controller.get().findViewById<View>(R.id.dropdown_language)
        )
    }

    @Test
    fun pressingTheButtonAgainDoesNotStackASecondSettingsScreen() {
        start()
        clickSettingsButton()
        shadowOf(Looper.getMainLooper()).idle()
        clickSettingsButton()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("the up arrow should get back to the input screen", navController.navigateUp())
        assertEquals(R.id.FirstFragment, navController.currentDestination?.id)
    }

    @Test
    fun theUpArrowReturnsToWhicheverScreenOpenedTheSettings() {
        start()
        click(R.id.button5)
        click(R.id.button_solve)
        val deadline = System.currentTimeMillis() + 20_000
        while (navController.currentDestination?.id != R.id.SecondFragment &&
            System.currentTimeMillis() < deadline
        ) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(R.id.SecondFragment, navController.currentDestination?.id)

        clickSettingsButton()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(R.id.SettingsFragment, navController.currentDestination?.id)

        navController.navigateUp()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "settings opened from the result screen should return there",
            R.id.SecondFragment,
            navController.currentDestination?.id
        )
    }

    /**
     * The bug: visiting the settings screen destroys the input screen's view and rebuilds it on the
     * way back, and the rebuilt view used to re-read the configured default — so changing the default
     * quietly changed the target in use.
     */
    @Test
    fun aDefaultChangedInTheSettingsScreenDoesNotTouchTheTargetInUse() {
        start()
        click(R.id.button_target)
        type(7)
        click(R.id.button_next)
        assertEquals("TARGET: 7", text(R.id.text_target))

        clickSettingsButton()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(R.id.SettingsFragment, navController.currentDestination?.id)
        settings().defaultTargetPreset = DefaultTargetPreset.SIXTY

        navController.navigateUp()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(R.id.FirstFragment, navController.currentDestination?.id)
        rebind() // the view was rebuilt; a stale reference would read the old screen

        assertEquals(
            "the screen was rebuilt, the puzzle was not restarted",
            "TARGET: 7",
            text(R.id.text_target)
        )
        assertEquals(60, settings().defaultTarget)
    }
}
