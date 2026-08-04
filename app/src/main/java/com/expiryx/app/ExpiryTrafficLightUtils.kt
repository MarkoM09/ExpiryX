package com.expiryx.app

import kotlin.math.floor

/**
 * FUNCTIONALITY: Enumerates the different visual urgency states for product expiration.
 * USE OF DATA: Acts as a custom data type for classification results.
 */
enum class ExpiryTrafficLight {
    EXPIRED,
    URGENT,
    SAFE,
    UNKNOWN,
}

/**
 * FUNCTIONALITY: Provides low-level temporal calculation and classification for the 
 * "traffic light" (Red/Yellow/Green) expiry status system.
 * USE OF DATA: Processes 'Long' epoch timestamps and returns categorized 'ExpiryTrafficLight' enums.
 * USE OF CODE STRUCTURES: Utilizes an 'object' singleton, null-safety selection (Elvis operator), 
 * and 'when' branching logic for numeric range classification.
 */
object ExpiryTrafficLightUtils {

    private const val DAY_MS = 86_400_000L // Constant for one day in milliseconds

    /**
     * FUNCTIONALITY: Calculates the difference in days between two timestamps, 
     * normalized to the start of each day.
     * USE OF DATA: Consumes 'expiryMillis' and 'now' (Longs), returns 'Long?'.
     * USE OF CODE STRUCTURES: Standard arithmetic with 'floor' conversion and 
     * null-safe early return.
     */
    fun dayDiff(expiryMillis: Long?, now: Long = System.currentTimeMillis()): Long? {
        // CODE STRUCTURE: Guard clause handling products with no set expiration
        expiryMillis ?: return null
        val startToday = ExpiryBucketUtils.getStartOfDay(now)
        val startExpiry = ExpiryBucketUtils.getStartOfDay(expiryMillis)
        return floor((startExpiry - startToday).toDouble() / DAY_MS).toLong()
    }

    /**
     * FUNCTIONALITY: Maps a temporal day difference to a specific urgency classification.
     * USE OF DATA: Takes 'expiryMillis' (Long), returns 'ExpiryTrafficLight' enum.
     * USE OF CODE STRUCTURES: 'when' selection structure evaluating numeric ranges 
     * (e.g., < 0 for expired, <= 3 for urgent).
     */
    fun classify(expiryMillis: Long?, now: Long = System.currentTimeMillis()): ExpiryTrafficLight {
        val diff = dayDiff(expiryMillis, now) ?: return ExpiryTrafficLight.UNKNOWN
        // USE OF CODE STRUCTURES: Selection structure determining urgency bucket
        return when {
            diff < 0 -> ExpiryTrafficLight.EXPIRED
            diff <= 3 -> ExpiryTrafficLight.URGENT
            else -> ExpiryTrafficLight.SAFE
        }
    }
}