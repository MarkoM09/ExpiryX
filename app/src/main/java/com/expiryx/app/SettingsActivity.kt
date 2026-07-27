package com.expiryx.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.expiryx.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importDataFromCsv(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableEdgeToEdge(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        binding.appVersionText.text = getString(R.string.version_format, getString(R.string.app_version_name))

        // Restore scroll position
        val scrollY = intent.getIntExtra("SCROLL_Y", 0)
        if (scrollY > 0) {
            binding.settingsScrollView.post {
                binding.settingsScrollView.scrollTo(0, scrollY)
            }
        }

        setupAccountSection()
        setupDarkModeToggle()
        setupAccentThemePicker()
        setupSyncToggle()
        setupAccessibilityToggles()

        binding.notificationsCard.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }

        binding.exportCard.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Export Data")
                .setMessage("Do you want to export your data to the Downloads folder as a CSV file?")
                .setPositiveButton("Yes") { _, _ -> exportDataToCsv() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.importCard.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Import Data")
                .setMessage("Select a CSV file to restore your products and history. This will merge with your current data.")
                .setPositiveButton("Select File") { _, _ -> importLauncher.launch(arrayOf("text/*", "application/octet-stream", "text/csv")) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.deleteDataCard.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Local Data")
                .setMessage("This will permanently erase all offline products, history, and statistics stored on this device. Cloud data remains safe. Continue?")
                .setPositiveButton("Reset") { _, _ -> deleteAllData() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupBottomNav()
        refreshAccentSubtitle()
    }

    private fun smartRecreate() {
        val scrollY = binding.settingsScrollView.scrollY
        val intent = intent
        intent.putExtra("SCROLL_Y", scrollY)
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            insets
        }
    }

    private fun refreshAccentSubtitle() {
        binding.accentThemeSubtitle.text = getString(
            R.string.accent_color_current,
            ThemeManager.getAccentLabel(this)
        )
        AccentThemePicker.bindInlineSwatch(this, binding.accentSwatchPreview) {
            smartRecreate()
        }
    }

    private fun setupAccentThemePicker() {
        binding.accentThemeCard.setOnClickListener {
            AccentThemePicker.show(this) { smartRecreate() }
        }
    }

    private fun setupAccountSection() {
        val user = AccountManager.getCurrentUser()
        if (user != null) {
            binding.userNameText.text = user.displayName ?: "User"
            binding.userEmailText.text = user.email
            binding.btnAccountAction.text = "Manage"

            if (user.photoUrl != null) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .circleCrop()
                    .into(binding.userProfileImage)
            }

            val openAccount = {
                startActivity(Intent(this, AccountActivity::class.java))
            }

            binding.btnAccountAction.setOnClickListener { openAccount() }
            binding.accountCard.setOnClickListener { openAccount() }
        } else {
            binding.userNameText.text = "Not signed in"
            binding.userEmailText.text = "Sign in to sync your data"
            binding.btnAccountAction.text = "Sign In"
            binding.userProfileImage.setImageResource(R.drawable.ic_google_logo)
            
            binding.btnAccountAction.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("force_login", true)
                startActivity(intent)
            }
            binding.accountCard.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("force_login", true)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, binding.bottomNav.bottomNavigationView, R.id.nav_settings)
    }

    private fun setupDarkModeToggle() {
        val currentMode = ThemeManager.getThemeMode(this)
        val toggle = binding.toggleGroupTheme
        
        when (currentMode) {
            ThemeManager.THEME_SYSTEM -> toggle.check(R.id.btnThemeSystem)
            ThemeManager.THEME_LIGHT -> toggle.check(R.id.btnThemeLight)
            ThemeManager.THEME_DARK -> toggle.check(R.id.btnThemeDark)
        }

        binding.txtThemeStatus.text = when (currentMode) {
            ThemeManager.THEME_SYSTEM -> "Follow System Theme"
            ThemeManager.THEME_LIGHT -> "Light Mode"
            ThemeManager.THEME_DARK -> "Dark Mode"
            else -> "Follow System Theme"
        }

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btnThemeSystem -> ThemeManager.THEME_SYSTEM
                    R.id.btnThemeLight -> ThemeManager.THEME_LIGHT
                    R.id.btnThemeDark -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                ThemeManager.setThemeMode(this, newMode)
                smartRecreate()
            }
        }
    }

    private fun setupSyncToggle() {
        binding.syncSwitch.isChecked = Prefs.isSyncEnabled(this)
        binding.syncSwitch.isEnabled = AccountManager.isLoggedIn()
        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setSyncEnabled(this, isChecked)
            if (AccountManager.isLoggedIn()) AccountManager.startSync(this)
        }
    }

    private fun setupAccessibilityToggles() {
        binding.switchHighContrast.isChecked = Prefs.isHighContrastEnabled(this)
        binding.switchHighContrast.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setHighContrastEnabled(this, isChecked)
            smartRecreate()
        }

        binding.switchColorblind.isChecked = Prefs.isColorblindModeEnabled(this)
        binding.switchColorblind.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setColorblindModeEnabled(this, isChecked)
            smartRecreate()
        }
    }

    private fun exportDataToCsv() {
        val repo = (application as ProductApplication).repository
        lifecycleScope.launch(Dispatchers.IO) {
            val products = repo.getAllProductsNow()
            val history = repo.getAllHistoryNow()

            val escape = { text: String? -> text?.replace("\"", "\"\"") ?: "" }
            val csvContent = buildString {
                appendLine("TYPE,NAME,EXPIRY,QTY,WEIGHT,UNIT,BRAND,FAV,IMAGE,ACTION,TIMESTAMP,BARCODE,DATE_ADDED,DATE_MODIFIED")
                for (p in products) {
                    appendLine("PRODUCT,\"${escape(p.name)}\",${p.expirationDate ?: ""},${p.quantity},${p.weight ?: ""},${p.weightUnit},\"${escape(p.brand)}\",${p.isFavorite},\"${escape(p.imageUri)}\",,,${p.barcode ?: ""},${p.dateAdded},${p.dateModified ?: ""}")
                }
                for (h in history) {
                    appendLine("HISTORY,\"${escape(h.productName)}\",${h.expirationDate ?: ""},${h.quantity},${h.weight ?: ""},${h.weightUnit},\"${escape(h.brand)}\",${h.isFavorite},\"${escape(h.imageUri)}\",${h.action},${h.timestamp},${h.barcode ?: ""},${h.dateAdded},${h.dateModified ?: ""}")
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "expiryx_backup_${System.currentTimeMillis()}.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                val uri = contentResolver.insert(collection, values)
                uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(csvContent.toByteArray()) } }
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Backup saved to Downloads", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun importDataFromCsv(uri: Uri) {
        val repo = (application as ProductApplication).repository
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                if (lines.isEmpty()) return@launch

                var productsImported = 0
                var historyImported = 0
                val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

                for (i in 1 until lines.size) {
                    val line = lines[i]
                    if (line.isBlank()) continue
                    val parts = line.split(regex).map { it.trim().removeSurrounding("\"") }
                    if (parts.size < 9) continue

                    val type = parts[0]
                    if (type == "PRODUCT") {
                        val product = Product(
                            name = parts[1],
                            expirationDate = parts[2].toLongOrNull(),
                            quantity = parts[3].toIntOrNull() ?: 1,
                            weight = parts[4].toIntOrNull(),
                            weightUnit = parts[5],
                            brand = parts[6].takeIf { it.isNotBlank() },
                            isFavorite = parts[7].toBoolean(),
                            imageUri = parts[8].takeIf { it.isNotBlank() },
                            barcode = parts.getOrNull(11)?.takeIf { it.isNotBlank() },
                            dateAdded = parts.getOrNull(12)?.toLongOrNull() ?: System.currentTimeMillis(),
                            dateModified = parts.getOrNull(13)?.toLongOrNull()
                        )
                        repo.insertProduct(product)
                        productsImported++
                    } else if (type == "HISTORY") {
                        val history = History(
                            productName = parts[1],
                            expirationDate = parts[2].toLongOrNull(),
                            quantity = parts[3].toIntOrNull() ?: 1,
                            weight = parts[4].toIntOrNull(),
                            weightUnit = parts[5],
                            brand = parts[6].takeIf { it.isNotBlank() },
                            isFavorite = parts[7].toBoolean(),
                            imageUri = parts[8].takeIf { it.isNotBlank() },
                            action = parts[9],
                            timestamp = parts[10].toLongOrNull() ?: System.currentTimeMillis(),
                            barcode = parts.getOrNull(11)?.takeIf { it.isNotBlank() },
                            dateAdded = parts.getOrNull(12)?.toLongOrNull() ?: System.currentTimeMillis(),
                            dateModified = parts.getOrNull(13)?.toLongOrNull()
                        )
                        repo.insertHistory(history)
                        historyImported++
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Imported $productsImported products and $historyImported history items", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("Settings", "Import failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Import failed: Check file format", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun deleteAllData() {
        val repo = (application as ProductApplication).repository
        lifecycleScope.launch(Dispatchers.IO) {
            repo.clearAllProducts()
            repo.clearAllHistory()
            withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Offline database wiped", Toast.LENGTH_SHORT).show() }
        }
    }
}
