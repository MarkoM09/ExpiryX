package com.expiryx.app

import android.content.Context
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExpiryDisplayUtils {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

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

    fun applyTrafficLightPill(textView: TextView, expiryMillis: Long?) {
        val context = textView.context
        val theme = context.theme
        val typedValue = android.util.TypedValue()

        fun getThemeColor(attr: Int): Int {
            theme.resolveAttribute(attr, typedValue, true)
            return typedValue.data
        }

        val isColorblind = Prefs.isColorblindModeEnabled(context)
        val isHighContrast = Prefs.isHighContrastEnabled(context)

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

        when (status) {
            ExpiryTrafficLight.EXPIRED -> {
                textView.setBackgroundResource(if (isColorblind) R.drawable.pill_expiry_expired_high_contrast else R.drawable.pill_expiry_expired_list_bg)
                textView.setTextColor(getThemeColor(R.attr.expiryTextExpired))
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
                // If High Contrast is enabled, it should override the specific Blue color used for colorblindness
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
