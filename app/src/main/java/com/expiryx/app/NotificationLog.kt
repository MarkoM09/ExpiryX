package com.expiryx.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FUNCTIONALITY: Represents a single notification event logged in the database for user review.
 * USE OF DATA: Stores 'title' and 'message' (Strings), a 'timestamp' (Long), and 
 * an 'urgency' level (Int) to categorize notification importance.
 * USE OF CODE STRUCTURES: Standard Kotlin 'data class' with Room '@Entity' annotation 
 * for storage in the 'notification_logs' table.
 */
@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0, // Primary key for unique log identification
    val title: String, // Heading of the notification
    val message: String, // Detailed body text of the notification
    val timestamp: Long = System.currentTimeMillis(), // When the notification was triggered
    val urgency: Int = 0 // Priority level (e.g., 0 for normal, 1 for critical)
)
