package com.a24solver.wakusei

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.a24solver.wakusei.databinding.ActivityMainBinding

/*
┌─────────────────────────────┐
│         24 Solver           │
├─────────────────────────────┤
│ Target: 24                  │
│ Numbers                     │
│ [  3  ] [  5  ] [ ___ ]     │
│                             │
│                             │
│ Input: 7                    │
├─────────────────────────────┤
│   [  1 ] [ 2 ] [  3  ]      │
│   [  4 ] [ 5 ] [  6  ]      │
│   [  7 ] [ 8 ] [  9  ]      │
│   [NEXT] [ 0 ] [TARGET]     │
├─────────────────────────────┤
│         [ SOLVE ]           │
└─────────────────────────────┘
 */

/**
 * Single-activity shell: a [NavHostFragment] with the two screens of the app (input and results)
 * and a 40dp toolbar that shows the current screen's title and the up arrow.
 *
 * Window insets are applied **here**, explicitly, to the root view: under [enableEdgeToEdge] the
 * window spans the whole screen, so the app itself has to keep its content out of the status and
 * navigation bars. Padding the root by the system bar insets does that once, for every screen.
 *
 * The layouts deliberately carry no `android:fitsSystemWindows` attribute. Relying on that flag
 * instead is the trap this code fell into before: it keeps the top clear but leaves the *bottom* of
 * the screen under the navigation bar, which put the SOLVE and BACK buttons within reach of the
 * system navigation keys; and combining the flag with a listener pads the top twice.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The ads SDK is initialized defensively: see Ads — a failing ad SDK must never take the
        // app down, and the launch crash of MobileAdsInitProvider is prevented by the
        // APPLICATION_ID meta-data in AndroidManifest.xml rather than by code.
        Ads.initialize(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        return navHostFragment.navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
