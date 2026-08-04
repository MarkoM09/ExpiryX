package com.expiryx.app

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FUNCTIONALITY: Provides utility methods to handle "edge-to-edge" layouts by dynamically 
 * adjusting view padding and margins based on system bar insets (Status Bar, Navigation Bar).
 * USE OF DATA: Consumes 'WindowInsetsCompat' pixel data (Int) and 'Boolean' flags to 
 * determine which screen edges to protect.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton, 'WindowCompat' for system UI 
 * configuration, and 'setOnApplyWindowInsetsListener' lambda callbacks for reactive UI adjustment.
 */
object WindowInsetsHelper {

    /**
     * FUNCTIONALITY: Configures the activity window to allow content to be drawn under system bars.
     * USE OF DATA: Accepts 'AppCompatActivity' and updates its 'window' attributes.
     */
    fun enableEdgeToEdge(activity: AppCompatActivity) {
        // CODE STRUCTURE: Disabling default window fitting to allow custom inset management
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /**
     * FUNCTIONALITY: Attaches an inset listener to a view that updates its padding based on system bar sizes.
     * USE OF DATA: Consumes 'view' (View) and optional 'Boolean' flags for each direction. 
     * Caches 'initial' padding (Int).
     * USE OF CODE STRUCTURES: Implements a lambda listener that executes 'updatePadding' 
     * with 'if' selection logic to conditionally apply bar insets.
     */
    fun applyPadding(
        view: View,
        applyLeft: Boolean = true,
        applyTop: Boolean = true,
        applyRight: Boolean = true,
        applyBottom: Boolean = true,
    ) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // USE OF CODE STRUCTURES: Selection structure determining which direction to pad
            v.updatePadding(
                left = if (applyLeft) initialLeft + bars.left else initialLeft,
                top = if (applyTop) initialTop + bars.top else initialTop,
                right = if (applyRight) initialRight + bars.right else initialRight,
                bottom = if (applyBottom) initialBottom + bars.bottom else initialBottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * FUNCTIONALITY: Sets the bottom margin of a view to match the system navigation bar height plus a buffer.
     * USE OF DATA: Converts 'extraBottomDp' (Int) to pixels and reads 'bars.bottom' (Int).
     * USE OF CODE STRUCTURES: Uses 'updateLayoutParams' with a lambda to modify 'MarginLayoutParams'.
     */
    fun applyBottomMargin(view: View, extraBottomDp: Int = 0) {
        val extraPx = (extraBottomDp * view.resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // DATA: Calculation to ensure UI elements stay above the navigation bar
                bottomMargin = bars.bottom + extraPx
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * FUNCTIONALITY: Configures a BottomSheet to correctly handle edge-to-edge behavior 
     * and apply background styling.
     * USE OF DATA: Accesses 'fragment.dialog' and 'rootView' (View).
     * USE OF CODE STRUCTURES: Uses 'setOnShowListener' and 'let' scope functions 
     * to perform one-time setup when the dialog becomes visible.
     */
    fun setupBottomSheetEdgeToEdge(fragment: BottomSheetDialogFragment, rootView: View) {
        fragment.dialog?.setOnShowListener { dialogInterface ->
            val sheetDialog = dialogInterface as BottomSheetDialog
            WindowCompat.setDecorFitsSystemWindows(sheetDialog.window!!, false)
            val bottomSheet = sheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                it.setBackgroundResource(R.drawable.bottom_sheet_surface_bg)
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isFitToContents = true
            }
            applyPadding(rootView, applyTop = false, applyBottom = true)
        }
    }
}