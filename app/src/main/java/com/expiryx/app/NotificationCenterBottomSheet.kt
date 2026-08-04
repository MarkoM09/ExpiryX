package com.expiryx.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.expiryx.app.databinding.BottomsheetNotificationLogBinding
import com.expiryx.app.databinding.ItemNotificationLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FUNCTIONALITY: Renders a scrollable history log of all background push alerts delivered 
 * to the user, allowing for log review and cleanup.
 * USE OF DATA: Binds a 'List' of 'NotificationLog' objects (containing 'title', 'message', 
 * and 'timestamp' data).
 * USE OF CODE STRUCTURES: Extends 'ThemedBottomSheetDialogFragment'; observes a 'LiveData' 
 * stream from the DAO and uses a coroutine block for asynchronous log deletion.
 */
class NotificationCenterBottomSheet : ThemedBottomSheetDialogFragment() {

    private var _binding: BottomsheetNotificationLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetNotificationLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * FUNCTIONALITY: Initializes the log display UI and binds data from the database.
     * USE OF DATA: Accesses 'NotificationLogDao' to retrieve records.
     * USE OF CODE STRUCTURES: Observer pattern updating the adapter and 'if' selection 
     * toggling the empty state visibility.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.setupBottomSheetEdgeToEdge(this, binding.root)

        val dao = (requireActivity().application as ProductApplication).database.notificationLogDao()
        val adapter = NotificationLogAdapter()
        
        binding.recyclerNotificationLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotificationLogs.adapter = adapter

        // CODE STRUCTURE: Observer pattern reacting to changes in the notification log table
        dao.getRecentLogs().observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
            // CODE STRUCTURE: Selection structure for empty state UI control
            binding.txtNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnClearLogs.setOnClickListener {
            // CODE STRUCTURE: Coroutine launch handling background database cleanup
            lifecycleScope.launch(Dispatchers.IO) {
                dao.clearAll()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * FUNCTIONALITY: Adapter for displaying individual notification log entries.
     * USE OF DATA: Maps 'NotificationLog' fields to UI components.
     * USE OF CODE STRUCTURES: Extends 'ListAdapter' for animated updates.
     */
    class NotificationLogAdapter : ListAdapter<NotificationLog, NotificationLogAdapter.ViewHolder>(DiffCallback()) {
        class ViewHolder(val binding: ItemNotificationLogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemNotificationLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        /**
         * FUNCTIONALITY: Populates the log row with specific alert data.
         * USE OF DATA: Formats 'timestamp' (Long) into a short date string.
         * USE OF CODE STRUCTURES: 'if' selection to show/hide the urgency indicator.
         */
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = getItem(position)
            holder.binding.txtLogTitle.text = log.title
            holder.binding.txtLogMessage.text = log.message
            // DATA: Date formatting for consistent log timestamps
            holder.binding.txtLogTime.text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(log.timestamp))
            
            // CODE STRUCTURE: Visibility selection based on log urgency level
            holder.binding.urgencyIndicator.visibility = if (log.urgency == 1) View.VISIBLE else View.INVISIBLE
        }

        class DiffCallback : DiffUtil.ItemCallback<NotificationLog>() {
            override fun areItemsTheSame(oldItem: NotificationLog, newItem: NotificationLog) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NotificationLog, newItem: NotificationLog) = oldItem == newItem
        }
    }
}