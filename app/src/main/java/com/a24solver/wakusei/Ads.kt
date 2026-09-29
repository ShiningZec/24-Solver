package com.a24solver.wakusei

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exception-safe entry point for the Google Mobile Ads (AdMob) SDK.
 *
 * This class guards *every* call the app makes into the ads SDK. It cannot, however, guard the
 * launch crash
 *
 * ```
 * java.lang.RuntimeException: Unable to get provider
 * com.google.android.gms.ads.MobileAdsInitProvider: java.lang.IllegalStateException
 * ```
 *
 * because that provider is instantiated inside `ActivityThread.handleBindApplication`, before
 * `Application.onCreate()` — no application code is on the stack, so `try/catch` is useless there.
 * The crash is caused by a missing or malformed `com.google.android.gms.ads.APPLICATION_ID`
 * meta-data, which is why the tag is declared in `AndroidManifest.xml` (and asserted by
 * `AdsTest`). Everything that happens *after* that — initialization, and the loading of future
 * banners/interstitials — goes through [loadSafely] and can therefore never take the app down.
 */
object Ads {

    private const val TAG = "Ads"

    /** Manifest key the ads SDK reads its app ID from. */
    const val APP_ID_META = "com.google.android.gms.ads.APPLICATION_ID"

    private val initialized = AtomicBoolean(false)

    /** The AdMob app ID declared in the manifest, or null when it is missing or blank. */
    fun appId(context: Context): String? = try {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString(APP_ID_META)
            ?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        Log.w(TAG, "could not read $APP_ID_META from the manifest", t)
        null
    }

    /** True when the declared app ID looks like an AdMob app ID (`ca-app-pub-…~…`). */
    fun isConfigured(context: Context): Boolean {
        val id = appId(context) ?: return false
        return id.startsWith("ca-app-pub-") && id.contains('~')
    }

    /**
     * Initializes the SDK at most once. Returns true when the SDK is usable, false when the app ID
     * is missing/invalid or initialization failed. Never throws — failures are logged only.
     */
    fun initialize(context: Context): Boolean {
        if (initialized.get()) return true
        if (!isConfigured(context)) {
            Log.w(TAG, "AdMob app ID missing or invalid — skipping MobileAds.initialize()")
            return false
        }
        return loadSafely("MobileAds.initialize") {
            MobileAds.initialize(context)
            initialized.set(true)
            true
        } ?: false
    }

    /**
     * Runs an ad operation (initialize / load / show) defensively: returns the result, or null
     * when it failed. The failure is logged and never propagated.
     *
     * `Throwable` is caught on purpose: besides ordinary exceptions, the SDK (or a missing ads
     * artifact) fails with `NoClassDefFoundError` / `ExceptionInInitializerError`.
     */
    fun <T> loadSafely(operation: String, load: () -> T): T? = try {
        load()
    } catch (t: Throwable) {
        Log.w(TAG, "ad operation failed and was suppressed: $operation", t)
        null
    }
}
