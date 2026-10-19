package com.a24solver.wakusei.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.a24solver.wakusei.R
import kotlin.math.abs

/**
 * The languages the app ships. [SYSTEM] follows the device; the others pin the app to one language
 * until the user changes it again.
 *
 * The tag is also what `res/xml/locales_config.xml` advertises to the system, so the same choice is
 * available from Android's own per-app language settings.
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en"),
    JAPANESE("ja");

    /** Empty means "no preference", which is how AppCompat spells "follow the system". */
    val locales: LocaleListCompat
        get() = languageTag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()

    /** The label to show in the settings list: the language's own name, or "follow system". */
    val labelRes: Int
        get() = when (this) {
            SYSTEM -> R.string.language_system
            CHINESE -> R.string.language_chinese
            ENGLISH -> R.string.language_english
            JAPANESE -> R.string.language_japanese
        }

    companion object {
        /** Unknown or absent tags (including a language we do not ship) fall back to [SYSTEM]. */
        fun forTag(tag: String?): AppLanguage = entries.firstOrNull { it.languageTag == tag } ?: SYSTEM

        /**
         * Matches a locale list like `zh-Hans-CN,en` — the form [AppCompatDelegate] reports — against
         * the languages the app ships, by prefix, so a regional variant still counts as its language.
         */
        fun forLanguageTags(tags: String?): AppLanguage = tags
            ?.split(',')
            ?.asSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?.let { tag -> entries.firstOrNull { it.languageTag != null && tag.startsWith(it.languageTag) } }
            ?: SYSTEM
    }
}

/**
 * The nodes of the default-target slider, in the order they appear.
 *
 * [CUSTOMIZE] is not a value: it is the position that hands control to the number field. The
 * negative presets are there on purpose — they are how a user finds out that a target may be
 * negative at all.
 */
enum class DefaultTargetPreset(val presetValue: Int?) {
    CUSTOMIZE(null),
    MINUS_24(-24),
    MINUS_10(-10),
    ZERO(0),
    TEN(10),
    TWENTY_FOUR(24),
    SIXTY(60);

    val isCustom: Boolean get() = presetValue == null

    companion object {
        fun forIndex(index: Int): DefaultTargetPreset = entries[index.coerceIn(entries.indices)]
    }
}

/**
 * The app's settings, backed by `SharedPreferences`.
 *
 * Everything here is read where it is used rather than cached: the language, the default target,
 * whether the keypad makes a sound, and whether the help page has already introduced itself.
 */
