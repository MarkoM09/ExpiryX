package com.expiryx.app

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

/**
 * FUNCTIONALITY: Defines core data transfer objects (DTOs), enumerations, and state data classes 
 * used to represent food waste analytics, KPI summaries, category ratios, and timeline metrics.
 * USE OF DATA: Utilizes Enums ('TimeRange'), primitives ('Int', 'Float', 'Long', 'String'), 
 * and custom Kotlin 'data class' objects ('ExpiryBucketStat', 'BrandStat', 'WeeklyActivity', 'StatsUiState')
 * to encapsulate mathematical outputs from the analytics calculator engine.
 * USE OF CODE STRUCTURES: Employs Enum classes with constructor parameters and immutable 
 * Kotlin data classes for structured state representation.
 */

/**
 * FUNCTIONALITY: Defines selectable temporal filtering windows for dashboard calculations (e.g., 7 Days, 30 Days, All Time).
 * USE OF DATA: Stores string resource ID ('labelRes': Int) and optional day offset parameter ('days': Int? - null represents All Time).
 * USE OF CODE STRUCTURES: Enum constructor structure mapping discrete selection options to numeric day limits.
 */
enum class TimeRange(@StringRes val labelRes: Int, val days: Int?) {
    DAYS_7(R.string.stats_range_7d, 7),     // 7-day rolling window for short-term waste tracking
    DAYS_30(R.string.stats_range_30d, 30),  // 30-day window for monthly household waste review
    DAYS_90(R.string.stats_range_90d, 90),  // 90-day window for quarterly household trend evaluation
    YEAR(R.string.stats_range_1y, 365),     // 1-year window for annual review
    ALL(R.string.stats_range_all, null);    // Null days parameter indicates unbounded history aggregation

    /**
     * FUNCTIONALITY: Computes the start timestamp in milliseconds based on the current time and range offset.
     * USE OF DATA: Calculates result using 'DAY_MS' (Long) and 'days' (Int). Returns nullable 'Long'.
     * USE OF CODE STRUCTURES: Early return selection structure for the 'ALL' range.
     */
    fun startMillis(now: Long = System.currentTimeMillis()): Long? {
        days ?: return null
        return now - days * DAY_MS
    }

    companion object {
        private const val DAY_MS = 86_400_000L // Constant for one day in milliseconds
    }
}

/**
 * FUNCTIONALITY: Encapsulates waste distribution data grouped by expiry status buckets.
 * USE OF DATA: Ingests category label String, item counts (Int), and Color res Int.
 * USE OF CODE STRUCTURES: Data class entity used by the stats dashboard to render distribution bar UI elements.
 */
data class ExpiryBucketStat(
    val label: String,
    val count: Int,
    @param:ColorRes val colorRes: Int,
)

/**
 * FUNCTIONALITY: Encapsulates brand-specific waste or consumption metrics for analytics display.
 * USE OF DATA: Holds brand name String and occurrence count Int.
 * USE OF CODE STRUCTURES: Structural object bound directly to StatBrandAdapter ViewHolder instances.
 */
data class BrandStat(
    val brand: String,
    val count: Int,
)

/**
 * FUNCTIONALITY: Represents aggregated activity counts for a specific week in the trend chart.
 * USE OF DATA: Holds epoch timestamp Long and calculated item counts (Int) for different actions.
 * USE OF CODE STRUCTURES: Data class structure consumed by custom graph drawing procedures.
 */
data class WeeklyActivity(
    val weekLabel: String,
    val weekStartMillis: Long,
    val added: Int = 0,
    val used: Int = 0,
    val expired: Int = 0,
    val deleted: Int = 0,
)

/**
 * FUNCTIONALITY: Holds the complete aggregate UI state for the statistics dashboard, including KPIs and chart data.
 * USE OF DATA: Contains total counts (Int), waste ratios (Float), and lists of specific stat objects.
 * USE OF CODE STRUCTURES: Kotlin 'data class' combining multiple metrics into a single immutable payload object.
 */
data class StatsUiState(
    val timeRange: TimeRange = TimeRange.DAYS_30,
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val hasHistoryInRange: Boolean = false,
    val activeItems: Int = 0,
    val activeQuantity: Int = 0,
    val consumedCount: Int = 0,
    val expiredCount: Int = 0,
    val deletedCount: Int = 0,
    val wasteRate: Float = 0f,
    val itemsAddedInRange: Int = 0,
    val avgDaysToUse: Float? = null,
    val consumedBeforeExpiryPercent: Float = 0f,
    val lifecycleUsed: Int = 0,
    val lifecycleExpired: Int = 0,
    val lifecycleDeleted: Int = 0,
    val weeklyActivity: List<WeeklyActivity> = emptyList(),
    val expiryBuckets: List<ExpiryBucketStat> = emptyList(),
    val topBrandsConsumed: List<BrandStat> = emptyList(),
    val topBrandsWasted: List<BrandStat> = emptyList(),
    val favoritesCount: Int = 0,
    val barcodeScanRate: Float = 0f,
    val noExpiryCount: Int = 0,
    val totalWeightG: Int = 0,
    val totalVolumeMl: Int = 0,
)
