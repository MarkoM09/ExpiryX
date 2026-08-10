package com.expiryx.app

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.expiryx.app.databinding.ActivityManualEntryBinding
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/**
 * FUNCTIONALITY: Manages the manual entry and editing of grocery items, including 
 * input validation, image selection, and date picking.
 * USE OF DATA: Processes 'Product' objects for editing. Handles Strings (names, brands), 
 * Ints (quantity, weight), and Longs (expiry timestamps). Uses View Binding for UI interaction.
 * USE OF CODE STRUCTURES: Employs 'enum' for date input modes, 'if/else' sequence 
 * validation checks, and 'NumberPicker' listeners for reactive date calculation.
 */
class ManualEntryActivity : ThemedAppCompatActivity() {

    /**
     * FUNCTIONALITY: Represents the different ways a user can input an expiration date.
     * USE OF DATA: Enum constants for WHEEL, CALENDAR, and manual TEXT entry.
     */
    private enum class DateInputMode { WHEEL, CALENDAR, TEXT }

    private val productViewModel: ProductViewModel by viewModels {
        ProductViewModelFactory((application as ProductApplication).repository)
    }
    private lateinit var binding: ActivityManualEntryBinding

    private val dateFormat =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }

    private val monthLabels = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    private var editingProduct: Product? = null
    private var expiryMillis: Long? = null
    private var selectedImageUri: String? = null
    private var productBarcode: String? = null
    private var selectedWeightUnit: String = "g"
    private var isInternalUpdate = false
    private var dateInputMode = DateInputMode.WHEEL

    private val safeTextFilter = InputFilter { source, _, _, _, _, _ ->
        val allowed = Pattern.compile("^[a-zA-Z0-9 .,'&()\\-]*$")
        if (!allowed.matcher(source).matches()) "" else source
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) { /* ignore */ }
                selectedImageUri = it.toString()
                Glide.with(this).load(it).into(binding.imageProductPreview)
            }
        }

    /**
     * FUNCTIONALITY: Orchestrates activity initialization, view setup, and data loading.
     * USE OF DATA: Inflates 'ActivityManualEntryBinding' and checks intent for existing product data.
     * USE OF CODE STRUCTURES: Sequential calls to setup methods to modularize UI configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)

        binding = ActivityManualEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupFilters()
        setupWeightUnitDropdown()
        setupExpiryDateWheel()
        setupDateModeToggle()
        loadProductData()
        updateToolbarTitle()
        setupListeners()
        applyDateInputMode(DateInputMode.WHEEL)
        setupKeyboardDismissal()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardDismissal() {
        binding.root.setOnTouchListener { _, _ ->
            currentFocus?.let { view ->
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
            false
        }
    }

    /**
     * FUNCTIONALITY: Dynamically adjusts view margins and padding to prevent UI overlap 
     * with system bars and the IME (keyboard).
     * USE OF DATA: Reads 'systemBars' and 'ime' insets from 'WindowInsetsCompat'.
     * USE OF CODE STRUCTURES: Uses 'maxOf' selection logic to determine the largest 
     * bottom inset required and applies it to the layout.
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // CODE STRUCTURE: Selection logic to ensure visibility above navigation bar or keyboard
            val bottomInset = maxOf(systemBars.bottom, imeInsets.bottom)
            
            binding.appBarManualEntry.setPadding(0, systemBars.top, 0, 0)
            
            binding.btnSaveProduct.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (16 * resources.displayMetrics.density).toInt() + bottomInset
            }

            binding.manualEntryScroll.setPadding(0, 0, 0, (80 * resources.displayMetrics.density).toInt() + bottomInset)

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar() {
        updateToolbarTitle()

        binding.toolbarManualEntry.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_cancel -> {
                    finish()
                    true
                }
                R.id.action_clear -> {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Reset Form")
                        .setMessage("Are you sure you want to clear all fields?")
                        .setPositiveButton("Reset") { _, _ -> resetForm() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarTitle() {
        val isEditExtra = intent.getBooleanExtra("isEdit", false)
        binding.toolbarManualEntry.title = when {
            (editingProduct != null && (isEditExtra || (editingProduct!!.id != 0))) -> getString(R.string.edit_product)
            editingProduct != null -> getString(R.string.save_product)
            else -> getString(R.string.add_product)
        }
    }

    private fun setupFilters() {
        binding.editTextProductName.filters = arrayOf(safeTextFilter, InputFilter.LengthFilter(100))
        binding.editTextBrand.filters = arrayOf(safeTextFilter, InputFilter.LengthFilter(50))
        binding.editTextQuantity.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.editTextWeight.filters = arrayOf(InputFilter.LengthFilter(6))
    }

    private fun setupWeightUnitDropdown() {
        val weightUnits = arrayOf("g", "ml")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, weightUnits)
        binding.spinnerWeightUnit.setAdapter(adapter)
        binding.spinnerWeightUnit.setText(selectedWeightUnit, false)
        binding.spinnerWeightUnit.setOnItemClickListener { _, _, position, _ ->
            selectedWeightUnit = weightUnits[position]
        }
    }

    /**
     * FUNCTIONALITY: Initializes the day/month/year NumberPickers with appropriate bounds.
     * USE OF DATA: Sets min/max values and month labels for the custom date wheel UI.
     * USE OF CODE STRUCTURES: Attaches 'OnValueChangeListener' to the pickers to 
     * trigger 'updateDayPickerMax' and 'syncExpiryFromPickers' iteratively as data changes.
     */
    private fun setupExpiryDateWheel() {
        val wheel = binding.expiryDateWheel
        val pickerDay = wheel.pickerDay
        val pickerMonth = wheel.pickerMonth
        val pickerYear = wheel.pickerYear

        val today = Calendar.getInstance()
        val startYear = today[Calendar.YEAR]
        val endYear = startYear + 10

        pickerYear.minValue = startYear
        pickerYear.maxValue = endYear
        pickerYear.value = startYear

        pickerMonth.minValue = 0
        pickerMonth.maxValue = monthLabels.size - 1
        pickerMonth.displayedValues = monthLabels
        pickerMonth.value = today[Calendar.MONTH]

        updateDayPickerMax(pickerDay, pickerMonth.value + 1, pickerYear.value)
        pickerDay.value = today[Calendar.DAY_OF_MONTH].coerceAtMost(pickerDay.maxValue)

        // USE OF CODE STRUCTURES: Callback listener to handle user interactions with the wheel
        val listener = NumberPicker.OnValueChangeListener { _, _, _ ->
            if (isInternalUpdate) return@OnValueChangeListener
            updateDayPickerMax(pickerDay, pickerMonth.value + 1, pickerYear.value)
            syncExpiryFromPickers()
        }

        pickerDay.setOnValueChangedListener(listener)
        pickerMonth.setOnValueChangedListener(listener)
        pickerYear.setOnValueChangedListener(listener)

        syncExpiryFromPickers()
    }

    private fun setupDateModeToggle() {
        val wheel = binding.expiryDateWheel
        wheel.btnToggleDateMode.setOnClickListener { cycleDateInputMode() }
        wheel.btnOpenCalendarPicker.setOnClickListener { showCalendarPicker() }
        wheel.editTextExpiryDate.doAfterTextChanged {
            if (isInternalUpdate) return@doAfterTextChanged
            if (dateInputMode == DateInputMode.TEXT) {
                parseTextExpiryDate(wheel.editTextExpiryDate.text?.toString().orEmpty())
            }
        }
    }

    private fun cycleDateInputMode() {
        val next = when (dateInputMode) {
            DateInputMode.WHEEL -> DateInputMode.CALENDAR
            DateInputMode.CALENDAR -> DateInputMode.TEXT
            DateInputMode.TEXT -> DateInputMode.WHEEL
        }
        applyDateInputMode(next)
        if (next == DateInputMode.CALENDAR) {
            showCalendarPicker()
        }
    }

    /**
     * FUNCTIONALITY: Switches the active UI component for date entry (Wheel, Calendar, or Text).
     * USE OF DATA: Updates 'dateInputMode' enum and toggles visibility of layout containers.
     * USE OF CODE STRUCTURES: Selection structure (when) determining icon resources 
     * and string labels based on the active mode.
     */
    private fun applyDateInputMode(mode: DateInputMode) {
        dateInputMode = mode
        val wheel = binding.expiryDateWheel

        // CODE STRUCTURE: Toggle visibility of different date input views
        val showWheel = mode == DateInputMode.WHEEL
        wheel.containerWheel.visibility = if (showWheel) View.VISIBLE else View.GONE
        wheel.wheelLabelsRow.visibility = if (showWheel) View.VISIBLE else View.GONE
        wheel.containerCalendar.visibility = if (mode == DateInputMode.CALENDAR) View.VISIBLE else View.GONE
        wheel.layoutExpiryText.visibility = if (mode == DateInputMode.TEXT) View.VISIBLE else View.GONE

        wheel.btnToggleDateMode.setImageResource(
            when (mode) {
                DateInputMode.WHEEL -> R.drawable.ic_calendar
                DateInputMode.CALENDAR -> R.drawable.ic_keyboard
                DateInputMode.TEXT -> R.drawable.ic_wheel
            }
        )

        wheel.textDateModeHint.setText(
            when (mode) {
                DateInputMode.WHEEL -> R.string.expiry_date_wheel_hint
                DateInputMode.CALENDAR -> R.string.expiry_date_calendar_hint
                DateInputMode.TEXT -> R.string.expiry_date_text_hint
            }
        )

        expiryMillis?.let { millis ->
            wheel.textSelectedExpiryDate.text = dateFormat.format(Date(millis))
            if (mode == DateInputMode.TEXT) {
                wheel.editTextExpiryDate.setText(dateFormat.format(Date(millis)))
            }
        }
    }

    /**
     * FUNCTIONALITY: Displays the system DatePickerDialog for calendar-based selection.
     * USE OF DATA: Extracts Long 'expiryMillis' and converts to Year/Month/Day for the dialog.
     * USE OF CODE STRUCTURES: Implementation of 'OnDateSetListener' callback to commit the chosen date.
     */
    private fun showCalendarPicker() {
        val cal = Calendar.getInstance()
        expiryMillis?.let { cal.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                // USE OF DATA: Constructing a precise timestamp at the end of the day
                val calendar = Calendar.getInstance().apply {
                    set(y, m, d, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                setExpiryFromMillis(calendar.timeInMillis)
            },
            cal[Calendar.YEAR],
            cal[Calendar.MONTH],
            cal[Calendar.DAY_OF_MONTH]
        ).show()
    }

    /**
     * FUNCTIONALITY: Validates and parses a raw string date manually typed by the user.
     * USE OF DATA: Ingests raw 'String', attempts 'dateFormat.parse()', and updates 'expiryMillis' (Long).
     * USE OF CODE STRUCTURES: Uses 'try/catch' for error handling during parsing 
     * and 'if' selection for error visibility toggle.
     */
    private fun parseTextExpiryDate(raw: String) {
        val wheel = binding.expiryDateWheel
        if (raw.isBlank()) {
            expiryMillis = null
            wheel.textSelectedExpiryDate.text = getString(R.string.expiry_date_not_set)
            return
        }
        try {
            // CODE STRUCTURE: Attempting to convert string data to numeric timestamp
            val parsed = dateFormat.parse(raw.trim())
            if (parsed != null) {
                setExpiryFromMillis(parsed.time)
                wheel.textExpiryDateError.visibility = View.GONE
            }
        } catch (_: ParseException) {
            // CODE STRUCTURE: Error handling path for malformed date strings
            wheel.textExpiryDateError.text = getString(R.string.expiry_date_invalid)
            wheel.textExpiryDateError.visibility = View.VISIBLE
        }
    }

    private fun setExpiryFromMillis(millis: Long) {
        expiryMillis = millis
        binding.expiryDateWheel.textSelectedExpiryDate.text = dateFormat.format(Date(millis))
        binding.expiryDateWheel.textExpiryDateError.visibility = View.GONE
        setPickersFromMillis(millis)
        
        isInternalUpdate = true
        binding.expiryDateWheel.editTextExpiryDate.setText(dateFormat.format(Date(millis)))
        isInternalUpdate = false
    }

    private fun updateDayPickerMax(pickerDay: NumberPicker, month: Int, year: Int) {
        val maxDay = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)

        val previousDay = pickerDay.value
        pickerDay.minValue = 1
        pickerDay.maxValue = maxDay
        pickerDay.value = if (previousDay in 1..maxDay) previousDay else maxDay
    }

    /**
     * FUNCTIONALITY: Reads values from the day/month/year NumberPickers and computes 
     * the final millisecond timestamp.
     * USE OF DATA: Consolidates values from 3 Int pickers into a single 'Long'.
     * USE OF CODE STRUCTURES: Sequential 'Calendar.set' operations to standardize 
     * the time to the end of the day.
     */
    private fun syncExpiryFromPickers() {
        val wheel = binding.expiryDateWheel
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, wheel.pickerYear.value)
            set(Calendar.MONTH, wheel.pickerMonth.value)
            set(Calendar.DAY_OF_MONTH, wheel.pickerDay.value)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        expiryMillis = calendar.timeInMillis
        wheel.textSelectedExpiryDate.text = dateFormat.format(calendar.time)
        wheel.textExpiryDateError.visibility = View.GONE
    }

    private fun setPickersFromMillis(millis: Long) {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val wheel = binding.expiryDateWheel

        isInternalUpdate = true
        wheel.pickerYear.value = calendar[Calendar.YEAR]
        wheel.pickerMonth.value = calendar[Calendar.MONTH]
        updateDayPickerMax(wheel.pickerDay, wheel.pickerMonth.value + 1, wheel.pickerYear.value)
        wheel.pickerDay.value = calendar[Calendar.DAY_OF_MONTH]
        isInternalUpdate = false
    }

    private fun loadProductData() {
        editingProduct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("product", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("product")
        }

        selectedImageUri = intent.getStringExtra("imageUri") ?: editingProduct?.imageUri
        productBarcode = intent.getStringExtra("barcode") ?: editingProduct?.barcode

        editingProduct?.let { product ->
            binding.editTextProductName.setText(product.name)
            binding.editTextBrand.setText(product.brand)
            product.expirationDate?.let { setExpiryFromMillis(it) }
            binding.editTextQuantity.setText(product.quantity.toString())
            binding.editTextWeight.setText(product.weight?.toString() ?: "")
            binding.checkboxFavorite.isChecked = product.isFavorite
            selectedWeightUnit = product.weightUnit
            binding.spinnerWeightUnit.setText(product.weightUnit, false)
        }

        updateToolbarTitle()

        val barcode = productBarcode
        if (!barcode.isNullOrBlank()) {
            binding.textViewBarcodeValue.text = getString(R.string.barcode_with_value, barcode)
            binding.textViewBarcodeValue.visibility = View.VISIBLE
        } else {
            binding.textViewBarcodeValue.visibility = View.GONE
        }

        val imageUri = selectedImageUri
        if (!imageUri.isNullOrBlank()) {
            Glide.with(this).load(imageUri.toUri())
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .into(binding.imageProductPreview)
        } else {
            binding.imageProductPreview.setImageResource(R.drawable.ic_placeholder)
        }
    }

    private fun setupListeners() {
        binding.btnSaveProduct.setOnClickListener { saveProduct() }
        binding.imageProductPreview.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }

        binding.btnPlusQuantity.setOnClickListener {
            val currentQty = binding.editTextQuantity.text.toString().toIntOrNull() ?: 1
            if (currentQty < 999) {
                binding.editTextQuantity.setText((currentQty + 1).toString())
            }
        }

        binding.btnMinusQuantity.setOnClickListener {
            val currentQty = binding.editTextQuantity.text.toString().toIntOrNull() ?: 1
            if (currentQty > 1) {
                binding.editTextQuantity.setText((currentQty - 1).toString())
            }
        }

        binding.editTextQuantity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.editTextQuantity.text.toString().toIntOrNull()
                if (value != null) {
                    if (value < 1) binding.editTextQuantity.setText("1")
                    else if (value > 999) binding.editTextQuantity.setText("999")
                } else if (binding.editTextQuantity.text.isNullOrBlank()) {
                    binding.editTextQuantity.setText("1")
                }
            }
        }

        binding.editTextWeight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.editTextWeight.text.toString().toIntOrNull()
                if (value != null) {
                    if (value < 1) binding.editTextWeight.setText("1")
                    else if (value > 999999) binding.editTextWeight.setText("999999")
                }
            }
        }

        binding.imageProductPreview.setOnLongClickListener {
            selectedImageUri = null
            binding.imageProductPreview.setImageResource(R.drawable.ic_placeholder)
            Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun resetForm() {
        // Clear text fields
        binding.editTextProductName.text?.clear()
        binding.editTextBrand.text?.clear()
        binding.editTextQuantity.text?.clear()
        binding.editTextWeight.text?.clear()
        binding.checkboxFavorite.isChecked = false
        
        // Reset expiry
        expiryMillis = null
        binding.expiryDateWheel.textSelectedExpiryDate.text = getString(R.string.expiry_date_not_set)
        binding.expiryDateWheel.editTextExpiryDate.text?.clear()
        val today = Calendar.getInstance()
        setPickersFromMillis(today.timeInMillis)
        
        // Clear image
        selectedImageUri = null
        binding.imageProductPreview.setImageResource(R.drawable.ic_placeholder)
        
        // Clear barcode
        productBarcode = null
        binding.textViewBarcodeValue.text = ""
        binding.textViewBarcodeValue.visibility = View.GONE

        // Clear product state
        editingProduct = null
        
        // Reset errors
        binding.layoutProductName.error = null
        binding.layoutBrand.error = null
        binding.layoutQuantity.error = null
        binding.layoutWeight.error = null
        binding.expiryDateWheel.layoutExpiryText.error = null
        binding.expiryDateWheel.textExpiryDateError.visibility = View.GONE
        
        // Update title (will revert to "Add Product")
        updateToolbarTitle()
        
        Toast.makeText(this, "All fields cleared", Toast.LENGTH_SHORT).show()
    }

    /**
     * FUNCTIONALITY: Validates all user input fields and commits a new or modified 
     * Product to the database.
     * USE OF DATA: Reads raw String inputs from EditTexts, parses 'Int' quantities/weights, 
     * and 'Long' timestamps. Instantiates a 'Product' object.
     * USE OF CODE STRUCTURES: Employs sequential 'if' selection blocks for existence checks, 
     * range validation (1 to 999), and unreasonable date filtering.
     */
    private fun saveProduct() {
        // Reset errors
        binding.layoutProductName.error = null
        binding.layoutQuantity.error = null
        binding.layoutWeight.error = null
        binding.layoutBrand.error = null
        binding.expiryDateWheel.layoutExpiryText.error = null
        binding.expiryDateWheel.textExpiryDateError.visibility = View.GONE

        // 1. Trim strings (strip leading/trailing whitespace)
        val name = binding.editTextProductName.text.toString().trim()
        val brand = binding.editTextBrand.text.toString().trim().takeIf { it.isNotBlank() }

        // CODE STRUCTURE: Existence check for mandatory product name field
        if (name.isBlank()) {
            binding.layoutProductName.error = "Product name is required"
            binding.manualEntryScroll.smoothScrollTo(0, binding.layoutProductName.top)
            return
        }
        if (name.length > 100) {
            binding.layoutProductName.error = "Name must be 100 characters or less"
            binding.manualEntryScroll.smoothScrollTo(0, binding.layoutProductName.top)
            return
        }

        // Parse date if in text mode
        if (dateInputMode == DateInputMode.TEXT) {
            parseTextExpiryDate(binding.expiryDateWheel.editTextExpiryDate.text?.toString().orEmpty())
        }

        // CODE STRUCTURE: Validation check for mandatory expiry date
        val finalExpiryMillis = expiryMillis
        if (finalExpiryMillis == null) {
            if (dateInputMode == DateInputMode.TEXT) {
                binding.expiryDateWheel.layoutExpiryText.error = getString(R.string.expiry_date_invalid)
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            } else {
                binding.expiryDateWheel.textExpiryDateError.text = getString(R.string.error_expiry_date_required)
                binding.expiryDateWheel.textExpiryDateError.visibility = View.VISIBLE
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            }
            return
        }

        // USE OF CODE STRUCTURES: Logic check to prevent invalid or extreme expiration dates
        val currentMillis = System.currentTimeMillis()
        val oneYearAgoMillis = currentMillis - (365L * 24 * 60 * 60 * 1000)
        val tenYearsFutureMillis = currentMillis + (10L * 365 * 24 * 60 * 60 * 1000)
        
        if (finalExpiryMillis < oneYearAgoMillis) {
            val errorMsg = "Date cannot be more than 1 year in the past"
            if (dateInputMode == DateInputMode.TEXT) {
                binding.expiryDateWheel.layoutExpiryText.error = errorMsg
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            } else {
                binding.expiryDateWheel.textExpiryDateError.text = errorMsg
                binding.expiryDateWheel.textExpiryDateError.visibility = View.VISIBLE
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            }
            return
        }

        if (finalExpiryMillis > tenYearsFutureMillis) {
            val errorMsg = "Date cannot be more than 10 years in the future"
            if (dateInputMode == DateInputMode.TEXT) {
                binding.expiryDateWheel.layoutExpiryText.error = errorMsg
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            } else {
                binding.expiryDateWheel.textExpiryDateError.text = errorMsg
                binding.expiryDateWheel.textExpiryDateError.visibility = View.VISIBLE
                binding.manualEntryScroll.smoothScrollTo(0, binding.expiryDateWheel.root.top)
            }
            return
        }

        // CODE STRUCTURE: Range check for quantity using conditional operators
        val qtyString = binding.editTextQuantity.text.toString().trim()
        var qtyInt = qtyString.toIntOrNull() ?: 1
        if (qtyInt < 1) qtyInt = 1
        if (qtyInt > 999) {
            binding.layoutQuantity.error = "Quantity must be 999 or less"
            binding.manualEntryScroll.smoothScrollTo(0, binding.layoutQuantity.top)
            return
        }

        // CODE STRUCTURE: Range check for numeric weight data
        val weightString = binding.editTextWeight.text.toString().trim()
        val parsedWeight = weightString.toIntOrNull()
        val finalWeight: Int?
        if (weightString.isNotBlank()) {
            if (parsedWeight == null || parsedWeight < 1 || parsedWeight > 999999) {
                binding.layoutWeight.error = "Weight must be between 1 and 999,999"
                binding.manualEntryScroll.smoothScrollTo(0, binding.layoutWeight.top)
                return
            }
            finalWeight = parsedWeight
        } else {
            finalWeight = null
        }

        // 5. Instantiate Product() & Commit to SQLite Room DB.
        val currentTime = System.currentTimeMillis()
        val isEditing = editingProduct != null && editingProduct!!.id != 0

        val product = Product(
            id = editingProduct?.id ?: 0,
            uuid = editingProduct?.uuid ?: UUID.randomUUID().toString(),
            name = name,
            brand = brand,
            expirationDate = finalExpiryMillis,
            quantity = qtyInt,
            weight = finalWeight,
            weightUnit = selectedWeightUnit,
            imageUri = selectedImageUri,
            isFavorite = binding.checkboxFavorite.isChecked,
            barcode = productBarcode,
            dateAdded = editingProduct?.dateAdded ?: currentTime,
            dateModified = if (isEditing) currentTime else null
        )

        // USE OF CODE STRUCTURES: Selection structure to determine between Insert or Update operation
        if (isEditing) {
            productViewModel.update(product)
            editingProduct?.let { NotificationScheduler.cancelForProduct(this, it) }
            NotificationScheduler.scheduleForProduct(this, product)
            Toast.makeText(this, "Product updated", Toast.LENGTH_SHORT).show()
        } else {
            productViewModel.insert(product)
            NotificationScheduler.scheduleForProduct(this, product)
            Toast.makeText(this, "Product saved", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}