package com.expiryx.app

import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FUNCTIONALITY: Acts as a bridge between the UI and the data layer, managing the lifecycle 
 * of product data and exposing it through observable LiveData.
 * USE OF DATA: Injects a 'ProductRepository' for data operations. Exposes 'allProducts' 
 * as 'LiveData<List<Product>>' for UI observation.
 * USE OF CODE STRUCTURES: Utilizes 'viewModelScope' for launching coroutines on the 
 * IO dispatcher to ensure non-blocking database interactions.
 */
class ProductViewModel(private val repository: ProductRepository) : ViewModel() {

    /**
     * FUNCTIONALITY: Exposes the full list of products from the repository to the UI.
     * USE OF DATA: Observable 'LiveData' containing a 'List' of 'Product' objects.
     * USE OF CODE STRUCTURES: Directly delegates to the repository's LiveData property.
     */
    val allProducts: LiveData<List<Product>> = repository.allProducts

    /**
     * FUNCTIONALITY: Initiates a database insertion for a new product.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: Launches a coroutine in 'viewModelScope' using 'Dispatchers.IO' 
     * to perform the write operation asynchronously.
     */
    fun insert(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertProduct(product)
    }

    /**
     * FUNCTIONALITY: Updates an existing product's information in the database.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: Executes the update operation within an IO-bound coroutine.
     */
    fun update(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(product)
    }

    /**
     * FUNCTIONALITY: Deletes a specific product from the database.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: Executes the deletion within an IO-bound coroutine.
     */
    fun delete(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteProduct(product)
    }

    /**
     * FUNCTIONALITY: Marks a product as used, which typically moves it to history.
     * USE OF DATA: Accepts a 'Product' object.
     * USE OF CODE STRUCTURES: Asynchronous execution of the 'markAsUsed' business logic 
     * via the repository.
     */
    fun markAsUsed(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        repository.markAsUsed(product)
    }

    /**
     * FUNCTIONALITY: Checks for and archives all products that have passed their expiration date.
     * USE OF DATA: None.
     * USE OF CODE STRUCTURES: IO-bound coroutine launch to process batch archival logic.
     */
    fun archiveExpiredProducts() = viewModelScope.launch(Dispatchers.IO) {
        repository.archiveExpiredProducts()
    }
}

/**
 * FUNCTIONALITY: Factory class to instantiate the ProductViewModel with its required dependencies.
 * USE OF DATA: Ingests the 'ProductRepository' needed by the ViewModel.
 * USE OF CODE STRUCTURES: Overrides 'create' and uses 'isAssignableFrom' check for type-safe 
 * ViewModel instantiation.
 */
class ProductViewModelFactory(
    private val repository: ProductRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // CODE STRUCTURE: Type selection check to ensure the correct ViewModel is being created
        if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProductViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}