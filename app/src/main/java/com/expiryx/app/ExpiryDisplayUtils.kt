package com.expiryx.app

import android.content.Context
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FUNCTIONALITY: Provides formatting logic for displaying expiration-related data, 
 * including relative date strings and accessibility-conscious UI styling (pills).
 * USE OF DATA: Consumes 'expiryMillis' (Long), 'Context', and accessibility preference 
 * 'Boolean' flags. Produces formatted 'String' labels and applies 'Int' resource styles.
 * USE OF CODE STRUCTURES: Employs 'when' selection for relative date branching and 
 * exhaustive 'when' blocks for mapping expiry status to specific UI theme attributes.
 */
object ExpiryDisplayUtils {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /**
     * FUNCTIONALITY: Converts an expiry timestamp into a localized relative string (e.g., "Expires tomorrow").
     * USE OF DATA: Accepts 'expiryMillis' (Long) and 'Context'.
     * USE OF CODE STRUCTURES: 'when' selection structure evaluating calculated day differences 
     * to pick the appropriate string resource.
     */
    fun formatDaysRemaining(context: Context, expiryMillis: Long?): String {
        if (expiryMillis == null) return context.getString(R.string.expiry_none)
        val diff = ExpiryTrafficLightUtils.dayDiff(expiryMillis)
        return when {
            diff == null -> context.getString(R.string.expiry_none)
            diff < 0 -> context.getString(R.string.expiry_expired)
            diff == 0L -> context.getString(R.string.expiry_today)
            diff == 1L -> context.getString(R.string.expiry_tomorrow)
            else -> context.getString(R.string.expiry_days_left, diff.toInt())
        }
    }

    fun formatExpiryDate(expiryMillis: Long?): String {
        expiryMillis ?: return "N/A"
        return dateFormat.format(Date(expiryMillis))
    }

    /**
     * FUNCTIONALITY: Applies complex visual styling to a TextView (e.g., color, background, icons) 
     * based on expiry urgency and accessibility settings (Colorblind/High Contrast modes).
     * USE OF DATA: Reads theme attributes (Int), accessibility preferences (Boolean), 
     * and expiration status (Enum).
     * USE OF CODE STRUCTURES: Uses nested selection structures ('if' for mode checks, 
     * 'when' for status classification) to implement dual-coding accessibility standards.
     */
    fun applyTrafficLightPill(textView: TextView, expiryMillis: Long?) {
        val context = textView.context
        val theme = context.theme
        val typedValue = android.util.TypedValue()

        /**
         * FUNCTIONALITY: Helper to resolve theme attributes to actual color values.
         * USE OF DATA: Takes 'attr' (Int) and returns color data (Int).
         */
        fun getThemeColor(attr: Int): Int {
            theme.resolveAttribute(attr, typedValue, true)
            return typedValue.data
        }

        val isColorblind = Prefs.isColorblindModeEnabled(context)
        val isHighContrast = Prefs.isHighContrastEnabled(context)

        // CODE STRUCTURE: Guard clause for products without expiration dates
        if (expiryMillis == null) {
            textView.setBackgroundResource(R.drawable.pill_expiry_safe_bg)
            textView.setTextColor(getThemeColor(R.attr.expiryTextUnknown))
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            textView.text = context.getString(R.string.expiry_none)
            return
        }

        val status = ExpiryTrafficLightUtils.classify(expiryMillis)
        val daysText = formatDaysRemaining(context, expiryMillis)

        // Reset drawables
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        textView.compoundDrawablePadding = if (isColorblind) 8 else 0

        // USE OF CODE STRUCTURES: Main selection structure for applying status-specific styling
        when (status) {
            ExpiryTrafficLight.EXPIRED -> {
                textView.setBackgroundResource(if (isColorblind) R.drawable.pill_expiry_expired_high_contrast else R.drawable.pill_expiry_expired_list_bg)
                textView.setTextColor(getThemeColor(R.attr.expiryTextExpired))
                // CODE STRUCTURE: Accessibility branch for colorblind users adding icons and labels
                if (isColorblind) {
                    textView.text = "${context.getString(R.string.expiry_status_expired)} • $daysText"
                    textView.setTypeface(null, android.graphics.Typeface.BOLD)
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_triangle, 0, 0, 0)
                    TextViewCompat.setCompoundDrawableTintList(textView, android.content.res.ColorStateList.valueOf(getThemeColor(R.attr.expiryTextExpired)))
                } else {
                    textView.text = daysText
                    textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            ExpiryTrafficLight.URGENT -> {
                textView.setBackgroundResource(if (isColorblind) R.drawable.pill_expiry_urgent_high_contrast else R.drawable.pill_expiry_urgent_bg)
                textView.setTextColor(getThemeColor(R.attr.expiryTextUrgent))
                if (isColorblind) {
                    textView.text = "${context.getString(R.string.expiry_status_urgent)} • $daysText"
                    textView.setTypeface(null, android.graphics.Typeface.BOLD)
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_square_filled, 0, 0, 0)
                    TextViewCompat.setCompoundDrawableTintList(textView, android.content.res.ColorStateList.valueOf(getThemeColor(R.attr.expiryTextUrgent)))
                } else {
                    textView.text = daysText
                    textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            ExpiryTrafficLight.SAFE -> {
                // DATA: Selection structure to determine the final safe-status color based on accessibility flags
                val safeColor = if (isHighContrast) {
                    getThemeColor(R.attr.expiryTextSafe)
                } else if (isColorblind) {
                    context.getColor(R.color.blue)
                } else {
                    getThemeColor(R.attr.expiryTextSafe)
                }
                
                textView.setBackgroundResource(if (isColorblind) R.drawable.pill_expiry_safe_high_contrast else R.drawable.pill_expiry_safe_bg)
                textView.setTextColor(safeColor)
                
                if (isColorblind) {
                    textView.text = "${context.getString(R.string.expiry_status_safe)} • $daysText"
                    textView.setTypeface(null, android.graphics.Typeface.BOLD)
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_circle_filled, 0, 0, 0)
                    TextViewCompat.setCompoundDrawableTintList(textView, android.content.res.ColorStateList.valueOf(safeColor))
                } else {
                    textView.text = daysText
                    textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            ExpiryTrafficLight.UNKNOWN -> {
                textView.setBackgroundResource(R.drawable.pill_expiry_safe_bg)
                textView.setTextColor(getThemeColor(R.attr.expiryTextUnknown))
                textView.text = context.getString(R.string.expiry_none)
            }
        }
    }
}