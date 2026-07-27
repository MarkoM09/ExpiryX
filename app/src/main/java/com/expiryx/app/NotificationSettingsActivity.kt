package com.expiryx.app

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expiryx.app.databinding.ActivityNotificationSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationSettingsActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Base options that always exist
    private val baseIntervals = listOf("0", "1", "3", "7", "14", "30")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupBottomNav()
    }

    private fun setupUI() {
        refreshUI()

        binding.btnBack.setOnClickListener { finish() }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setNotificationsEnabled(this, isChecked)
            rescheduleAllNotifications()
            refreshUI()
        }

        binding.cardIntervals.setOnClickListener { showIntervalsDialog() }
        
        binding.cardTime.setOnClickListener { showClockTimePicker() }

        binding.cardSnooze.setOnClickListener { showSnoozeWheelPicker() }
    }

    private fun refreshUI() {
        val enabled = Prefs.isNotificationsEnabled(this)
        binding.switchNotifications.isChecked = enabled

        // Intervals summary
        val selectedIntervals = Prefs.getReminderIntervals(this).toList()
            .map { it.toIntOrNull() ?: 0 }
            .sorted()
        
        val summary = if (selectedIntervals.isEmpty()) {
            "On the day"
        } else {
            selectedIntervals.map { value ->
                when (value) {
                    0 -> "On the day"
                    1 -> "1 day before"
                    else -> "$value days before"
                }
            }.joinToString(", ")
        }
        binding.txtSelectedIntervals.text = summary

        // Time summary
        val hour = Prefs.getDefaultHour(this)
        val minute = Prefs.getDefaultMinute(this)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.txtSelectedTime.text = timeFormat.format(calendar.time)

        // Snooze status
        if (Prefs.isSnoozeActive(this)) {
            val end = Prefs.getSnoozeEndTimestamp(this)
            binding.txtSnoozeStatus.text = "Active until ${dateFormat.format(Date(end))}"
            binding.txtSnoozeStatus.setTextColor(getColor(R.color.orange))
        } else {
            binding.txtSnoozeStatus.text = "None"
            binding.txtSnoozeStatus.setTextColor(getColor(R.color.gray))
        }

        // Enable/Disable cards based on master switch
        binding.cardIntervals.alpha = if (enabled) 1.0f else 0.5f
        binding.cardTime.alpha = if (enabled) 1.0f else 0.5f
        binding.cardIntervals.isEnabled = enabled
        binding.cardTime.isEnabled = enabled
    }

    private fun showIntervalsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_intervals_list, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerIntervals)
        val btnAdd = dialogView.findViewById<View>(R.id.btnAddCustom)
        
        val currentIntervals = Prefs.getReminderIntervals(this).toMutableSet()
        currentIntervals.add("0") // Always force 0

        val allAvailable = (baseIntervals + currentIntervals).distinct().sortedBy { it.toInt() }
        
        val adapter = IntervalsAdapter(allAvailable, currentIntervals) { updatedSet ->
            Prefs.setReminderIntervals(this, updatedSet)
            rescheduleAllNotifications()
            refreshUI()
        }
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .create()

        btnAdd.setOnClickListener {
            dialog.dismiss()
            showCustomIntervalWheel()
        }

        dialog.show()
    }

    private fun showCustomIntervalWheel() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null)
        val picker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)
        val editNumber = dialogView.findViewById<EditText>(R.id.editTextNumber)
        val btnToggle = dialogView.findViewById<ImageButton>(R.id.btnToggleInputMode)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val subtitle = dialogView.findViewById<TextView>(R.id.dialogSubtitle)

        title.text = "Add Custom Reminder"
        subtitle.text = "days before expiry"
        picker.minValue = 1
        picker.maxValue = 365
        picker.value = 5
        editNumber.setText("5")

        btnToggle.setOnClickListener {
            val isWheel = picker.isVisible
            picker.isVisible = !isWheel
            editNumber.isVisible = isWheel
            btnToggle.setImageResource(if (isWheel) R.drawable.ic_wheel else R.drawable.ic_keyboard)
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val newVal = if (picker.isVisible) picker.value.toString() else editNumber.text.toString()
                if (newVal.isNotBlank()) {
                    val current = Prefs.getReminderIntervals(this).toMutableSet()
                    current.add(newVal)
                    Prefs.setReminderIntervals(this, current)
                    rescheduleAllNotifications()
                    refreshUI()
                    showIntervalsDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClockTimePicker() {
        val currentHour = Prefs.getDefaultHour(this)
        val currentMinute = Prefs.getDefaultMinute(this)

        TimePickerDialog(this, { _, hourOfDay, minute ->
            Prefs.setDefaultTime(this, hourOfDay, minute)
            rescheduleAllNotifications()
            refreshUI()
        }, currentHour, currentMinute, false).show()
    }

    private fun showSnoozeWheelPicker() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null)
        val picker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)
        val editNumber = dialogView.findViewById<EditText>(R.id.editTextNumber)
        val btnToggle = dialogView.findViewById<ImageButton>(R.id.btnToggleInputMode)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val subtitle = dialogView.findViewById<TextView>(R.id.dialogSubtitle)

        title.text = "Snooze All Notifications"
        subtitle.text = "number of days"
        
        picker.minValue = 0
        picker.maxValue = 30
        picker.setFormatter { if (it == 0) "Clear (Off)" else "$it Days" }
        
        val currentSnoozeDays = if (Prefs.isSnoozeActive(this)) {
            val remainingMs = Prefs.getSnoozeEndTimestamp(this) - System.currentTimeMillis()
            val days = (remainingMs / (24 * 60 * 60 * 1000)).toInt()
            if (days <= 0) 1 else days
        } else 0
        
        picker.value = currentSnoozeDays
        editNumber.setText(currentSnoozeDays.toString())
        
        // Final attempt to force NumberPicker to show the initial formatted value
        // We find the EditText inside the NumberPicker and set its text manually
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) {
                child.filters = arrayOfNulls(0) // Remove filters to allow manual text update
                child.setText(if (currentSnoozeDays == 0) "Clear (Off)" else "$currentSnoozeDays Days")
                break
            }
        }

        btnToggle.setOnClickListener {
            val isWheel = picker.isVisible
            picker.isVisible = !isWheel
            editNumber.isVisible = isWheel
            btnToggle.setImageResource(if (isWheel) R.drawable.ic_wheel else R.drawable.ic_keyboard)
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                val finalVal = if (picker.isVisible) picker.value else editNumber.text.toString().toIntOrNull() ?: 0
                Prefs.setSnooze(this, finalVal)
                refreshUI()
                val msg = if (finalVal == 0) "Snooze cleared" else "Notifications snoozed for $finalVal days"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, binding.bottomNav.bottomNavigationView, R.id.nav_settings)
    }

    private fun rescheduleAllNotifications() {
        val repo = (application as ProductApplication).repository
        lifecycleScope.launch(Dispatchers.IO) {
            val products = repo.getAllProductsNow()
            NotificationScheduler.rescheduleAll(this@NotificationSettingsActivity, products)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NotificationSettingsActivity, "Notifications updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class IntervalsAdapter(
        private var options: List<String>,
        private val selected: MutableSet<String>,
        private val onUpdate: (Set<String>) -> Unit
    ) : RecyclerView.Adapter<IntervalsAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val check: CheckBox = view.findViewById(R.id.checkInterval)
            val name: TextView = view.findViewById(R.id.txtIntervalName)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteInterval)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_interval_selection, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val valStr = options[position]
            val value = valStr.toInt()

            holder.name.text = when (value) {
                0 -> "On the day (Always On)"
                1 -> "1 day before"
                else -> "$value days before"
            }

            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = selected.contains(valStr) || value == 0
            
            if (value == 0) {
                holder.check.isEnabled = false
                holder.check.alpha = 0.3f
                holder.name.alpha = 0.3f
                holder.btnDelete.visibility = View.GONE
            } else {
                holder.check.isEnabled = true
                holder.check.alpha = 1.0f
                holder.name.alpha = 1.0f
                holder.btnDelete.visibility = View.VISIBLE
                
                holder.check.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selected.add(valStr) else selected.remove(valStr)
                    onUpdate(selected)
                }
                
                holder.btnDelete.setOnClickListener {
                    val currentPos = holder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        val removedVal = options[currentPos]
                        selected.remove(removedVal)
                        val newOptions = options.toMutableList()
                        newOptions.removeAt(currentPos)
                        options = newOptions
                        notifyItemRemoved(currentPos)
                        onUpdate(selected)
                    }
                }
            }
        }

        override fun getItemCount(): Int = options.size
    }
}
