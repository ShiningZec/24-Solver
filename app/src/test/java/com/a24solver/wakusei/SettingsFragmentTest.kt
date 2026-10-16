package com.a24solver.wakusei

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import com.a24solver.wakusei.settings.AppLanguage
import com.a24solver.wakusei.settings.DefaultTargetPreset
import com.a24solver.wakusei.settings.Settings
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast
import org.robolectric.annotation.Config

/**
 * The settings screen, driven through the real activity.
 *
 * Every case checks both halves of the contract: what the widgets show, and what ends up stored —
 * the screen is a view onto [Settings], not the owner of the values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>

    @Before
    fun setUp() {
        resetSettings()
    }

    @After
    fun tearDown() {
        // the app locale lives in a static; leaving it set would translate every later test
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        resetSettings()
    }

    private fun resetSettings() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun settings() = Settings(RuntimeEnvironment.getApplication())

    /** Opens the settings screen through the navigation graph, the way the toolbar button does. */
    private fun openSettings() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val fm = controller.get().supportFragmentManager
        fm.executePendingTransactions()
        val navHost = fm.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHost.navController.navigate(R.id.SettingsFragment)
        fm.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun <T : View> view(id: Int): T = controller.get().findViewById(id)

    private val dropdown: AutoCompleteTextView get() = view(R.id.dropdown_language)
    private val slider: Slider get() = view(R.id.slider_default_target)
    private val targetInput: TextInputEditText get() = view(R.id.input_default_target)
    private val targetLayout: TextInputLayout get() = view(R.id.input_default_target_layout)
    private val soundSwitch: MaterialSwitch get() = view(R.id.switch_sound)

    // -------------------------------------------------------------- defaults

    @Test
    fun theScreenOpensOnTheStoredValues() {
        openSettings()

        assertEquals("Follow system", dropdown.text.toString())
        assertEquals(DefaultTargetPreset.TWENTY_FOUR.ordinal.toFloat(), slider.value, 0.001f)
        assertEquals("24", targetInput.text.toString())
        assertEquals("Current: 24", currentLabel())
        assertFalse("a preset node greys the number field out", targetInput.isEnabled)
        assertTrue(soundSwitch.isChecked)
    }

    @Test
    fun theLanguageListOffersSystemPlusTheThreeShippedLanguages() {
        openSettings()

        assertEquals(4, dropdown.adapter.count)
        val labels = (0 until dropdown.adapter.count).map { dropdown.adapter.getItem(it).toString() }
        assertEquals(listOf("Follow system", "中文", "English", "日本語"), labels)
    }

    // -------------------------------------------------------------- language

    @Test
    fun choosingALanguageIsRecordedButAppliesOnlyWhenLeaving() {
        openSettings()

        selectLanguage(AppLanguage.JAPANESE.ordinal)

        assertEquals(AppLanguage.JAPANESE, settings().requestedLanguage)
        assertEquals(
            "the running app must not switch language on the spot, was " +
                "${AppCompatDelegate.getApplicationLocales()}",
            "",
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        )
        assertTrue(settings().languageChangeIsPending)
        assertTrue(
            "the screen should say which language is in use and which is waiting",
            status().contains("Follow system") && status().contains("日本語")
        )
        assertTrue(
            "the user should be told it applies when the screen is left, was " +
                "'${ShadowToast.getTextOfLatestToast()}'",
            ShadowToast.getTextOfLatestToast().contains("leave the settings screen")
        )
    }

    @Test
    fun leavingTheSettingsScreenAppliesTheChosenLanguage() {
        openSettings()
        selectLanguage(AppLanguage.JAPANESE.ordinal)
        assertEquals("", AppCompatDelegate.getApplicationLocales().toLanguageTags())

        leaveSettings()

        assertTrue(
            "leaving the screen should switch the language, was " +
                "${AppCompatDelegate.getApplicationLocales()}",
            AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ja")
        )
        assertFalse(settings().languageChangeIsPending)
    }

    @Test
    fun aRotationIsNotLeavingTheSettingsScreen() {
        // the rule on its own: a rotation also destroys the view, but must not switch languages
        assertFalse(LanguageApplyRule.shouldApply(isChangingConfigurations = true, isPending = true))
        assertTrue(LanguageApplyRule.shouldApply(isChangingConfigurations = false, isPending = true))
        assertFalse(LanguageApplyRule.shouldApply(isChangingConfigurations = false, isPending = false))
    }

    private fun leaveSettings() {
        val navHost = controller.get().supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHost.navController.navigateUp()
        controller.get().supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun thePendingChoiceIsStillShownInTheList() {
        openSettings()
        selectLanguage(AppLanguage.JAPANESE.ordinal)

        assertEquals("日本語", dropdown.text.toString())
        assertTrue(status().startsWith("In use now: Follow system"))
    }

    @Test
    fun followingTheSystemAgainClearsThePendingChoice() {
        openSettings()
        selectLanguage(AppLanguage.CHINESE.ordinal)
        assertEquals(AppLanguage.CHINESE, settings().requestedLanguage)

        selectLanguage(AppLanguage.SYSTEM.ordinal)

        assertEquals(AppLanguage.SYSTEM, settings().requestedLanguage)
        assertFalse(settings().languageChangeIsPending)
        assertEquals("In use now: Follow system", status())
    }

    private fun status(): String =
        view<android.widget.TextView>(R.id.text_language_status).text.toString()

    private fun selectLanguage(position: Int) {
        val listener = requireNotNull(dropdown.onItemClickListener) { "the list has no listener" }
        listener.onItemClick(null, null, position, position.toLong())
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ---------------------------------------------------------- default target

    @Test
    fun pickingAPresetNodeStoresItAndFillsTheField() {
        openSettings()

        selectNode(DefaultTargetPreset.MINUS_10)

        assertEquals(DefaultTargetPreset.MINUS_10, settings().defaultTargetPreset)
        assertEquals(-10, settings().defaultTarget)
        assertEquals("-10", targetInput.text.toString())
        assertEquals("Current: -10", currentLabel())
        assertFalse(targetInput.isEnabled)
        assertNull(targetLayout.error)
    }

    @Test
    fun theCustomizeNodeHandsOverToTheNumberField() {
        openSettings()

        selectNode(DefaultTargetPreset.CUSTOMIZE)

        assertEquals(DefaultTargetPreset.CUSTOMIZE, settings().defaultTargetPreset)
        assertTrue("CUSTOMIZE must enable the number field", targetInput.isEnabled)
        assertNotNull("the field should explain what it wants", targetLayout.helperText)
        assertTrue(
            "the node in force should be visible, was ${currentLabel()}",
            currentLabel().contains("CUSTOMIZE")
        )
    }

    @Test
    fun aCustomValueIsStoredAsItIsTyped() {
        openSettings()
        selectNode(DefaultTargetPreset.CUSTOMIZE)

        typeTarget("-7")

        assertEquals(-7, settings().customDefaultTarget)
        assertEquals(-7, settings().defaultTarget)
        assertNull(targetLayout.error)
        assertTrue(currentLabel().contains("-7"))
    }

    @Test
    fun anOutOfRangeCustomValueIsReportedAndNotStored() {
        openSettings()
        selectNode(DefaultTargetPreset.CUSTOMIZE)
        typeTarget("60")
        assertEquals(60, settings().customDefaultTarget)

        typeTarget("99999")

        assertNotNull("the field must complain", targetLayout.error)
        assertTrue(
            "the message should mention the bound, was ${targetLayout.error}",
            targetLayout.error.toString().contains("16384")
        )
        assertEquals("the stored value must not move", 60, settings().customDefaultTarget)
    }

    @Test
    fun aNonNumericCustomValueIsReported() {
        openSettings()
        selectNode(DefaultTargetPreset.CUSTOMIZE)

        typeTarget("abc")

        assertNotNull(targetLayout.error)
    }

    @Test
    fun anEmptyingFieldIsNotAnErrorWhileTyping() {
        openSettings()
        selectNode(DefaultTargetPreset.CUSTOMIZE)
        typeTarget("99999")
        assertNotNull(targetLayout.error)

        typeTarget("")

        assertNull("clearing the field is mid-edit, not a mistake", targetLayout.error)
    }

    /** Selecting a node the way the widget does when the value changes. */
    private fun selectNode(preset: DefaultTargetPreset) {
        slider.value = preset.ordinal.toFloat()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun typeTarget(text: String) {
        targetInput.setText(text)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun currentLabel(): String = view<android.widget.TextView>(R.id.text_default_target)
        .text.toString()

    // ------------------------------------------------------------------ sound

    @Test
    fun theSoundSwitchStoresTheChoice() {
        openSettings()
        assertTrue(soundSwitch.isChecked)

        soundSwitch.isChecked = false
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(settings().soundEnabled)

        soundSwitch.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(settings().soundEnabled)
    }

    // ------------------------------------------------------------- round trip

    @Test
    fun theScreenShowsWhatWasStoredEarlier() {
        settings().apply {
            soundEnabled = false
            defaultTargetPreset = DefaultTargetPreset.SIXTY
        }

        openSettings()

        assertEquals(DefaultTargetPreset.SIXTY.ordinal.toFloat(), slider.value, 0.001f)
        assertEquals("60", targetInput.text.toString())
        assertFalse(soundSwitch.isChecked)
    }
}
