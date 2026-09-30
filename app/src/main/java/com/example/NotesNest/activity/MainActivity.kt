package com.example.NotesNest.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.widget.ViewPager2
import com.example.NotesNest.R
import com.example.NotesNest.adapter.MainPagerAdapter
import com.example.NotesNest.databases.AppDatabase
import com.example.NotesNest.fragments.NotesFragment
import com.example.NotesNest.utils.AnalyticsHelper
import com.example.NotesNest.utils.AppPreferences
import com.example.NotesNest.utils.BillingManager
import com.example.NotesNest.utils.CurvedFloatingNavDrawable
import com.example.NotesNest.utils.DBSeedUtil
import com.example.NotesNest.utils.PermissionManager
import com.example.NotesNest.utils.ThemeManager
import com.example.NotesNest.utils.constants.PrefKeys
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var pref: AppPreferences
    private var permissionManager: PermissionManager? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var fabCreate: View

    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            permissionManager?.let {
                it.checkExactAlarmPermission()
                it.checkFullScreenIntentPermission()
            }
        }

    private val premiumReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Updated via ViewModel / Fragment observers
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen: SplashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        pref = AppPreferences.getInstance()

        // 1. Check Routing before initializing MainActivity views
        val isOnboardingCompleted = pref.getBoolean(PrefKeys.IS_ONBOARDING_COMPLETED, false)
        if (!isOnboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        if (!pref.login) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 2. Setup theme and EdgeToEdge for MainActivity
        enableEdgeToEdge()
        ThemeManager.applyTheme()

        var isInitialized = false
        splashScreen.setKeepOnScreenCondition { !isInitialized }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                val rootView: View = splashScreenView.view
                rootView.animate()
                    .alpha(0f)
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(350L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction { splashScreenView.remove() }
                    .start()
            }
        }

        setContentView(R.layout.activity_main)

        thread {
            val userId = pref.userId
            if (!userId.isNullOrEmpty()) {
                DBSeedUtil.seedDefaultCategories(this, userId)
            }
            isInitialized = true
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            premiumReceiver, IntentFilter(AppPreferences.ACTION_PREMIUM_UPDATED)
        )

        // Edge-to-Edge System Bar Inset application
        val floatingNavContainer = findViewById<View>(R.id.floatingNavContainer)
        viewPager = findViewById(R.id.viewPager)
        fabCreate = findViewById(R.id.fabCreate)

        ViewCompat.setOnApplyWindowInsetsListener(floatingNavContainer) { view, insets ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        fabCreate.addTouchScaleEffect()
        fabCreate.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
            if (fragment is NotesFragment) {
                fragment.handleCreateNoteFlow()
            } else {
                startActivity(Intent(this, EditNoteActivity::class.java))
            }
        }

        setupNavigation()

        AppDatabase.getInstance(this)

        permissionManager = PermissionManager(this)
        permissionManager?.checkAndRequestPermissions(requestNotificationPermissionLauncher)

        BillingManager.getInstance(this).syncPurchases()
    }

    private fun setupNavigation() {
        val navItemNotes = findViewById<View>(R.id.navItemNotes)
        val navItemReminders = findViewById<View>(R.id.navItemReminders)
        val navItemSettings = findViewById<View>(R.id.navItemSettings)
        val navItemProfile = findViewById<View>(R.id.navItemProfile)

        val navIconNotes = findViewById<ImageView>(R.id.navIconNotes)
        val navTextNotes = findViewById<TextView>(R.id.navTextNotes)

        val navIconReminders = findViewById<ImageView>(R.id.navIconReminders)
        val navTextReminders = findViewById<TextView>(R.id.navTextReminders)

        val navIconSettings = findViewById<ImageView>(R.id.navIconSettings)
        val navTextSettings = findViewById<TextView>(R.id.navTextSettings)

        val navIconProfile = findViewById<ImageView>(R.id.navIconProfile)
        val navTextProfile = findViewById<TextView>(R.id.navTextProfile)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.isUserInputEnabled = false

        fun updateCustomNavSelection(position: Int) {
            val unselectedColor = ContextCompat.getColor(this, R.color.grey_400)
            val selectedYellowColor = ContextCompat.getColor(this, R.color.tabSelectedTextColor)

            navItemNotes.setBackgroundResource(android.R.color.transparent)
            navIconNotes.setColorFilter(unselectedColor)
            navTextNotes.setTextColor(unselectedColor)
            navTextNotes.setTypeface(null, Typeface.NORMAL)

            navItemReminders.setBackgroundResource(android.R.color.transparent)
            navIconReminders.setColorFilter(unselectedColor)
            navTextReminders.setTextColor(unselectedColor)
            navTextReminders.setTypeface(null, Typeface.NORMAL)

            navItemSettings.setBackgroundResource(android.R.color.transparent)
            navIconSettings.setColorFilter(unselectedColor)
            navTextSettings.setTextColor(unselectedColor)
            navTextSettings.setTypeface(null, Typeface.NORMAL)

            navItemProfile.setBackgroundResource(android.R.color.transparent)
            navIconProfile.setColorFilter(unselectedColor)
            navTextProfile.setTextColor(unselectedColor)
            navTextProfile.setTypeface(null, Typeface.NORMAL)

            when (position) {
                0 -> {
                    navIconNotes.setColorFilter(selectedYellowColor)
                    navTextNotes.setTextColor(selectedYellowColor)
                    navTextNotes.setTypeface(null, Typeface.BOLD)
                }
                1 -> {
                    navIconReminders.setColorFilter(selectedYellowColor)
                    navTextReminders.setTextColor(selectedYellowColor)
                    navTextReminders.setTypeface(null, Typeface.BOLD)
                }
                2 -> {
                    navIconProfile.setColorFilter(selectedYellowColor)
                    navTextProfile.setTextColor(selectedYellowColor)
                    navTextProfile.setTypeface(null, Typeface.BOLD)
                }
            }
        }

        navItemNotes.addTouchScaleEffect()
        navItemReminders.addTouchScaleEffect()
        navItemSettings.addTouchScaleEffect()
        navItemProfile.addTouchScaleEffect()

        navItemNotes.setOnClickListener { viewPager.setCurrentItem(0, false) }
        navItemReminders.setOnClickListener { viewPager.setCurrentItem(1, false) }
        navItemSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        navItemProfile.setOnClickListener { viewPager.setCurrentItem(2, false) }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCustomNavSelection(position)
            }
        })

        updateCustomNavSelection(0)
    }

    private fun View.addTouchScaleEffect() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120L).start()
                }
            }
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(premiumReceiver)
    }

    override fun onResume() {
        super.onResume()
        AnalyticsHelper.logScreenView(javaClass.simpleName, javaClass.simpleName)
    }
}
