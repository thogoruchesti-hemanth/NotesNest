package com.example.NotesNest.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.NotesNest.R;
import com.example.NotesNest.adapter.OnboardingAdapter;
import com.example.NotesNest.databinding.ActivityOnboardingBinding;
import com.example.NotesNest.models.OnBoardItem;
import com.example.NotesNest.utils.AnalyticsHelper;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.constants.PrefKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust Onboarding Activity.
 * Updated to custom expanding pill indicators, string localization, haptic feedback,
 * stateListAnimator press scale, and clean minimalist text-only action buttons (10/10 UX).
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String KEY_CURRENT_PAGE = "current_onboarding_page";
    private ActivityOnboardingBinding binding;
    private OnboardingAdapter onboardingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // Skip onboarding if already completed
        if (AppPreferences.getInstance().getBoolean(PrefKeys.IS_ONBOARDING_COMPLETED, false)) {
            navigateToLogin();
            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.onBoardingActivity, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupOnBoardingItems();

        // Restore page if rotated
        int startPage = 0;
        if (savedInstanceState != null) {
            startPage = savedInstanceState.getInt(KEY_CURRENT_PAGE, 0);
        }

        binding.vpOnboarding.setCurrentItem(startPage, false);
        updateIndicators(startPage);
        updateButtonText(startPage);

        setupListeners();
        setupBackPressed();
    }

    private void setupListeners() {
        binding.vpOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
                updateButtonText(position);
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            int current = binding.vpOnboarding.getCurrentItem();
            if (current + 1 < onboardingAdapter.getItemCount()) {
                binding.vpOnboarding.setCurrentItem(current + 1);
            } else {
                completeOnboarding();
            }
        });

        binding.tvSkip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            completeOnboarding();
        });
    }

    private void updateIndicators(int position) {
        View[] dots = {binding.dot1, binding.dot2, binding.dot3};
        int selectedWidth = getResources().getDimensionPixelSize(R.dimen.size_24);
        int unselectedWidth = getResources().getDimensionPixelSize(R.dimen.padding_8);

        for (int i = 0; i < dots.length; i++) {
            boolean isSelected = (i == position);
            View dot = dots[i];

            dot.setBackgroundResource(isSelected ? R.drawable.dot_selected : R.drawable.dot_unselected);
            ViewGroup.LayoutParams params = dot.getLayoutParams();
            params.width = isSelected ? selectedWidth : unselectedWidth;
            dot.setLayoutParams(params);
        }
    }

    private void updateButtonText(int position) {
        boolean isLastPage = (position == onboardingAdapter.getItemCount() - 1);
        binding.btnNext.setText(isLastPage ? R.string.text_get_started : R.string.text_next);

        // Clean, minimalist text-only action button for elite modern UI
        binding.btnNext.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        binding.tvSkip.setVisibility(isLastPage ? View.GONE : View.VISIBLE);
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int current = binding.vpOnboarding.getCurrentItem();
                if (current > 0) {
                    binding.vpOnboarding.setCurrentItem(current - 1);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void completeOnboarding() {
        AppPreferences.getInstance().setOnboardingCompleted(true);
        navigateToLogin();
    }

    private void navigateToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setupOnBoardingItems() {
        List<OnBoardItem> items = new ArrayList<>();
        items.add(new OnBoardItem(
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1),
                R.drawable.onboarding_image1
        ));
        items.add(new OnBoardItem(
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2),
                R.drawable.onboarding_image2
        ));
        items.add(new OnBoardItem(
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3),
                R.drawable.onboarding_image3
        ));

        onboardingAdapter = new OnboardingAdapter(items);
        binding.vpOnboarding.setAdapter(onboardingAdapter);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt(KEY_CURRENT_PAGE, binding.vpOnboarding.getCurrentItem());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AnalyticsHelper.logScreenView(getClass().getSimpleName(), getClass().getSimpleName());
    }
}
