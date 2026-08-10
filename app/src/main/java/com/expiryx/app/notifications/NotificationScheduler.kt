package com.expiryx.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * FUNCTIONALITY: Manages the scheduling and cancellation of system alarms for product 
 * expiration reminders using the Android AlarmManager API.
 * USE OF DATA: Processes 'Product' objects, 'Context', and user preferences from 'Prefs' 
 * (intervals, times). Uses 'Long' timestamps for exact alarm triggers.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton for centralized access, 
 * 'forEach' iteration for batch scheduling, and conditional version checks (SDK_INT) 
 * for exact alarm permissions.
 */
object NotificationScheduler {
    private const val TAG = "NotifScheduler"

    // Intervals: 0 (today), 1 (tomorrow), 3, 7, 14, 30 days before
    val POSSIBLE_INTERVAL_VALUES = arrayOf("0", "1", "3", "7", "14", "30")

    /**
     * FUNCTIONALITY: Schedules all user-enabled reminders for a specific grocery product.
     * USE OF DATA: Reads 'Product' metadata and global 'Prefs'. Calculates trigger 'Long' timestamps.
     * USE OF CODE STRUCTURES: Uses 'if' selection for early exit (master switch/snooze) 
     * and 'forEach' to iterate over chosen reminder intervals.
     */
    fun scheduleForProduct(context: Context, product: Product) {
        // CODE STRUCTURE: Guard clause ensures notifications are only scheduled when enabled and not snoozed
        if (!Prefs.isNotificationsEnabled(context) || product.isSnoozed) {
            cancelForProduct(context, product)
            return
        }

        val expiryMillis = product.expirationDate ?: return
        val intervals = Prefs.getReminderIntervals(context)
        val targetHour = Prefs.getDefaultHour(context)
        val targetMinute = Prefs.getDefaultMinute(context)

        // CODE STRUCTURE: Clears existing alarms first to avoid duplicate notifications for the same ID
        cancelForProduct(context, product)

        intervals.forEach { intervalStr ->
            val daysBefore = intervalStr.toIntOrNull() ?: return@forEach
            val triggerTime = calculateTriggerTime(expiryMillis, daysBefore, targetHour, targetMinute)

            // CODE STRUCTURE: Logic check to ensure alarm is set only for future timestamps
            if (triggerTime > System.currentTimeMillis()) {
                scheduleAlarm(context, product, daysBefore, triggerTime)
            }
        }
    }

    /**
     * FUNCTIONALITY: Removes all active alarms associated with a specific product ID.
     * USE OF DATA: Ingests 'Product' and 'Context'.
     * USE OF CODE STRUCTURES: Iterates through all 'POSSIBLE_INTERVAL_VALUES' to find and 
     * cancel every potential pending intent.
     */
    fun cancelForProduct(context: Context, product: Product) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        POSSIBLE_INTERVAL_VALUES.forEach { interval ->
            val pendingIntent = createPendingIntent(context, product.id, interval.toInt())
            alarmManager.cancel(pendingIntent)
        }
        Log.d(TAG, "Cancelled all alarms for product: ${product.name}")
    }

    /**
     * FUNCTIONALITY: Batch updates all notification schedules, typically used after a device reboot or setting change.
     * USE OF DATA: Accepts a 'List<Product>'.
     * USE OF CODE STRUCTURES: Standard 'forEach' iteration calling 'scheduleForProduct' for each item.
     */
    fun rescheduleAll(context: Context, products: List<Product>) {
        products.forEach { scheduleForProduct(context, it) }
        Log.d(TAG, "Rescheduled all notifications for ${products.size} products")
    }

    /**
     * FUNCTIONALITY: Registers an exact alarm with the Android System via AlarmManager.
     * USE OF DATA: Takes 'Product', 'triggerTime' (Long), and 'daysBefore' (Int).
     * USE OF CODE STRUCTURES: Selection structure (if/else) for SDK version compatibility (S+) 
     * and 'try/catch' for exception handling during registration.
     */
    private fun scheduleAlarm(context: Context, product: Product, daysBefore: Int, triggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, product.id, daysBefore)

        try {
            // CODE STRUCTURE: Version selection for handling Android 12+ exact alarm permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled alarm for ${product.name} at $daysBefore days before ($triggerTime)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    /**
     * FUNCTIONALITY: Creates a unique PendingIntent to trigger the NotificationReceiver.
     * USE OF DATA: Combines 'productId' and 'daysBefore' into a unique 'requestCode' (Int).
     * USE OF CODE STRUCTURES: Intent configuration using the 'apply' scope function.
     */
    private fun createPendingIntent(context: Context, productId: Int, daysBefore: Int): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("product_id", productId)
            putExtra("days_before", daysBefore)
        }
        // DATA: Calculation to ensure unique ID per product/interval combo to prevent overwriting
        val requestCode = productId * 100 + daysBefore
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * FUNCTIONALITY: Computes the precise millisecond timestamp for an alarm.
     * USE OF DATA: Accepts 'expiryMillis' (Long), 'daysBefore', 'hour', and 'minute' (Ints).
     * USE OF CODE STRUCTURES: Uses 'Calendar' instance with sequential 'set' and 'add' logic.
     */
    private fun calculateTriggerTime(expiryMillis: Long, daysBefore: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = expiryMillis
            add(Calendar.DAY_OF_YEAR, -daysBefore)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}