package com.expiryx.app

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FUNCTIONALITY: Defines database schema version migration paths for Room SQLite, 
 * ensuring data persistence during application updates.
 * USE OF DATA: Executes raw SQL DDL and DML commands to modify table structures, 
 * rename columns, and migrate existing records between versions.
 * USE OF CODE STRUCTURES: Implements multiple 'Migration' anonymous classes; uses 
 * 'try/catch' for cursor management and 'if' selection logic to safely check column existence.
 */
object Migrations {

    /**
     * FUNCTIONALITY: Robustly verifies the existence of a specific column within a table 
     * to prevent redundant 'ALTER TABLE' crashes.
     * USE OF DATA: Queries the SQLite 'PRAGMA table_info' meta-data. Returns 'Boolean'.
     * USE OF CODE STRUCTURES: Iterative 'while' loop through database cursor results 
     * with string-comparison selection logic.
     */
    private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        try {
            // CODE STRUCTURE: Querying database metadata using PRAGMA structural commands
            db.query("PRAGMA table_info($tableName)", emptyArray<Any?>())?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        // CODE STRUCTURE: String selection check to find matching column name
                        if (columnName.equals(cursor.getString(nameIndex), ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Optional: Log the exception
        }
        return false
    }

    /**
     * FUNCTIONALITY: Migrates from v1 to v2 by adding barcode and audit timestamp columns.
     * USE OF DATA: Appends 'TEXT' and 'INTEGER' columns to 'product_table'.
     * USE OF CODE STRUCTURES: Sequential SQL 'ALTER TABLE' execution within an anonymous 
     * Migration class override.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // CODE STRUCTURE: Guarded SQL execution to ensure idempotent structural changes
            if (!columnExists(database, "product_table", "barcode")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN barcode TEXT")
            }
            if (!columnExists(database, "product_table", "dateAdded")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
            }
            if (!columnExists(database, "product_table", "dateModified")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN dateModified INTEGER")
            }
        }
    }

    /**
     * FUNCTIONALITY: Migrates from v2 to v3, performing a complex data-type change 
     * (String to Integer) for product weights.
     * USE OF DATA: Transfers data between old and new tables using SQL 'CAST' and 'CASE' logic.
     * USE OF CODE STRUCTURES: Complex sequence of SQL DDL (Create/Drop/Rename) and 
     * DML (Insert/Select) commands to restructure the database safely.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // PRODUCT TABLE: Change weight from TEXT to INTEGER
            // CODE STRUCTURE: Full table recreation strategy for changing primary column data types
            database.execSQL("""
                CREATE TABLE product_table_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    expirationDate INTEGER,
                    brand TEXT,
                    weight INTEGER,
                    imageUri TEXT,
                    reminderDays INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL,
                    barcode TEXT,
                    dateAdded INTEGER NOT NULL,
                    dateModified INTEGER
                )
            """)
            // DATA TRANSFORMATION: SQL selection with CASE structure to handle data conversion
            database.execSQL("""
                INSERT INTO product_table_new (id, name, quantity, expirationDate, brand, weight, imageUri, reminderDays, isFavorite, barcode, dateAdded, dateModified)
                SELECT id, name, quantity, expirationDate, brand, 
                    CASE 
                        WHEN weight IS NULL OR weight = '' THEN NULL
                        ELSE CAST(weight AS INTEGER)
                    END, 
                    imageUri, reminderDays, isFavorite, barcode, dateAdded, dateModified
                FROM product_table
            """)
            database.execSQL("DROP TABLE product_table")
            database.execSQL("ALTER TABLE product_table_new RENAME TO product_table")

            // HISTORY TABLE: Change weight from TEXT to INTEGER
            database.execSQL("""
                CREATE TABLE history_table_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    productId INTEGER,
                    productName TEXT NOT NULL,
                    expirationDate INTEGER,
                    quantity INTEGER NOT NULL,
                    weight INTEGER,
                    brand TEXT,
                    imageUri TEXT,
                    isFavorite INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    barcode TEXT,
                    dateAdded INTEGER NOT NULL,
                    dateModified INTEGER
                )
            """)
            database.execSQL("""
                INSERT INTO history_table_new (id, productId, productName, expirationDate, quantity, weight, brand, imageUri, isFavorite, action, timestamp, barcode, dateAdded, dateModified)
                SELECT id, productId, productName, expirationDate, quantity, 
                    CASE 
                        WHEN weight IS NULL OR weight = '' THEN NULL
                        ELSE CAST(weight AS INTEGER)
                    END, 
                    brand, imageUri, isFavorite, action, timestamp, barcode, dateAdded, dateModified
                FROM history_table
            """)
            database.execSQL("DROP TABLE history_table")
            database.execSQL("ALTER TABLE history_table_new RENAME TO history_table")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            if (!columnExists(database, "product_table", "weightUnit")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'g'")
            }
            if (!columnExists(database, "history_table", "weightUnit")) {
                database.execSQL("ALTER TABLE history_table ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'g'")
            }
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            if (!columnExists(database, "product_table", "uuid")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
            if (!columnExists(database, "history_table", "uuid")) {
                database.execSQL("ALTER TABLE history_table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
            if (!columnExists(database, "history_table", "productUuid")) {
                database.execSQL("ALTER TABLE history_table ADD COLUMN productUuid TEXT")
            }
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            if (!columnExists(database, "product_table", "isSnoozed")) {
                database.execSQL("ALTER TABLE product_table ADD COLUMN isSnoozed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}