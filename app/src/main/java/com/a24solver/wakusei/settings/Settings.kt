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
 * Only three things live here, and all three are read where they are used rather than cached: the
 * language, the default target, and whether the keypad makes a sound.
 */
class Settings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The language the user picked; [AppLanguage.SYSTEM] until they pick one. */
    val language: AppLanguage
        get() = AppLanguage.forTag(preferences.getString(KEY_LANGUAGE, null))

    /**
     * Stores [language] and switches the running app over to it straight away — including the
     * screens that are already on the back stack, which AppCompat recreates.
     *
     * This is the only way to change the language on purpose: the [language] property is read-only
     * so a stored choice can never disagree with what is on screen.
     */
    fun applyLanguage(language: AppLanguage) {
        preferences.edit { putString(KEY_LANGUAGE, language.languageTag) }
        AppCompatDelegate.setApplicationLocales(language.locales)
    }

    /** Whether the keypad plays notes. On by default. */
    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND, true)
        set(value) {
            preferences.edit { putBoolean(KEY_SOUND, value) }
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
        const val MAX_ABS_TARGET = 16384
        const val DEFAULT_TARGET = 24

        /** Visible to the tests, which clear it between cases. */
        internal const val FILE_NAME = "wakusei.settings"

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SOUND = "soundEnabled"
        private const val KEY_PRESET = "defaultTargetPreset"
        private const val KEY_CUSTOM_TARGET = "customDefaultTarget"

        /** [DefaultTargetPreset.TWENTY_FOUR], i.e. the app's long-standing default. */
        private val DEFAULT_PRESET_INDEX = DefaultTargetPreset.TWENTY_FOUR.ordinal

        fun isValidTarget(value: Int): Boolean = abs(value) <= MAX_ABS_TARGET
    }
}
