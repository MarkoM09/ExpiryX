package com.expiryx.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bumptech.glide.Glide
import com.expiryx.app.databinding.ActivityAccountBinding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val user = AccountManager.getCurrentUser()
        if (user != null) {
            binding.txtUserName.text = user.displayName ?: "User"
            binding.txtUserEmail.text = user.email
            
            if (user.photoUrl != null) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .circleCrop()
                    .into(binding.imgProfile)
            }

            if (Prefs.isSyncEnabled(this)) {
                binding.txtSyncStatus.text = "Active"
                binding.txtSyncStatus.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.green)
                )
            } else {
                binding.txtSyncStatus.text = "Disabled"
                binding.txtSyncStatus.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        R.color.gray
                    )
                )
            }
        } else {
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.navHomeWrapper.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            overridePendingTransition(0, 0)
            finish()
        }
        binding.navHistoryWrapper.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            overridePendingTransition(0, 0)
            finish()
        }
        binding.navStatsWrapper.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            overridePendingTransition(0, 0)
            finish()
        }
        binding.navSettingsWrapper.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            overridePendingTransition(0, 0)
            finish()
        }

        binding.btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    AccountManager.signOut(this) {
                        // Redirect back to Settings (management context) as a guest
                        val intent = Intent(this, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnForceSync.setOnClickListener {
            AccountManager.startSync(this)
            Toast.makeText(this, "Sync triggered", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteCloudData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Cloud Data")
                .setMessage("This will permanently remove all your products and history from the cloud. Local data will remain. Continue?")
                .setPositiveButton("Delete", { _, _ ->
                    AccountManager.deleteCloudData { success ->
                        val message = if (success) "Cloud data cleared" else "Failed to clear cloud data"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                })
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnDeleteAccount.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Account & Data")
                .setMessage("DANGER: This will permanently erase all your data from both this device and the cloud, including history and statistics. This cannot be undone. Are you sure?")
                .setPositiveButton("Erase Everything") { _, _ ->
                    performFullDataWipe()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun performFullDataWipe() {
        val repo = (application as ProductApplication).repository
        AccountManager.deleteCloudData { _ ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.clearAllProducts()
                repo.clearAllHistory()
                withContext(Dispatchers.Main) {
                    AccountManager.signOut(this@AccountActivity) {
                        Toast.makeText(this@AccountActivity, "Account and data wiped", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@AccountActivity, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }
}
