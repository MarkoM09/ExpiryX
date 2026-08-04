package com.expiryx.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * FUNCTIONALITY: Base activity class that automatically manages and applies the user's 
 * selected theme and accessibility settings before the UI is inflated.
 * USE OF DATA: Stores 'currentThemeRes' (Int) and 'Boolean' flags for accessibility modes 
 * to detect theme changes on activity restart.
 * USE OF CODE STRUCTURES: Extends 'AppCompatActivity', overrides 'onCreate' for initial 
 * theme injection, and uses 'if' selection in 'onRestart' to decide if 'recreate()' is needed.
 */
abstract class ThemedAppCompatActivity : AppCompatActivity() {

    private var currentThemeRes: Int = -1
    private var isHighContrastActive: Boolean = false
    private var isColorblindActive: Boolean = false

    /**
     * FUNCTIONALITY: Injects the active theme into the activity context.
     * USE OF DATA: Caches active settings in local variables for comparison.
     * USE OF CODE STRUCTURES: Calls 'ThemeManager.applyActivityTheme' prior to 'super.onCreate' 
     * to ensure the correct style is applied to the entire view hierarchy.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        currentThemeRes = ThemeManager.getAccentThemeRes(this)
        isHighContrastActive = Prefs.isHighContrastEnabled(this)
        isColorblindActive = Prefs.isColorblindModeEnabled(this)
        ThemeManager.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
    }

    /**
     * FUNCTIONALITY: Detects if the user changed theme settings while this activity was in the background.
     * USE OF DATA: Compares cached 'currentThemeRes' against latest values from 'ThemeManager' and 'Prefs'.
     * USE OF CODE STRUCTURES: Uses a compound 'if' selection structure to trigger an 
     * activity recreation if a visual setting mismatch is found.
     */
    override fun onRestart() {
        super.onRestart()
        val newTheme = ThemeManager.getAccentThemeRes(this)
        val newHC = Prefs.isHighContrastEnabled(this)
        val newCB = Prefs.isColorblindModeEnabled(this)
        // CODE STRUCTURE: Logical comparison to detect if the UI needs to refresh with new theme data
        if (newTheme != currentThemeRes || newHC != isHighContrastActive || newCB != isColorblindActive) {
            recreate()
        }
    }
}