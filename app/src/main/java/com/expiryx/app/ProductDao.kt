package com.expiryx.app

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * FUNCTIONALITY: Data Access Object (DAO) for the 'product_table', providing methods 
 * for CRUD operations on Product entities.
 * USE OF DATA: Interacts with 'Product' objects and uses 'Int' IDs for specific lookups. 
 * Returns 'LiveData' for reactive UI updates and standard 'List' for one-time fetches.
 * USE OF CODE STRUCTURES: Employs Room annotations (@Insert, @Update, @Delete, @Query) 
 * to define SQL operations and Kotlin 'suspend' functions for non-blocking DB access.
 */
@Dao
interface ProductDao {
    /**
     * FUNCTIONALITY: Persists a new product or updates an existing one if the primary key conflicts.
     * USE OF DATA: Accepts a 'Product' entity object containing all record metadata.
     * USE OF CODE STRUCTURES: Uses '@Insert' annotation with 'REPLACE' conflict strategy.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    /**
     * FUNCTIONALITY: Updates an existing product record in the database.
     * USE OF DATA: Accepts a 'Product' entity object with a matching primary key.
     * USE OF CODE STRUCTURES: Uses '@Update' Room annotation.
     */
    @Update
    suspend fun update(product: Product)

    /**
     * FUNCTIONALITY: Removes a specific product record from the database.
     * USE OF DATA: Accepts the 'Product' entity object to be deleted.
     * USE OF CODE STRUCTURES: Uses '@Delete' Room annotation.
     */
    @Delete
    suspend fun delete(product: Product)

    /**
     * FUNCTIONALITY: Wipes all records from the 'product_table'.
     * USE OF DATA: None.
     * USE OF CODE STRUCTURES: Executes a raw SQL 'DELETE' statement via '@Query'.
     */
    @Query("DELETE FROM product_table")
    suspend fun clearAllProducts()

    /**
     * FUNCTIONALITY: Retrieves all products ordered by expiration date, putting items 
     * without dates at the end.
     * USE OF DATA: Returns a 'LiveData' wrapper around a 'List' of 'Product' objects.
     * USE OF CODE STRUCTURES: Implements a complex SQL 'ORDER BY' with a 'CASE' selection 
     * structure to handle null values.
     */
    @Query(
        """
        SELECT * FROM product_table 
        ORDER BY 
            CASE WHEN expirationDate IS NULL THEN 1 ELSE 0 END,
            expirationDate ASC
    """,
    )
    fun getAllProducts(): LiveData<List<Product>>

    /**
     * FUNCTIONALITY: Synchronously fetches all products for background processing (e.g., notifications).
     * USE OF DATA: Returns a 'List' of 'Product' objects.
     * USE OF CODE STRUCTURES: Sequential SQL execution within a 'suspend' function.
     */
    @Query(
        """
        SELECT * FROM product_table 
        ORDER BY 
            CASE WHEN expirationDate IS NULL THEN 1 ELSE 0 END,
            expirationDate ASC
    """,
    )
    suspend fun getAllProductsNow(): List<Product>

    /**
     * FUNCTIONALITY: Finds a specific product by its unique integer database ID.
     * USE OF DATA: Accepts an 'id' (Int) and returns a nullable 'Product'.
     * USE OF CODE STRUCTURES: SQL selection with parameter binding (:id).
     */
    @Query("SELECT * FROM product_table WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): Product?
}