package com.expiryx.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Calendar
import kotlin.math.floor

/**
 * FUNCTIONALITY: Manages the display and interaction logic for the main product list, 
 * supporting both flat and grouped (by expiry status) views.
 * USE OF DATA: Binds 'ProductListItem' objects (Headers or Products) to UI views. 
 * Uses 'Product' entity data for display.
 * USE OF CODE STRUCTURES: Implements a 'sealed class' for multi-type list items, 
 * 'ListAdapter' for efficient diffing, and complex grouping algorithms for expiry status.
 */
sealed class ProductListItem {
    data class Header(val title: String, val colorRes: Int) : ProductListItem()
    data class ProductItem(val product: Product) : ProductListItem()
}

class ProductAdapter(
    private val onFavoriteClick: (Product) -> Unit,
    private val onItemClick: (Product) -> Unit,
    private val onDeleteLongPress: (Product) -> Unit,
) : ListAdapter<ProductListItem, RecyclerView.ViewHolder>(ProductListDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PRODUCT = 1
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bar: View = view.findViewById(R.id.headerBar)
        val title: TextView = view.findViewById(R.id.headerTitle)
    }

    /**
     * FUNCTIONALITY: Holds and initializes references to UI components for a product item row.
     * USE OF DATA: Maps 'Product' fields to ImageViews and TextViews.
     */
    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageProduct: ImageView = view.findViewById(R.id.imageProduct)
        val textProductName: TextView = view.findViewById(R.id.textProductName)
        val textProductBrand: TextView = view.findViewById(R.id.textProductBrand)
        val textProductExpiry: TextView = view.findViewById(R.id.textProductExpiry)
        val textProductQuantity: TextView = view.findViewById(R.id.textProductQuantity)
        val btnFavorite: ImageButton = view.findViewById(R.id.btnFavorite)
    }

    /**
     * FUNCTIONALITY: Returns the view type for a given position in the list.
     * USE OF DATA: Checks the type of 'ProductListItem' at the specified position.
     * USE OF CODE STRUCTURES: 'when' selection structure to branch by sealed class type.
     */
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ProductListItem.Header -> TYPE_HEADER
            is ProductListItem.ProductItem -> TYPE_PRODUCT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
            ProductViewHolder(view)
        }
    }

    /**
     * FUNCTIONALITY: Populates a ViewHolder with data from a 'ProductListItem'.
     * USE OF DATA: Ingests a 'Header' or 'ProductItem'. Sets text, images, and click listeners.
     * USE OF CODE STRUCTURES: Uses a 'when' selection to handle different ViewHolder types 
     * and 'if' checks for optional data like brand strings.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ProductListItem.Header -> {
                val h = holder as HeaderViewHolder
                h.title.text = item.title
                h.bar.setBackgroundColor(h.itemView.context.getColor(item.colorRes))
            }
            is ProductListItem.ProductItem -> {
                val h = holder as ProductViewHolder
                val product = item.product

                h.textProductName.text = product.name

                // CODE STRUCTURE: Optional brand display check
                val brandText = product.brand?.takeIf { it.isNotBlank() }
                if (!brandText.isNullOrBlank()) {
                    h.textProductBrand.text = brandText
                    h.textProductBrand.visibility = View.VISIBLE
                } else {
                    h.textProductBrand.visibility = View.GONE
                }

                h.textProductQuantity.text = "Qty: ${product.quantity}"
                h.textProductExpiry.text = ExpiryDisplayUtils.formatDaysRemaining(h.itemView.context, product.expirationDate)
                ExpiryDisplayUtils.applyTrafficLightPill(h.textProductExpiry, product.expirationDate)
                h.textProductExpiry.visibility = View.VISIBLE

                h.btnFavorite.setImageResource(
                    if (product.isFavorite) R.drawable.ic_fav_filled else R.drawable.ic_fav_unfilled
                )
                h.btnFavorite.setOnClickListener { onFavoriteClick(product) }

                // CODE STRUCTURE: Async image loading via Glide with URI parsing
                Glide.with(h.itemView.context)
                    .load(if (product.imageUri.isNullOrBlank()) R.drawable.ic_placeholder else android.net.Uri.parse(product.imageUri))
                    .error(R.drawable.ic_placeholder)
                    .into(h.imageProduct)

                h.itemView.setOnClickListener { onItemClick(product) }
                h.itemView.setOnLongClickListener {
                    onDeleteLongPress(product)
                    true
                }
            }
        }
    }

    /**
     * FUNCTIONALITY: Prepares the internal list structure (flat or grouped) and submits it to the adapter.
     * USE OF DATA: Takes a 'List<Product>' and the current 'SortMode'.
     * USE OF CODE STRUCTURES: 'if/else' selection to choose between flat mapping or 
     * categorical grouping based on the active sort.
     */
    fun updateData(products: List<Product>, sortMode: MainActivity.SortMode) {
        val listItems = if (sortMode == MainActivity.SortMode.EXPIRY_ASC || sortMode == MainActivity.SortMode.EXPIRY_DESC) {
            createGroupedList(products, sortMode == MainActivity.SortMode.EXPIRY_DESC)
        } else {
            createFlatList(products)
        }
        submitList(listItems)
    }

    private fun createFlatList(products: List<Product>): List<ProductListItem> {
        return products.map { ProductListItem.ProductItem(it) }
    }

    /**
     * FUNCTIONALITY: Categorizes products into time-based buckets (e.g., "Expired", "Today") 
     * and inserts header items into the list.
     * USE OF DATA: Calculates 'dayDiff' (Long) by comparing expiry to 'now'.
     * USE OF CODE STRUCTURES: Employs nested helper functions ('dayDiff', 'addGroup') 
     * and sequential calls to 'addGroup' with predicate lambda conditions to build the hierarchy.
     */
    private fun createGroupedList(products: List<Product>, reverseOrder: Boolean = false): List<ProductListItem> {
        val grouped = mutableListOf<ProductListItem>()
        val now = System.currentTimeMillis()
        val startToday = getStartOfDay(now)

        // USE OF CODE STRUCTURES: Functional mapping to compute days remaining
        fun dayDiff(expiryMillis: Long?): Long? {
            expiryMillis ?: return null
            val startExpiry = getStartOfDay(expiryMillis)
            val diffMs = startExpiry - startToday
            return floor(diffMs.toDouble() / DAY_MS).toLong()
        }

        // USE OF CODE STRUCTURES: Helper to filter and bucket products into the UI list
        fun addGroup(title: String, colorRes: Int, condition: (Product) -> Boolean) {
            val filtered = products.filter(condition)
            if (filtered.isNotEmpty()) {
                grouped.add(ProductListItem.Header(title, colorRes))
                grouped.addAll(filtered.map { ProductListItem.ProductItem(it) })
            }
        }

        // CODE STRUCTURE: Branching logic for ascending vs descending expiry sort
        if (reverseOrder) {
            addGroup("No expiry date", R.color.gray) { it.expirationDate == null }
            addGroup("Expiring in 1+ year", R.color.gray) { val d = dayDiff(it.expirationDate); d != null && d > 365L }
            addGroup("Expiring in 3-12 months", R.color.purple) { val d = dayDiff(it.expirationDate); d != null && d in 91L..365L }
            addGroup("Expiring in 15-90 days", R.color.grey_600) { val d = dayDiff(it.expirationDate); d != null && d in 15L..90L }
            addGroup("Expiring in 4-14 days", R.color.blue) { val d = dayDiff(it.expirationDate); d != null && d in 4L..14L }
            addGroup("Expiring in 2-3 days", R.color.green) { val d = dayDiff(it.expirationDate); d != null && d in 2L..3L }
            addGroup("Expiring tomorrow", R.color.yellow) { val d = dayDiff(it.expirationDate); d != null && d == 1L }
            addGroup("Expiring today", R.color.orange) { val d = dayDiff(it.expirationDate); d != null && d == 0L }
            addGroup("Expired", R.color.red) { val d = dayDiff(it.expirationDate); d != null && d < 0 }
        } else {
            addGroup("Expired", R.color.red) { val d = dayDiff(it.expirationDate); d != null && d < 0 }
            addGroup("Expiring today", R.color.orange) { val d = dayDiff(it.expirationDate); d != null && d == 0L }
            addGroup("Expiring tomorrow", R.color.yellow) { val d = dayDiff(it.expirationDate); d != null && d == 1L }
            addGroup("Expiring in 2-3 days", R.color.green) { val d = dayDiff(it.expirationDate); d != null && d in 2L..3L }
            addGroup("Expiring in 4-14 days", R.color.blue) { val d = dayDiff(it.expirationDate); d != null && d in 4L..14L }
            addGroup("Expiring in 15-90 days", R.color.grey_600) { val d = dayDiff(it.expirationDate); d != null && d in 15L..90L }
            addGroup("Expiring in 3-12 months", R.color.purple) { val d = dayDiff(it.expirationDate); d != null && d in 91L..365L }
            addGroup("Expiring in 1+ year", R.color.gray) { val d = dayDiff(it.expirationDate); d != null && d > 365L }
            addGroup("No expiry date", R.color.gray) { it.expirationDate == null }
        }

        return grouped
    }

    private fun getStartOfDay(ts: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

/**
 * FUNCTIONALITY: Provides efficient comparison logic for ListAdapter to animate list changes.
 * USE OF DATA: Compares 'ProductListItem' objects.
 * USE OF CODE STRUCTURES: Uses 'when' selection to handle comparison for different item types.
 */
private class ProductListDiffCallback : DiffUtil.ItemCallback<ProductListItem>() {
    override fun areItemsTheSame(oldItem: ProductListItem, newItem: ProductListItem): Boolean {
        return when {
            oldItem is ProductListItem.Header && newItem is ProductListItem.Header -> oldItem.title == newItem.title
            oldItem is ProductListItem.ProductItem && newItem is ProductListItem.ProductItem -> oldItem.product.uuid == newItem.product.uuid
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ProductListItem, newItem: ProductListItem): Boolean {
        return oldItem == newItem
    }
}