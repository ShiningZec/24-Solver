package com.a24solver.wakusei

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Guards the bottom of both screens against the system navigation bar.
 *
 * `enableEdgeToEdge()` lets the app draw under the system bars, so the app has to keep its own
 * controls clear of them. This used to be left to `android:fitsSystemWindows`, which keeps the top
 * clear but leaves the *bottom* of the screen under the navigation bar — the SOLVE and BACK buttons
 * then sat within reach of the navigation keys on devices with a chunky bar.
 *
 * The test drives the real code path: it dispatches synthetic system bar insets into the activity's
 * root view (Robolectric reports zero insets on its own, and zero insets is exactly the case that
 * hides this bug) and then measures where the buttons ended up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomControlsTest {

    private lateinit var controller: ActivityController<MainActivity>

    /**
     * What a device reports: a status bar on top and a navigation bar at the bottom.
     *
     * The navigation bar is deliberately *taller* than `@dimen/bottom_action_clearance`. With a
     * typical 48dp bar the clearance margin alone would already keep the buttons clear, and the
     * test could not tell whether the insets were applied at all; a bar that outgrows the margin is
     * the case where only the inset handling saves the button (tablets with a taskbar, landscape
     * cutouts, large system bar configurations).
     */
    private val statusBarDp = 24
    private val navigationBarDp = 96

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().supportFragmentManager.executePendingTransactions()
    }

    // ------------------------------------------------------------------ insets

    @Test
    fun theRootIsPaddedByTheSystemBars() {
        dispatchSystemBars()

        assertEquals(px(statusBarDp), root().paddingTop)
        assertEquals(px(navigationBarDp), root().paddingBottom)
    }

    @Test
    fun nothingElseAlsoFitsTheSystemWindows() {
        // The activity pads the root; if a layout fitted the insets as well, the top would be
        // padded twice and the bottom would still be ignored.
        assertFalse("the root must not fit system windows itself", root().fitsSystemWindows)
        val appBar = controller.get().findViewById<View>(R.id.toolbar).parent as View
        assertFalse("the app bar must not fit system windows either", appBar.fitsSystemWindows)
    }

    // --------------------------------------------------------------- geometry

    @Test
    fun solveSitsAboveTheNavigationBar() {
        dispatchSystemBars()
        assertReachable("SOLVE", R.id.button_solve)
    }

    @Test
    fun backSitsAboveTheNavigationBar() {
        navigateToResultScreen()
        dispatchSystemBars()
        assertReachable("BACK", R.id.button_back)
    }

    // ---------------------------------------------------------------- helpers

    private fun assertReachable(label: String, id: Int) {
        val button = controller.get().findViewById<View>(id)
        val margin = button.layoutParams as ViewGroup.MarginLayoutParams
        val clearance = resources().getDimension(R.dimen.bottom_action_clearance).toInt()

        val rootLocation = IntArray(2).also { root().getLocationOnScreen(it) }
        val buttonLocation = IntArray(2).also { button.getLocationOnScreen(it) }

        val screenBottom = rootLocation[1] + root().height
        // measured against the inset that was dispatched, not against the padding this test is
        // supposed to be checking — otherwise the assertion could not fail
        val navigationBarTop = screenBottom - px(navigationBarDp)
        val buttonBottom = buttonLocation[1] + button.height

        assertEquals(
            "$label keeps @dimen/bottom_action_clearance underneath it",
            clearance,
            margin.bottomMargin
        )
        assertTrue(
            "$label (bottom $buttonBottom) must stay above the navigation bar (top $navigationBarTop)",
            buttonBottom <= navigationBarTop
        )
        assertTrue(
            "$label (bottom $buttonBottom) must keep at least $clearance px clear of the screen " +
                "edge ($screenBottom), was ${screenBottom - buttonBottom}",
            screenBottom - buttonBottom >= clearance
        )
    }

    /**
     * Pushes synthetic insets through the activity's root view.
     *
     * Call it last, immediately before measuring: Robolectric's own window reports zero insets, and
     * every traversal of the view tree re-dispatches those zeros, which would wipe the padding this
     * test just applied. (On a device the dispatch always carries the real, current insets, so the
     * listener is free to overwrite the padding every time.)
     */
    private fun dispatchSystemBars() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.systemBars(),
                Insets.of(0, px(statusBarDp), 0, px(navigationBarDp))
            )
            .build()
        ViewCompat.dispatchApplyWindowInsets(root(), insets)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun navigateToResultScreen() {
        val navHost = controller.get().supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHost.navController.navigate(R.id.SecondFragment)
        controller.get().supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun root(): View = controller.get().findViewById(R.id.main)

    private fun resources() = controller.get().resources

    private fun px(dp: Int): Int = (dp * resources().displayMetrics.density).toInt()
}
