package com.expiryx.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * FUNCTIONALITY: Acts as the global application entry point, responsible for initializing 
 * singleton instances of the database, repository, and external services like Firebase.
 * USE OF DATA: Instantiates 'ProductDatabase' and 'ProductRepository' as lazy properties. 
 * Manages global application 'instance'.
 * USE OF CODE STRUCTURES: Extends 'Application'; overrides 'onCreate' to bootstrap 
 * theme logic, sync listeners, and service initializations within 'try/catch' blocks.
 */
class ProductApplication : Application() {
    companion object {
        lateinit var instance: ProductApplication
            private set
    }

    /**
     * USE OF DATA: Lazy initialization defers database instance memory allocation until first use.
     */
    val database: ProductDatabase by lazy { ProductDatabase.getDatabase(this) }
    
    /**
     * USE OF DATA: Injects both Product and History DAOs into a single shared repository instance.
     */
    val repository: ProductRepository by lazy {
        ProductRepository(database.productDao(), database.historyDao())
    }
    
    /**
     * FUNCTIONALITY: Orchestrates the startup sequence of the solution.
     * USE OF CODE STRUCTURES: Sequential calls to initialization methods and 'if' selection 
     * to conditionally start cloud synchronization based on login status.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this
        // CODE STRUCTURE: Securely bootstapping Firebase with error handling to prevent startup crashes
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("ProductApplication", "Firebase initialization failed.", e)
        }

        // CODE STRUCTURE: Injects default UI styling before any activities are launched
        ThemeManager.initializeTheme(this)

        // CODE STRUCTURE: Logical check to resume background synchronization if previously configured
        if (AccountManager.isLoggedIn() && Prefs.isSyncEnabled(this)) {
            AccountManager.startSync(this)
        }
    }
}