package com.example.NotesNest.fragments;

import static com.example.NotesNest.editor.CKEditorHelper.getThemeColor;
import static com.example.NotesNest.utils.ValidationUtils.isNetworkAvailable;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.NotesNest.R;
import com.example.NotesNest.activity.EditNoteActivity;
import com.example.NotesNest.adapter.NoteAdapter;
import com.example.NotesNest.adapter.NoteShimmerAdapter;
import com.example.NotesNest.databases.ViewModels.CategoryViewModel;
import com.example.NotesNest.databases.ViewModels.NoteViewModel;
import com.example.NotesNest.databases.entities.CategoryEntity;
import com.example.NotesNest.databases.entities.NoteEntity;
import com.example.NotesNest.utils.AdManager;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.CategoryManager;
import com.example.NotesNest.utils.AnalyticsHelper;
import com.example.NotesNest.utils.CommonDialogs;
import com.example.NotesNest.utils.LayoutToggleViewModel;
import com.example.NotesNest.utils.PremiumManager;
import com.example.NotesNest.utils.ThemeManager;
import com.example.NotesNest.utils.constants.PrefKeys;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NotesFragment extends Fragment implements ThemeManager.ThemeChangeListener {

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> categoryNoteCounts = new HashMap<>();
    private RecyclerView recyclerView;
    private NoteAdapter adapter;
    private EditText searchEditText;
    private ImageButton clearSearchBtn;
    private TabLayout tabLayout;
    private Button createButton;
    private TextView titleTextView;
    private NoteViewModel noteViewModel;
    private CategoryViewModel categoryViewModel;
    private Runnable searchRunnable;
    private String selectedCategory = "All";
    private List<CategoryEntity> categoryList = new ArrayList<>();
    private int unselectedTabColor = -1;
    private LiveData<List<NoteEntity>> currentNotesLiveData;
    private Observer<List<NoteEntity>> currentNotesObserver;
    private ActivityResultLauncher<Intent> addEditNoteLauncher;
    private String currentUserId = null;
    private AppPreferences appPreferences;
    private PremiumManager premiumManager;
    private int currentNotesCount = 0;
    private Context context;
    private NoteShimmerAdapter shimmerAdapter;
    private boolean isLoading = false;
    private AdView adView;
    private boolean isGridLayout = true;
    private View emptyStateLayout;
    private final Handler shimmerHandler = new Handler(Looper.getMainLooper());
    private Runnable shimmerRunnable;

    private final BroadcastReceiver premiumReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AppPreferences.ACTION_PREMIUM_UPDATED.equals(intent.getAction())) {
                boolean isPremium = AppPreferences.getInstance().isUserPremium();
                if (isPremium) {
                    if (adView != null) {
                        adView.setVisibility(View.GONE);
                        adView.destroy();
                    }
                    if (createButton != null) {
                        createButton.setAlpha(1.0f);
                    }
                } else {
                    if (adView != null && adView.getVisibility() == View.GONE) {
                        adView.setVisibility(View.VISIBLE);
                        setupBannerAd();
                    }
                    observeNoteCount(); // Re-check limits
                }
            }
        }
    };

    public NotesFragment() { /* Required empty constructor */ }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        tabLayout = view.findViewById(R.id.tabLayout);
        ImageButton manageCategoryButton = view.findViewById(R.id.btnManageCategory);
        createButton = view.findViewById(R.id.btnCreate);
        recyclerView = view.findViewById(R.id.recyclerView);
        titleTextView = view.findViewById(R.id.tvTitle);
        searchEditText = view.findViewById(R.id.searchEditText);
        clearSearchBtn = view.findViewById(R.id.clearSearchBtn);
        adView = view.findViewById(R.id.adViewNotes);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);

        context = getContext();
        appPreferences = AppPreferences.getInstance();
        premiumManager = new PremiumManager(context);
        currentUserId = appPreferences.getUserId();

        // Load initial layout preference
        isGridLayout = appPreferences.getString(PrefKeys.KEY_NOTES_LAYOUT, "Grid").equals("Grid");

        addEditNoteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        runSearch(searchEditText.getText().toString().trim());
                    }
                });


        if (context != null) {
            unselectedTabColor = getThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
        }

        noteViewModel = new ViewModelProvider(requireActivity()).get(NoteViewModel.class);
        categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        setupRecycler();
        setupSearch();
        setupCreateButton();
        setupTabs();
        manageCategoryButton.setOnClickListener(v -> showCategoryManager());

        observeCategories();
        ThemeManager.registerListener(this);
        observeNoteCount();
        setupBannerAd();

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                premiumReceiver, new IntentFilter(AppPreferences.ACTION_PREMIUM_UPDATED));

        return view;
    }

    private void setupBannerAd() {
        if (premiumManager.isPremium()) {
            adView.setVisibility(View.GONE);
            return;
        }
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void showCategoryManager() {
        if (categoryViewModel == null || noteViewModel == null) {
            Toast.makeText(context, "Error loading categories", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryManager categoryManager = new CategoryManager(
                requireContext(),
                categoryViewModel,
                noteViewModel,
                currentUserId
        );

        categoryManager.show(getParentFragmentManager(), "CategoryManager");
    }

    private void setupRecycler() {
        adapter = new NoteAdapter(new ArrayList<>(), requireContext(), categoryViewModel, noteViewModel);
        shimmerAdapter = new NoteShimmerAdapter(10);

        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(isGridLayout ? 2 : 1, StaggeredGridLayoutManager.VERTICAL));
        recyclerView.setAdapter(shimmerAdapter);

        LayoutToggleViewModel layoutToggleViewModel = new ViewModelProvider(requireActivity()).get(LayoutToggleViewModel.class);
        layoutToggleViewModel.getLayoutType().observe(getViewLifecycleOwner(), isGrid -> {
            isGridLayout = isGrid;
            refreshLayout();
        });
    }

    private void refreshLayout() {
        if (!isLoading) {
            recyclerView.setLayoutManager(isGridLayout ?
                    new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL) : 
                    new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
            if (recyclerView.getAdapter() != adapter) recyclerView.setAdapter(adapter);
        }
    }


    private void setupCreateButton() {
        createButton.setOnClickListener(v -> {
            if (premiumManager.isPremium()) {
                openCreateItem();
            } else {
                if (currentNotesCount >= PremiumManager.MAX_FREE_NOTES) {
                    if (!isNetworkAvailable(context)) {
                        CommonDialogs.showPremiumRequiredDialog(context, 
                                "Note limit reached (30 notes). Premium required for more. Please connect to internet to upgrade.");
                    } else {
                        CommonDialogs.showConfirmDialog(context, "Note Limit Reached", 
                                "You have reached the limit of 30 notes. To add more notes, you need to upgrade to Premium. Would you like to watch an ad to add this note?", 
                                "Watch Ad", "Upgrade", () -> AdManager.showRewardedAd(requireActivity(), this::openCreateItem), () -> premiumManager.showUpgradeScreen());
                    }
                } else {
                    openCreateItem();
                }
            }
        });
    }

    private void openCreateItem() {
        Intent intent = new Intent(context, EditNoteActivity.class);
        if (!"All".equalsIgnoreCase(selectedCategory)) {
            String catId = getCategoryIdByName(selectedCategory);
            if (catId != null) {
                intent.putExtra("selectedCategoryId", catId);
            }
        }
        addEditNoteLauncher.launch(intent);
    }

    private void setupSearch() {
        updateSearchHint();
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                clearSearchBtn.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> runSearch(query);
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        clearSearchBtn.setOnClickListener(v -> {
            searchEditText.setText("");
            searchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) searchEditText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            updateSearchHint();
            runSearch("");
        });
    }

    private void updateSearchHint() {
        if (!isAdded()) return;

        if ("All".equalsIgnoreCase(selectedCategory)) {
            int[] hints = {
                    R.string.hint_search_general,
                    R.string.hint_search_ideas,
                    R.string.hint_search_tasks,
                    R.string.hint_search_memories
            };
            int randomIndex = (int) (Math.random() * hints.length);
            searchEditText.setHint(getString(hints[randomIndex]));
        } else {
            searchEditText.setHint(getString(R.string.hint_search_category, selectedCategory));
        }
    }

    private void observeCategories() {
        categoryViewModel.getAllCategories(currentUserId).observe(getViewLifecycleOwner(), categories -> {
            List<CategoryEntity> list = new ArrayList<>();
            if (categories != null) list.addAll(categories);
            list.sort(Comparator.comparingInt(c -> c.order));

            boolean hasAll = false;
            for (CategoryEntity c : list) {
                if ("All".equalsIgnoreCase(c.name)) {
                    hasAll = true;
                    break;
                }
            }
            if (!hasAll) {
                CategoryEntity all = new CategoryEntity();
                all.id = "all";
                all.name = "All";
                list.add(0, all);
            }

            if (isCategoryListSame(categoryList, list)) {
                return;
            }

            categoryList = list;
            buildTabs();
            setupCountObservers();
            selectTabByName(selectedCategory);
            runSearch(searchEditText.getText().toString().trim());
        });
    }

    private void setupCountObservers() {
        categoryNoteCounts.clear();
        noteViewModel.getNotesCount(currentUserId).observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                categoryNoteCounts.put("All", count);
                updateTabCountDisplay("All", count);
            }
        });

        for (CategoryEntity category : categoryList) {
            if (!"All".equals(category.name)) {
                noteViewModel.getNotesCountByCategory(currentUserId, category.id)
                        .observe(getViewLifecycleOwner(), count -> {
                            if (count != null) {
                                categoryNoteCounts.put(category.name, count);
                                updateTabCountDisplay(category.name, count);
                            }
                        });
            }
        }
    }

    private boolean isCategoryListSame(List<CategoryEntity> oldList, List<CategoryEntity> newList) {
        if (oldList.size() != newList.size()) return false;
        for (int i = 0; i < oldList.size(); i++) {
            if (!java.util.Objects.equals(oldList.get(i).id, newList.get(i).id) || 
                !java.util.Objects.equals(oldList.get(i).name, newList.get(i).name)) {
                return false;
            }
        }
        return true;
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                setTabSelected(tab);
                String name = extractTabName(tab);
                if (name != null) {
                    selectedCategory = name;
                    titleTextView.setText(selectedCategory);
                    updateSearchHint();
                    runSearch(searchEditText.getText().toString().trim());
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { setTabUnselected(tab); }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                runSearch(searchEditText.getText().toString().trim());
            }
        });
    }

    private void buildTabs() {
        tabLayout.removeAllTabs();
        for (CategoryEntity category : categoryList) {
            TabLayout.Tab tab = tabLayout.newTab();
            View customView = createCustomTab(category.name, category.name.equals(selectedCategory));
            tab.setCustomView(customView);
            tab.setTag(category.name);
            tabLayout.addTab(tab);
        }
        refreshTabCountDisplays();
    }

    private String extractTabName(TabLayout.Tab tab) {
        if (tab == null || tab.getCustomView() == null) return null;
        TextView txt = tab.getCustomView().findViewById(R.id.tvName);
        return txt == null ? null : txt.getText().toString();
    }

    private View createCustomTab(String title, boolean selected) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.custom_tab, tabLayout, false);
        TextView text = view.findViewById(R.id.tvName);
        TextView count = view.findViewById(R.id.tvCount);
        text.setText(title);
        text.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextColor(selected ? ContextCompat.getColor(requireContext(), R.color.tabSelectedTextColor) : unselectedTabColor);

        Integer noteCount = categoryNoteCounts.get(title);
        if (selected && noteCount != null && noteCount > 0) {
            count.setText(noteCount > 99 ? "99+" : String.valueOf(noteCount));
            count.setVisibility(View.VISIBLE);
        } else {
            count.setVisibility(View.GONE);
        }
        return view;
    }

    private void setTabSelected(@Nullable TabLayout.Tab tab) {
        if (tab == null || tab.getCustomView() == null) return;
        TextView text = tab.getCustomView().findViewById(R.id.tvName);
        TextView count = tab.getCustomView().findViewById(R.id.tvCount);
        if (text == null) return;
        text.setTypeface(null, Typeface.BOLD);
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.tabSelectedTextColor));

        String categoryName = extractTabName(tab);
        Integer noteCount = categoryNoteCounts.get(categoryName);
        if (noteCount != null && noteCount > 0) {
            count.setText(noteCount > 99 ? "99+" : String.valueOf(noteCount));
            count.setVisibility(View.VISIBLE);
        } else {
            count.setVisibility(View.GONE);
        }
    }

    private void setTabUnselected(@Nullable TabLayout.Tab tab) {
        if (tab == null || tab.getCustomView() == null) return;
        TextView text = tab.getCustomView().findViewById(R.id.tvName);
        TextView count = tab.getCustomView().findViewById(R.id.tvCount);
        if (text == null) return;
        text.setTypeface(null, Typeface.NORMAL);
        text.setTextColor(unselectedTabColor);
        count.setVisibility(View.GONE);
    }

    private void selectTabByName(String categoryName) {
        boolean matched = false;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab t = tabLayout.getTabAt(i);
            if (t == null || t.getCustomView() == null) continue;
            TextView txt = t.getCustomView().findViewById(R.id.tvName);
            if (txt != null && txt.getText().toString().equals(categoryName)) {
                tabLayout.selectTab(t);
                setTabSelected(t);
                matched = true;
                break;
            }
        }
        if (!matched && tabLayout.getTabCount() > 0) {
            TabLayout.Tab first = tabLayout.getTabAt(0);
            if (first != null) {
                tabLayout.selectTab(first);
                setTabSelected(first);
                TextView txt = Objects.requireNonNull(first.getCustomView()).findViewById(R.id.tvName);
                if (txt != null) selectedCategory = txt.getText().toString();
            }
        }
        titleTextView.setText(selectedCategory);
    }

    private void runSearch(String query) {
        if (shimmerRunnable != null) shimmerHandler.removeCallbacks(shimmerRunnable);
        
        if (currentNotesLiveData != null && currentNotesObserver != null) {
            currentNotesLiveData.removeObserver(currentNotesObserver);
        }

        shimmerRunnable = () -> {
            if (isAdded()) {
                showShimmerAdapter();
            }
        };
        shimmerHandler.postDelayed(shimmerRunnable, 100);

        currentNotesObserver = notes -> updateRecycler(notes != null ? notes : new ArrayList<>());

        if (query == null) query = "";
        query = query.trim();

        if (query.isEmpty()) {
            if ("All".equalsIgnoreCase(selectedCategory)) {
                currentNotesLiveData = noteViewModel.getAllNotes(currentUserId);
            } else {
                String catId = getCategoryIdByName(selectedCategory);
                if ("all".equals(catId) || catId == null) {
                    currentNotesLiveData = noteViewModel.getAllNotes(currentUserId);
                } else {
                    currentNotesLiveData = noteViewModel.getNotesByCategory(currentUserId, catId);
                }
            }
        } else {
            if ("All".equalsIgnoreCase(selectedCategory)) {
                currentNotesLiveData = noteViewModel.searchNotes(currentUserId, query);
            } else {
                String catId = getCategoryIdByName(selectedCategory);
                if ("all".equals(catId) || catId == null) {
                    currentNotesLiveData = noteViewModel.searchNotes(currentUserId, query);
                } else {
                    currentNotesLiveData = noteViewModel.searchNotesInCategory(currentUserId, catId, query);
                }
            }
        }
        
        if (currentNotesLiveData != null) {
            currentNotesLiveData.observe(getViewLifecycleOwner(), currentNotesObserver);
        }
        refreshTabCountDisplays();
    }

    private void refreshTabCountDisplays() {
        for (Map.Entry<String, Integer> entry : categoryNoteCounts.entrySet()) {
            updateTabCountDisplay(entry.getKey(), entry.getValue());
        }
    }

    private void updateRecycler(List<NoteEntity> notes) {
        if (shimmerRunnable != null) shimmerHandler.removeCallbacks(shimmerRunnable);

        if (isLoading) {
            isLoading = false;
            refreshLayout();
        }
        adapter.updateData(notes);
        if (notes == null || notes.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private String getCategoryIdByName(String name) {
        for (CategoryEntity c : categoryList) {
            if (c != null && name.equals(c.name)) return c.id;
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Load latest preference on resume
        isGridLayout = appPreferences.getBoolean(PrefKeys.KEY_NOTES_LAYOUT, true);
        refreshLayout();

        runSearch(searchEditText.getText().toString().trim());
        refreshTabCountDisplays();
        AnalyticsHelper.logScreenView("Notes", "NotesFragment");
    }

    private void updateTabCountDisplay(String categoryName, int count) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && categoryName.equals(extractTabName(tab))) {
                View customView = tab.getCustomView();
                if (customView != null) {
                    TextView countView = customView.findViewById(R.id.tvCount);
                    if (countView != null) {
                        if (count > 0 && categoryName.equals(selectedCategory)) {
                            countView.setText(count > 99 ? "99+" : String.valueOf(count));
                            countView.setVisibility(View.VISIBLE);
                        } else {
                            countView.setVisibility(View.GONE);
                        }
                    }
                }
                break;
            }
        }
    }

    private void showShimmerAdapter() {
        if (!isLoading) {
            isLoading = true;
            recyclerView.setAdapter(shimmerAdapter);
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ThemeManager.unregisterListener(this);
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        if (shimmerRunnable != null) shimmerHandler.removeCallbacks(shimmerRunnable);
        searchHandler.removeCallbacksAndMessages(null);
        shimmerHandler.removeCallbacksAndMessages(null);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(premiumReceiver);
    }

    @Override
    public void onThemeChanged(@NonNull String newTheme) {
        if (!isAdded()) return;
        unselectedTabColor = getThemeColor(requireContext(), androidx.appcompat.R.attr.colorPrimary);
        buildTabs();
        runSearch(searchEditText.getText().toString().trim());
    }

    private void observeNoteCount() {
        noteViewModel.getNotesCount(currentUserId).observe(getViewLifecycleOwner(), count -> {
            if (count == null) return;
            currentNotesCount = count;
            
            if (!premiumManager.canCreateNote(currentNotesCount)) {
                createButton.setAlpha(0.6f);
            } else {
                createButton.setAlpha(1.0f);
            }
        });
    }
}
