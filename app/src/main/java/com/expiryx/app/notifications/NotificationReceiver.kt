package com.expiryx.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FUNCTIONALITY: Listens for system alarms and triggers local notifications to alert 
 * the user about upcoming product expirations.
 * USE OF DATA: Extracts 'product_id' (Int) and 'days_before' (Int) from the incoming 
 * 'Intent'. Checks 'Prefs' for snooze status.
 * USE OF CODE STRUCTURES: Inherits from 'BroadcastReceiver', uses 'if' selection for 
 * validation and snooze checks, and 'when' selection for message formatting.
 */
class NotificationReceiver : BroadcastReceiver() {
    /**
     * FUNCTIONALITY: Callback triggered when the alarm fires.
     * USE OF DATA: Accesses 'Intent' extras and the application-wide 'ProductRepository'.
     * USE OF CODE STRUCTURES: Uses a coroutine to fetch product details from the 
     * database without blocking the receiver's execution on the main thread.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val productId = intent.getIntExtra("product_id", -1)
        val daysBefore = intent.getIntExtra("days_before", -1)

        // CODE STRUCTURE: Guard clause to ensure valid product data was passed
        if (productId == -1) return

        // CODE STRUCTURE: Selection structure checking global snooze preference
        if (Prefs.isSnoozeActive(context)) {
            Log.d("NotifReceiver", "Snooze active, skipping notification for product $productId")
            return
        }

        val app = context.applicationContext as ProductApplication
        val repo = app.repository

        // CODE STRUCTURE: Background coroutine for database retrieval and notification triggering
        CoroutineScope(Dispatchers.IO).launch {
            val product = repo.getProductById(productId)
            if (product != null) {
                // USE OF CODE STRUCTURES: Selection structure to format the alert message based on interval
                val message = when (daysBefore) {
                    0 -> "${product.name} expires today!"
                    1 -> "${product.name} expires tomorrow."
                    else -> "${product.name} expires in $daysBefore days."
                }
                
                NotificationUtils.showExpiryNotification(
                    context,
                    product.name,
                    message,
                    product.id,
                    product.imageUri
                )
            }
        }
    }
}