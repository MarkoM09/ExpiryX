package com.expiryx.app

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

import java.util.UUID

/**
 * FUNCTIONALITY: Represents a historical log entry of a product's lifecycle (used, expired, or deleted).
 * USE OF DATA: Stores primary key 'id' (Int), references 'productUuid' (String), and mirrors 
 * product metadata (Strings/Ints) to maintain a persistent record even after product deletion.
 * USE OF CODE STRUCTURES: Implements 'Parcelable' for data transfer and Room '@Entity' 
 * for persistence in the 'history_table'.
 */
@Parcelize
@Entity(tableName = "history_table")
data class History(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0, // Unique identifier for the history entry
    val uuid: String = UUID.randomUUID().toString(), // Unique UUID for this log entry
    val productUuid: String? = null, // Reference to the original Product's UUID
    val productName: String = "", // Snapshot of product name at the time of action
    val expirationDate: Long? = null, // Snapshot of expiration date
    val quantity: Int = 1, // Quantity involved in the action
    @ColumnInfo(name = "weight") 
    val weight: Int? = null, // Snapshot of weight/volume
    val weightUnit: String = "g", // Unit for weight/volume
    val brand: String? = null, // Snapshot of brand name
    val imageUri: String? = null, // Path to product image
    val isFavorite: Boolean = false, // Snapshot of favorite status
    @ColumnInfo(name = "action") 
    val action: String = "", // Type of historical event: "Expired", "Used", "Deleted"
    val timestamp: Long = System.currentTimeMillis(), // When this history record was created
    val barcode: String? = null, // Snapshot of barcode data
    val dateAdded: Long = System.currentTimeMillis(), // Original creation date of the product
    val dateModified: Long? = null, // Last modification date of the product
) : Parcelable