package com.example.NotesNest.fragments

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.NotesNest.R
import com.example.NotesNest.activity.EditNoteActivity
import com.example.NotesNest.adapter.NoteAdapter
import com.example.NotesNest.adapter.NoteShimmerAdapter
import com.example.NotesNest.databases.ViewModels.CategoryViewModel
import com.example.NotesNest.databases.ViewModels.NoteViewModel
import com.example.NotesNest.databases.entities.CategoryEntity
import com.example.NotesNest.databases.entities.NoteEntity
import com.example.NotesNest.utils.AdManager
import com.example.NotesNest.utils.AnalyticsHelper
import com.example.NotesNest.utils.AppPreferences
import com.example.NotesNest.utils.CategoryManager
import com.example.NotesNest.utils.CommonDialogs
import com.example.NotesNest.utils.LayoutToggleViewModel
import com.example.NotesNest.utils.PremiumManager
import com.example.NotesNest.utils.ThemeManager
import com.example.NotesNest.utils.ValidationUtils
import com.example.NotesNest.utils.constants.PrefKeys
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Comparator

class NotesFragment : Fragment(), ThemeManager.ThemeChangeListener {

    private val categoryNoteCounts = HashMap<String, Int>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NoteAdapter
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchBtn: ImageButton
    private lateinit var tabLayout: TabLayout
    private lateinit var emptyStateLayout: View
    private var adView: AdView? = null

    private var noteViewModel: NoteViewModel? = null
    private var categoryViewModel: CategoryViewModel? = null

    private var selectedCategory = "All"
    private var lastCategory: String? = null
    private var categoryList: List<CategoryEntity> = ArrayList()
    private var unselectedTabColor = -1

    private var currentNotesLiveData: LiveData<List<NoteEntity>>? = null
    private var currentNotesObserver: Observer<List<NoteEntity>>? = null
    private lateinit var addEditNoteLauncher: ActivityResultLauncher<Intent>

    private var currentUserId: String? = null
    private lateinit var appPreferences: AppPreferences
    private lateinit var premiumManager: PremiumManager
    private var currentNotesCount = 0

    private var shimmerAdapter: NoteShimmerAdapter? = null
    private var isLoading = false
    private var isGridLayout = true

    private var searchJob: Job? = null
    private var shimmerJob: Job? = null

