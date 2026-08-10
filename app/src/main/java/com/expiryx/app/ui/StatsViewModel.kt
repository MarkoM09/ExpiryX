package com.expiryx.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FUNCTIONALITY: Manages analytics state, transforms raw Room database streams into calculated statistics,
 * and exposes reactive UI data streams for StatsActivity.
 * USE OF DATA: Combines LiveData<List<Product>>, LiveData<List<History>>, and MutableLiveData<TimeRange>
 * to emit recalculated 'StatsUiState' payloads.
 * USE OF CODE STRUCTURES: Employs 'MediatorLiveData' to observe multiple source streams simultaneously,
 * uses coroutines for background calculation, and implements 'ViewModelProvider.Factory' for dependency injection.
 */
class StatsViewModel(private val repository: ProductRepository) : ViewModel() {

    // USE OF DATA: Holds the currently active temporal filter selected by the user (Defaults to 30 Days)
    private val timeRange = MutableLiveData(TimeRange.DAYS_30)

    // USE OF DATA: Composite MediatorLiveData combining multiple upstream database source streams into UI state
    private val _statsState = MediatorLiveData<StatsUiState>().apply {
        value = StatsUiState(isLoading = true)
    }
    val statsState: LiveData<StatsUiState> = _statsState

    private var latestProducts: List<Product> = emptyList()
    private var latestHistory: List<History> = emptyList()

    init {
        // CODE STRUCTURE: Attaches sources to MediatorLiveData to trigger recalculations when data changes
        _statsState.addSource(repository.allProducts) { products ->
            latestProducts = products ?: emptyList()
            recompute()
        }
        _statsState.addSource(repository.allHistory) { history ->
            latestHistory = history ?: emptyList()
            recompute()
        }
        _statsState.addSource(timeRange) {
            recompute()
        }
    }

    /**
     * FUNCTIONALITY: Updates the selected temporal filter window and triggers analytics recalculation.
     * USE OF DATA: Accepts TimeRange enum parameter and updates internal MutableLiveData value.
     * USE OF CODE STRUCTURES: Selection structure checking if new selection differs from current active range.
     */
    fun setTimeRange(range: TimeRange) {
        if (timeRange.value != range) {
            timeRange.value = range
        }
    }

    /**
     * FUNCTIONALITY: Executes central calculation algorithms by invoking StatsCalculator on a background thread.
     * USE OF DATA: Passes snapshots of products and history along with the selected 'TimeRange'.
     * USE OF CODE STRUCTURES: Launches a coroutine in 'viewModelScope' using 'Dispatchers.Default' 
     * for CPU-intensive mathematical operations.
     */
    private fun recompute() {
        val range = timeRange.value ?: TimeRange.DAYS_30
        
        // CODE STRUCTURE: Initial state assignment to show loading indicator
        _statsState.value = _statsState.value?.copy(isLoading = true, timeRange = range)
            ?: StatsUiState(isLoading = true, timeRange = range)

        // CODE STRUCTURE: Asynchronous calculation block using Kotlin coroutines
        viewModelScope.launch {
            val state = withContext(Dispatchers.Default) {
                // DATA TRANSFORMATION: Externalizing heavy arithmetic to the StatsCalculator engine
                StatsCalculator.compute(latestProducts, latestHistory, range)
            }
            _statsState.value = state
        }
    }
}

/**
 * FUNCTIONALITY: Factory class to instantiate StatsViewModel with its mandatory repository dependency.
 * USE OF DATA: Ingests the 'ProductRepository' needed by the ViewModel.
 * USE OF CODE STRUCTURES: Overrides 'create' and uses 'isAssignableFrom' check for type-safe instantiation.
 */
class StatsViewModelFactory(private val repository: ProductRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // CODE STRUCTURE: Type selection check to ensure the correct ViewModel is being created
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
