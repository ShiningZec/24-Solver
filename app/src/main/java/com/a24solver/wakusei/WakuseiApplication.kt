package com.a24solver.wakusei

import android.app.Application
import com.a24solver.wakusei.settings.Settings

/**
 * The one thing the app does before any screen exists: put the requested language into effect.
 *
 * The settings screen only *records* a language choice, so that picking one does not rebuild the
 * interface under the user's finger. This is the other half of that bargain — the choice is applied
 * here, once per process, which is why the change appears "the next time the app starts" and not
 * when the screen is rotated. (A configuration change does not run this.)
 */
class WakuseiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Settings(this).applyRequestedLanguage()
    }
}
