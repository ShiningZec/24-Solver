package com.a24solver.wakusei

import android.content.Context
import android.graphics.Typeface
import android.os.Looper
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.LinearLayout
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import com.a24solver.wakusei.settings.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The help page itself: it renders the bundled document, it follows the app language, and it opens
 * itself on the first start only.
 *
 * The title and the bold label are asserted through the real views — that is the only place where
 * "the parser understood the document" and "the fragment put it on screen" meet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpFragmentTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var controller: ActivityController<MainActivity>

    @Before
    fun setUp() {
        clearSettings()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    @After
    fun tearDown() {
        clearSettings()
    }

    private fun clearSettings() {
        context.getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun navController() = (controller.get().supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment).navController

    private fun openHelp() {
        navController().navigate(R.id.HelpFragment)
        idle()
    }

    private fun helpContent(): LinearLayout {
        val navHost = controller.get().supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val fragment = navHost.childFragmentManager.fragments
            .filterIsInstance<HelpFragment>()
            .firstOrNull()
        requireNotNull(fragment) { "the help page is not on screen" }
        return fragment.requireView().findViewById(R.id.help_content)
    }

    private fun blocks(): List<TextView> =
        (0 until helpContent().childCount).map { helpContent().getChildAt(it) as TextView }

    // --------------------------------------------------------------- content

    @Test
    fun thePageShowsTheTitleAndTheParagraphsOfTheDocument() {
        openHelp()

        val views = blocks()
        assertTrue("the page should have several blocks", views.size >= 5)
        assertEquals(
            "the first block is the document's title",
            "Help - How to Use 24 Solver?",
            views.first().text.toString()
        )
        assertTrue(
            "a paragraph from the document should be on screen",
            views.any { it.text.contains("[NEXT]") }
        )
    }

    @Test
    fun theTitleIsStyledAsATitleAndTheBodyIsNot() {
        openHelp()

        val views = blocks()
        val titleSize = views.first().textSize
        val bodySize = views[1].textSize
        assertTrue(
            "the title (${titleSize}px) should be larger than the body (${bodySize}px)",
            titleSize > bodySize
        )
    }

    @Test
    fun theBoldLabelFromTheDocumentIsBoldOnScreen() {
        openHelp()

        val bold = blocks().firstOrNull { it.text.startsWith("How to use:") }
        requireNotNull(bold) { "the 'How to use' paragraph is missing" }
        val spanned = bold.text as Spanned
        val spans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertTrue("no bold span on the 'How to use' label", spans.isNotEmpty())
    }

    @Test
    @Config(qualifiers = "zh")
    fun thePageSpeaksChineseOnAChineseDevice() {
        clearSettings()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        openHelp()

        assertEquals("帮助 - 如何使用 24 Solver", blocks().first().text.toString())
        assertTrue(blocks().any { it.text.contains("确认") })
    }

    @Test
    @Config(qualifiers = "ja")
    fun thePageSpeaksJapaneseOnAJapaneseDevice() {
        clearSettings()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        openHelp()

        assertEquals("ヘルプ - 「24 Solver」の使い方", blocks().first().text.toString())
        assertTrue(blocks().any { it.text.contains("削除") })
    }

    // ------------------------------------------------------------ first start

    @Test
    fun theFirstStartOpensTheHelpPageByItself() {
        // setUp() clears the settings, so this *is* a first start
        assertEquals(R.id.HelpFragment, navController().currentDestination?.id)
        assertTrue("the flag should be stored as soon as the page opens", Settings(context).helpShown)
    }

    @Test
    fun theSecondStartGoesStraightToTheKeypad() {
        clearSettings()
        Settings(context).helpShown = true
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()

        assertEquals(R.id.FirstFragment, navController().currentDestination?.id)
    }

    @Test
    fun leavingTheHelpPageLandsOnTheKeypad() {
        idle()
        assertTrue(navController().navigateUp())
        idle()

        assertEquals(R.id.FirstFragment, navController().currentDestination?.id)
    }

    // ---------------------------------------------------------- settings entry

    @Test
    fun theSettingsScreenOpensTheHelpPage() {
        navController().navigate(R.id.SettingsFragment)
        idle()

        controller.get().findViewById<android.view.View>(R.id.button_help).performClick()
        idle()

        assertEquals(R.id.HelpFragment, navController().currentDestination?.id)
        assertTrue("the second visit must not touch the first-start flag", Settings(context).helpShown)
    }
}
