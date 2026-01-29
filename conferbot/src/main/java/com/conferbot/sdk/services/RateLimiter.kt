package com.conferbot.sdk.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe rate limiter using sliding window algorithm
 *
 * @param maxRequests Maximum number of requests allowed in the time window
 * @param perMillis Time window duration in milliseconds
 */
class RateLimiter(
    private val maxRequests: Int,
    private val perMillis: Long
) {
    // Thread-safe queue to track request timestamps
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()

    // Mutex for coordinating access during cleanup and waiting
    private val mutex = Mutex()

    // Metrics tracking
    private val totalRequests = AtomicLong(0)
    private val rejectedRequests = AtomicLong(0)
    private val waitTimeMs = AtomicLong(0)

    /**
     * Acquire a permit, suspending if rate limit is exceeded.
     * This method will wait until a permit becomes available.
     */
    suspend fun acquire() {
        totalRequests.incrementAndGet()

        while (true) {
            // Clean up expired timestamps first
            cleanupExpiredTimestamps()

            mutex.withLock {
                val currentTime = System.currentTimeMillis()

                if (requestTimestamps.size < maxRequests) {
                    // We have capacity, add timestamp and proceed
                    requestTimestamps.add(currentTime)
                    return
                }

                // Calculate wait time based on oldest request
                val oldestTimestamp = requestTimestamps.peek()
                if (oldestTimestamp != null) {
                    val waitTime = (oldestTimestamp + perMillis) - currentTime
                    if (waitTime > 0) {
                        waitTimeMs.addAndGet(waitTime)
                        // Release lock before waiting
                        delay(waitTime)
                    }
                }
            }
        }
    }

    /**
     * Try to acquire a permit without waiting.
     * Returns true if permit was acquired, false if rate limit exceeded.
     */
    fun tryAcquire(): Boolean {
        totalRequests.incrementAndGet()

        // Clean up expired timestamps
        cleanupExpiredTimestamps()

        val currentTime = System.currentTimeMillis()

        // Check if we have capacity
        if (requestTimestamps.size >= maxRequests) {
            rejectedRequests.incrementAndGet()
            return false
        }

        // Try to add timestamp
        requestTimestamps.add(currentTime)

        // Double-check we didn't exceed (race condition mitigation)
        if (requestTimestamps.size > maxRequests) {
            // Remove our timestamp if we exceeded
            requestTimestamps.poll()
            rejectedRequests.incrementAndGet()
            return false
        }

        return true
    }

    /**
     * Try to acquire a permit, waiting up to the specified timeout.
     * Returns true if permit was acquired, false if timeout exceeded.
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     */
    suspend fun tryAcquire(timeoutMs: Long): Boolean {
        totalRequests.incrementAndGet()

        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            cleanupExpiredTimestamps()

            mutex.withLock {
                val currentTime = System.currentTimeMillis()

                if (requestTimestamps.size < maxRequests) {
                    requestTimestamps.add(currentTime)
                    return true
                }

                val oldestTimestamp = requestTimestamps.peek()
                if (oldestTimestamp != null) {
                    val waitTime = (oldestTimestamp + perMillis) - currentTime
                    val remainingTimeout = deadline - currentTime

                    if (waitTime > 0 && waitTime <= remainingTimeout) {
                        waitTimeMs.addAndGet(waitTime)
                        delay(waitTime)
                    } else if (waitTime > remainingTimeout) {
                        // Not enough time to wait
                        rejectedRequests.incrementAndGet()
                        return false
                    }
                }
            }
        }

        rejectedRequests.incrementAndGet()
        return false
    }

    /**
     * Get current number of requests in the window
     */
    fun getCurrentCount(): Int {
        cleanupExpiredTimestamps()
        return requestTimestamps.size
    }

    /**
     * Get remaining permits available
     */
    fun getAvailablePermits(): Int {
        cleanupExpiredTimestamps()
        return (maxRequests - requestTimestamps.size).coerceAtLeast(0)
    }

    /**
     * Check if rate limit is currently exceeded
     */
    fun isLimitExceeded(): Boolean {
        cleanupExpiredTimestamps()
        return requestTimestamps.size >= maxRequests
    }

    /**
     * Get time until next permit is available (in milliseconds)
     * Returns 0 if permits are available now
     */
    fun getTimeUntilNextPermit(): Long {
        cleanupExpiredTimestamps()

        if (requestTimestamps.size < maxRequests) {
            return 0
        }

        val oldestTimestamp = requestTimestamps.peek() ?: return 0
        val currentTime = System.currentTimeMillis()
        val waitTime = (oldestTimestamp + perMillis) - currentTime

        return waitTime.coerceAtLeast(0)
    }

    /**
     * Reset the rate limiter, clearing all tracked requests
     */
    fun reset() {
        requestTimestamps.clear()
    }

    /**
     * Get rate limiter statistics
     */
    fun getStats(): RateLimiterStats {
        cleanupExpiredTimestamps()
        return RateLimiterStats(
            totalRequests = totalRequests.get(),
            rejectedRequests = rejectedRequests.get(),
            totalWaitTimeMs = waitTimeMs.get(),
            currentWindowSize = requestTimestamps.size,
            maxRequests = maxRequests,
            windowMs = perMillis
        )
    }

    /**
     * Reset statistics counters
     */
    fun resetStats() {
        totalRequests.set(0)
        rejectedRequests.set(0)
        waitTimeMs.set(0)
    }

    /**
     * Remove timestamps that are outside the sliding window
     */
    private fun cleanupExpiredTimestamps() {
        val cutoffTime = System.currentTimeMillis() - perMillis

        // Remove all timestamps older than the window
        while (true) {
            val oldest = requestTimestamps.peek() ?: break
            if (oldest < cutoffTime) {
                requestTimestamps.poll()
            } else {
                break
            }
        }
    }
}

