package com.expiryx.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FUNCTIONALITY: Performs complex arithmetic and data aggregation on product and history 
 * lists to generate analytical insights for the Stats UI.
 * USE OF DATA: Processes 'List<Product>' and 'List<History>', 'TimeRange' enums, 
 * and returns a 'StatsUiState' data object containing calculated percentages and counts.
 * USE OF CODE STRUCTURES: Employs functional collection operators (.filter, .map, .count, .groupBy) 
 * for data transformation and 'if/else' selection for range-based filtering.
 */
object StatsCalculator {

    private const val DAY_MS = 86_400_000L // Constant for one day in milliseconds
    private const val UNKNOWN_BRAND = "Unknown" // Fallback label for products without a brand

    /**
     * FUNCTIONALITY: Main entry point for computing all dashboard statistics.
     * USE OF DATA: Ingests raw lists of products and history, outputs 'StatsUiState'.
     * USE OF CODE STRUCTURES: Uses iterative filtering logic to isolate data within 
     * specific time windows and sequential calculation blocks for various KPIs.
     */
    fun compute(
        products: List<Product>,
        history: List<History>,
        timeRange: TimeRange,
        now: Long = System.currentTimeMillis(),
    ): StatsUiState {
        // CODE STRUCTURE: Selection structure to determine the temporal scope of the analysis
        val rangeStart = timeRange.startMillis(now)
        val filteredHistory = if (rangeStart == null) {
            history
        } else {
            // USE OF CODE STRUCTURES: Lambda predicate filtering based on timestamp comparison
            history.filter { it.timestamp >= rangeStart }
        }

        val isEmpty = products.isEmpty() && history.isEmpty()
        val hasHistoryInRange = filteredHistory.isNotEmpty()

        // DATA TRANSFORMATION: Categorizing and counting actions using predicate filters
        val used = filteredHistory.count { it.action == "Used" }
        val expired = filteredHistory.count { it.action == "Expired" }
        val deleted = filteredHistory.count { it.action == "Deleted" }
        val totalActions = used + expired + deleted

        val wasteRate = safePercent(expired, totalActions)
        val itemsAddedInRange = countAddedInRange(products, rangeStart)

        // USE OF CODE STRUCTURES: Functional mapping and filtering to calculate lifecycle averages
        val usedItems = filteredHistory.filter { it.action == "Used" }
        val avgDaysToUse = usedItems
            .mapNotNull { item ->
                val days = (item.timestamp - item.dateAdded).toFloat() / DAY_MS
                // CODE STRUCTURE: Range check to discard illogical negative dates
                if (days >= 0) days else null
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

        val consumedBeforeExpiry = usedItems.count { item ->
            val expiry = item.expirationDate ?: return@count false
            item.timestamp <= expiry
        }
        val consumedBeforeExpiryPercent = safePercent(consumedBeforeExpiry, usedItems.size)

        val withBarcode = products.count { !it.barcode.isNullOrBlank() }
        val barcodeScanRate = safePercent(withBarcode, products.size)

        // Return the compiled UI state object
        return StatsUiState(
            timeRange = timeRange,
            isLoading = false,
            isEmpty = isEmpty,
            hasHistoryInRange = hasHistoryInRange,
            activeItems = products.size,
            activeQuantity = products.sumOf { it.quantity },
            consumedCount = used,
            expiredCount = expired,
            deletedCount = deleted,
            wasteRate = wasteRate,
            itemsAddedInRange = itemsAddedInRange,
            avgDaysToUse = avgDaysToUse,
            consumedBeforeExpiryPercent = consumedBeforeExpiryPercent,
            lifecycleUsed = used,
            lifecycleExpired = expired,
            lifecycleDeleted = deleted,
            weeklyActivity = buildWeeklyActivity(products, filteredHistory, rangeStart, now),
            expiryBuckets = ExpiryBucketUtils.countByBucket(products, now),
            topBrandsConsumed = topBrands(filteredHistory, "Used"),
            topBrandsWasted = topBrands(filteredHistory, "Expired"),
            favoritesCount = products.count { it.isFavorite },
            barcodeScanRate = barcodeScanRate,
            noExpiryCount = products.count { it.expirationDate == null },
            totalWeightG = products.filter { it.weightUnit == "g" }.sumOf { it.weight ?: 0 },
            totalVolumeMl = products.filter { it.weightUnit == "ml" }.sumOf { it.weight ?: 0 },
        )
    }

    /**
     * FUNCTIONALITY: Counts how many products were added within a specific time period.
     * USE OF DATA: Reads 'List<Product>', returns 'Int'.
     * USE OF CODE STRUCTURES: Selection structure to handle 'null' (infinite) ranges.
     */
    private fun countAddedInRange(
        products: List<Product>,
        rangeStart: Long?,
    ): Int {
        if (rangeStart == null) {
            return products.size
        }
        return products.count { it.dateAdded >= rangeStart }
    }

    /**
     * FUNCTIONALITY: Identifies the most frequently occurring brands for a specific action (e.g., Waste).
     * USE OF DATA: Groups 'History' by 'brand' string and counts occurrences.
     * USE OF CODE STRUCTURES: Chain of functional operations: filter -> groupBy -> map -> sortedBy -> take.
     */
    private fun topBrands(history: List<History>, action: String, limit: Int = 5): List<BrandStat> {
        return history
            .filter { it.action == action }
            .groupBy { it.brand?.takeIf { b -> b.isNotBlank() } ?: UNKNOWN_BRAND }
            .map { (brand, items) -> BrandStat(brand, items.size) }
            .sortedByDescending { it.count }
            .take(limit)
    }

    /**
     * FUNCTIONALITY: Generates data points for the weekly activity chart.
     * USE OF DATA: Iterates over time chunks, returning 'List<WeeklyActivity>'.
     * USE OF CODE STRUCTURES: Uses a 'while' loop to increment temporal cursors and 
     * nested 'count' predicates to bucket events into weeks.
     */
    private fun buildWeeklyActivity(
        products: List<Product>,
        history: List<History>,
        rangeStart: Long?,
        now: Long,
    ): List<WeeklyActivity> {
        val effectiveStart = rangeStart ?: minOf(
            products.minOfOrNull { it.dateAdded } ?: now,
            history.minOfOrNull { it.timestamp } ?: now,
        )
        // CODE STRUCTURE: Guard clause to prevent processing future or empty ranges
        if (effectiveStart >= now) return emptyList()

        val weekStarts = mutableListOf<Long>()
        var cursor = ExpiryBucketUtils.getStartOfDay(effectiveStart)
        val end = ExpiryBucketUtils.getStartOfDay(now)
        
        // USE OF CODE STRUCTURES: Iteration structure to build a timeline of week-start timestamps
        while (cursor <= end) {
            weekStarts.add(cursor)
            cursor += 7 * DAY_MS
        }
        if (weekStarts.isEmpty()) weekStarts.add(end)

        val labelFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        return weekStarts.map { weekStart ->
            val weekEnd = weekStart + 7 * DAY_MS
            val label = labelFormat.format(weekStart)
            WeeklyActivity(
                weekLabel = label,
                weekStartMillis = weekStart,
                added = products.count { it.dateAdded in weekStart until weekEnd },
                used = history.count { it.action == "Used" && it.timestamp in weekStart until weekEnd },
                expired = history.count { it.action == "Expired" && it.timestamp in weekStart until weekEnd },
                deleted = history.count { it.action == "Deleted" && it.timestamp in weekStart until weekEnd },
            )
        }.takeLast(12)
    }

    /**
     * FUNCTIONALITY: Safely calculates percentage to avoid division-by-zero errors.
     * USE OF DATA: Accepts two 'Int' values, returns a 'Float'.
     * USE OF CODE STRUCTURES: 'if' selection for safety check.
     */
    private fun safePercent(numerator: Int, denominator: Int): Float {
        if (denominator <= 0) {
            android.util.Log.d("ExpiryX_Debug", "[TC-12] Guarded divide-by-zero: Denominator is 0. Returning 0%")
            return 0f
        }
        return (numerator.toFloat() / denominator) * 100f
    }
}