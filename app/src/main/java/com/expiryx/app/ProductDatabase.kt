package com.expiryx.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * FUNCTIONALITY: Acts as the main access point to the Room database, maintaining a 
 * singleton instance for the entire application.
 * USE OF DATA: Manages 'Product', 'History', and 'NotificationLog' entities. Provides 
 * access to their respective DAOs.
 * USE OF CODE STRUCTURES: Implements the 'Singleton' pattern using a companion object 
 * with '@Volatile' and thread-safe 'synchronized' blocks to prevent multiple instances.
 */
@Database(entities = [Product::class, History::class, NotificationLog::class], version = 9, exportSchema = false)
abstract class ProductDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun historyDao(): HistoryDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: ProductDatabase? = null

        /**
         * FUNCTIONALITY: Returns the singleton instance of ProductDatabase, initializing 
         * it if it doesn't exist.
         * USE OF DATA: Accepts a 'Context' to build the database, returns 'ProductDatabase'.
         * USE OF CODE STRUCTURES: Uses double-checked locking with 'synchronized(this)' 
         * to ensure thread safety during database creation.
         */
        fun getDatabase(context: Context): ProductDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProductDatabase::class.java,
                    "product_database"
                )
                    .addMigrations(
                        Migrations.MIGRATION_1_2,
                        Migrations.MIGRATION_2_3,
                        Migrations.MIGRATION_3_4,
                        Migrations.MIGRATION_4_5,
                        Migrations.MIGRATION_6_7
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}