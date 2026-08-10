package com.expiryx.app

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * FUNCTIONALITY: Data Access Object (DAO) for the 'history_table', handling persistent 
 * logs of product lifecycle events.
 * USE OF DATA: Manages 'History' entity objects and utilizes 'timestamp' (Long) for 
 * chronological sorting.
 * USE OF CODE STRUCTURES: Defines SQL queries via Room annotations and uses 
 * 'suspend' functions for asynchronous database execution.
 */
@Dao
interface HistoryDao {
    /**
     * FUNCTIONALITY: Records a new historical event (Used, Expired, Deleted) in the database.
     * USE OF DATA: Accepts a 'History' object containing event metadata.
     * USE OF CODE STRUCTURES: Uses '@Insert' with 'REPLACE' conflict strategy.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)

    /**
     * FUNCTIONALITY: Retrieves the complete history log sorted from newest to oldest.
     * USE OF DATA: Returns observable 'LiveData<List<History>>'.
     * USE OF CODE STRUCTURES: SQL 'SELECT' with 'ORDER BY timestamp DESC'.
     */
    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    fun getAllHistory(): LiveData<List<History>>

    /**
     * FUNCTIONALITY: Synchronously fetches all history entries for data export or processing.
     * USE OF DATA: Returns a 'List' of 'History' objects.
     * USE OF CODE STRUCTURES: Non-blocking 'suspend' function executing a 'SELECT' query.
     */
    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    suspend fun getAllHistoryNow(): List<History>

    /**
     * FUNCTIONALITY: Clears all records from the 'history_table'.
     * USE OF DATA: None.
     * USE OF CODE STRUCTURES: SQL 'DELETE' statement execution.
     */
    @Query("DELETE FROM history_table")
    suspend fun clearAllHistory()

    /**
     * FUNCTIONALITY: Removes a specific history entry by its ID.
     * USE OF DATA: Accepts 'id' (Int).
     * USE OF CODE STRUCTURES: Parameterized SQL 'DELETE' query.
     */
    @Query("DELETE FROM history_table WHERE id = :id")
    suspend fun deleteById(id: Int)
}