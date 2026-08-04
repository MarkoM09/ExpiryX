package com.expiryx.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * FUNCTIONALITY: Provides a bottom sheet interface for users to choose how they want 
 * to add a product (Manual, Camera Scan, or Image Upload).
 * USE OF DATA: Manages 'Uri' objects for uploaded images and interacts with ML Kit 
 * for barcode detection. Returns results via starting new Activities.
 * USE OF CODE STRUCTURES: Inherits from 'ThemedBottomSheetDialogFragment', 
 * implements an Image Picker callback, and uses coroutines for network-bound data fetching.
 */
class AddProductBottomSheet : ThemedBottomSheetDialogFragment() {

    private var progressBar: ProgressBar? = null

    /**
     * FUNCTIONALITY: Callback listener that triggers when a user selects an image from the device.
     * USE OF DATA: Ingests a nullable 'Uri' representing the picked file.
     * USE OF CODE STRUCTURES: Uses 'try/catch' to handle permission acquisition 
     * and calls 'analyseImageForBarcode' if the URI is valid.
     */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        try {
            // CODE STRUCTURE: Attempting to persist read permission for the selected image
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}
        analyseImageForBarcode(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_add_product, container, false)

        val optionManual: View = view.findViewById(R.id.optionManual)
        val optionCamera: View = view.findViewById(R.id.optionCamera)
        val optionUpload: View = view.findViewById(R.id.optionUpload)
        val btnClose: View = view.findViewById(R.id.btnCloseSheet)
        progressBar = view.findViewById(R.id.progressBarUpload)

        // USE OF CODE STRUCTURES: Lambda listeners to navigate to different input modes
        optionManual.setOnClickListener {
            startActivity(Intent(requireContext(), ManualEntryActivity::class.java).apply {
                putExtra("isEdit", false)
            })
            dismissAllowingStateLoss()
        }
        optionCamera.setOnClickListener {
            startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
            dismissAllowingStateLoss()
        }
        optionUpload.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }
        btnClose.setOnClickListener { dismissAllowingStateLoss() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.setupBottomSheetEdgeToEdge(this, view.findViewById(R.id.addProductBottomRoot))
        // CODE STRUCTURE: Auto-processing if an image was passed in during instantiation
        arguments?.getParcelable<Uri>(ARG_INITIAL_IMAGE_URI)?.let { uri ->
            analyseImageForBarcode(uri)
        }
    }

    /**
     * FUNCTIONALITY: Toggles the visibility of the loading indicator and locks the UI.
     * USE OF DATA: Updates 'progressBar' visibility and 'isCancelable' (Boolean).
     */
    private fun showLoading(show: Boolean) {
        progressBar?.isVisible = show
        isCancelable = !show
    }

    /**
     * FUNCTIONALITY: Processes a static image file to find EAN or UPC barcodes using ML Kit.
     * USE OF DATA: Ingests an image 'Uri' and converts it to an 'InputImage'.
     * USE OF CODE STRUCTURES: Configures 'BarcodeScannerOptions' and uses async 
     * success/failure listeners for result handling.
     */
    private fun analyseImageForBarcode(uri: Uri) {
        showLoading(true)
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E
                ).build()
            val scanner = BarcodeScanning.getClient(options)

            // CODE STRUCTURE: Async processing of image data via ML Kit library
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val code = barcodes.firstOrNull()?.rawValue
                    // CODE STRUCTURE: Selection path depending on whether a code was detected
                    if (!code.isNullOrBlank()) fetchProductInfo(code, uri)
                    else {
                        showLoading(false)
                        Toast.makeText(requireContext(), "No barcode found in image", Toast.LENGTH_SHORT).show()
                        dismissAllowingStateLoss()
                    }
                }
                .addOnFailureListener {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Failed to analyse image", Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                }
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(requireContext(), "Error reading image", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
        }
    }

    /**
     * FUNCTIONALITY: Retrieves product metadata from an external API after a successful scan.
     * USE OF DATA: Ingests 'barcode' (String) and 'uploadedImage' (Uri). Returns a 'Product' object.
     * USE OF CODE STRUCTURES: Launches a coroutine in 'lifecycleScope' using 
     * 'withContext(Dispatchers.IO)' for networking and switches to Main for UI updates.
     */
    private fun fetchProductInfo(barcode: String, uploadedImage: Uri) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            .build()

        // USE OF CODE STRUCTURES: Coroutine block to handle API communication asynchronously
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) null else response.body?.string()
                }

                if (body == null) {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Product not found", Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                    return@launch
                }

                val json = JSONObject(body)
                // CODE STRUCTURE: Branching logic based on API success status
                if (json.optInt("status") == 1) {
                    val prod = json.getJSONObject("product")
                    val name = prod.optString("product_name", "").trim()
                    val apiImage = prod.optString("image_url", null)

                    val weightString = prod.optString("quantity", "")
                    val weightUnit = when {
                        weightString.contains("ml", ignoreCase = true) -> "ml"
                        weightString.contains("g", ignoreCase = true) -> "g"
                        else -> "g"
                    }

                    val product = Product(
                        id = 0,
                        name = name,
                        expirationDate = null,
                        quantity = 1,
                        brand = prod.optString("brands", "").takeIf { it.isNotBlank() },
                        weight = weightString.substringBefore(" ").trim().toIntOrNull(),
                        weightUnit = weightUnit,
                        imageUri = apiImage ?: uploadedImage.toString(),
                        isFavorite = false,
                        barcode = barcode,
                        dateAdded = System.currentTimeMillis(),
                        dateModified = null
                    )

                    showLoading(false)
                    startActivity(Intent(requireContext(), ManualEntryActivity::class.java).apply {
                        putExtra("product", product)
                        putExtra("isEdit", false)
                        putExtra("barcode", barcode)
                    })
                    dismissAllowingStateLoss()
                } else {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Product not found", Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(requireContext(), "Error fetching product info", Toast.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {
        private const val ARG_INITIAL_IMAGE_URI = "initial_image_uri"

        /**
         * FUNCTIONALITY: Static creator for the bottom sheet with optional initial data.
         * USE OF DATA: Accepts an optional 'initialImageUri'.
         */
        fun newInstance(initialImageUri: Uri? = null): AddProductBottomSheet {
            val fragment = AddProductBottomSheet()
            initialImageUri?.let {
                val args = Bundle()
                args.putParcelable(ARG_INITIAL_IMAGE_URI, it)
                fragment.arguments = args
            }
            return fragment
        }
    }
}