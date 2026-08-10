package com.expiryx.app

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * FUNCTIONALITY: Data Access Object (DAO) for the 'notification_logs' table, managing 
 * the storage and retrieval of system notification events.
 * USE OF DATA: Processes 'NotificationLog' objects and returns observable 'LiveData' 
 * containing a 'List' of recent logs.
 * USE OF CODE STRUCTURES: Employs Room annotations for SQL operations and 'suspend' 
 * functions for thread-safe database interactions.
 */
@Dao
interface NotificationLogDao {
    /**
     * FUNCTIONALITY: Persists a new notification log entry.
     * USE OF DATA: Ingests a 'NotificationLog' entity.
     * USE OF CODE STRUCTURES: Uses '@Insert' annotation.
     */
    @Insert
    suspend fun insert(log: NotificationLog)

    /**
     * FUNCTIONALITY: Fetches the 50 most recent notification logs.
     * USE OF DATA: Returns 'LiveData<List<NotificationLog>>'.
     * USE OF CODE STRUCTURES: SQL 'SELECT' with 'LIMIT' and 'ORDER BY' logic.
     */
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): LiveData<List<NotificationLog>>

    /**
     * FUNCTIONALITY: Deletes all entries from the 'notification_logs' table.
     * USE OF DATA: None.
     * USE OF CODE STRUCTURES: SQL 'DELETE' statement.
     */
    @Query("DELETE FROM notification_logs")
    suspend fun clearAll()
}
