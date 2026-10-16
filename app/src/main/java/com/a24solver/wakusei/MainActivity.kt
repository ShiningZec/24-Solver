package com.a24solver.wakusei

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
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
 * Window insets are handled declaratively: the root layout and its app bar carry
 * `android:fitsSystemWindows="true"`, which is what makes the content avoid the status and
 * navigation bars under [enableEdgeToEdge]. There is deliberately no
 * `ViewCompat.setOnApplyWindowInsetsListener` here as well — that would pad the same insets a
 * second time and push the toolbar down by an extra status bar.
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
