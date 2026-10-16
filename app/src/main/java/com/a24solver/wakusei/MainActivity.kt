package com.a24solver.wakusei

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
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

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    /** The three-line button at the right end of the toolbar opens the settings screen. */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            // Guard against pushing a second copy of the screen when the button is already there
            if (navController.currentDestination?.id != R.id.SettingsFragment) {
                navController.navigate(R.id.SettingsFragment)
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private val navController: NavController
        get() = (supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment).navController
}
