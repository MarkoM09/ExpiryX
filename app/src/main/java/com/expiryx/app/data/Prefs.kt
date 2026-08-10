package com.expiryx.app

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * FUNCTIONALITY: Manages application-wide persistent settings and user preferences using 
 * Android SharedPreferences.
 * USE OF DATA: Stores and retrieves primitive values (Boolean, Int, Long) and String 
 * sets for settings like notifications, snooze timestamps, and accessibility options.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton for shared access, 
 * helper functions for SharedPreferences interaction, and 'edit().apply()' for 
 * asynchronous writes.
 */
object Prefs {
    private const val NAME = "expiryx_prefs" // Filename for the shared preferences storage

    private const val KEY_NOTIF_ENABLED = "notifications_enabled" // Keep general on/off
    private const val KEY_DEFAULT_HOUR = "notif_default_hour" // Hour for daily notification check
    private const val KEY_DEFAULT_MINUTE = "notif_default_minute" // Minute for daily notification check
    private const val KEY_REMINDER_INTERVALS = "reminder_intervals" // Set of days before expiry to notify
    private const val KEY_SNOOZE_END_TIMESTAMP = "snooze_end_timestamp" // Epoch timestamp when snooze expires
    private const val KEY_SYNC_ENABLED = "sync_enabled" // Flag for cloud synchronization
    private const val KEY_HIGH_CONTRAST = "high_contrast" // Accessibility setting for UI contrast
    private const val KEY_COLORBLIND_MODE = "colorblind_mode" // Accessibility setting for colorblind support

    /**
     * FUNCTIONALITY: Provides access to the SharedPreferences instance.
     * USE OF DATA: Takes 'Context' and returns 'SharedPreferences'.
     * USE OF CODE STRUCTURES: Standard private helper function.
     */
    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- Sync ----
    /**
     * FUNCTIONALITY: Checks if cloud synchronization is enabled.
     * USE OF DATA: Returns a 'Boolean' from storage, defaulting to true.
     * USE OF CODE STRUCTURES: Single-expression function retrieving from SharedPreferences.
     */
    fun isSyncEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_SYNC_ENABLED, true)

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    // ---- General Notifications Enabled ----
    fun isNotificationsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NOTIF_ENABLED, true)

    /**
     * FUNCTIONALITY: Enables or disables notifications and clears snooze if disabling.
     * USE OF DATA: Accepts 'enabled' (Boolean).
     * USE OF CODE STRUCTURES: Uses 'if' selection to trigger 'clearSnooze()' when disabled.
     */
    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()
        if (!enabled) { // If turning all notifications off, also clear any active snooze
            clearSnooze(context)
        }
    }

    // ---- Default reminder time ----
    fun getDefaultHour(context: Context): Int =
        sp(context).getInt(KEY_DEFAULT_HOUR, 9) // Default 9 AM

    fun getDefaultMinute(context: Context): Int =
        sp(context).getInt(KEY_DEFAULT_MINUTE, 0)

    fun setDefaultTime(context: Context, hour: Int, minute: Int) {
        sp(context).edit()
            .putInt(KEY_DEFAULT_HOUR, hour)
            .putInt(KEY_DEFAULT_MINUTE, minute)
            .apply()
    }

    // ---- Reminder Intervals ----
    fun getReminderIntervals(context: Context): Set<String> {
        return sp(context).getStringSet(KEY_REMINDER_INTERVALS, setOf("0", "1")) ?: setOf("0", "1")
    }

    fun setReminderIntervals(context: Context, intervals: Set<String>) {
        sp(context).edit().putStringSet(KEY_REMINDER_INTERVALS, intervals).apply()
    }

    // ---- Snooze ----
    fun getSnoozeEndTimestamp(context: Context): Long =
        sp(context).getLong(KEY_SNOOZE_END_TIMESTAMP, 0L)

    fun isSnoozeActive(context: Context): Boolean {
        val snoozeEndTime = getSnoozeEndTimestamp(context)
        return snoozeEndTime > 0 && System.currentTimeMillis() < snoozeEndTime
    }

    /**
     * FUNCTIONALITY: Sets a notification snooze for a specified number of days.
     * USE OF DATA: Calculates a future epoch timestamp (Long) based on 'days' (Int).
     * USE OF CODE STRUCTURES: Uses 'if/else' selection for range validation and 
     * 'Calendar' operations to compute the target date.
     */
    fun setSnooze(context: Context, days: Int) {
        if (days > 0) {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, days)
                set(Calendar.HOUR_OF_DAY, 23) // Snooze until end of the last day
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            sp(context).edit().putLong(KEY_SNOOZE_END_TIMESTAMP, calendar.timeInMillis).apply()
        } else {
            clearSnooze(context) // Clear snooze if days is 0 or negative
        }
    }

    fun clearSnooze(context: Context) {
        sp(context).edit().putLong(KEY_SNOOZE_END_TIMESTAMP, 0L).apply()
    }

    // ---- Accessibility ----
    fun isHighContrastEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrastEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun isColorblindModeEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_COLORBLIND_MODE, false)

    fun setColorblindModeEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_COLORBLIND_MODE, enabled).apply()
    }
}
