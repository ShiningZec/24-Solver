package com.a24solver.wakusei

import android.content.Context
import com.a24solver.wakusei.settings.Settings

/**
 * A "second run" of the app, for tests that are not about the first one.
 *
 * The help page opens itself on the very first start (see `FirstFragment`), so a freshly cleared
 * settings file puts a screen in front of the keypad. Every test that wants to look at the keypad,
 * the result screen or the settings starts with this, which also says out loud what those tests
 * assume: the user has already seen the help.
 *
 * Call it *before* building the activity — the push happens while the first screen is created.
 */
internal fun markHelpSeen(context: Context) {
    Settings(context).helpShown = true
}
