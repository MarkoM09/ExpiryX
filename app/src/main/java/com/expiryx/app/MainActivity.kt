package com.expiryx.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.expiryx.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProductAdapter

    private val productViewModel: ProductViewModel by viewModels {
        ProductViewModelFactory((application as ProductApplication).repository)
    }

    private var allProducts: List<Product> = emptyList()
    private var pendingActions = mutableSetOf<String>() // Set of UUIDs being "undone"
    private var showFavoritesOnly = false
    private var currentSearchQuery: String = ""

    enum class SortMode {
        EXPIRY_ASC, EXPIRY_DESC,
        ALPHA_AZ, ALPHA_ZA,
        ADDED_ASC, ADDED_DESC,
        QTY_ASC, QTY_DESC,
        WEIGHT_ASC, WEIGHT_DESC,
        FAVORITES_FIRST
    }

    private var sortMode: SortMode = SortMode.EXPIRY_ASC

    private val manualEntryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            AddProductBottomSheet.newInstance(uri).show(supportFragmentManager, "AddProductWithUriTag")
        }

    private val requestNotifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scheduleAllProductNotifications()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        NotificationUtils.createChannel(this)
        productViewModel.archiveExpiredProducts()

        setupRecycler()
        setupObservers()
        setupListeners()
        setupBottomNavigation()

        if (AccountManager.isLoggedIn()) {
            AccountManager.startSync(this)
        }

        checkAndMaybeRequestNotificationPermission()
        handleNotificationIntent(intent)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCoordinator) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            val bottomPadding = systemBars.bottom
            val keyboardHeight = imeInsets.bottom
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            binding.topBar.updatePadding(top = systemBars.top)

            // Standard padding for recycler so items aren't hidden by nav/fab
            val fabPadding = (88 * resources.displayMetrics.density).toInt()
            binding.recyclerProducts.updatePadding(bottom = fabPadding + bottomPadding)
            
            // Centering logic for empty state by applying padding to inner container
            val hPad = (32 * resources.displayMetrics.density).toInt()
            val vPad = (32 * resources.displayMetrics.density).toInt()
            if (isKeyboardVisible) {
                binding.emptyStateLayout.emptyStateInnerContainer.setPadding(hPad, vPad, hPad, keyboardHeight)
            } else {
                binding.emptyStateLayout.emptyStateInnerContainer.setPadding(hPad, vPad, hPad, bottomPadding)
            }

            insets
        }
        ViewCompat.requestApplyInsets(binding.rootCoordinator)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val productId = intent.getIntExtra("show_product_id", -1)
        if (productId != -1) {
            lifecycleScope.launch {
                val product = (application as ProductApplication).repository.getProductById(productId)
                if (product != null) {
                    ProductDetailBottomSheet.newInstance(product).show(supportFragmentManager, "Detail")
                }
            }
        }
    }

    private fun setupRecycler() {
        adapter = ProductAdapter(
            onFavoriteClick = { p -> productViewModel.update(p.copy(isFavorite = !p.isFavorite)) },
            onItemClick = { p -> ProductDetailBottomSheet.newInstance(p).show(supportFragmentManager, "Detail") },
            onDeleteLongPress = { p -> deleteProductWithConfirmation(p) },
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerProducts.adapter = adapter

        val swipeHandler = SwipeActionCallback(
            context = this,
            leftLabel = "Mark Used",
            leftColor = android.graphics.Color.parseColor("#4CAF50"),
            leftIconRes = R.drawable.ic_check,
            rightLabel = "Edit",
            rightColor = android.graphics.Color.parseColor("#2196F3"),
            rightIconRes = R.drawable.ic_palette, // Using palette for edit icon
            onSwipeLeft = { position ->
                val item = adapter.currentList[position]
                if (item is ProductListItem.ProductItem) {
                    editProduct(item.product)
                    adapter.notifyItemChanged(position)
                }
            },
            onSwipeRight = { position ->
                val item = adapter.currentList[position]
                if (item is ProductListItem.ProductItem) {
                    markProductAsUsed(item.product)
                }
            }
        )
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerProducts)
    }

    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        BottomNavHelper.setup(this, binding.bottomNavInclude.bottomNavigationView, R.id.nav_home)
    }

    private fun setupObservers() {
        productViewModel.allProducts.observe(this) { products ->
            allProducts = products
            refreshList()
        }
    }

    fun editProduct(product: Product) {
        val intent = Intent(this, ManualEntryActivity::class.java).apply {
            putExtra("product", product)
            putExtra("imageUri", product.imageUri)
        }
        startActivity(intent)
    }

    private fun setupListeners() {
        binding.btnAddProduct.setOnClickListener { showAddProductOptions() }
        binding.btnSortByCard.setOnClickListener { showSortOptions(it) }

        binding.btnNotificationCenter.setOnClickListener {
            NotificationCenterBottomSheet().show(supportFragmentManager, "NotificationCenter")
        }

        binding.btnFavoriteCard.setOnClickListener {
            showFavoritesOnly = !showFavoritesOnly
            binding.imgFavoriteToggle.setImageResource(
                if (showFavoritesOnly) R.drawable.ic_heart_filled else R.drawable.ic_heart_unfilled
            )
            binding.textFavoriteToggle.text = if (showFavoritesOnly) "Favourites Only" else "Show Favourites"
            refreshList()
        }

        binding.btnSearch.setOnClickListener {
            if (binding.searchView.visibility == View.VISIBLE) closeSearchCompletely() else openSearch()
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText.orEmpty()
                refreshList()
                return true
            }
        })
        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.searchView.query.isNullOrEmpty()) closeSearchCompletely()
        }

        val closeBtn = binding.searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeBtn?.setOnClickListener {
            if (!binding.searchView.query.isNullOrEmpty()) binding.searchView.setQuery("", false)
            else closeSearchCompletely()
        }
    }

    private fun showAddProductOptions() {
        AddProductBottomSheet.newInstance().show(supportFragmentManager, "AddProductGeneralTag")
    }

    private fun showSortOptions(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, 1, 0, getString(R.string.sort_expiry_soon))
            add(0, 2, 0, getString(R.string.sort_expiry_late))
            add(0, 3, 0, getString(R.string.sort_name_az))
            add(0, 4, 0, getString(R.string.sort_name_za))
            add(0, 5, 0, getString(R.string.sort_qty_low))
            add(0, 6, 0, getString(R.string.sort_qty_high))
            add(0, 7, 0, getString(R.string.sort_weight_low))
            add(0, 8, 0, getString(R.string.sort_weight_high))
            add(0, 9, 0, getString(R.string.sort_favorites))
            add(0, 10, 0, getString(R.string.sort_added_old))
            add(0, 11, 0, getString(R.string.sort_added_new))
        }
        popup.setOnMenuItemClickListener { item ->
            binding.textCurrentSort.text = item.title
            sortMode = when (item.itemId) {
                1 -> SortMode.EXPIRY_ASC
                2 -> SortMode.EXPIRY_DESC
                3 -> SortMode.ALPHA_AZ
                4 -> SortMode.ALPHA_ZA
                5 -> SortMode.QTY_ASC
                6 -> SortMode.QTY_DESC
                7 -> SortMode.WEIGHT_ASC
                8 -> SortMode.WEIGHT_DESC
                9 -> SortMode.FAVORITES_FIRST
                10 -> SortMode.ADDED_ASC
                11 -> SortMode.ADDED_DESC
                else -> SortMode.EXPIRY_ASC
            }
            refreshList()
            true
        }
        popup.show()
    }

    private fun refreshList() {
        var list = allProducts.filter { !pendingActions.contains(it.uuid) }
        
        if (showFavoritesOnly) {
            list = list.filter { it.isFavorite }
        }

        if (currentSearchQuery.isNotBlank()) {
            val queryText = currentSearchQuery.lowercase(Locale.getDefault())
            list = list.filter { product ->
                product.name.lowercase(Locale.getDefault()).contains(queryText) ||
                        (product.brand?.lowercase(Locale.getDefault())?.contains(queryText) ?: false) ||
                        (product.barcode?.lowercase(Locale.getDefault())?.contains(queryText) ?: false)
            }
        }

        list = when (sortMode) {
            SortMode.ALPHA_AZ -> list.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortMode.ALPHA_ZA -> list.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            SortMode.EXPIRY_ASC -> list.sortedBy { it.expirationDate ?: Long.MAX_VALUE }
            SortMode.EXPIRY_DESC -> list.sortedByDescending { it.expirationDate ?: 0L }
            SortMode.QTY_ASC -> list.sortedBy { it.quantity }
            SortMode.QTY_DESC -> list.sortedByDescending { it.quantity }
            SortMode.WEIGHT_ASC -> list.sortedBy { it.weight ?: Int.MAX_VALUE }
            SortMode.WEIGHT_DESC -> list.sortedByDescending { it.weight ?: 0 }
            SortMode.FAVORITES_FIRST -> list.sortedByDescending { it.isFavorite }
            SortMode.ADDED_ASC -> list.sortedBy { it.dateAdded }
            SortMode.ADDED_DESC -> list.sortedByDescending { it.dateAdded }
        }

        updateList(list)
    }

    private fun updateList(products: List<Product>) {
        val emptyState = binding.emptyStateLayout

        // Priority 1: Base state (No products at all in the database, excluding pending deletes)
        if (allProducts.none { !pendingActions.contains(it.uuid) }) {
            binding.recyclerProducts.visibility = View.GONE
            emptyState.root.visibility = View.VISIBLE
            emptyState.emptyStateIcon.setImageResource(R.drawable.ic_big_plus)
            emptyState.emptyStateTitle.text = getString(R.string.empty_fridge_title)
            emptyState.emptyStateSubtitle.text = getString(R.string.empty_fridge_subtitle)
            return
        }

        // Priority 2: Filter/Search state (Results are empty due to filtering)
        if (products.isEmpty()) {
            binding.recyclerProducts.visibility = View.GONE
            emptyState.root.visibility = View.VISIBLE
            
            if (showFavoritesOnly && allProducts.none { it.isFavorite && !pendingActions.contains(it.uuid) }) {
                emptyState.emptyStateIcon.setImageResource(R.drawable.ic_heart_unfilled)
                emptyState.emptyStateTitle.text = getString(R.string.empty_state_title_no_favorites)
                emptyState.emptyStateSubtitle.text = getString(R.string.empty_state_subtitle_no_favorites)
            } else {
                emptyState.emptyStateIcon.setImageResource(R.drawable.ic_search_unfilled)
                emptyState.emptyStateTitle.text = getString(R.string.empty_state_title_no_results)
                emptyState.emptyStateSubtitle.text = getString(R.string.empty_state_subtitle_no_results)
            }
        } else {
            // Priority 3: Show data
            binding.recyclerProducts.visibility = View.VISIBLE
            emptyState.root.visibility = View.GONE
            adapter.updateData(products, sortMode)
        }
    }

    fun deleteProductWithConfirmation(product: Product) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage(getString(R.string.dialog_delete_message, product.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                performUndoableAction(product, "Product deleted") {
                    productViewModel.delete(product)
                    NotificationScheduler.cancelForProduct(this@MainActivity, product)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    fun markProductAsUsed(product: Product) {
        performUndoableAction(product, "${product.name} marked as used") {
            productViewModel.markAsUsed(product)
            NotificationScheduler.cancelForProduct(this@MainActivity, product)
        }
    }

    private fun performUndoableAction(product: Product, message: String, onCommit: () -> Unit) {
        pendingActions.add(product.uuid)
        refreshList()

        val snackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.rootCoordinator,
            message,
            5000
        )
        
        var undone = false
        snackbar.setAction("UNDO") {
            undone = true
            pendingActions.remove(product.uuid)
            refreshList()
        }
        
        snackbar.addCallback(object : com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback<com.google.android.material.snackbar.Snackbar>() {
            override fun onDismissed(transientBottomBar: com.google.android.material.snackbar.Snackbar?, event: Int) {
                if (!undone) {
                    pendingActions.remove(product.uuid)
                    onCommit()
                }
            }
        })
        
        snackbar.show()
    }

    private fun openSearch() {
        binding.searchView.visibility = View.VISIBLE
        binding.searchView.isIconified = false
        binding.searchView.requestFocus()
        
        // Hide dashboard bar to give more room and avoid layout glitching
        binding.dashboardBar.visibility = View.GONE
    }

    private fun closeSearchCompletely() {
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
        binding.searchView.visibility = View.GONE
        
        // Restore dashboard bar
        binding.dashboardBar.visibility = View.VISIBLE

        refreshList()
    }

    private fun scheduleAllProductNotifications() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch(Dispatchers.IO) {
                val products = productViewModel.allProducts.value
                    ?: (application as ProductApplication).repository.getAllProductsNow()
                NotificationScheduler.rescheduleAll(this@MainActivity, products)
            }
        }
    }

    private fun checkAndMaybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleAllProductNotifications()
            }
        } else {
            scheduleAllProductNotifications()
        }
    }
}
