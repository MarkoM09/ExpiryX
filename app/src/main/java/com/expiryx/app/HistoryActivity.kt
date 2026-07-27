package com.expiryx.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.expiryx.app.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    // ✅ FIX: Removed 'private' to allow access from BottomSheet
    val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory((application as ProductApplication).repository)
    }

    private var fullList: List<History> = emptyList()
    private var sortIndex: Int = 0
    private var searchQuery: String = ""

    private var showExpired = true
    private var showConsumed = true
    private var showDeleted = true
    private var onlyFavourites = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        // Setup RecyclerView
        adapter = HistoryAdapter(
            onItemClick = { h -> HistoryDetailBottomSheet.newInstance(h).show(supportFragmentManager, "HistoryDetail") },
            onItemLongPress = { h ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.history_permanently_delete_title))
                    .setMessage(getString(R.string.history_permanently_delete_msg, h.productName))
                    .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.permanentlyDelete(h) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            },
        )

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        val swipeHandler = SwipeActionCallback(
            context = this,
            leftLabel = "Restore",
            leftColor = android.graphics.Color.parseColor("#4CAF50"),
            leftIconRes = R.drawable.ic_history,
            rightLabel = "Delete",
            rightColor = android.graphics.Color.parseColor("#F44336"),
            rightIconRes = R.drawable.ic_delete,
            onSwipeLeft = { position ->
                val h = adapter.currentList[position]
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.history_permanently_delete_title))
                    .setMessage(getString(R.string.history_permanently_delete_msg, h.productName))
                    .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.permanentlyDelete(h) }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ -> adapter.notifyItemChanged(position) }
                    .setOnCancelListener { adapter.notifyItemChanged(position) }
                    .show()
            },
            onSwipeRight = { position ->
                val h = adapter.currentList[position]
                handleRestore(h)
            }
        )
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerHistory)

        setupSearch()
        setupSort()
        setupFilter()
        setupBottomNav()

        // Observe data
        viewModel.allHistory.observe(this) { list ->
            fullList = list ?: emptyList()
            applyFilters()
        }
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            
            val bottomPadding = systemBars.bottom
            val keyboardHeight = imeInsets.bottom
            val isKeyboardVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())

            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )

            // Standardize padding for recycler
            binding.recyclerHistory.setPadding(
                binding.recyclerHistory.paddingLeft,
                binding.recyclerHistory.paddingTop,
                binding.recyclerHistory.paddingRight,
                (80 * resources.displayMetrics.density).toInt() + bottomPadding
            )
            
            // Center empty state between top bar and keyboard by applying padding to inner container
            val hPad = (32 * resources.displayMetrics.density).toInt()
            val vPad = (32 * resources.displayMetrics.density).toInt()
            if (isKeyboardVisible) {
                binding.emptyStateLayoutHistory.emptyStateInnerContainer.setPadding(hPad, vPad, hPad, keyboardHeight)
            } else {
                binding.emptyStateLayoutHistory.emptyStateInnerContainer.setPadding(hPad, vPad, hPad, bottomPadding)
            }

            insets
        }
    }

    private fun handleRestore(h: History) {
        when (h.action) {
            "Expired" -> {
                // For expired, we show a date picker to choose a new expiry before restoring
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(
                    this,
                    { _, y, m, d ->
                        val calendar = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                        viewModel.changeExpiry(h, calendar.timeInMillis)
                        Toast.makeText(this, getString(R.string.history_toast_restored), Toast.LENGTH_SHORT).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
                // We need to notify the adapter that the item didn't actually vanish yet if they cancel the picker
                // but actually the swipe is done. It's better to refresh list.
                adapter.notifyItemChanged(fullList.indexOf(h))
            }
            "Used" -> {
                viewModel.unuse(h)
                Toast.makeText(this, "Restored to Home", Toast.LENGTH_SHORT).show()
            }
            "Deleted" -> {
                viewModel.restoreDeleted(h)
                Toast.makeText(this, "Restored to Home", Toast.LENGTH_SHORT).show()
            }
            else -> {
                viewModel.restoreDeleted(h)
                Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- SEARCH ----------
    private fun setupSearch() {
        binding.btnSearch.setOnClickListener { openSearch() }

        binding.searchViewHistory.setOnCloseListener {
            closeSearch()
            true
        }

        binding.searchViewHistory.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus && searchQuery.isEmpty()) closeSearch()
        }

        binding.searchViewHistory.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                applyFilters()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applyFilters()
                return true
            }
        })
    }

    private fun openSearch() {
        binding.searchViewHistory.visibility = View.VISIBLE
        binding.searchViewHistory.isIconified = false
        binding.searchViewHistory.requestFocus()

        binding.countersLayout.visibility = View.GONE
        binding.layoutSortHistoryInclude.root.visibility = View.GONE

        showKeyboard()
    }

    private fun closeSearch() {
        binding.searchViewHistory.setQuery("", false)
        binding.searchViewHistory.clearFocus()
        binding.searchViewHistory.visibility = View.GONE

        binding.countersLayout.visibility = View.VISIBLE
        binding.layoutSortHistoryInclude.root.visibility = View.VISIBLE

        searchQuery = ""
        applyFilters()
        hideKeyboard()
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.searchViewHistory.findFocus(), InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // ---------- SORT ----------
    private fun setupSort() {
        val sortBinding = binding.layoutSortHistoryInclude
        val sortLayout = sortBinding.root
        val sortText: TextView = sortBinding.textSortHistory

        sortLayout.setOnClickListener {
            val popup = PopupMenu(this, sortLayout)
            popup.menuInflater.inflate(R.menu.menu_sort_history, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                sortIndex = when (item.itemId) {
                    R.id.sort_date_desc -> 0
                    R.id.sort_date_asc -> 1
                    R.id.sort_expired_first -> 2
                    R.id.sort_name_asc -> 3
                    R.id.sort_name_desc -> 4
                    R.id.sort_quantity_asc -> 5
                    R.id.sort_quantity_desc -> 6
                    R.id.sort_weight_asc -> 7
                    R.id.sort_weight_desc -> 8
                    R.id.sort_expiry_soon -> 9
                    R.id.sort_expiry_late -> 10
                    R.id.sort_favourites -> 11
                    else -> 0
                }
                sortText.text = item.title
                applyFilters()
                true
            }
            popup.show()
        }
    }

    override fun onResume() {
        super.onResume()
        setupBottomNav()
    }

    // ---------- NAV ----------
    private fun setupBottomNav() {
        BottomNavHelper.setup(this, binding.bottomNav.bottomNavigationView, R.id.nav_history)
    }

    // ---------- FILTERS & SORT ----------
    private fun setupFilter() {
        binding.btnFilter.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menuInflater.inflate(R.menu.menu_filter_history, popup.menu)

            popup.menu.findItem(R.id.filter_expired).isChecked = showExpired
            popup.menu.findItem(R.id.filter_consumed).isChecked = showConsumed
            popup.menu.findItem(R.id.filter_deleted).isChecked = showDeleted
            popup.menu.findItem(R.id.filter_favourites).isChecked = onlyFavourites

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.filter_expired -> {
                        if (showExpired && !(showConsumed || showDeleted)) {
                            Toast.makeText(this, "At least one type must be selected", Toast.LENGTH_SHORT).show()
                        } else {
                            showExpired = !showExpired
                            item.isChecked = showExpired
                            applyFilters()
                        }
                    }
                    R.id.filter_consumed -> {
                        if (showConsumed && !(showExpired || showDeleted)) {
                            Toast.makeText(this, "At least one type must be selected", Toast.LENGTH_SHORT).show()
                        } else {
                            showConsumed = !showConsumed
                            item.isChecked = showConsumed
                            applyFilters()
                        }
                    }
                    R.id.filter_deleted -> {
                        if (showDeleted && !(showExpired || showConsumed)) {
                            Toast.makeText(this, "At least one type must be selected", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleted = !showDeleted
                            item.isChecked = showDeleted
                            applyFilters()
                        }
                    }
                    R.id.filter_favourites -> {
                        onlyFavourites = !onlyFavourites
                        item.isChecked = onlyFavourites
                        applyFilters()
                    }
                }
                // Return true to close the menu, false to keep it open.
                // The default behavior is to close, which is fine here.
                true
            }
            popup.show()
        }
    }

    private fun applyFilters() {
        var filtered = fullList

        // Type filter
        filtered = filtered.filter { h ->
            ((h.action == "Expired") && showExpired) ||
                    ((h.action == "Used") && showConsumed) ||
                    ((h.action == "Deleted") && showDeleted)
        }

        // Favourites filter
        if (onlyFavourites) {
            filtered = filtered.filter { it.isFavorite }
        }

        // Search filter
        if (searchQuery.isNotBlank()) {
            val qLower = searchQuery.lowercase(Locale.getDefault())
            filtered = filtered.filter { h ->
                val nameMatch = h.productName.contains(qLower, ignoreCase = true)
                val actionMatch = h.action.contains(qLower, ignoreCase = true)
                val brandMatch = h.brand?.contains(qLower, ignoreCase = true) ?: false
                val weightMatch = h.weight?.toString()?.contains(qLower, ignoreCase = true) ?: false
                val quantityMatch = h.quantity.toString().contains(qLower)
                val expiryMatch = h.expirationDate?.let { formatDate(it).contains(qLower) } ?: false
                val timestampMatch = formatDateTime(h.timestamp).contains(qLower)

                nameMatch || actionMatch || brandMatch || weightMatch || quantityMatch || expiryMatch || timestampMatch
            }
        }

        // Sorting
        filtered = when (sortIndex) {
            0 -> filtered.sortedByDescending { it.timestamp } // Newest first
            1 -> filtered.sortedBy { it.timestamp } // Oldest first
            2 -> filtered.sortedWith(compareByDescending<History> { it.action == "Expired" }.thenByDescending { it.timestamp }) // Expired first
            3 -> filtered.sortedBy { it.productName.lowercase(Locale.getDefault()) } // Name A-Z
            4 -> filtered.sortedByDescending { it.productName.lowercase(Locale.getDefault()) } // Name Z-A
            5 -> filtered.sortedBy { it.quantity } // Quantity low-high
            6 -> filtered.sortedByDescending { it.quantity } // Quantity high-low
            7 -> filtered.sortedBy { it.weight ?: Int.MAX_VALUE } // Weight low-high
            8 -> filtered.sortedByDescending { it.weight ?: 0 } // Weight high-low
            9 -> filtered.sortedBy { it.expirationDate ?: Long.MAX_VALUE } // Expiry soonest
            10 -> filtered.sortedByDescending { it.expirationDate ?: Long.MIN_VALUE } // Expiry latest
            11 -> filtered.sortedWith(compareByDescending<History> { it.isFavorite }.thenByDescending { it.timestamp }) // Favourites first
            else -> filtered.sortedByDescending { it.timestamp } // Default case
        }

        updateUI(filtered)
    }

    private fun updateUI(list: List<History>) {
        adapter.updateData(list)

        val isSearchVisible = binding.searchViewHistory.visibility == View.VISIBLE
        val isQueryNotEmpty = searchQuery.isNotEmpty()
        val hasResults = list.isNotEmpty()

        // Hide pills and sort bar if search is active (even if query is empty)
        binding.countersLayout.visibility = if (isSearchVisible) View.GONE else View.VISIBLE
        binding.layoutSortHistoryInclude.root.visibility = if (isSearchVisible) View.GONE else View.VISIBLE

        val emptyState = binding.emptyStateLayoutHistory
        
        // Priority 1: Base state (No history at all)
        if (fullList.isEmpty()) {
            binding.recyclerHistory.visibility = View.GONE
            emptyState.root.visibility = View.VISIBLE
            emptyState.emptyStateIcon.setImageResource(R.drawable.ic_clock_unfilled)
            emptyState.emptyStateTitle.text = getString(R.string.empty_history_title)
            emptyState.emptyStateSubtitle.text = getString(R.string.empty_history_subtitle)
            return
        }

        if (hasResults) {
            binding.recyclerHistory.visibility = View.VISIBLE
            emptyState.root.visibility = View.GONE
        } else {
            binding.recyclerHistory.visibility = View.GONE
            emptyState.root.visibility = View.VISIBLE

            when {
                isQueryNotEmpty -> {
                    emptyState.emptyStateIcon.setImageResource(R.drawable.ic_search_unfilled)
                    emptyState.emptyStateTitle.text = getString(R.string.empty_state_title_no_results)
                    emptyState.emptyStateSubtitle.text = getString(R.string.empty_state_subtitle_no_results)
                }
                onlyFavourites -> {
                    emptyState.emptyStateIcon.setImageResource(R.drawable.ic_heart_unfilled)
                    emptyState.emptyStateTitle.text = getString(R.string.empty_state_title_no_favorites)
                    emptyState.emptyStateSubtitle.text = getString(R.string.empty_state_subtitle_no_favorites)
                }
                else -> {
                    // This handles cases where items are filtered out by type but search is empty
                    emptyState.emptyStateIcon.setImageResource(R.drawable.ic_search_unfilled)
                    emptyState.emptyStateTitle.text = getString(R.string.empty_state_title_no_results)
                    emptyState.emptyStateSubtitle.text = getString(R.string.empty_state_subtitle_no_matches)
                }
            }
        }

        if (!isSearchVisible) {
            val expiredCount = fullList.count { it.action == "Expired" }
            val usedCount = fullList.count { it.action == "Used" }
            val deletedCount = fullList.count { it.action == "Deleted" }

            binding.textExpiredCount.text = getString(R.string.history_expired_count, expiredCount)
            binding.textConsumedCount.text = getString(R.string.history_consumed_count, usedCount)
            binding.textDeletedCount.text = getString(R.string.history_deleted_count, deletedCount)
        }
    }

    // ---------- Helpers ----------
    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}