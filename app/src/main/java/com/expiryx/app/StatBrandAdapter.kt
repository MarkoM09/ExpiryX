package com.expiryx.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * FUNCTIONALITY: Displays brand-specific waste and usage statistics in a ranked list format 
 * with relative progress bar indicators.
 * USE OF DATA: Consumes a 'List' of 'BrandStat' objects (String name, Int count). 
 * Tracks 'maxCount' (Int) to normalize progress bar scaling.
 * USE OF CODE STRUCTURES: Extends 'RecyclerView.Adapter'; uses functional 'maxOfOrNull' 
 * and 'coerceAtLeast' selection logic to calculate dynamic bar maximums.
 */
class StatBrandAdapter(
    private var items: List<BrandStat> = emptyList(),
    private var maxCount: Int = 1,
) : RecyclerView.Adapter<StatBrandAdapter.VH>() {

    /**
     * FUNCTIONALITY: Updates the adapter's data set and recalculates scaling metrics.
     * USE OF DATA: Accepts 'newItems' (List<BrandStat>).
     * USE OF CODE STRUCTURES: Selection structure with fallback values ensures 
     * 'maxCount' is never zero, preventing layout glitches.
     */
    fun submitList(newItems: List<BrandStat>) {
        items = newItems
        // DATA: Dynamic calculation of the highest frequency to set visual chart bounds
        maxCount = newItems.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txtBrand: TextView = view.findViewById(R.id.txtBrandName)
        val txtCount: TextView = view.findViewById(R.id.txtBrandCount)
        val progress: ProgressBar = view.findViewById(R.id.brandProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stat_brand_row, parent, false)
        return VH(view)
    }

    /**
     * FUNCTIONALITY: Binds brand data to UI components and scales the progress bar proportionally.
     * USE OF DATA: Maps 'item.count' (Int) to 'progress.progress'.
     * USE OF CODE STRUCTURES: Standard sequential binding.
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.txtBrand.text = item.brand
        holder.txtCount.text = holder.itemView.context.getString(R.string.stats_brand_count, item.count)
        
        // CODE STRUCTURE: Normalizing numeric data for relative visual display
        holder.progress.max = maxCount
        holder.progress.progress = item.count
        holder.progress.progressTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.grey_800)
    }

    override fun getItemCount(): Int = items.size
}