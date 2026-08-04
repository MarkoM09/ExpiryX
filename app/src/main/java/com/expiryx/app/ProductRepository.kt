package com.expiryx.app

import androidx.lifecycle.LiveData

/**
 * FUNCTIONALITY: Coordinates data operations across local (Room) and remote (Cloud) sources, 
 * abstracting database logic from the ViewModels.
 * USE OF DATA: Manages 'Product' and 'History' entities. Utilizes 'ProductDao' and 'HistoryDao' 
 * for persistence and 'AccountManager' for synchronization.
 * USE OF CODE STRUCTURES: Employs 'suspend' functions for asynchronous execution and 
 * data mapping logic to transform products into history entries during deletion or usage.
 */
class ProductRepository(
    private val productDao: ProductDao,
    private val historyDao: HistoryDao
) {
    /**
     * FUNCTIONALITY: Exposes streams of all products and history records.
     * USE OF DATA: Returns 'LiveData' collections for reactive UI updates.
     * USE OF CODE STRUCTURES: Delegates directly to DAO query methods.
     */
    val allProducts: LiveData<List<Product>> = productDao.getAllProducts()
    val allHistory: LiveData<List<History>> = historyDao.getAllHistory()

    /**
     * FUNCTIONALITY: Persists a new product locally and triggers a cloud sync.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: 'if/else' selection to ensure 'dateModified' is initialized, 
     * followed by sequential local save and cloud push.
     */
    suspend fun insertProduct(product: Product) {
        // USE OF CODE STRUCTURES: Selection to handle initial metadata creation
        val updatedProduct = if (product.dateModified == null) {
            product.copy(dateModified = System.currentTimeMillis())
        } else {
            product
        }
        productDao.insert(updatedProduct)
        AccountManager.pushProductToCloud(updatedProduct)
    }

    /**
     * FUNCTIONALITY: Updates an existing product and syncs changes.
     * USE OF DATA: Accepts 'Product' with updated fields.
     * USE OF CODE STRUCTURES: Creates a copy with a new 'dateModified' timestamp.
     */
    suspend fun update(product: Product) {
        val updatedProduct = product.copy(dateModified = System.currentTimeMillis())
        productDao.update(updatedProduct)
        AccountManager.pushProductToCloud(updatedProduct)
    }

    suspend fun insertHistory(history: History) {
        historyDao.insert(history)
        AccountManager.pushHistoryToCloud(history)
    }

    /**
     * FUNCTIONALITY: Deletes a product and archives it into the history table as a 'Deleted' event.
     * USE OF DATA: Converts a 'Product' object into a 'History' object.
     * USE OF CODE STRUCTURES: Sequential logic: instantiate history -> delete product -> insert history -> sync.
     */
    suspend fun deleteProduct(product: Product) {
        // DATA: Mapping product fields to a new History record
        val historyEntry = History(
            productUuid = product.uuid,
            productName = product.name,
            expirationDate = product.expirationDate,
            quantity = product.quantity,
            weight = product.weight,
            weightUnit = product.weightUnit,
            brand = product.brand,
            imageUri = product.imageUri,
            isFavorite = product.isFavorite,
            action = "Deleted",
            timestamp = System.currentTimeMillis(),
            barcode = product.barcode, 
            dateAdded = product.dateAdded, 
            dateModified = System.currentTimeMillis()
        )
        
        productDao.delete(product)
        historyDao.insert(historyEntry)
        
        AccountManager.deleteProductFromCloud(product.uuid)
        AccountManager.pushHistoryToCloud(historyEntry)
    }

    /**
     * FUNCTIONALITY: Marks a product as used, archiving it as a 'Used' event.
     * USE OF DATA: Transforms 'Product' to 'History'.
     * USE OF CODE STRUCTURES: Sequential operations for local database and cloud cleanup.
     */
    suspend fun markAsUsed(product: Product) {
        val historyEntry = History(
            productUuid = product.uuid,
            productName = product.name,
            expirationDate = product.expirationDate,
            quantity = product.quantity,
            weight = product.weight,
            weightUnit = product.weightUnit,
            brand = product.brand,
            imageUri = product.imageUri,
            isFavorite = product.isFavorite,
            action = "Used",
            timestamp = System.currentTimeMillis(),
            barcode = product.barcode, 
            dateAdded = product.dateAdded, 
            dateModified = System.currentTimeMillis()
        )
        
        productDao.delete(product)
        historyDao.insert(historyEntry)
        
        AccountManager.deleteProductFromCloud(product.uuid)
        AccountManager.pushHistoryToCloud(historyEntry)
    }

    /**
     * FUNCTIONALITY: Batch processes products to find and archive those that expired more than a week ago.
     * USE OF DATA: Compares 'expirationDate' (Long) against the current timestamp.
     * USE OF CODE STRUCTURES: Uses a 'for' loop for iteration and 'if' selection for expiry and grace period checks.
     */
    suspend fun archiveExpiredProducts() {
        val now = System.currentTimeMillis()
        val all = productDao.getAllProductsNow()
        // USE OF CODE STRUCTURES: Iteration structure to evaluate every product's status
        for (p in all) {
            val expiry = p.expirationDate ?: continue
            // CODE STRUCTURE: Calculation check for 7-day grace period
            if (now - expiry >= (7L * 24 * 60 * 60 * 1000)) {
                val historyEntry = History(
                    productUuid = p.uuid,
                    productName = p.name,
                    expirationDate = p.expirationDate,
                    quantity = p.quantity,
                    weight = p.weight,
                    weightUnit = p.weightUnit,
                    brand = p.brand,
                    imageUri = p.imageUri,
                    isFavorite = p.isFavorite,
                    action = "Expired",
                    timestamp = now,
                    barcode = p.barcode, 
                    dateAdded = p.dateAdded, 
                    dateModified = now
                )
                productDao.delete(p)
                historyDao.insert(historyEntry)
                
                AccountManager.deleteProductFromCloud(p.uuid)
                AccountManager.pushHistoryToCloud(historyEntry)
            }
        }
    }

    suspend fun getAllProductsNow(): List<Product> = productDao.getAllProductsNow()
    suspend fun getAllHistoryNow(): List<History> = historyDao.getAllHistoryNow()
    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)

    suspend fun clearAllProducts() = productDao.clearAllProducts()
    suspend fun clearAllHistory() = historyDao.clearAllHistory()

    // Sync helpers to avoid loops
    suspend fun insertProductLocallyOnly(product: Product) = productDao.insert(product)
    suspend fun updateProductLocallyOnly(product: Product) = productDao.update(product)
    suspend fun deleteProductLocallyOnly(product: Product) = productDao.delete(product)
    
    suspend fun insertHistoryLocallyOnly(history: History) = historyDao.insert(history)
    suspend fun deleteHistoryEntryLocallyOnly(history: History) = historyDao.deleteById(history.id)

    suspend fun deleteHistoryEntry(history: History) {
        historyDao.deleteById(history.id)
        AccountManager.deleteHistoryFromCloud(history.uuid)
    }

    /**
     * FUNCTIONALITY: Re-adds a product from the history log back into active inventory.
     * USE OF DATA: Maps 'History' fields back to a new 'Product' instance.
     * USE OF CODE STRUCTURES: Sequential insertion and deletion across local and cloud sources.
     */
    suspend fun restoreFromHistory(history: History) {
        val product = Product(
            id = 0, 
            uuid = history.productUuid ?: java.util.UUID.randomUUID().toString(),
            name = history.productName,
            expirationDate = history.expirationDate,
            quantity = history.quantity,
            weight = history.weight,
            weightUnit = history.weightUnit,
            brand = history.brand,
            imageUri = history.imageUri,
            isFavorite = history.isFavorite,
            barcode = history.barcode, 
            dateAdded = history.dateAdded, 
            dateModified = System.currentTimeMillis()
        )
        productDao.insert(product)
        historyDao.deleteById(history.id)
        
        AccountManager.pushProductToCloud(product)
        AccountManager.deleteHistoryFromCloud(history.uuid)
    }

    /**
     * FUNCTIONALITY: Restores a historical item as a new product while overriding its expiration date.
     * USE OF DATA: Accepts 'History' and 'newExpiry' (Long).
     * USE OF CODE STRUCTURES: Instantiates a modified 'Product' copy for re-insertion.
     */
    suspend fun restoreWithNewExpiry(history: History, newExpiry: Long) {
        val product = Product(
            id = 0, 
            uuid = history.productUuid ?: java.util.UUID.randomUUID().toString(),
            name = history.productName,
            expirationDate = newExpiry,
            quantity = history.quantity,
            weight = history.weight,
            weightUnit = history.weightUnit,
            brand = history.brand,
            imageUri = history.imageUri,
            isFavorite = history.isFavorite,
            barcode = history.barcode, 
            dateAdded = history.dateAdded, 
            dateModified = System.currentTimeMillis()
        )
        productDao.insert(product)
        historyDao.deleteById(history.id)
        
        AccountManager.pushProductToCloud(product)
        AccountManager.deleteHistoryFromCloud(history.uuid)
    }
}