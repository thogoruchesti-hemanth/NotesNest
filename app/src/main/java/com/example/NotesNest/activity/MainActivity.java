package com.example.NotesNest.activity;

import static com.example.NotesNest.utils.ThemeManager.applyTheme;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.NotesNest.R;
import com.example.NotesNest.databases.AppDatabase;
import com.example.NotesNest.utils.AnalyticsHelper;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.DBSeedUtil;
import com.example.NotesNest.utils.DrawerHelper;
import com.example.NotesNest.utils.PermissionManager;
import com.example.NotesNest.utils.constants.PrefKeys;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private AppPreferences pref;
    private DrawerHelper drawerHelper;
    private PermissionManager permissionManager;
    private final Random random = new Random();

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Once notification permission is handled, check for the others
                if (permissionManager != null) {
                    permissionManager.checkExactAlarmPermission();
                    permissionManager.checkFullScreenIntentPermission();
                }
            });

    private final BroadcastReceiver premiumReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AppPreferences.ACTION_PREMIUM_UPDATED.equals(intent.getAction()) && drawerHelper != null) {
                    drawerHelper.refreshUI();
                }

        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Install Android 12+ Core Splash Screen API before super.onCreate()
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        EdgeToEdge.enable(this);
        applyTheme();
        super.onCreate(savedInstanceState);

        AppPreferences prefs = AppPreferences.getInstance();

        // 2. Check Onboarding and Login Routing
        boolean isOnboardingCompleted = prefs.getBoolean(PrefKeys.IS_ONBOARDING_COMPLETED, false);
        if (!isOnboardingCompleted) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        if (!prefs.getLogin()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 3. Keep splash screen visible while performing background initialization
        final boolean[] isInitialized = {false};
        splashScreen.setKeepOnScreenCondition(() -> !isInitialized[0]);

        // Smooth exit animation polish for Android 12+ (fading out entire splash window so icon and branding vanish together)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener(splashScreenView -> {
                final View rootView = splashScreenView.getView();

                rootView.animate()
                        .alpha(0f)
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(350L)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .withEndAction(splashScreenView::remove)
                        .start();
            });
        }

        setContentView(R.layout.activity_main);

        // Background initialization work
        new Thread(() -> {
            String userId = prefs.getUserId();
            if (userId != null && !userId.isEmpty()) {
                DBSeedUtil.seedDefaultCategories(this, userId);
            }
            isInitialized[0] = true;
        }).start();

        // Register receiver early to catch status updates from BillingManager sync
        LocalBroadcastManager.getInstance(this).registerReceiver(
                premiumReceiver, new IntentFilter(AppPreferences.ACTION_PREMIUM_UPDATED));

        ViewGroup drawerLayout = findViewById(R.id.mainLayout);
        View contentContainer = drawerLayout.getChildAt(0);

        ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigationView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        drawerHelper = new DrawerHelper(this);
        AppDatabase.getInstance(this);
        pref = AppPreferences.getInstance();
        
        permissionManager = new PermissionManager(this);
        permissionManager.checkAndRequestPermissions(requestNotificationPermissionLauncher);

        // Initialize Billing and Sync status
        com.example.NotesNest.utils.BillingManager.getInstance(this).syncPurchases();

        initGreeting();
    }

    private void initGreeting() {
        TextView greetingText = findViewById(R.id.tvName);
        String[] greetings = {"Hi", "Hello", "Hey", "Welcome", "Yo", "Ola", "Hoi", "Hiya", "Hola"};
        String greeting = greetings[random.nextInt(greetings.length)];
        greetingText.setText(String.format("%s, %s", greeting, pref.getUserName()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(premiumReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AnalyticsHelper.logScreenView(getClass().getSimpleName(), getClass().getSimpleName());
    }
}