class Settings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * The language the user last picked. It does **not** take effect when it is stored — see
     * [requestLanguage] — only at the next start of the app.
     */
    val requestedLanguage: AppLanguage
        get() = AppLanguage.forTag(preferences.getString(KEY_LANGUAGE, null))

    /**
     * The language the app is running in right now, or [AppLanguage.SYSTEM] when it follows the
     * device.
     *
     * Read back from AppCompat rather than from a private copy, so a language chosen from Android's
     * own per-app language settings is reflected here too. While a newly requested language is
     * waiting for a restart this still reports the old one — which is exactly what the settings
     * screen needs to show both.
     */
    val activeLanguage: AppLanguage
        get() = AppLanguage.forLanguageTags(
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        )

    /**
     * Records [language] as the one to use. Nothing changes on screen yet: the switch happens when
     * the settings screen is left, via [applyRequestedLanguage].
     *
     * Applying it here instead would recreate every screen the moment the user touches the list —
     * including the settings screen they are standing on, mid-scroll.
     */
    fun requestLanguage(language: AppLanguage) {
        preferences.edit { putString(KEY_LANGUAGE, language.languageTag) }
    }

    /**
     * Puts [requestedLanguage] into effect. Called when the settings screen is left, and once at
     * start-up so a choice made before the process died is not lost.
     *
     * The choice made in the app always wins, deliberately: it is the newest thing the user said
     * about the language. A language set from Android's own per-app language settings is shown as
     * the current one while it lasts, and is replaced the next time this runs.
     */
    fun applyRequestedLanguage() {
        AppCompatDelegate.setApplicationLocales(requestedLanguage.locales)
    }

    /** Whether the language the user asked for is the one actually on screen. */
    val languageChangeIsPending: Boolean
        get() = requestedLanguage != activeLanguage

    /** Whether the keypad plays notes. On by default. */
    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND, true)
        set(value) {
            preferences.edit { putBoolean(KEY_SOUND, value) }
        }

    /**
     * How long a press on CLEAR or DEL has to last before it counts as a long press, in
     * milliseconds. One value for both keys, by design: they are the same gesture.
     *
     * The platform's own timeout is a device-wide constant, so the app measures the press itself
     * (see `LongPressGesture`) and this is the number it uses.
     */
    var longPressMs: Int
        get() = preferences.getInt(KEY_LONG_PRESS, DEFAULT_LONG_PRESS_MS)
        set(value) {
            require(isValidLongPress(value)) {
                "$value ms is outside $MIN_LONG_PRESS_MS..$MAX_LONG_PRESS_MS"
            }
            preferences.edit { putInt(KEY_LONG_PRESS, value) }
        }

    /**
     * Whether the help page has already been shown on its own. It is opened automatically once, on
     * the very first start, and never again — afterwards it is one tap away in the settings screen.
     *
     * The flag is stored when the page is opened rather than when it is closed, so a start that goes
     * wrong cannot turn into a loop where the help page is forced on every launch.
     */
    var helpShown: Boolean
        get() = preferences.getBoolean(KEY_HELP_SHOWN, false)
        set(value) {
            preferences.edit { putBoolean(KEY_HELP_SHOWN, value) }
        }

    /** Which slider node is selected. */
    var defaultTargetPreset: DefaultTargetPreset
        get() = DefaultTargetPreset.forIndex(preferences.getInt(KEY_PRESET, DEFAULT_PRESET_INDEX))
        set(value) {
            preferences.edit { putInt(KEY_PRESET, value.ordinal) }
        }

    /** The value typed in the number field; only meaningful while the node is [DefaultTargetPreset.CUSTOMIZE]. */
    var customDefaultTarget: Int
        get() = preferences.getInt(KEY_CUSTOM_TARGET, DEFAULT_TARGET)
        set(value) {
            require(isValidTarget(value)) { "$value is outside ±$MAX_ABS_TARGET" }
            preferences.edit { putInt(KEY_CUSTOM_TARGET, value) }
        }

    /**
     * The target a fresh input screen starts with, and the one CLEAR long-press returns to.
     *
     * Changing it never touches the target currently in use — that is the user's, not the setting's.
     */
    val defaultTarget: Int
        get() = defaultTargetPreset.presetValue ?: customDefaultTarget

    companion object {
        const val MAX_ABS_TARGET = 99999
        const val DEFAULT_TARGET = 24

        /** The platform's own long-press timeout, and the value a fresh install starts with. */
        const val DEFAULT_LONG_PRESS_MS = 500

        /**
         * The range the long-press threshold may be set to.
         *
         * Below the minimum a long press would fire during an ordinary tap; above the maximum the
         * user would have to hold the key so long that the gesture feels broken rather than slow.
         */
        const val MIN_LONG_PRESS_MS = 100
        const val MAX_LONG_PRESS_MS = 2000

        fun isValidLongPress(value: Int): Boolean = value in MIN_LONG_PRESS_MS..MAX_LONG_PRESS_MS

        /** Visible to the tests, which clear it between cases. */
        internal const val FILE_NAME = "wakusei.settings"

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SOUND = "soundEnabled"
        private const val KEY_LONG_PRESS = "longPressMs"
        private const val KEY_HELP_SHOWN = "helpShown"
        private const val KEY_PRESET = "defaultTargetPreset"
        private const val KEY_CUSTOM_TARGET = "customDefaultTarget"

        /** [DefaultTargetPreset.TWENTY_FOUR], i.e. the app's long-standing default. */
        private val DEFAULT_PRESET_INDEX = DefaultTargetPreset.TWENTY_FOUR.ordinal

        fun isValidTarget(value: Int): Boolean = abs(value) <= MAX_ABS_TARGET
    }
}
