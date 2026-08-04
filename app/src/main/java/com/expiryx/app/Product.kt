package com.expiryx.app

import android.os.Parcelable
import androidx.room.ColumnInfo // Ensure this import is present
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

import java.util.UUID

/**
 * FUNCTIONALITY: Represents a single product entity within the Room SQLite database,
 * tracking grocery items and their expiration metadata.
 * USE OF DATA: Stores primary key 'id' (Int), a unique 'uuid' (String), product details
 * (Strings for name/brand/unit), numeric values (Ints for quantity/weight/reminder),
 * and epoch timestamps (Longs) for dates.
 * USE OF CODE STRUCTURES: Utilizes Kotlin 'data class' with Room '@Entity' annotations
 * and '@Parcelize' for easy inter-process communication between activities.
 */
@Parcelize
@Entity(tableName = "product_table")
data class Product(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0, // Auto-incrementing primary key for unique database identification
    val uuid: String = UUID.randomUUID().toString(), // Unique identifier for syncing and referencing
    val name: String = "", // Display name of the product
    val quantity: Int = 1, // Number of items available
    val expirationDate: Long? = null, // Epoch timestamp in millis for expiration tracking
    val brand: String? = null, // Optional brand name of the product
    @ColumnInfo(name = "weight") 
    val weight: Int? = null, // Numeric weight or volume value
    val weightUnit: String = "g", // Unit for weight/volume (e.g., "g", "ml")
    val imageUri: String? = null, // URI path to the product's image for UI display
    val reminderDays: Int = 3, // Number of days before expiry to trigger a notification
    val isFavorite: Boolean = false, // Flag to mark item for quick access
    val barcode: String? = null, // Scanned barcode string for lookup
    val isSnoozed: Boolean = false, // Flag to suppress notifications for this specific item
    val dateAdded: Long = System.currentTimeMillis(), // Timestamp when the record was created
    val dateModified: Long? = null, // Timestamp when the record was last edited
) : Parcelable
// Trivial change to ensure recompilation