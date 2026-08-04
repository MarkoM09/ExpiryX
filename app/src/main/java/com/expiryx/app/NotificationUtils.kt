package com.expiryx.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FUNCTIONALITY: Provides utility methods for creating and displaying high-priority 
 * Android notifications, including channel management and image loading.
 * USE OF DATA: Processes 'title' and 'message' (Strings), 'productId' (Int), and 
 * 'imageUri' (String). Utilizes 'NotificationCompat.Builder' to construct UI alerts.
 * USE OF CODE STRUCTURES: Employs 'Glide' callbacks for asynchronous image loading 
 * and 'if/else' selection for SDK version-specific channel creation.
 */
object NotificationUtils {
    private const val CHANNEL_ID = "expiry_notifications"
    private const val CHANNEL_NAME = "Expiry Reminders"
    private const val CHANNEL_DESC = "Notifications about product expirations"

    /**
     * FUNCTIONALITY: Configures and displays a rich notification to the user.
     * USE OF DATA: Ingests alert content and an optional 'imageUri' for a thumbnail.
     * USE OF CODE STRUCTURES: Uses a builder pattern for notification assembly and 
     * an 'if' selection to conditionally load images via Glide's 'CustomTarget' callback.
     */
    fun showExpiryNotification(
        context: Context,
        title: String,
        message: String,
        productId: Int = 0,
        imageUri: String? = null
    ) {
        createChannel(context)

        // CODE STRUCTURE: Intent configuration to define the navigation path when the notification is tapped
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_product_id", productId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            productId.takeIf { it > 0 } ?: 0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Fallback small icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        // USE OF CODE STRUCTURES: Conditional logic to handle image loading asynchronously
        if (!imageUri.isNullOrBlank()) {
            Glide.with(context)
                .asBitmap()
                .load(Uri.parse(imageUri))
                .into(object : CustomTarget<Bitmap>() {
                    // CODE STRUCTURE: Callback triggered when image is successfully fetched
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        builder.setLargeIcon(resource)
                        sendNotification(context, builder.build(), productId, title, message)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // CODE STRUCTURE: Fallback logic ensures notification is sent even if image fails
                        sendNotification(context, builder.build(), productId, title, message)
                    }
                })
        } else {
            sendNotification(context, builder.build(), productId, title, message)
        }

        // Log the notification to the internal Notification Log Center
        logNotification(context, title, message)
    }

    /**
     * FUNCTIONALITY: Persists a record of the sent notification into the local database log.
     * USE OF DATA: Maps 'title' and 'message' to a 'NotificationLog' entity.
     * USE OF CODE STRUCTURES: Uses 'if' selection to determine 'urgency' level based on 
     * message keywords and launches a coroutine for DB insertion.
     */
    private fun logNotification(context: Context, title: String, message: String) {
        val app = context.applicationContext as ProductApplication
        val db = app.database
        // CODE STRUCTURE: Selection structure for prioritizing logs based on content
        val urgency = if (message.contains("today", ignoreCase = true) || message.contains("expired", ignoreCase = true)) 1 else 0
        
        CoroutineScope(Dispatchers.IO).launch {
            db.notificationLogDao().insert(NotificationLog(title = title, message = message, urgency = urgency))
        }
    }

    /**
     * FUNCTIONALITY: Delivers the finalized notification object to the Android System.
     * USE OF DATA: Generates a stable 'notifyId' (Int) from string hashes.
     * USE OF CODE STRUCTURES: Uses 'try/catch' for handling potential 'SecurityException' 
     * on Android 13+.
     */
    private fun sendNotification(context: Context, notification: android.app.Notification, productId: Int, title: String, message: String) {
        // DATA: Hash calculation for ID ensures unique notifications per product/message
        val stableKey = if (productId > 0) "${productId}_${message}" else "global_${title}_${message}"
        val notifyId = stableKey.hashCode()

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notifyId, notification)
            }
        } catch (e: SecurityException) {
            Log.e("NotificationUtils", "Permission missing for notification", e)
        }
    }

    /**
     * FUNCTIONALITY: Creates the notification channel required for Android O and above.
     * USE OF DATA: Defines 'CHANNEL_ID', 'NAME', and 'IMPORTANCE'.
     * USE OF CODE STRUCTURES: Selection structure (Build.VERSION) for OS compatibility.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_DESC }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}