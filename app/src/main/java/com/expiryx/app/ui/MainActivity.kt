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

/**
 * FUNCTIONALITY: Serves as the primary entry point and dashboard for the application, 
 * displaying the user's inventory, handling search/sort, and facilitating product management.
 * USE OF DATA: Manages a list of 'Product' objects, maintains UI state for 'SortMode', 
 * search queries, and filter flags. Uses View Binding for layout interaction.
 * USE OF CODE STRUCTURES: Implements an 'enum' for sorting modes, utilizes a 
 * 'ProductViewModel' for data observation, and handles complex window insets for edge-to-edge UI.
 */
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

    /**
     * FUNCTIONALITY: Defines the available sorting algorithms for the product inventory.
     * USE OF DATA: Enum constants representing different comparative properties (date, name, quantity, weight).
     */
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

    /**
     * FUNCTIONALITY: Initializes the activity, sets up the UI components, and triggers 
     * initial data loading and background maintenance.
     * USE OF DATA: Sets up 'ActivityMainBinding', initializes the ViewModel, and checks notification permissions.
     * USE OF CODE STRUCTURES: Calls a sequence of 'setup' methods and executes 
     * 'archiveExpiredProducts' to clean up the database on launch.
     */
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

    /**
     * FUNCTIONALITY: Adjusts view padding and margins to accommodate system bars 
     * (status, navigation) and the software keyboard.
     * USE OF DATA: Reads 'WindowInsetsCompat' to get pixel dimensions for system bars and IME.
     * USE OF CODE STRUCTURES: Uses 'setOnApplyWindowInsetsListener' with 'if/else' 
     * logic to adjust empty state positioning based on keyboard visibility.
     */
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
            
            // CODE STRUCTURE: Centering logic for empty state using selection structure
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

    /**
     * FUNCTIONALITY: Processes incoming intents from notifications to show specific product details.
     * USE OF DATA: Extracts 'show_product_id' (Int) from the intent.
     * USE OF CODE STRUCTURES: Launches a coroutine to fetch product data from the repository 
     * and shows a 'ProductDetailBottomSheet' if the product exists.
     */
    private fun handleNotificationIntent(intent: Intent) {
        val productId = intent.getIntExtra("show_product_id", -1)
        // CODE STRUCTURE: Selection check to see if intent contains a valid product ID
        if (productId != -1) {
            lifecycleScope.launch {
                val product = (application as ProductApplication).repository.getProductById(productId)
                if (product != null) {
                    ProductDetailBottomSheet.newInstance(product).show(supportFragmentManager, "Detail")
                }
            }
        }
    }

    /**
     * FUNCTIONALITY: Configures the RecyclerView with an adapter and swipe-to-action behaviors.
     * USE OF DATA: Instantiates 'ProductAdapter' with click listeners.
     * USE OF CODE STRUCTURES: Configures 'SwipeActionCallback' and attaches an 
     * 'ItemTouchHelper' for gesture processing.
     */
    private fun setupRecycler() {
        adapter = ProductAdapter(
            onFavoriteClick = { p -> productViewModel.update(p.copy(isFavorite = !p.isFavorite)) },
            onItemClick = { p -> ProductDetailBottomSheet.newInstance(p).show(supportFragmentManager, "Detail") },
            onDeleteLongPress = { p -> deleteProductWithConfirmation(p) },
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerProducts.adapter = adapter

        // USE OF CODE STRUCTURES: Implementation of swipe gesture callbacks for item interaction
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

    /**
     * FUNCTIONALITY: Listens for changes in the product database via the ViewModel.
     * USE OF DATA: Observes 'LiveData<List<Product>>'.
     * USE OF CODE STRUCTURES: Callback updates the local 'allProducts' cache and 
     * triggers 'refreshList()' for UI updates.
     */
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

    /**
     * FUNCTIONALITY: Attaches event listeners to UI components like buttons and search fields.
     * USE OF DATA: Binds click and text change events to layout views.
     * USE OF CODE STRUCTURES: Implements 'OnQueryTextListener' for real-time searching 
     * and lambda listeners for button interactions.
     */
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

    /**
     * FUNCTIONALITY: Displays a popup menu for the user to choose inventory sorting criteria.
     * USE OF DATA: Updates 'sortMode' (SortMode enum) based on user selection.
     * USE OF CODE STRUCTURES: Uses a 'PopupMenu' with a 'when' selection structure 
     * to map menu IDs to enum values.
     */
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
            // CODE STRUCTURE: Selection structure mapping UI IDs to application logic enums
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

    /**
     * FUNCTIONALITY: Filters and sorts the cached product list based on search queries, 
     * favorite filters, and the active sort mode.
     * USE OF DATA: Reads from 'allProducts', applies 'currentSearchQuery' and 'showFavoritesOnly'.
     * USE OF CODE STRUCTURES: A sequence of functional operations (.filter, .sortedBy) 
     * within a 'when' selection block to generate the final display list.
     */
    private fun refreshList() {
        // CODE STRUCTURE: Filter out products currently undergoing an "undoable" deletion
        var list = allProducts.filter { !pendingActions.contains(it.uuid) }
        
        if (showFavoritesOnly) {
            list = list.filter { it.isFavorite }
        }

        if (currentSearchQuery.isNotBlank()) {
            val queryText = currentSearchQuery.lowercase(Locale.getDefault())
            // USE OF CODE STRUCTURES: Iterative filtering for search across name, brand, and barcode
            list = list.filter { product ->
                product.name.lowercase(Locale.getDefault()).contains(queryText) ||
                        (product.brand?.lowercase(Locale.getDefault())?.contains(queryText) ?: false) ||
                        (product.barcode?.lowercase(Locale.getDefault())?.contains(queryText) ?: false)
            }
        }

        // USE OF CODE STRUCTURES: Selection structure to determine the sorting algorithm to apply
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

    /**
     * FUNCTIONALITY: Updates the UI visibility based on list content, showing empty states or the data recycler.
     * USE OF DATA: Evaluates a 'List<Product>' and updates layout view visibilities.
     * USE OF CODE STRUCTURES: Sequential 'if' checks to determine whether to show 
     * a generic empty state, a "no search results" state, or the populated list.
     */
    private fun updateList(products: List<Product>) {
        val emptyState = binding.emptyStateLayout

        // CODE STRUCTURE: Priority selection for UI state determination
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
            
            // CODE STRUCTURE: Nested selection for specific empty filter messages
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

    /**
     * FUNCTIONALITY: Provides an "Undo" mechanism for destructive actions by temporarily 
     * hiding data and showing a Snackbar.
     * USE OF DATA: Manages a 'pendingActions' set of UUIDs and an 'undone' flag.
     * USE OF CODE STRUCTURES: Uses a 'Snackbar' with an action callback and a 
     * dismissal listener to either commit the action or restore the item.
     */
    private fun performUndoableAction(product: Product, message: String, onCommit: () -> Unit) {
        pendingActions.add(product.uuid)
        android.util.Log.d("ExpiryX_Debug", "[TC-10] Swipe action initiated for: ${product.name}. UUID: ${product.uuid}")
        refreshList()

        val snackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.rootCoordinator,
            message,
            5000
        )
        
        var undone = false
        // CODE STRUCTURE: Listener for user interaction with the "UNDO" button
        snackbar.setAction("UNDO") {
            undone = true
            pendingActions.remove(product.uuid)
            android.util.Log.d("ExpiryX_Debug", "[TC-10] UNDO clicked for: ${product.name}")
            refreshList()
        }
        
        // CODE STRUCTURE: Callback logic for handling the expiration of the undo window
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

    /**
     * FUNCTIONALITY: Re-registers all product alarms with the system AlarmManager.
     * USE OF DATA: Fetches latest 'List<Product>' from ViewModel or Repository.
     * USE OF CODE STRUCTURES: Launches a coroutine on 'Dispatchers.IO' and uses 
     * 'NotificationScheduler' for batch processing.
     */
    private fun scheduleAllProductNotifications() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch(Dispatchers.IO) {
                val products = productViewModel.allProducts.value
                    ?: (application as ProductApplication).repository.getAllProductsNow()
                NotificationScheduler.rescheduleAll(this@MainActivity, products)
            }
        }
    }

    /**
     * FUNCTIONALITY: Verifies notification permissions for Android 13+ and requests them if necessary.
     * USE OF DATA: Checks 'POST_NOTIFICATIONS' permission string.
     * USE OF CODE STRUCTURES: Uses 'if' selection for OS version and permission checks.
     */
    private fun checkAndMaybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // CODE STRUCTURE: Version selection for handling new permission requirements in T+
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