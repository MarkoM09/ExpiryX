package com.expiryx.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

/**
 * FUNCTIONALITY: Recycles and displays 'History' entity records within a list view, 
 * providing chronological logging of product lifecycle events.
 * USE OF DATA: Consumes 'History' record objects and binds properties like 'productName' 
 * (String) and 'timestamp' (Long) to UI components.
 * USE OF CODE STRUCTURES: Extends 'ListAdapter' with a 'DiffUtil' callback for 
 * efficient UI updates and uses custom 'ViewHolder' for view recycling.
 */
class HistoryAdapter(
    private val onItemClick: (History) -> Unit,
    private val onItemLongPress: (History) -> Unit
) : ListAdapter<History, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    /**
     * FUNCTIONALITY: Holds and initializes references to UI components for a history item row.
     * USE OF DATA: Maps 'History' fields to ImageViews and TextViews.
     */
    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageHistoryProduct: ImageView = view.findViewById(R.id.imageHistoryProduct)
        val textHistoryProduct: TextView = view.findViewById(R.id.textHistoryProduct)
        val textHistoryAction: TextView = view.findViewById(R.id.textHistoryAction)
        val textHistoryDate: TextView = view.findViewById(R.id.textHistoryDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    /**
     * FUNCTIONALITY: Binds specific history record data to the recycled view holder.
     * USE OF DATA: Formats 'timestamp' (Long) into a human-readable 'String'.
     * USE OF CODE STRUCTURES: Executes sequential data binding and triggers async 
     * image loading via Glide.
     */
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)

        // CODE STRUCTURE: Direct property binding from the History entity
        holder.textHistoryProduct.text = item.productName
        holder.textHistoryAction.text = item.action

        // DATA: Formatting epoch millisecond data for user-facing display
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.textHistoryDate.text = sdf.format(Date(item.timestamp))

        Glide.with(holder.imageHistoryProduct.context)
            .load(item.imageUri)
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_placeholder)
            .into(holder.imageHistoryProduct)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongPress(item)
            true
        }
    }

    fun updateData(newItems: List<History>) {
        submitList(newItems)
    }
}

/**
 * FUNCTIONALITY: Calculates the difference between two history lists to optimize 
 * RecyclerView animations.
 * USE OF DATA: Compares 'id' (Int) for structural changes and full equality for content changes.
 * USE OF CODE STRUCTURES: Implements DiffUtil.ItemCallback selection methods.
 */
private class HistoryDiffCallback : DiffUtil.ItemCallback<History>() {
    override fun areItemsTheSame(oldItem: History, newItem: History): Boolean {
        // CODE STRUCTURE: Primary key comparison for structural identity
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: History, newItem: History): Boolean {
        // CODE STRUCTURE: Data class equality check for content identity
        return oldItem == newItem
    }
}