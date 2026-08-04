package com.expiryx.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.expiryx.app.R

/**
 * FUNCTIONALITY: Central controller responsible for applying and persisting system-wide 
 * Day/Night modes and user-selected accent theme configurations.
 * USE OF DATA: Reads and writes Theme Mode 'Int' identifiers and Accent Color resource 
 * 'Int' keys to SharedPreferences. Manages a 'List' of 'AccentOption' objects.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton for shared state access, 
 * 'when' selection for Night Mode delegation, and functional 'firstOrNull' searches 
 * for theme mapping.
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent_theme"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val ACCENT_AQUA = 0
    const val ACCENT_CORAL = 1
    const val ACCENT_SKY = 2
    const val ACCENT_VIOLET = 3
    const val ACCENT_FOREST = 4
    const val ACCENT_ROSE = 5

    /**
     * FUNCTIONALITY: Encapsulates the metadata for a specific accent theme variant.
     * USE OF DATA: Stores 'id' (Int), 'label' (String), 'themeRes' (StyleRes Int), 
     * and a 'previewColorRes' (Int).
     */
    data class AccentOption(
        val id: Int,
        val label: String,
        @StyleRes val themeRes: Int,
        val previewColorRes: Int,
    )

    val accentOptions: List<AccentOption> = listOf(
        AccentOption(ACCENT_AQUA, "Lighter Aqua", R.style.Theme_ExpiryX_Aqua, R.color.teal_200),
        AccentOption(ACCENT_CORAL, "Coral", R.style.Theme_ExpiryX_Coral, R.color.coral_primary),
        AccentOption(ACCENT_SKY, "Sky Blue", R.style.Theme_ExpiryX_Sky, R.color.sky_primary),
        AccentOption(ACCENT_VIOLET, "Violet", R.style.Theme_ExpiryX_Violet, R.color.violet_primary),
        AccentOption(ACCENT_FOREST, "Forest Green", R.style.Theme_ExpiryX_Forest, R.color.forest_primary),
        AccentOption(ACCENT_ROSE, "Rose Pink", R.style.Theme_ExpiryX_Rose, R.color.rose_primary),
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    /**
     * FUNCTIONALITY: Updates the global theme mode and applies it immediately to the OS delegate.
     * USE OF DATA: Ingests 'themeMode' (Int).
     * USE OF CODE STRUCTURES: Sequential logic: write to prefs -> trigger OS UI update.
     */
    fun setThemeMode(context: Context, themeMode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, themeMode).apply()
        applyNightMode(themeMode)
    }

    fun getAccentTheme(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, ACCENT_AQUA)

    fun setAccentTheme(context: Context, accent: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, accent).apply()
    }

    fun getAccentLabel(context: Context): String {
        val accent = getAccentTheme(context)
        // CODE STRUCTURE: Functional search for the matching accent label
        return accentOptions.firstOrNull { it.id == accent }?.label ?: accentOptions.first().label
    }

    /**
     * FUNCTIONALITY: Maps the user's accent preference to its corresponding XML Style resource.
     * USE OF DATA: Returns a '@StyleRes Int'.
     */
    @StyleRes
    fun getAccentThemeRes(context: Context): Int {
        return accentOptions.firstOrNull { it.id == getAccentTheme(context) }?.themeRes
            ?: R.style.Theme_ExpiryX_Aqua
    }

    /**
     * FUNCTIONALITY: Injects the active accent and accessibility styles into an activity context.
     * USE OF DATA: Consumes 'Activity' and resolved 'themeRes' (Int).
     * USE OF CODE STRUCTURES: Selection structure (if) to conditionally apply high-contrast 
     * overlays on top of the primary theme.
     */
    fun applyActivityTheme(activity: Activity) {
        activity.setTheme(getAccentThemeRes(activity))
        // CODE STRUCTURE: Accessibility style injection based on user preference
        if (Prefs.isHighContrastEnabled(activity)) {
            activity.theme.applyStyle(R.style.ThemeOverlay_ExpiryX_HighContrast, true)
        }
    }

    /**
     * FUNCTIONALITY: Communicates theme changes to the Android System's AppCompatDelegate.
     * USE OF DATA: Accepts 'themeMode' (Int).
     * USE OF CODE STRUCTURES: 'when' selection structure mapping internal mode constants 
     * to system 'MODE_NIGHT' flags.
     */
    fun applyNightMode(themeMode: Int) {
        // CODE STRUCTURE: Branching selection based on user mode preference
        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun initializeTheme(context: Context) {
        applyNightMode(getThemeMode(context))
    }

    /**
     * FUNCTIONALITY: Determines if the UI is currently rendering in a dark state.
     * USE OF DATA: Returns 'Boolean'.
     * USE OF CODE STRUCTURES: Compound 'when' selection evaluating both user 
     * preferences and system configuration bitmasks.
     */
    fun isDarkMode(context: Context): Boolean {
        return when (getThemeMode(context)) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> {
                // CODE STRUCTURE: Logic check using bitwise operations to detect OS-level dark mode
                val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}