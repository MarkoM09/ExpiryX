package com.expiryx.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

/**
 * FUNCTIONALITY: Provides utility methods to display and manage the accent theme selection UI, 
 * allowing users to customize the application's primary color.
 * USE OF DATA: Interacts with 'ThemeManager.AccentOption' objects, Integer color resources, 
 * and persistent theme identifiers (Strings/Ints).
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton, iterative layout inflation, 
 * and 'if/else' selection for visual state management.
 */
object AccentThemePicker {

    /**
     * FUNCTIONALITY: Displays a BottomSheetDialog containing accent color options for selection.
     * USE OF DATA: Processes 'ThemeManager.accentOptions' and 'currentAccent' (String). 
     * Takes an 'onAccentSelected' callback lambda.
     * USE OF CODE STRUCTURES: Uses 'forEachIndexed' for layout distribution and 'setOnClickListener' 
     * with selection logic to commit theme changes.
     */
    fun show(context: Context, onAccentSelected: () -> Unit) {
        val accentTheme = ThemeManager.getAccentThemeRes(context)
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, accentTheme)
        
        val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_ExpiryX_BottomSheetDialog)
        val content = LayoutInflater.from(themedContext).inflate(R.layout.bottom_sheet_accent_picker, null)
        dialog.setContentView(content)

        val rowTop = content.findViewById<LinearLayout>(R.id.accentPickerRowTop)
        val rowBottom = content.findViewById<LinearLayout>(R.id.accentPickerRowBottom)
        val currentAccent = ThemeManager.getAccentTheme(context)

        // CODE STRUCTURE: Iteration structure to dynamically populate selection rows
        ThemeManager.accentOptions.forEachIndexed { index, option ->
            // CODE STRUCTURE: Selection logic to distribute items between top and bottom rows
            val row = if (index < 3) rowTop else rowBottom
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_accent_option, row, false)
            bindOption(context, itemView, option, option.id == currentAccent)
            
            itemView.setOnClickListener {
                // CODE STRUCTURE: Guard clause to avoid redundant theme applications
                if (option.id != ThemeManager.getAccentTheme(context)) {
                    ThemeManager.setAccentTheme(context, option.id)
                    onAccentSelected()
                }
                dialog.dismiss()
            }
            row.addView(itemView)
        }

        dialog.show()
    }

    /**
     * FUNCTIONALITY: Populates a LinearLayout with small circular color swatches for inline UI usage.
     * USE OF DATA: Reads density metrics (Float) to calculate pixel dimensions (Int) for swatches.
     * USE OF CODE STRUCTURES: Iterative layout building with 'setOnClickListener' triggers 
     * for immediate theme updates.
     */
    fun bindInlineSwatch(
        context: Context,
        container: LinearLayout,
        onAccentSelected: () -> Unit,
    ) {
        container.removeAllViews()
        val currentAccent = ThemeManager.getAccentTheme(context)
        // DATA: Calculation of UI dimensions based on screen density
        val size = (36 * context.resources.displayMetrics.density).toInt()
        val margin = (6 * context.resources.displayMetrics.density).toInt()

        ThemeManager.accentOptions.forEach { option ->
            val selected = option.id == currentAccent
            val ringSize = size + (8 * context.resources.displayMetrics.density).toInt()
            val wrapper = android.widget.FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                    marginEnd = margin
                }
            }
            val swatch = View(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = circleDrawable(context, option.previewColorRes)
                contentDescription = option.label
            }
            // CODE STRUCTURE: Selection structure for applying visual feedback to the active accent
            if (selected) {
                wrapper.foreground = ContextCompat.getDrawable(context, R.drawable.accent_swatch_selected_ring)
            }
            wrapper.addView(swatch)
            wrapper.setOnClickListener {
                if (option.id != ThemeManager.getAccentTheme(context)) {
                    ThemeManager.setAccentTheme(context, option.id)
                    onAccentSelected()
                }
            }
            container.addView(wrapper)
        }
    }

    private fun bindOption(
        context: Context,
        itemView: View,
        option: ThemeManager.AccentOption,
        selected: Boolean,
    ) {
        val swatch = itemView.findViewById<View>(R.id.accentSwatchColor)
        val ring = itemView.findViewById<View>(R.id.accentSwatchRing)
        val check = itemView.findViewById<ImageView>(R.id.accentSwatchCheck)
        val label = itemView.findViewById<TextView>(R.id.accentOptionLabel)
        val card = itemView.findViewById<MaterialCardView>(R.id.accentOptionCard)

        swatch.background = circleDrawable(context, option.previewColorRes)
        label.text = option.label

        ring.visibility = if (selected) View.VISIBLE else View.GONE
        check.visibility = if (selected) View.VISIBLE else View.GONE

        if (selected) {
            card.strokeColor = ContextCompat.getColor(context, option.previewColorRes)
            card.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        }
    }

    /**
     * FUNCTIONALITY: Creates a circular background drawable with a specific color.
     * USE OF DATA: Consumes 'colorRes' (Int).
     * USE OF CODE STRUCTURES: Uses the 'apply' scope function for object configuration.
     */
    private fun circleDrawable(context: Context, colorRes: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
