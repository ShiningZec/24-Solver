package com.a24solver.wakusei

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.a24solver.wakusei.settings.AppLanguage
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The language change is deliberately *not* applied the moment it is chosen: the settings screen only
 * records it, and it takes effect when that screen is left (plus once at start-up, so a choice made
 * before the process died is not lost).
 *
 * What that means in practice is checked here — picking a language leaves the running app alone, the
 * recorded choice survives, and applying it is what switches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageDeferralTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /**
     * A running screen is part of the setup on purpose: AppCompat only reflects (and applies) a
     * language once a delegate is attached, so without an activity "the language did not change"
     * could not be told apart from "nothing was listening".
     */
    private lateinit var controller: ActivityController<MainActivity>

    @Before
    fun setUp() {
        clearSettings()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @After
    fun tearDown() {
        clearSettings()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    private fun clearSettings() {
        context.getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun settings() = Settings(context)

    private fun activeTags(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    // ------------------------------------------------------------ the request

    @Test
    fun choosingALanguageDoesNotChangeItYet() {
        settings().requestLanguage(AppLanguage.JAPANESE)

        assertEquals("the app must keep running in its current language", "", activeTags())
        assertEquals("but the choice has to be remembered", AppLanguage.JAPANESE, settings().requestedLanguage)
        assertTrue(settings().languageChangeIsPending)
    }

    @Test
    fun theChoiceSurvivesUntilTheNextStart() {
        settings().requestLanguage(AppLanguage.CHINESE)

        // a fresh Settings instance, as a new process would build
        assertEquals(AppLanguage.CHINESE, Settings(context).requestedLanguage)
        assertEquals("nothing may have been applied in between", "", activeTags())
    }

    @Test
    fun requestingTheLanguageAlreadyInUseIsNotPending() {
        settings().requestLanguage(AppLanguage.SYSTEM)

        assertFalse(
            "following the system while following the system is not a change",
            settings().languageChangeIsPending
        )
    }

    // ------------------------------------------------------------ the switch

    @Test
    fun theNextStartPutsTheRequestedLanguageIntoEffect() {
        settings().requestLanguage(AppLanguage.CHINESE)

        // what WakuseiApplication does on the next launch, and what leaving the settings screen does
        settings().applyRequestedLanguage()

        assertTrue("expected zh, was ${activeTags()}", activeTags().startsWith("zh"))
        assertEquals(AppLanguage.CHINESE, settings().activeLanguage)
        assertFalse(settings().languageChangeIsPending)
    }

    @Test
    fun followingTheSystemAgainClearsThePinnedLanguage() {
        settings().requestLanguage(AppLanguage.JAPANESE)
        settings().applyRequestedLanguage()
        assertTrue(activeTags().startsWith("ja"))

        settings().requestLanguage(AppLanguage.SYSTEM)
        settings().applyRequestedLanguage()

        assertEquals("an empty locale list means follow the system", "", activeTags())
        assertEquals(AppLanguage.SYSTEM, settings().activeLanguage)
    }

    @Test
    fun theInAppChoiceIsWhatIsApplied() {
        // the language may also be set from Android's own per-app language settings; the choice made
        // in the app is the newest thing the user said about it, so it is the one that wins
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
        settings().requestLanguage(AppLanguage.CHINESE)

        settings().applyRequestedLanguage()

        assertTrue("expected zh, was ${activeTags()}", activeTags().startsWith("zh"))
        assertEquals(AppLanguage.CHINESE, settings().requestedLanguage)
    }

    // ------------------------------------------------- the application really does it

    @Test
    fun theRunningApplicationIsTheOneThatAppliesTheLanguageAtStartUp() {
        // Robolectric builds the Application the manifest declares, and its onCreate is what puts
        // the requested language into effect; if the manifest pointed at the plain Application, the
        // recorded choice would never be applied.
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        assertEquals(
            "the manifest must name WakuseiApplication",
            WakuseiApplication::class.java.name,
            info.className
        )
        assertTrue(
            "the running application should be the app's own class, was ${context::class.java.name}",
            context is WakuseiApplication
        )
    }

    @Test
    fun applyingTheRequestIsTheStepThatSwitchesTheLanguage() {
        // the body of WakuseiApplication.onCreate, on its own, so the switch is pinned even if the
        // application class is ever restructured
        settings().requestLanguage(AppLanguage.JAPANESE)
        assertEquals("", activeTags())

        settings().applyRequestedLanguage()

        assertTrue("expected ja after the start-up step, was ${activeTags()}", activeTags().startsWith("ja"))
    }
}
