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

/**
 * FUNCTIONALITY: Displays user account details, cloud synchronization status, 
 * and provides options for signing out or performing a full data wipe.
 * USE OF DATA: Accesses 'FirebaseUser' profile data (displayName, email, photoUrl) 
 * and 'Prefs' sync state Booleans. Uses 'ActivityAccountBinding' for layout interaction.
 * USE OF CODE STRUCTURES: Extends 'ThemedAppCompatActivity'; employs sequential 'if/else' 
 * selection for UI updates and coroutine blocks for destructive data operations.
 */
class AccountActivity : ThemedAppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
        setupBottomNav()
    }

    /**
     * FUNCTIONALITY: Populates the UI with the current user's profile information and sync status.
     * USE OF DATA: Reads 'AccountManager.getCurrentUser()' and 'Prefs.isSyncEnabled()'.
     * USE OF CODE STRUCTURES: Branching selection logic to handle logged-in vs. guest states 
     * and applying visual status colors based on Boolean flags.
     */
    private fun setupUI() {
        val user = AccountManager.getCurrentUser()
        // CODE STRUCTURE: Branching selection based on active user login state
        if (user != null) {
            binding.txtUserName.text = user.displayName ?: "User"
            binding.txtUserEmail.text = user.email
            
            // CODE STRUCTURE: Conditional loading of user profile picture via Glide
            if (user.photoUrl != null) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .circleCrop()
                    .into(binding.imgProfile)
            }

            // CODE STRUCTURE: UI status selection for cloud sync indicator
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

    override fun onResume() {
        super.onResume()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, binding.bottomNav.bottomNavigationView, R.id.nav_settings)
    }

    /**
     * FUNCTIONALITY: Configures interaction listeners for navigation, sign out, 
     * and data management buttons.
     * USE OF DATA: Consumes 'MaterialAlertDialogBuilder' results and 'AccountManager' callbacks.
     * USE OF CODE STRUCTURES: Lambda execution blocks for dialog confirmation and 
     * subsequent navigation routing.
     */
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSignOut.setOnClickListener {
            // CODE STRUCTURE: Dialog selection structure for safety-confirming sign out action
            MaterialAlertDialogBuilder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    AccountManager.signOut(this) {
                        // CODE STRUCTURE: Navigation callback redirecting to Settings as a guest
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

    /**
     * FUNCTIONALITY: Orchestrates a global removal of all user data from both local 
     * and remote sources.
     * USE OF DATA: Interacts with 'ProductRepository' and 'AccountManager'.
     * USE OF CODE STRUCTURES: Sequential coroutine execution: delete cloud -> delete local 
     * products -> delete local history -> main thread UI update.
     */
    private fun performFullDataWipe() {
        val repo = (application as ProductApplication).repository
        // CODE STRUCTURE: Callback chain for multi-source data erasure
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