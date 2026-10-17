package com.a24solver.wakusei.help

import android.content.Context

/**
 * Where the help text comes from: one Markdown file per language, bundled from `docs/help`.
 *
 * The documents live in `docs/help` and nowhere else — the build adds that directory as an asset
 * source (see `app/build.gradle.kts`), so the text on screen, the file in the repository and the
 * archived documentation are the same bytes. There is no second copy to drift.
 *
 * The language is whatever the app is running in, which is what makes the help page follow the
 * language setting for free: on API 33+ and below alike, the resource configuration already carries
 * the per-app locale the settings screen chose. Anything that is not Chinese or Japanese falls back
 * to English, matching the app's own fallback.
 */
object HelpText {

    const val ENGLISH = "help_doc.md"
    const val CHINESE = "help_doc_CN.md"
    const val JAPANESE = "help_doc_JP.md"

    /** The asset that holds the help text for a [language] tag (`zh`, `zh-Hans`, `ja`, …). */
    fun assetName(language: String): String = when {
        language.startsWith("zh", ignoreCase = true) -> CHINESE
        language.startsWith("ja", ignoreCase = true) -> JAPANESE
        else -> ENGLISH
    }

    /** The help text for the language the app is currently showing, or "" if it cannot be read. */
    fun forCurrentLocale(context: Context): String =
        read(context, context.resources.configuration.locales[0].language)

    /** The help text for [language], or "" if the asset is missing or unreadable. */
    fun read(context: Context, language: String): String = readAsset(context, assetName(language))

    /**
     * Reads the named asset, or "" if it cannot be read.
     *
     * A missing document must never take the screen down with it: the page says it could not load the
     * text (see `HelpFragment`) instead of crashing or showing a blank page with no explanation.
     */
    internal fun readAsset(context: Context, assetName: String): String = runCatching {
        context.assets.open(assetName).bufferedReader().use { it.readText() }
    }.getOrDefault("")
}
