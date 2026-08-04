package com.expiryx.app

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FUNCTIONALITY: Base bottom sheet dialog class that dynamically applies the application's 
 * active accent theme to all layouts inflated within the dialog.
 * USE OF DATA: Binds dialog theme resource Int identifiers and wraps 'Context' with 'accentTheme' (Int).
 * USE OF CODE STRUCTURES: Extends 'BottomSheetDialogFragment', overrides 'getTheme' for 
 * overlay styling, and uses 'ContextThemeWrapper' to inject custom styles into the 
 * layout inflation process.
 */
abstract class ThemedBottomSheetDialogFragment : BottomSheetDialogFragment() {

    /**
     * FUNCTIONALITY: Returns the primary overlay theme for the bottom sheet.
     * USE OF DATA: Returns a hardcoded Style resource 'Int'.
     */
    override fun getTheme(): Int {
        // DATA: specialized BottomSheet overlay theme resource
        return R.style.ThemeOverlay_ExpiryX_BottomSheetDialog
    }

    /**
     * FUNCTIONALITY: Intercepts layout inflation to apply the current accent theme to 
     * all views within the sheet.
     * USE OF DATA: Reads 'accentTheme' (Int) from 'ThemeManager' and returns a 'themed' LayoutInflater.
     * USE OF CODE STRUCTURES: Uses 'cloneInContext' to propagate the 'ContextThemeWrapper' 
     * down the view hierarchy.
     */
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        
        // DATA: Retrieve the specific accent color resource currently chosen by the user
        val accentTheme = ThemeManager.getAccentThemeRes(requireContext())
        
        // CODE STRUCTURE: Context wrapping ensures that UI elements resolve accent colors correctly
        val themedContext = ContextThemeWrapper(requireContext(), accentTheme)

        return inflater.cloneInContext(themedContext)
    }
}