    private val premiumReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AppPreferences.ACTION_PREMIUM_UPDATED == intent?.action) {
                val isPremium = AppPreferences.getInstance().isUserPremium
                if (isPremium) {
                    adView?.let {
                        it.visibility = View.GONE
                        it.destroy()
                    }
                } else {
                    adView?.let {
                        if (it.visibility == View.GONE) {
                            it.visibility = View.VISIBLE
                            setupBannerAd()
                        }
                    }
                    observeNoteCount()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notes, container, false)

        tabLayout = view.findViewById(R.id.tabLayout)
        val manageCategoryButton = view.findViewById<ImageButton>(R.id.btnManageCategory)
        val btnLayoutToggle = view.findViewById<ImageButton>(R.id.btnLayoutToggle)
        recyclerView = view.findViewById(R.id.recyclerView)
        searchEditText = view.findViewById(R.id.searchEditText)
        clearSearchBtn = view.findViewById(R.id.clearSearchBtn)
        adView = view.findViewById(R.id.adViewNotes)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)

        appPreferences = AppPreferences.getInstance()
        premiumManager = PremiumManager(requireContext())
        currentUserId = appPreferences.userId

        isGridLayout = try {
            appPreferences.getBoolean(PrefKeys.KEY_NOTES_LAYOUT, true)
        } catch (e: Exception) {
            true
        }

        val topHeaderContainer = view.findViewById<View>(R.id.topHeaderContainer)
        ViewCompat.setOnApplyWindowInsetsListener(topHeaderContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val extraPaddingTop = (8 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, statusBars.top + extraPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        addEditNoteLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                runSearch(searchEditText.text.toString().trim())
            }
        }

        unselectedTabColor = getThemeAttrColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary
        )

        noteViewModel = ViewModelProvider(requireActivity())[NoteViewModel::class.java]
        categoryViewModel = ViewModelProvider(requireActivity())[CategoryViewModel::class.java]

        setupRecycler()
        setupSearch()
        setupTabs()
        setupLayoutToggle(btnLayoutToggle)
        manageCategoryButton.setOnClickListener { showCategoryManager() }

        observeCategories()
        ThemeManager.registerListener(this)
        observeNoteCount()
        setupBannerAd()

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            premiumReceiver, IntentFilter(AppPreferences.ACTION_PREMIUM_UPDATED)
        )

        return view
    }

    private fun setupBannerAd() {
        if (premiumManager.isPremium) {
            adView?.visibility = View.GONE
            return
        }
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun showCategoryManager() {
        val catVm = categoryViewModel
        val noteVm = noteViewModel
        if (catVm == null || noteVm == null) {
            Toast.makeText(context, "Error loading categories", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryManager = CategoryManager(
            requireContext(),
            catVm,
            noteVm,
            currentUserId
        )
        categoryManager.show(parentFragmentManager, "CategoryManager")
    }

    private fun setupRecycler() {
        val catVm = categoryViewModel
        val noteVm = noteViewModel
        if (catVm != null && noteVm != null) {
            adapter = NoteAdapter(ArrayList(), requireContext(), catVm, noteVm)
        }
        shimmerAdapter = NoteShimmerAdapter(10)

        recyclerView.layoutManager = StaggeredGridLayoutManager(if (isGridLayout) 2 else 1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = shimmerAdapter

        val layoutToggleViewModel = ViewModelProvider(requireActivity())[LayoutToggleViewModel::class.java]
        layoutToggleViewModel.layoutType.observe(viewLifecycleOwner) { isGrid ->
            isGridLayout = isGrid
            refreshLayout()
        }
    }

    private fun refreshLayout() {
        if (!isAdded) return
        val newLayoutManager = if (isGridLayout) {
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        } else {
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }

        val applyLayout = {
            recyclerView.layoutManager = newLayoutManager
            if (::adapter.isInitialized && !isLoading && recyclerView.adapter != adapter) {
                recyclerView.adapter = adapter
            }
        }

        if (recyclerView.isComputingLayout) {
            recyclerView.post(applyLayout)
        } else {
            applyLayout()
        }
    }

    fun handleCreateNoteFlow() {
        if (!isAdded) return
        if (premiumManager.isPremium) {
            openCreateItem()
        } else {
            if (currentNotesCount >= PremiumManager.MAX_FREE_NOTES) {
                if (!ValidationUtils.isNetworkAvailable(requireContext())) {
                    CommonDialogs.showPremiumRequiredDialog(
                        requireContext(),
                        "Note limit reached (30 notes). Premium required for more. Please connect to internet to upgrade."
                    )
                } else {
                    CommonDialogs.showConfirmDialog(
                        requireContext(),
                        "Note Limit Reached",
                        "You have reached the limit of 30 notes. To add more notes, you need to upgrade to Premium. Would you like to watch an ad to add this note?",
                        "Watch Ad",
                        "Upgrade",
                        { AdManager.showRewardedAd(requireActivity()) { openCreateItem() } },
                        { premiumManager.showUpgradeScreen() }
                    )
                }
            } else {
                openCreateItem()
            }
        }
    }

    private fun openCreateItem() {
        val intent = Intent(requireContext(), EditNoteActivity::class.java)
        if (!"All".equals(selectedCategory, ignoreCase = true)) {
            val catId = getCategoryIdByName(selectedCategory)
            if (catId != null) {
                intent.putExtra("selectedCategoryId", catId)
            }
        }
        addEditNoteLauncher.launch(intent)
    }

    private fun setupSearch() {
        updateSearchHint()
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                clearSearchBtn.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300L)
                    runSearch(query)
                }
            }
        })

        clearSearchBtn.setOnClickListener {
            searchEditText.setText("")
            searchEditText.clearFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(searchEditText.windowToken, 0)
            updateSearchHint()
            runSearch("")
        }
    }

    private fun setupLayoutToggle(btnLayoutToggle: ImageButton) {
        val layoutToggleViewModel = ViewModelProvider(requireActivity())[LayoutToggleViewModel::class.java]

        fun updateIcon(isGrid: Boolean) {
            btnLayoutToggle.setImageResource(if (isGrid) R.drawable.ic_view_agenda else R.drawable.ic_grid_view)
        }

        updateIcon(isGridLayout)

        btnLayoutToggle.setOnClickListener {
            val currentGridState = try {
                appPreferences.getBoolean(PrefKeys.KEY_NOTES_LAYOUT, true)
            } catch (e: Exception) {
                true
            }
            val newGridState = !currentGridState
            appPreferences.putBoolean(PrefKeys.KEY_NOTES_LAYOUT, newGridState)
            layoutToggleViewModel.setLayout(newGridState)
            updateIcon(newGridState)
        }
    }

    private fun updateSearchHint() {
        if (!isAdded) return

        if ("All".equals(selectedCategory, ignoreCase = true)) {
            val hints = intArrayOf(
                R.string.hint_search_general,
                R.string.hint_search_ideas,
                R.string.hint_search_tasks,
                R.string.hint_search_memories
            )
            val randomIndex = (Math.random() * hints.size).toInt()
            searchEditText.setHint(getString(hints[randomIndex]))
        } else {
            searchEditText.setHint(getString(R.string.hint_search_category, selectedCategory))
        }
    }

    private fun observeCategories() {
        categoryViewModel?.getAllCategories(currentUserId)?.observe(viewLifecycleOwner) { categories ->
            val list = ArrayList<CategoryEntity>()
            if (categories != null) list.addAll(categories)
            list.sortWith(Comparator.comparingInt { c -> c.order })

            var hasAll = false
            for (c in list) {
                if ("All".equals(c.name, ignoreCase = true)) {
                    hasAll = true
                    break
                }
            }
            if (!hasAll) {
                val all = CategoryEntity().apply {
                    id = "all"
                    name = "All"
                }
                list.add(0, all)
            }

            if (isCategoryListSame(categoryList, list)) {
                return@observe
            }

            categoryList = list
            buildTabs()
            setupCountObservers()
            selectTabByName(selectedCategory)
            runSearch(searchEditText.text.toString().trim())
        }
    }

    private fun setupCountObservers() {
        categoryNoteCounts.clear()
        noteViewModel?.getNotesCount(currentUserId)?.observe(viewLifecycleOwner) { count ->
            if (count != null) {
                categoryNoteCounts["All"] = count
                updateTabCountDisplay("All", count)
            }
        }

        for (category in categoryList) {
            if ("All" != category.name) {
                noteViewModel?.getNotesCountByCategory(currentUserId, category.id)
                    ?.observe(viewLifecycleOwner) { count ->
                        if (count != null) {
                            categoryNoteCounts[category.name] = count
                            updateTabCountDisplay(category.name, count)
                        }
                    }
            }
        }
    }

    private fun isCategoryListSame(oldList: List<CategoryEntity>, newList: List<CategoryEntity>): Boolean {
        if (oldList.size != newList.size) return false
        for (i in oldList.indices) {
            if (oldList[i].id != newList[i].id || oldList[i].name != newList[i].name) {
                return false
            }
        }
        return true
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                setTabSelected(tab)
                val name = extractTabName(tab)
                if (name != null) {
                    selectedCategory = name
                    updateSearchHint()
                    runSearch(searchEditText.text.toString().trim())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                setTabUnselected(tab)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                runSearch(searchEditText.text.toString().trim())
            }
        })
    }

    private fun buildTabs() {
        tabLayout.removeAllTabs()
        for (category in categoryList) {
            val tab = tabLayout.newTab()
            val customView = createCustomTab(category.name, category.name == selectedCategory)
            tab.customView = customView
            tab.tag = category.name
            tabLayout.addTab(tab)
        }
        refreshTabCountDisplays()
    }

    private fun extractTabName(tab: TabLayout.Tab?): String? {
        val customView = tab?.customView ?: return null
        val txt = customView.findViewById<TextView>(R.id.tvName)
        return txt?.text?.toString()
    }

    private fun createCustomTab(title: String, selected: Boolean): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.custom_tab, tabLayout, false)
        val text = view.findViewById<TextView>(R.id.tvName)
        val count = view.findViewById<TextView>(R.id.tvCount)
        text.text = title
        text.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        text.setTextColor(
            if (selected) ContextCompat.getColor(requireContext(), R.color.tabSelectedTextColor)
            else unselectedTabColor
        )

        val noteCount = categoryNoteCounts[title]
        if (selected && noteCount != null && noteCount > 0) {
            count.text = if (noteCount > 99) "99+" else noteCount.toString()
            count.visibility = View.VISIBLE
        } else {
            count.visibility = View.GONE
        }
        return view
    }

    private fun setTabSelected(tab: TabLayout.Tab?) {
        val customView = tab?.customView ?: return
        val text = customView.findViewById<TextView>(R.id.tvName) ?: return
        val count = customView.findViewById<TextView>(R.id.tvCount)
        text.setTypeface(null, Typeface.BOLD)
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.tabSelectedTextColor))

        val categoryName = extractTabName(tab)
        val noteCount = categoryNoteCounts[categoryName]
        if (count != null && noteCount != null && noteCount > 0) {
            count.text = if (noteCount > 99) "99+" else noteCount.toString()
            count.visibility = View.VISIBLE
        } else {
            count?.visibility = View.GONE
        }
    }

    private fun setTabUnselected(tab: TabLayout.Tab?) {
        val customView = tab?.customView ?: return
        val text = customView.findViewById<TextView>(R.id.tvName) ?: return
        val count = customView.findViewById<TextView>(R.id.tvCount)
        text.setTypeface(null, Typeface.NORMAL)
        text.setTextColor(unselectedTabColor)
        count?.visibility = View.GONE
    }

    private fun selectTabByName(categoryName: String) {
        var matched = false
        for (i in 0 until tabLayout.tabCount) {
            val t = tabLayout.getTabAt(i)
            val customView = t?.customView ?: continue
            val txt = customView.findViewById<TextView>(R.id.tvName)
            if (txt != null && txt.text.toString() == categoryName) {
                tabLayout.selectTab(t)
                setTabSelected(t)
                matched = true
                break
            }
        }
        if (!matched && tabLayout.tabCount > 0) {
            val first = tabLayout.getTabAt(0)
            if (first != null) {
                tabLayout.selectTab(first)
                setTabSelected(first)
                val txt = first.customView?.findViewById<TextView>(R.id.tvName)
                if (txt != null) selectedCategory = txt.text.toString()
            }
        }
    }

    private fun runSearch(query: String?) {
        val cleanQuery = query?.trim() ?: ""

        shimmerJob?.cancel()

        if (currentNotesLiveData != null && currentNotesObserver != null) {
            currentNotesLiveData?.removeObserver(currentNotesObserver!!)
        }

        // Delay shimmer display by 150ms to prevent visual flickering on fast local DB updates
        shimmerJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(150L)
            showShimmerAdapter()
        }

        currentNotesObserver = Observer { notes ->
            shimmerJob?.cancel()
            updateRecycler(notes ?: ArrayList())
        }

        val vm = noteViewModel ?: return

        currentNotesLiveData = if (cleanQuery.isEmpty()) {
            if ("All".equals(selectedCategory, ignoreCase = true)) {
                vm.getAllNotes(currentUserId)
            } else {
                val catId = getCategoryIdByName(selectedCategory)
                if ("all" == catId || catId == null) {
                    vm.getAllNotes(currentUserId)
                } else {
                    vm.getNotesByCategory(currentUserId, catId)
                }
            }
        } else {
            if ("All".equals(selectedCategory, ignoreCase = true)) {
                vm.searchNotes(currentUserId, cleanQuery)
            } else {
                val catId = getCategoryIdByName(selectedCategory)
                if ("all" == catId || catId == null) {
                    vm.searchNotes(currentUserId, cleanQuery)
                } else {
                    vm.searchNotesInCategory(currentUserId, catId, cleanQuery)
                }
            }
        }

        currentNotesLiveData?.observe(viewLifecycleOwner, currentNotesObserver!!)
        refreshTabCountDisplays()
    }

    private fun refreshTabCountDisplays() {
        for ((key, value) in categoryNoteCounts) {
            updateTabCountDisplay(key, value)
        }
    }

    private fun updateRecycler(notes: List<NoteEntity>) {
        shimmerJob?.cancel()
        val isCategoryChange = lastCategory != null && lastCategory != selectedCategory
        lastCategory = selectedCategory

        if (isLoading) {
            isLoading = false
            refreshLayout()
        }
        if (::adapter.isInitialized) {
            val previousSize = adapter.itemCount
            adapter.updateData(notes, isCategoryChange)
            if (recyclerView.adapter != adapter) {
                recyclerView.adapter = adapter
            }
            if (previousSize < notes.size && notes.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
            }
        }
        if (notes.isEmpty()) {
            if (emptyStateLayout.visibility != View.VISIBLE) {
                emptyStateLayout.alpha = 0f
                emptyStateLayout.visibility = View.VISIBLE
                emptyStateLayout.animate().alpha(1f).setDuration(120L).start()
            }
            recyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            if (recyclerView.visibility != View.VISIBLE || isCategoryChange) {
                recyclerView.alpha = 0f
                recyclerView.visibility = View.VISIBLE
                recyclerView.animate().alpha(1f).setDuration(120L).start()
            }
        }
    }

    private fun getCategoryIdByName(name: String): String? {
        for (c in categoryList) {
            if (name == c.name) return c.id
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        isGridLayout = appPreferences.getBoolean(PrefKeys.KEY_NOTES_LAYOUT, true)
        refreshLayout()

        runSearch(searchEditText.text.toString().trim())
        refreshTabCountDisplays()
        AnalyticsHelper.logScreenView("Notes", "NotesFragment")
    }

    private fun updateTabCountDisplay(categoryName: String, count: Int) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            if (tab != null && categoryName == extractTabName(tab)) {
                val customView = tab.customView
                if (customView != null) {
                    val countView = customView.findViewById<TextView>(R.id.tvCount)
                    if (countView != null) {
                        if (count > 0 && categoryName == selectedCategory) {
                            countView.text = if (count > 99) "99+" else count.toString()
                            countView.visibility = View.VISIBLE
                        } else {
                            countView.visibility = View.GONE
                        }
                    }
                }
                break
            }
        }
    }

    private fun showShimmerAdapter() {
        if (!isLoading) {
            isLoading = true
            shimmerAdapter?.let {
                recyclerView.adapter = it
                recyclerView.visibility = View.VISIBLE
                emptyStateLayout.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ThemeManager.unregisterListener(this)
        searchJob?.cancel()
        shimmerJob?.cancel()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(premiumReceiver)
    }

    override fun onThemeChanged(newTheme: String) {
        if (!isAdded) return
        unselectedTabColor = getThemeAttrColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary
        )
        buildTabs()
        runSearch(searchEditText.text.toString().trim())
    }

    private fun getThemeAttrColor(context: Context, attrResId: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrResId, typedValue, true)
        return typedValue.data
    }

    private fun observeNoteCount() {
        noteViewModel?.getNotesCount(currentUserId)?.observe(viewLifecycleOwner) { count ->
            if (count == null) return@observe
            currentNotesCount = count
        }
    }

}
