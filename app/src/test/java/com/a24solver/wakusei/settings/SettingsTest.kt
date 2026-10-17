package com.a24solver.wakusei.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        // the chosen locale lives in a static for the rest of the JVM, so put it back
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun theDefaultsAreTwentyFourWithSoundOnAndFollowSystem() {
        val settings = Settings(context)

        assertEquals(24, settings.defaultTarget)
        assertEquals(DefaultTargetPreset.TWENTY_FOUR, settings.defaultTargetPreset)
        assertTrue(settings.soundEnabled)
        assertEquals(AppLanguage.SYSTEM, settings.requestedLanguage)
        assertEquals(AppLanguage.SYSTEM, settings.activeLanguage)
        assertFalse(settings.languageChangeIsPending)
    }

    @Test
    fun everyPresetNodeCarriesItsValue() {
        val settings = Settings(context)

        val expected = mapOf(
            DefaultTargetPreset.MINUS_24 to -24,
            DefaultTargetPreset.MINUS_10 to -10,
            DefaultTargetPreset.ZERO to 0,
            DefaultTargetPreset.TEN to 10,
            DefaultTargetPreset.TWENTY_FOUR to 24,
            DefaultTargetPreset.SIXTY to 60
        )
        // the six value nodes, in slider order, are exactly the ones the specification lists
        assertEquals(expected.keys.toList(), DefaultTargetPreset.entries.filterNot { it.isCustom })
        for ((preset, value) in expected) {
            settings.defaultTargetPreset = preset
            assertEquals(value, settings.defaultTarget)
        }
    }

    @Test
    fun theCustomValueOnlyCountsUnderCustomize() {
        val settings = Settings(context)

        settings.customDefaultTarget = -7
        settings.defaultTargetPreset = DefaultTargetPreset.TEN
        assertEquals("a preset ignores the custom value", 10, settings.defaultTarget)

        settings.defaultTargetPreset = DefaultTargetPreset.CUSTOMIZE
        assertEquals("CUSTOMIZE hands over to the number field", -7, settings.defaultTarget)
    }

    @Test
    fun customTargetsAreRangeChecked() {
        val settings = Settings(context)

        settings.customDefaultTarget = Settings.MAX_ABS_TARGET
        assertEquals(Settings.MAX_ABS_TARGET, settings.customDefaultTarget)
        settings.customDefaultTarget = -Settings.MAX_ABS_TARGET
        assertEquals(-Settings.MAX_ABS_TARGET, settings.customDefaultTarget)

        assertThrows(IllegalArgumentException::class.java) {
            settings.customDefaultTarget = Settings.MAX_ABS_TARGET + 1
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.customDefaultTarget = -Settings.MAX_ABS_TARGET - 1
        }
        assertFalse(Settings.isValidTarget(16385))
        assertTrue(Settings.isValidTarget(-16384))
    }

    @Test
    fun valuesSurviveANewInstance() {
        Settings(context).apply {
            soundEnabled = false
            defaultTargetPreset = DefaultTargetPreset.MINUS_10
            customDefaultTarget = -1234
        }

        val reopened = Settings(context)
        assertFalse(reopened.soundEnabled)
        assertEquals(DefaultTargetPreset.MINUS_10, reopened.defaultTargetPreset)
        assertEquals(-10, reopened.defaultTarget)
        assertEquals(-1234, reopened.customDefaultTarget)
    }

    @Test
    fun theLanguageTagsAreTheOnesTheAppShips() {
        assertEquals(null, AppLanguage.SYSTEM.languageTag)
        assertEquals("zh", AppLanguage.CHINESE.languageTag)
        assertEquals("en", AppLanguage.ENGLISH.languageTag)
        assertEquals("ja", AppLanguage.JAPANESE.languageTag)

        // an unknown or absent tag means "follow the system", never a crash
        assertEquals(AppLanguage.SYSTEM, AppLanguage.forTag("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.forTag(null))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.forTag("ja"))
    }

    @Test
    fun aReportedLocaleListIsMatchedByPrefix() {
        // the form AppCompat reports, including regional variants
        assertEquals(AppLanguage.CHINESE, AppLanguage.forLanguageTags("zh-Hans-CN,en"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.forLanguageTags("ja-JP"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forLanguageTags("en-GB"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.forLanguageTags(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.forLanguageTags("de-DE,fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.forLanguageTags(null))
    }

    @Test
    fun theRequestedLanguageRoundTripsWithoutBeingApplied() {
        val settings = Settings(context)
        settings.requestLanguage(AppLanguage.CHINESE)

        assertEquals(AppLanguage.CHINESE, settings.requestedLanguage)
        assertEquals(AppLanguage.CHINESE, Settings(context).requestedLanguage)
        assertEquals("requesting must not switch anything", AppLanguage.SYSTEM, settings.activeLanguage)

        settings.requestLanguage(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, settings.requestedLanguage)
    }

    @Test
    fun theLongPressDefaultsToThePlatformValueAndRoundTrips() {
        val settings = Settings(context)
        assertEquals(Settings.DEFAULT_LONG_PRESS_MS, settings.longPressMs)
        assertEquals(500, settings.longPressMs)

        settings.longPressMs = 800

        assertEquals(800, settings.longPressMs)
        assertEquals("it must be stored, not cached", 800, Settings(context).longPressMs)
    }

    @Test
    fun theLongPressRangeIsEnforcedOnBothEnds() {
        for (value in listOf(Settings.MIN_LONG_PRESS_MS, 500, Settings.MAX_LONG_PRESS_MS)) {
            assertTrue("$value should be allowed", Settings.isValidLongPress(value))
        }
        for (value in listOf(Settings.MIN_LONG_PRESS_MS - 1, Settings.MAX_LONG_PRESS_MS + 1, -1, 0)) {
            assertFalse("$value should be refused", Settings.isValidLongPress(value))
        }

        val settings = Settings(context)
        assertThrows(IllegalArgumentException::class.java) { settings.longPressMs = 50 }
        assertEquals("the stored value must not move", 500, settings.longPressMs)
    }
}