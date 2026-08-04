package com.expiryx.app

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * FUNCTIONALITY: Manages user authentication, session state, and real-time synchronization 
 * between the local database and Google Firestore.
 * USE OF DATA: Interacts with 'FirebaseUser', Firestore document snapshots, and 'Product'/'History' 
 * objects. Maintains an 'AtomicInteger' counter to prevent sync loops.
 * USE OF CODE STRUCTURES: Employs 'object' singleton for state management, Firebase listeners 
 * for real-time updates, and Kotlin coroutines ('await()', 'launch') for non-blocking I/O.
 */
@Suppress("DEPRECATION")
object AccountManager {
    private const val TAG = "AccountManager"
    
    private var productsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    // Counter to prevent local changes triggered by remote sync from being pushed back
    private val remoteChangeCounter = java.util.concurrent.atomic.AtomicInteger(0)
    
    /**
     * FUNCTIONALITY: Determines if the app is currently applying a remote database change.
     * USE OF DATA: Returns 'Boolean'.
     * USE OF CODE STRUCTURES: Atomic check of the 'remoteChangeCounter'.
     */
    val isApplyingRemoteChange: Boolean get() = remoteChangeCounter.get() > 0

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth initialization error", e)
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore initialization error", e)
            null
        }
    }

    /**
     * FUNCTIONALITY: Checks the current authentication status.
     * USE OF DATA: Returns 'Boolean' based on 'auth' presence.
     */
    fun isLoggedIn(): Boolean = auth?.currentUser != null

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    fun getUserId(): String? = auth?.currentUser?.uid

    /**
     * FUNCTIONALITY: Clears user session, stops sync, and signs out from Google/Firebase.
     * USE OF DATA: Takes 'Context' for Google Sign-In client and an 'onComplete' callback.
     * USE OF CODE STRUCTURES: Sequential teardown logic: stop sync -> Firebase signout -> Google signout callback.
     */
    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        stopSync()
        auth?.signOut()
        setWelcomeScreenPassed(context, false)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            onComplete()
        }
    }

    /**
     * FUNCTIONALITY: Detaches active Firestore listeners to conserve battery and data.
     * USE OF DATA: Nullifies 'ListenerRegistration' objects.
     */
    fun stopSync() {
        productsListener?.remove()
        historyListener?.remove()
        productsListener = null
        historyListener = null
        Log.d(TAG, "Cloud sync listeners stopped.")
    }

    fun isWelcomeScreenPassed(context: Context): Boolean {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("welcomeScreenPassed", false)
    }

    fun setWelcomeScreenPassed(context: Context, passed: Boolean) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit { putBoolean("welcomeScreenPassed", passed) }
    }
    
    /**
     * FUNCTIONALITY: Initiates real-time incremental pulls from Firestore for Products and History.
     * USE OF DATA: Reads user ID from Auth and initializes listeners for sub-collections.
     * USE OF CODE STRUCTURES: Uses 'if' selection for permission/permission state checks 
     * and 'addSnapshotListener' with inner coroutine launches for local persistence.
     */
    fun startSync(context: Context) {
        val userId = getUserId() ?: return
        val currentFirestore = firestore ?: return
        val repo = (context.applicationContext as ProductApplication).repository
        
        // CODE STRUCTURE: Selection structure checking user preference before starting sync
        if (!Prefs.isSyncEnabled(context)) {
            stopSync()
            return
        }

        if (productsListener != null) {
            Log.d(TAG, "Sync already active.")
            return
        }

        Log.d(TAG, "Starting modern real-time sync for: $userId")

        // 1. PRODUCTS LISTENER (Incremental Pull)
        productsListener = currentFirestore.collection("users").document(userId)
            .collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Products listen error", e)
                    return@addSnapshotListener
                }
                
                snapshots?.let {
                    // CODE STRUCTURE: Coroutine handles background processing of incoming cloud changes
                    CoroutineScope(Dispatchers.IO).launch {
                        processProductChanges(it.documentChanges, repo)
                    }
                }
            }

        // 2. HISTORY LISTENER (Incremental Pull)
        historyListener = currentFirestore.collection("users").document(userId)
            .collection("history")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "History listen error", e)
                    return@addSnapshotListener
                }
                
                snapshots?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        processHistoryChanges(it.documentChanges, repo)
                    }
                }
            }
    }

    /**
     * FUNCTIONALITY: Compares incoming Firestore changes with the local DB and applies updates.
     * USE OF DATA: Ingests 'List<DocumentChange>' and 'ProductRepository'.
     * USE OF CODE STRUCTURES: Uses a 'for' loop to iterate through changes and a 'when' 
     * selection structure to handle ADDED, MODIFIED, or REMOVED event types.
     */
    private suspend fun processProductChanges(changes: List<DocumentChange>, repo: ProductRepository) {
        // USE OF CODE STRUCTURES: Atomic counter increments to signal a sync-in-progress
        remoteChangeCounter.incrementAndGet()
        try {
            val localProducts = repo.getAllProductsNow()
            for (dc in changes) {
                val cloudProduct = dc.document.toObject(Product::class.java)
                if (cloudProduct.uuid.isBlank()) continue

                // CODE STRUCTURE: Selection path based on Firestore change type
                when (dc.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val local = localProducts.find { it.uuid == cloudProduct.uuid }
                        if (local == null) {
                            repo.insertProductLocallyOnly(cloudProduct.copy(id = 0))
                        } else if ((cloudProduct.dateModified ?: 0) > (local.dateModified ?: 0)) {
                            // CODE STRUCTURE: Comparison logic to ensure newer cloud data overwrites older local data
                            repo.updateProductLocallyOnly(cloudProduct.copy(id = local.id))
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        localProducts.find { it.uuid == cloudProduct.uuid }?.let {
                            repo.deleteProductLocallyOnly(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing remote product changes", e)
        } finally {
            // CODE STRUCTURE: Finally block ensures counter is decremented even after errors
            remoteChangeCounter.decrementAndGet()
        }
    }

    /**
     * FUNCTIONALITY: Syncs history records from cloud to local storage.
     * USE OF DATA: Converts Firestore documents to 'History' objects.
     * USE OF CODE STRUCTURES: Similar to processProductChanges, using 'when' selection for diffing.
     */
    private suspend fun processHistoryChanges(changes: List<DocumentChange>, repo: ProductRepository) {
        remoteChangeCounter.incrementAndGet()
        try {
            val localHistory = repo.getAllHistoryNow()
            for (dc in changes) {
                val cloudItem = dc.document.toObject(History::class.java)
                if (cloudItem.uuid.isBlank()) continue

                when (dc.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val local = localHistory.find { it.uuid == cloudItem.uuid }
                        if (local == null) {
                            repo.insertHistoryLocallyOnly(cloudItem.copy(id = 0))
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        localHistory.find { it.uuid == cloudItem.uuid }?.let {
                            repo.deleteHistoryEntryLocallyOnly(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing remote history changes", e)
        } finally {
            remoteChangeCounter.decrementAndGet()
        }
    }

    /**
     * FUNCTIONALITY: Pushes a local product change up to the Firestore cloud.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: Coroutine launch using 'await()' for asynchronous network write.
     */
    fun pushProductToCloud(product: Product) {
        // CODE STRUCTURE: Early return if change was triggered BY sync (prevents infinite loops)
        if (isApplyingRemoteChange) return
        val userId = getUserId() ?: return
        val db = firestore ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("users").document(userId)
                    .collection("products").document(product.uuid)
                    .set(product, SetOptions.merge())
                    .await()
                Log.d(TAG, "Pushed product to cloud: ${product.uuid}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push product ${product.uuid}", e)
            }
        }
    }

    /**
     * FUNCTIONALITY: Deletes a product from Firestore.
     * USE OF DATA: Accepts product 'uuid' (String).
     * USE OF CODE STRUCTURES: Coroutine-wrapped Firestore 'delete()' call.
     */
    fun deleteProductFromCloud(productUuid: String) {
        if (isApplyingRemoteChange) return
        val userId = getUserId() ?: return
        val db = firestore ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("users").document(userId)
                    .collection("products").document(productUuid)
                    .delete()
                    .await()
                Log.d(TAG, "Deleted product from cloud: $productUuid")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete product $productUuid", e)
            }
        }
    }

    fun pushHistoryToCloud(history: History) {
        if (isApplyingRemoteChange) return
        val userId = getUserId() ?: return
        val db = firestore ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("users").document(userId)
                    .collection("history").document(history.uuid)
                    .set(history, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push history ${history.uuid}", e)
            }
        }
    }

    fun deleteHistoryFromCloud(historyUuid: String) {
        if (isApplyingRemoteChange) return
        val userId = getUserId() ?: return
        val db = firestore ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("users").document(userId)
                    .collection("history").document(historyUuid)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete history $historyUuid", e)
            }
        }
    }

    /**
     * FUNCTIONALITY: Performs a full wipe of all user data stored in the cloud.
     * USE OF DATA: Iterates through Firestore collections. Returns status via 'onComplete' callback.
     * USE OF CODE STRUCTURES: Sequential collection fetching followed by a 'batch' delete operation 
     * and UI thread callback switch.
     */
    fun deleteCloudData(onComplete: (Boolean) -> Unit) {
        val userId = getUserId() ?: return
        val db = firestore ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val products = db.collection("users").document(userId).collection("products").get().await()
                val history = db.collection("users").document(userId).collection("history").get().await()
                
                // USE OF CODE STRUCTURES: Firestore Batch structure for efficient multiple deletions
                val batch = db.batch()
                products.forEach { batch.delete(it.reference) }
                history.forEach { batch.delete(it.reference) }
                batch.commit().await()
                
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Full cloud wipe failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
}