/**
 * Rate limiter statistics
 */
data class RateLimiterStats(
    val totalRequests: Long,
    val rejectedRequests: Long,
    val totalWaitTimeMs: Long,
    val currentWindowSize: Int,
    val maxRequests: Int,
    val windowMs: Long
) {
    val acceptedRequests: Long get() = totalRequests - rejectedRequests
    val rejectionRate: Double get() = if (totalRequests > 0) rejectedRequests.toDouble() / totalRequests else 0.0
    val averageWaitTimeMs: Double get() = if (acceptedRequests > 0) totalWaitTimeMs.toDouble() / acceptedRequests else 0.0
    val utilizationRate: Double get() = currentWindowSize.toDouble() / maxRequests
}

/**
 * Factory for creating common rate limiter configurations
 */
object RateLimiterFactory {
    /**
     * Create a rate limiter for typical API usage (100 requests per minute)
     */
    fun createDefault(): RateLimiter {
        return RateLimiter(maxRequests = 100, perMillis = 60_000)
    }

    /**
     * Create a rate limiter for high-throughput scenarios (1000 requests per minute)
     */
    fun createHighThroughput(): RateLimiter {
        return RateLimiter(maxRequests = 1000, perMillis = 60_000)
    }

    /**
     * Create a rate limiter for low-frequency APIs (10 requests per minute)
     */
    fun createConservative(): RateLimiter {
        return RateLimiter(maxRequests = 10, perMillis = 60_000)
    }

    /**
     * Create a rate limiter matching a "requests per second" specification
     */
    fun createPerSecond(requestsPerSecond: Int): RateLimiter {
        return RateLimiter(maxRequests = requestsPerSecond, perMillis = 1000)
    }

    /**
     * Create a rate limiter matching a "requests per minute" specification
     */
    fun createPerMinute(requestsPerMinute: Int): RateLimiter {
        return RateLimiter(maxRequests = requestsPerMinute, perMillis = 60_000)
    }

    /**
     * Create a rate limiter matching a "requests per hour" specification
     */
    fun createPerHour(requestsPerHour: Int): RateLimiter {
        return RateLimiter(maxRequests = requestsPerHour, perMillis = 3_600_000)
    }
}
