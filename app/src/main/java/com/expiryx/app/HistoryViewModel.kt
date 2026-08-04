package com.expiryx.app

import androidx.lifecycle.*
import kotlinx.coroutines.launch

/**
 * FUNCTIONALITY: Manages the presentation logic for historical product logs, 
 * allowing the UI to observe, delete, or restore historical entries.
 * USE OF DATA: Utilizes 'ProductRepository' to access history data. Exposes 
 * 'allHistory' as 'LiveData<List<History>>'.
 * USE OF CODE STRUCTURES: Employs 'viewModelScope' to handle background tasks 
 * like restoration or deletion without blocking the main UI thread.
 */
class HistoryViewModel(private val repository: ProductRepository) : ViewModel() {
    /**
     * FUNCTIONALITY: Provides a stream of all historical events from the database.
     * USE OF DATA: 'LiveData' containing a 'List' of 'History' objects.
     * USE OF CODE STRUCTURES: Maps repository-level data for UI observation.
     */
    val allHistory: LiveData<List<History>> = repository.allHistory

    /**
     * FUNCTIONALITY: Permanently removes an entry from the history log.
     * USE OF DATA: Accepts a 'History' record.
     * USE OF CODE STRUCTURES: Launches a coroutine to execute the deletion asynchronously.
     */
    fun permanentlyDelete(history: History) = viewModelScope.launch {
        repository.deleteHistoryEntry(history)
    }

    /**
     * FUNCTIONALITY: Restores a previously deleted product back into the active inventory.
     * USE OF DATA: Ingests a 'History' object.
     * USE OF CODE STRUCTURES: Coroutine block to process complex restoration logic in the repository.
     */
    fun restoreDeleted(history: History) = viewModelScope.launch {
        repository.restoreFromHistory(history)
    }

    /**
     * FUNCTIONALITY: Reverses a "marked as used" action, returning the item to active status.
     * USE OF DATA: Ingests a 'History' object.
     * USE OF CODE STRUCTURES: Coroutine execution of the restoration algorithm.
     */
    fun unuse(history: History) = viewModelScope.launch {
        repository.restoreFromHistory(history)
    }

    /**
     * FUNCTIONALITY: Restores a product from history while simultaneously assigning it a new expiration date.
     * USE OF DATA: Ingests a 'History' object and a 'newExpiry' (Long) timestamp.
     * USE OF CODE STRUCTURES: Coroutine launch passing multiple parameters to the repository logic.
     */
    fun changeExpiry(history: History, newExpiry: Long) = viewModelScope.launch {
        repository.restoreWithNewExpiry(history, newExpiry)
    }
}

/**
 * FUNCTIONALITY: Factory for creating HistoryViewModel with its mandatory repository dependency.
 * USE OF DATA: Passes 'ProductRepository' to the ViewModel constructor.
 * USE OF CODE STRUCTURES: Standard boilerplate for ViewModel creation with type validation.
 */
class HistoryViewModelFactory(private val repository: ProductRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // CODE STRUCTURE: Validation check to ensure correct ViewModel class instantiation
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}