package com.expiryx.app

import kotlin.math.floor

/**
 * FUNCTIONALITY: Provides categorical bucketing for products based on their relative 
 * time to expiration, enabling high-level statistical analysis.
 * USE OF DATA: Consumes 'Long' timestamps, calculates day differences, and groups results 
 * into 'ExpiryBucketStat' objects with localized labels and color resources.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton, functional mapping 
 * (mapNotNull), and lambda predicate matching ('matches') for flexible categorization.
 */
object ExpiryBucketUtils {

    private const val DAY_MS = 86_400_000L // Constant for one day in milliseconds

    /**
     * FUNCTIONALITY: Defines a specific temporal bucket (e.g., "Expired") with its 
     * visual styling and matching criteria.
     * USE OF DATA: Stores 'label' (String), 'colorRes' (Int), and a 'matches' predicate lambda.
     */
    data class BucketDefinition(
        val label: String,
        val colorRes: Int,
        val matches: (dayDiff: Long?) -> Boolean,
    )

    private val bucketDefinitions: List<BucketDefinition> = listOf(
        BucketDefinition("Expired", R.color.red) { it != null && it < 0 },
        BucketDefinition("Expiring today", R.color.orange) { it == 0L },
        BucketDefinition("Expiring tomorrow", R.color.yellow) { it == 1L },
        BucketDefinition("Expiring in 2-3 days", R.color.green) { it != null && it in 2L..3L },
        BucketDefinition("Expiring in 4-14 days", R.color.blue) { it != null && it in 4L..14L },
        BucketDefinition("Expiring in 15-90 days", R.color.grey_600) { it != null && it in 15L..90L },
        BucketDefinition("Expiring in 3-12 months", R.color.purple) { it != null && it in 91L..365L },
        BucketDefinition("Expiring in 1+ year", R.color.gray) { it != null && it > 365L },
        BucketDefinition("No expiry date", R.color.gray) { it == null },
    )

    /**
     * FUNCTIONALITY: Aggregates a list of products into counts per expiry bucket.
     * USE OF DATA: Takes 'List<Product>' and 'now' (Long), returns 'List<ExpiryBucketStat>'.
     * USE OF CODE STRUCTURES: Uses functional 'mapNotNull' and 'count' iteration to 
     * generate a frequency distribution across defined buckets.
     */
    fun countByBucket(products: List<Product>, now: Long = System.currentTimeMillis()): List<ExpiryBucketStat> {
        val startToday = getStartOfDay(now)
        // USE OF CODE STRUCTURES: Functional transformation iterating over bucket definitions
        return bucketDefinitions.mapNotNull { def ->
            val count = products.count { product ->
                // USE OF DATA: Passing calculated day delta into the bucket's matching predicate
                def.matches(dayDiff(product.expirationDate, startToday))
            }
            // CODE STRUCTURE: Only include buckets with at least one item
            if (count > 0) ExpiryBucketStat(def.label, count, def.colorRes) else null
        }
    }

    /**
     * FUNCTIONALITY: Computes the difference in days between a target timestamp and today.
     * USE OF DATA: Accepts nullable 'expiryMillis' and mandatory 'startToday' (Longs).
     * USE OF CODE STRUCTURES: Selection structure (null-check) and mathematical division logic.
     */
    private fun dayDiff(expiryMillis: Long?, startToday: Long): Long? {
        expiryMillis ?: return null
        val startExpiry = getStartOfDay(expiryMillis)
        val diffMs = startExpiry - startToday
        return floor(diffMs.toDouble() / DAY_MS).toLong()
    }

    /**
     * FUNCTIONALITY: Standardizes a timestamp to the very beginning of its day (00:00:00).
     * USE OF DATA: Consumes 'ts' (Long) and returns 'Long'.
     * USE OF CODE STRUCTURES: Uses 'Calendar' instance with sequential field-setting logic.
     */
    fun getStartOfDay(ts: Long): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = ts
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}