package com.a24solver.wakusei

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the ads safety net.
 *
 * The first test is the regression guard for the launch crash
 * `RuntimeException: Unable to get provider com.google.android.gms.ads.MobileAdsInitProvider:
 * java.lang.IllegalStateException` — it fails when the AdMob app ID meta-data is missing or
 * malformed, which is exactly the condition the SDK's ContentProvider aborts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun admobAppIdIsDeclaredInTheManifest() {
        val appId = Ads.appId(context)
        assertTrue(
            "the manifest must declare $APP_ID_META (see AndroidManifest.xml), was: $appId",
            appId != null
        )
        assertTrue("not an AdMob app ID: $appId", Ads.isConfigured(context))
        assertTrue("app IDs look like ca-app-pub-…~…, was: $appId", appId!!.startsWith("ca-app-pub-") && appId.contains('~'))
    }

    @Test
    fun adOperationsAreGuarded() {
        // an ordinary ad failure is suppressed and reported as null
        assertNull(Ads.loadSafely<Any>("explode") { throw IllegalStateException("boom") })
        // ... and so is an Error, e.g. NoClassDefFoundError when the ads artifact is absent
        assertNull(Ads.loadSafely<Any>("explode-hard") { throw NoClassDefFoundError("no ads sdk") })
        // a working call is passed through
        assertEquals("ok", Ads.loadSafely("works") { "ok" })
    }

    @Test(timeout = 15_000)
    fun initializeNeverThrows() {
        // whatever the SDK does here (it may fail outside a real device), it must not escape
        Ads.initialize(context)
        // calling it twice is a no-op
        Ads.initialize(context)
    }

    private companion object {
        private const val APP_ID_META = Ads.APP_ID_META
    }
}
