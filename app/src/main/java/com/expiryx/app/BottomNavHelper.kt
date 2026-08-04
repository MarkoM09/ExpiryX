package com.expiryx.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * FUNCTIONALITY: Utility helper that standardizes the configuration and behavior 
 * of the bottom navigation bar across multiple activities.
 * USE OF DATA: Takes Activity context, Navigation Bar View, and active tab Integer ID. 
 * Manages 'Intent' flags for efficient activity switching.
 * USE OF CODE STRUCTURES: Uses 'when' selection logic inside OnItemSelectedListener 
 * to handle tab navigation without duplicating boilerplate across activities.
 */
object BottomNavHelper {

    /**
     * FUNCTIONALITY: Binds selection listeners and highlights the active menu item for the current screen.
     * USE OF DATA: Ingests 'selectedItemId' (Int) to match against menu resource IDs.
     * USE OF CODE STRUCTURES: Employs 'when' selection for navigation routing and 'if' selection 
     * for same-activity guard clauses.
     */
    fun setup(
        activity: AppCompatActivity,
        bottomNav: BottomNavigationView,
        selectedItemId: Int,
    ) {
        bottomNav.itemIconSize = activity.resources.getDimensionPixelSize(R.dimen.bottom_nav_icon_size)
        
        // CODE STRUCTURE: Temporary removal of listener to prevent recursive triggers during initial setup
        bottomNav.setOnItemSelectedListener(null)
        bottomNav.selectedItemId = selectedItemId

        bottomNav.setOnItemSelectedListener { item ->
            // CODE STRUCTURE: Selection check to see if the user re-selected the current active tab
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            
            // CODE STRUCTURE: Branching logic determining the target activity class based on menu ID
            val targetClass = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_history -> HistoryActivity::class.java
                R.id.nav_stats -> StatsActivity::class.java
                R.id.nav_settings -> SettingsActivity::class.java
                else -> null
            }
            
            // CODE STRUCTURE: Final selection to trigger navigation if a valid target is identified
            if (targetClass != null && activity.javaClass != targetClass) {
                navigateTo(activity, targetClass)
                true
            } else {
                true
            }
        }
    }

    /**
     * FUNCTIONALITY: Executes the navigation intent with specific flags for task management.
     * USE OF DATA: Consumes 'activity' (AppCompatActivity) and 'target' (Class).
     * USE OF CODE STRUCTURES: Configures Intent using 'addFlags' to optimize activity stack usage.
     */
    private fun navigateTo(activity: AppCompatActivity, target: Class<*>) {
        val intent = Intent(activity, target)
        // DATA: FLAG_ACTIVITY_REORDER_TO_FRONT ensures the activity is reused rather than recreated
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(intent)
        // CODE STRUCTURE: Disabling default animations for an "instant" tab-switching feel
        activity.overridePendingTransition(0, 0)
    }
}