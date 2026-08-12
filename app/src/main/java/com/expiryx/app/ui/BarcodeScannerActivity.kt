package com.expiryx.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FUNCTIONALITY: Provides a real-time camera interface for scanning product barcodes 
 * and automatically fetching product metadata from the OpenFoodFacts API.
 * USE OF DATA: Utilizes CameraX 'PreviewView', ML Kit 'Barcode' objects, and JSON 
 * responses from external network calls.
 * USE OF CODE STRUCTURES: Implements CameraX lifecycle binding, ML Kit analysis 
 * callbacks, and OkHttp network requests within an IO-bound coroutine.
 */
@ExperimentalGetImage
class BarcodeScannerActivity : ThemedAppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var progressBar: ProgressBar
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private var isFlashlightOn = false
    private var analysis: ImageAnalysis? = null
    private val handled = AtomicBoolean(false) // Prevents multiple scans of the same barcode
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            android.util.Log.d("ExpiryX_Debug", "[TC-10] Camera permission GRANTED")
            startCamera()
        } else {
            android.util.Log.e("ExpiryX_Debug", "[TC-10] Camera permission DENIED")
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Camera Permission Required")
                .setMessage("This app requires camera access to scan barcodes. You can enter product details manually instead.")
                .setPositiveButton("Open Manual Entry") { _, _ ->
                    startActivity(Intent(this, ManualEntryActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)
        setContentView(R.layout.activity_barcode_scanner)

        // Force camera theme attributes after ThemedAppCompatActivity might have reset them
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val root = findViewById<View>(R.id.cameraRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        previewView = findViewById(R.id.previewView)
        progressBar = findViewById(R.id.progressBarScan)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<View>(R.id.btnToggleFlashlight).setOnClickListener {
            toggleFlashlight()
        }

        findViewById<View>(R.id.btnClose).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnManualEntry).setOnClickListener {
            startActivity(Intent(this, ManualEntryActivity::class.java))
            finish()
        }

        startScanAnimation()

        // CODE STRUCTURE: Permission selection check before starting camera hardware
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanAnimation() {
        val scanLine = findViewById<View>(R.id.scanLine)
        val animation = android.view.animation.TranslateAnimation(
            0f, 0f,
            0f, 280f * resources.displayMetrics.density
        ).apply {
            duration = 2000
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
            interpolator = android.view.animation.LinearInterpolator()
        }
        scanLine.startAnimation(animation)
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /**
     * FUNCTIONALITY: Configures and starts the CameraX preview and image analysis pipelines.
     * USE OF DATA: Manages 'ProcessCameraProvider' and 'ImageAnalysis' use cases.
     * USE OF CODE STRUCTURES: Uses a listener for the camera provider future and 
     * sets an analyzer callback that processes frames via ML Kit.
     */
    @ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                // USE OF CODE STRUCTURES: Frame-by-frame analysis callback for barcode detection
                analysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                    // CODE STRUCTURE: Atomic check to ignore frames if a barcode is already being processed
                    if (handled.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image ?: run {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    // CODE STRUCTURE: ML Kit async success/failure listeners for barcode results
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val candidate = barcodes.firstOrNull()?.rawValue

                            if (candidate != null && handled.compareAndSet(false, true)) {
                                Log.d("ExpiryX_Debug", "[BarcodeScan] Barcode detected: $candidate")
                                runOnUiThread {
                                    setLoading(true)
                                    Toast.makeText(this, "Barcode detected: $candidate", Toast.LENGTH_SHORT).show()
                                }
                                fetchProductInfo(candidate)
                            }
                            imageProxy.close()
                        }
                        .addOnFailureListener { e ->
                            Log.e("BarcodeScanner", "Scan failed", e)
                            imageProxy.close()
                        }
                }

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * FUNCTIONALITY: Toggles the device's camera flash (torch) on or off.
     * USE OF DATA: Accesses 'cameraControl' and updates 'isFlashlightOn' (Boolean).
     * USE OF CODE STRUCTURES: Selection logic to update UI button text based on state.
     */
    private fun toggleFlashlight() {
        camera?.let {
            isFlashlightOn = !isFlashlightOn
            it.cameraControl.enableTorch(isFlashlightOn)
            
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleFlashlight).apply {
                text = if (isFlashlightOn) "Flash OFF" else "Flash ON"
                alpha = if (isFlashlightOn) 1.0f else 0.8f
            }
        }
    }

    /**
     * FUNCTIONALITY: Queries the OpenFoodFacts API to retrieve detailed product information.
     * USE OF DATA: Ingests 'barcode' (String). Parses a JSON response into a 'Product' object.
     * USE OF CODE STRUCTURES: Executes network I/O within 'ioScope' (Dispatchers.IO) 
     * and uses 'withContext(Dispatchers.Main)' to return to the UI thread with results.
     */
    private fun fetchProductInfo(barcode: String) {
        Log.d("ExpiryX_Debug", "[BarcodeScan] Raw string ingested: '$barcode'")

        if (barcode.isBlank()) {
            Log.e("ExpiryX_Debug", "[BarcodeScan] Error: Scanned barcode string is empty")
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            .build()

        // USE OF CODE STRUCTURES: Coroutine block for non-blocking network communication
        ioScope.launch {
            Log.d("ExpiryX_Debug", "[OFF_API] Initiating lookup on IO Thread: ${Thread.currentThread().name}")
            try {
                var responseCode = -1
                val body = client.newCall(request).execute().use { resp ->
                    responseCode = resp.code
                    if (!resp.isSuccessful) {
                        Log.e("ExpiryX_Debug", "[OFF_API] HTTP Error: $responseCode")
                        null
                    } else resp.body?.string()
                }

                if (body != null) {
                    val json = JSONObject(body)
                    val status = json.optInt("status", 0)
                    Log.d("ExpiryX_Debug", "[OFF_API] Response body received. Status: $status")

                    if (status == 1) {
                        val productJson = json.getJSONObject("product")
                        val name = productJson.optString("product_name", "").trim()
                        Log.d("ExpiryX_Debug", "[OFF_API] Product found: '$name'")

                        val brand = productJson.optString("brands", "").takeIf { it.isNotBlank() }
                        val weightString = productJson.optString("quantity", "")
                        val imageUrl = productJson.optString("image_url", "").takeIf { it.isNotBlank() }

                        val weightUnit = when {
                            weightString.contains("ml", ignoreCase = true) -> "ml"
                            weightString.contains("g", ignoreCase = true) -> "g"
                            else -> "g"
                        }
                        
                        val product = Product(
                            id = 0,
                            name = if (name.isBlank()) "Unknown Product" else name,
                            expirationDate = null,
                            quantity = 1,
                            brand = brand,
                            weight = weightString.substringBefore(" ").trim().toIntOrNull(),
                            weightUnit = weightUnit,
                            imageUri = imageUrl,
                            isFavorite = false,
                            barcode = barcode,
                            dateAdded = System.currentTimeMillis(),
                            dateModified = null
                        )

                        withContext(Dispatchers.Main) {
                            Log.d("ExpiryX_Debug", "[OFF_API] Result received: success=true, name='${product.name}'")
                            setLoading(false)
                            val intent = Intent(this@BarcodeScannerActivity, ManualEntryActivity::class.java).apply {
                                putExtra("product", product)
                                putExtra("isEdit", false)
                                putExtra("barcode", barcode)
                            }
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        // Status is 0 (Not Found)
                        withContext(Dispatchers.Main) {
                            Log.w("ExpiryX_Debug", "[OFF_API] Product unmapped (Status 0). Falling back to manual entry.")
                            navigateToManualEntry(barcode)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setLoading(false)
                        if (responseCode == 404) {
                            Log.w("ExpiryX_Debug", "[OFF_API] Product not found (404). Falling back to manual entry.")
                            navigateToManualEntry(barcode)
                        } else {
                            Log.e("ExpiryX_Debug", "[OFF_API] Request failed with code: $responseCode")
                            Toast.makeText(this@BarcodeScannerActivity, "Server error ($responseCode). Try manual entry.", Toast.LENGTH_SHORT).show()
                            handled.set(false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ExpiryX_Debug", "[OFF_API] Exception during API call", e)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@BarcodeScannerActivity, "Connection error. Try manual entry.", Toast.LENGTH_SHORT).show()
                    handled.set(false)
                }
            }
        }
    }

    /**
     * Navigates to ManualEntryActivity with just the barcode when API lookup fails to find a product.
     */
    private fun navigateToManualEntry(barcode: String) {
        setLoading(false)
        Toast.makeText(this, "Product not found. Please enter details manually.", Toast.LENGTH_LONG).show()
        val intent = Intent(this, ManualEntryActivity::class.java).apply {
            putExtra("barcode", barcode)
            putExtra("isEdit", false)
        }
        startActivity(intent)
        finish()
    }

    /**
     * FUNCTIONALITY: Cleans up hardware resources and cancels pending background tasks.
     * USE OF DATA: Shuts down 'ExecutorService' and cancels 'ioScope'.
     * USE OF CODE STRUCTURES: Sequential teardown: unbind camera -> shutdown executor -> cancel coroutines.
     */
    override fun onDestroy() {
        super.onDestroy()
        try {
            analysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        cameraExecutor.shutdown()
        ioScope.cancel()
    }
}