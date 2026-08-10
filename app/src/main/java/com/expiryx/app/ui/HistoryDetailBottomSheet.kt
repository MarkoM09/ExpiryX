package com.expiryx.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bumptech.glide.Glide
import com.expiryx.app.databinding.BottomsheetHistoryDetailBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * FUNCTIONALITY: Displays granular detail parameters for an archived history record 
 * and provides options for restoration or permanent deletion.
 * USE OF DATA: Accepts a 'History' parcelable extra object containing 'action' (String) 
 * and various 'Long' timestamps. Uses 'BottomsheetHistoryDetailBinding' for UI population.
 * USE OF CODE STRUCTURES: Extends 'ThemedBottomSheetDialogFragment'; populates UI 
 * fields during 'onViewCreated' and uses 'when' selection logic for dynamic action button labeling.
 */
class HistoryDetailBottomSheet : ThemedBottomSheetDialogFragment() {

    private var _binding: BottomsheetHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var history: History
    private lateinit var viewModel: HistoryViewModel

    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = (requireActivity() as HistoryActivity).viewModel
        arguments?.let {
            history = it.getParcelable("history")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.setupBottomSheetEdgeToEdge(this, binding.root)
        populateUI(history)
        setupListeners(history)
    }

    /**
     * FUNCTIONALITY: Binds the historical record's attributes to the bottom sheet's UI components.
     * USE OF DATA: Reads snapshot data from the 'History' entity (productName, action, timestamps).
     * USE OF CODE STRUCTURES: Selection structures (if/else) to handle optional fields 
     * like brand, weight, and modification dates.
     */
    private fun populateUI(h: History) {
        // IMAGE LOADING: CODE STRUCTURE: Async image loading via Glide
        Glide.with(requireContext())
            .load(h.imageUri)
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_placeholder)
            .into(binding.imageHistoryDetail)

        // Core fields
        binding.textHistoryName.text = h.productName
        binding.textHistoryBrand.text = h.brand.takeIf { !it.isNullOrBlank() } ?: "No brand"
        
        // CODE STRUCTURE: Visibility selection based on data existence
        binding.textHistoryBrand.visibility = if (h.brand.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.textHistoryExpiry.text = getString(R.string.expiry_label, h.expirationDate?.let { formatDate(it) } ?: "N/A")
        binding.textHistoryQuantity.text = getString(R.string.quantity_label, h.quantity)
        
        if (h.weight != null) {
            binding.textHistoryWeight.text = getString(R.string.weight_label, h.weight, h.weightUnit)
            binding.textHistoryWeight.visibility = View.VISIBLE
        } else {
            binding.textHistoryWeight.visibility = View.GONE
        }
        binding.textHistoryFavourite.text = getString(R.string.favourite_label, if (h.isFavorite) getString(R.string.yes) else getString(R.string.no))

        // Barcode display
        if (!h.barcode.isNullOrBlank()) {
            binding.txtHistoryBarcode.text = "${getString(R.string.barcode_label)} ${h.barcode}"
            binding.txtHistoryBarcode.visibility = View.VISIBLE
        } else {
            binding.txtHistoryBarcode.visibility = View.GONE
        }

        // Timestamps
        binding.txtHistoryDateAdded.text = "${getString(R.string.added_label)} ${formatDateTime(h.dateAdded)}"
        
        if (h.dateModified != null) {
            binding.txtHistoryDateModified.text = "${getString(R.string.modified_label)} ${formatDateTime(h.dateModified)}"
            binding.txtHistoryDateModified.visibility = View.VISIBLE
        } else {
            binding.txtHistoryDateModified.visibility = View.GONE
        }
    }

    /**
     * FUNCTIONALITY: Attaches logic to the primary and secondary action buttons.
     * USE OF DATA: Consumes 'history.action' (String).
     * USE OF CODE STRUCTURES: 'when' selection structure to adapt button text 
     * and functionality based on the item's historical context.
     */
    private fun setupListeners(history: History) {
        // CODE STRUCTURE: Branching logic determining restore action workflow
        when (history.action) {
            "Deleted" -> {
                binding.btnPrimary.text = getString(R.string.restore)
                binding.btnPrimary.setOnClickListener {
                    viewModel.restoreDeleted(history)
                    dismiss()
                }
            }
            "Used" -> {
                binding.btnPrimary.text = getString(R.string.history_action_unuse)
                binding.btnPrimary.setOnClickListener {
                    viewModel.unuse(history)
                    dismiss()
                }
            }
            "Expired" -> {
                binding.btnPrimary.text = getString(R.string.history_action_restore_expiry)
                binding.btnPrimary.setOnClickListener { showDatePicker(history) } 
            }
        }

        // Permanent delete
        binding.btnSecondary.text = getString(R.string.history_permanently_delete_title)
        binding.btnSecondary.setOnClickListener {
            // CODE STRUCTURE: Confirmation dialog selection prior to destructive DB call
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.history_permanently_delete_title))
                .setMessage(getString(R.string.history_permanently_delete_msg, history.productName))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.permanentlyDelete(history)
                    dismiss()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showDatePicker(historyForRestore: History) { 
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val calendar = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                val newExpiryMillis = calendar.timeInMillis
                viewModel.changeExpiry(historyForRestore, newExpiryMillis) 
                Toast.makeText(requireContext(), getString(R.string.history_toast_restored), Toast.LENGTH_SHORT).show()
                dismiss()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(history: History): HistoryDetailBottomSheet {
            return HistoryDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable("history", history)
                }
            }
        }
    }
}