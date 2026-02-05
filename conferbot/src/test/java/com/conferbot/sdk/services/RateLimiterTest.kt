package com.conferbot.sdk.services

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for RateLimiter
 * Tests rate limit enforcement, burst handling, and reset timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== BASIC FUNCTIONALITY TESTS ====================

    @Test
    fun `tryAcquire succeeds within rate limit`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)

        // When & Then
        repeat(5) {
            assertThat(limiter.tryAcquire()).isTrue()
        }
    }

    @Test
    fun `tryAcquire fails when limit exceeded`() {
        // Given
        val limiter = RateLimiter(maxRequests = 3, perMillis = 1000)

        // Exhaust the limit
        repeat(3) {
            assertThat(limiter.tryAcquire()).isTrue()
        }

        // When - Try one more
        val result = limiter.tryAcquire()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `tryAcquire succeeds after window expires`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 2, perMillis = 100)

        // Exhaust limit
        assertThat(limiter.tryAcquire()).isTrue()
        assertThat(limiter.tryAcquire()).isTrue()
        assertThat(limiter.tryAcquire()).isFalse()

        // When - Wait for window to expire
        advanceTimeBy(150)

        // Then
        assertThat(limiter.tryAcquire()).isTrue()
    }

    // ==================== ACQUIRE (BLOCKING) TESTS ====================

    @Test
    fun `acquire succeeds within rate limit`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)

        // When & Then - Should not block
        repeat(5) {
            limiter.acquire()
        }
    }

    @Test
    fun `acquire waits when limit exceeded`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 2, perMillis = 1000)

        // Exhaust limit
        limiter.acquire()
        limiter.acquire()

        // When - Third acquire should wait
        val startTime = testScheduler.currentTime
        limiter.acquire()
        val endTime = testScheduler.currentTime

        // Then - Should have waited approximately 1000ms
        assertThat(endTime - startTime).isAtLeast(900)
    }

    @Test
    fun `acquire eventually succeeds after waiting`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 1, perMillis = 100)
        limiter.acquire()

        // When
        launch {
            delay(50)
            advanceTimeBy(100)
        }
        limiter.acquire()

        // Then - Acquired successfully (no timeout)
        assertThat(limiter.getCurrentCount()).isAtMost(1)
    }

    // ==================== TRY ACQUIRE WITH TIMEOUT TESTS ====================

    @Test
    fun `tryAcquire with timeout succeeds when permit available`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)

        // When
        val result = limiter.tryAcquire(timeoutMs = 500)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `tryAcquire with timeout succeeds after waiting`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 1, perMillis = 200)
        limiter.tryAcquire() // Exhaust limit

        // When - Wait up to 500ms
        val result = limiter.tryAcquire(timeoutMs = 500)
        advanceUntilIdle()

        // Then - Should succeed after window expires
        assertThat(result).isTrue()
    }

    @Test
    fun `tryAcquire with timeout fails when timeout exceeded`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 1, perMillis = 10000)
        limiter.tryAcquire() // Exhaust limit

        // When - Timeout is shorter than window
        val result = limiter.tryAcquire(timeoutMs = 100)
        advanceUntilIdle()

        // Then
        assertThat(result).isFalse()
    }

    // ==================== WINDOW MANAGEMENT TESTS ====================

    @Test
    fun `getCurrentCount returns active requests in window`() {
        // Given
        val limiter = RateLimiter(maxRequests = 10, perMillis = 1000)

        // When
        repeat(5) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.getCurrentCount()).isEqualTo(5)
    }

    @Test
    fun `getCurrentCount decreases as window expires`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 100)
        repeat(5) { limiter.tryAcquire() }

        // When - Wait for window to expire
        advanceTimeBy(150)

        // Then
        assertThat(limiter.getCurrentCount()).isEqualTo(0)
    }

    @Test
    fun `getAvailablePermits returns remaining capacity`() {
        // Given
        val limiter = RateLimiter(maxRequests = 10, perMillis = 1000)

        // When
        repeat(3) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.getAvailablePermits()).isEqualTo(7)
    }

    @Test
    fun `getAvailablePermits returns zero at capacity`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)

        // When
        repeat(5) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.getAvailablePermits()).isEqualTo(0)
    }

    // ==================== LIMIT EXCEEDED TESTS ====================

    @Test
    fun `isLimitExceeded returns false when under limit`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)
        repeat(3) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.isLimitExceeded()).isFalse()
    }

    @Test
    fun `isLimitExceeded returns true when at limit`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)
        repeat(5) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.isLimitExceeded()).isTrue()
    }

    // ==================== TIME UNTIL NEXT PERMIT TESTS ====================

    @Test
    fun `getTimeUntilNextPermit returns zero when permits available`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)
        repeat(3) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.getTimeUntilNextPermit()).isEqualTo(0)
    }

    @Test
    fun `getTimeUntilNextPermit returns positive when no permits`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 1, perMillis = 1000)
        limiter.tryAcquire()

        // When
        val waitTime = limiter.getTimeUntilNextPermit()

        // Then
        assertThat(waitTime).isGreaterThan(0)
        assertThat(waitTime).isAtMost(1000)
    }

    // ==================== RESET TESTS ====================

    @Test
    fun `reset clears all tracked requests`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)
        repeat(5) { limiter.tryAcquire() }
        assertThat(limiter.isLimitExceeded()).isTrue()

        // When
        limiter.reset()

        // Then
        assertThat(limiter.getCurrentCount()).isEqualTo(0)
        assertThat(limiter.isLimitExceeded()).isFalse()
        assertThat(limiter.tryAcquire()).isTrue()
    }

    // ==================== STATISTICS TESTS ====================

    @Test
    fun `getStats returns correct statistics`() {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 1000)
        repeat(3) { limiter.tryAcquire() } // 3 success
        repeat(2) { limiter.tryAcquire() } // 2 more success (total 5)
        limiter.tryAcquire() // 1 rejection

        // When
        val stats = limiter.getStats()

        // Then
        assertThat(stats.totalRequests).isEqualTo(6)
        assertThat(stats.rejectedRequests).isEqualTo(1)
        assertThat(stats.acceptedRequests).isEqualTo(5)
        assertThat(stats.maxRequests).isEqualTo(5)
        assertThat(stats.windowMs).isEqualTo(1000)
    }

    @Test
    fun `getStats calculates rejection rate correctly`() {
        // Given
        val limiter = RateLimiter(maxRequests = 2, perMillis = 1000)
        repeat(2) { limiter.tryAcquire() } // 2 accepted
        repeat(2) { limiter.tryAcquire() } // 2 rejected

        // When
        val stats = limiter.getStats()

        // Then
        assertThat(stats.rejectionRate).isWithin(0.01).of(0.5) // 50%
    }

    @Test
    fun `resetStats clears statistics counters`() {
        // Given
        val limiter = RateLimiter(maxRequests = 3, perMillis = 1000)
        repeat(5) { limiter.tryAcquire() }
        assertThat(limiter.getStats().totalRequests).isEqualTo(5)

        // When
        limiter.resetStats()

        // Then
        val stats = limiter.getStats()
        assertThat(stats.totalRequests).isEqualTo(0)
        assertThat(stats.rejectedRequests).isEqualTo(0)
    }

    // ==================== RATE LIMITER STATS TESTS ====================

    @Test
    fun `RateLimiterStats calculates acceptedRequests correctly`() {
        // Given
        val stats = RateLimiterStats(
            totalRequests = 100,
            rejectedRequests = 20,
            totalWaitTimeMs = 5000,
            currentWindowSize = 5,
            maxRequests = 10,
            windowMs = 1000
        )

        // Then
        assertThat(stats.acceptedRequests).isEqualTo(80)
    }

    @Test
    fun `RateLimiterStats calculates utilizationRate correctly`() {
        // Given
        val stats = RateLimiterStats(
            totalRequests = 100,
            rejectedRequests = 20,
            totalWaitTimeMs = 5000,
            currentWindowSize = 5,
            maxRequests = 10,
            windowMs = 1000
        )

        // Then
        assertThat(stats.utilizationRate).isWithin(0.01).of(0.5) // 5/10
    }

    @Test
    fun `RateLimiterStats calculates averageWaitTimeMs correctly`() {
        // Given
        val stats = RateLimiterStats(
            totalRequests = 100,
            rejectedRequests = 20,
            totalWaitTimeMs = 8000,
            currentWindowSize = 5,
            maxRequests = 10,
            windowMs = 1000
        )

        // Then - 8000ms / 80 accepted = 100ms average
        assertThat(stats.averageWaitTimeMs).isWithin(0.01).of(100.0)
    }

    @Test
    fun `RateLimiterStats handles zero requests`() {
        // Given
        val stats = RateLimiterStats(
            totalRequests = 0,
            rejectedRequests = 0,
            totalWaitTimeMs = 0,
            currentWindowSize = 0,
            maxRequests = 10,
            windowMs = 1000
        )

        // Then - Should not divide by zero
        assertThat(stats.rejectionRate).isEqualTo(0.0)
        assertThat(stats.averageWaitTimeMs).isEqualTo(0.0)
    }

    // ==================== BURST HANDLING TESTS ====================

    @Test
    fun `handles burst of requests correctly`() {
        // Given
        val limiter = RateLimiter(maxRequests = 10, perMillis = 1000)

        // When - Burst of 15 requests
        val results = (1..15).map { limiter.tryAcquire() }

        // Then
        val accepted = results.count { it }
        val rejected = results.count { !it }
        assertThat(accepted).isEqualTo(10)
        assertThat(rejected).isEqualTo(5)
    }

    @Test
    fun `allows requests after burst window expires`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 5, perMillis = 100)

        // Burst - exhaust limit
        repeat(5) { limiter.tryAcquire() }
        assertThat(limiter.tryAcquire()).isFalse()

        // Wait for window
        advanceTimeBy(150)

        // Then - Should allow new burst
        repeat(5) {
            assertThat(limiter.tryAcquire()).isTrue()
        }
    }

    // ==================== CONCURRENT ACCESS TESTS ====================

    @Test
    fun `handles concurrent tryAcquire calls`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 100, perMillis = 1000)
        val successCount = AtomicInteger(0)

        // When - 200 concurrent requests
        val jobs = (1..200).map {
            async {
                if (limiter.tryAcquire()) {
                    successCount.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()

        // Then - Should not exceed limit
        assertThat(successCount.get()).isAtMost(100)
    }

    // ==================== FACTORY TESTS ====================

    @Test
    fun `createDefault creates 100 requests per minute limiter`() {
        // When
        val limiter = RateLimiterFactory.createDefault()

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(100)
        assertThat(stats.windowMs).isEqualTo(60_000)
    }

    @Test
    fun `createHighThroughput creates 1000 requests per minute limiter`() {
        // When
        val limiter = RateLimiterFactory.createHighThroughput()

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(1000)
        assertThat(stats.windowMs).isEqualTo(60_000)
    }

    @Test
    fun `createConservative creates 10 requests per minute limiter`() {
        // When
        val limiter = RateLimiterFactory.createConservative()

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(10)
        assertThat(stats.windowMs).isEqualTo(60_000)
    }

    @Test
    fun `createPerSecond creates correct limiter`() {
        // When
        val limiter = RateLimiterFactory.createPerSecond(5)

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(5)
        assertThat(stats.windowMs).isEqualTo(1000)
    }

    @Test
    fun `createPerMinute creates correct limiter`() {
        // When
        val limiter = RateLimiterFactory.createPerMinute(30)

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(30)
        assertThat(stats.windowMs).isEqualTo(60_000)
    }

    @Test
    fun `createPerHour creates correct limiter`() {
        // When
        val limiter = RateLimiterFactory.createPerHour(100)

        // Then
        val stats = limiter.getStats()
        assertThat(stats.maxRequests).isEqualTo(100)
        assertThat(stats.windowMs).isEqualTo(3_600_000)
    }

    // ==================== EDGE CASES TESTS ====================

    @Test
    fun `handles single request limit`() {
        // Given
        val limiter = RateLimiter(maxRequests = 1, perMillis = 1000)

        // When & Then
        assertThat(limiter.tryAcquire()).isTrue()
        assertThat(limiter.tryAcquire()).isFalse()
    }

    @Test
    fun `handles very short window`() = runTest {
        // Given
        val limiter = RateLimiter(maxRequests = 10, perMillis = 10)

        // Exhaust limit
        repeat(10) { limiter.tryAcquire() }
        assertThat(limiter.tryAcquire()).isFalse()

        // When - Wait just over window
        advanceTimeBy(15)

        // Then
        assertThat(limiter.tryAcquire()).isTrue()
    }

    @Test
    fun `handles large number of requests`() {
        // Given
        val limiter = RateLimiter(maxRequests = 10000, perMillis = 1000)

        // When
        repeat(10000) { limiter.tryAcquire() }

        // Then
        assertThat(limiter.getCurrentCount()).isEqualTo(10000)
        assertThat(limiter.tryAcquire()).isFalse()
    }

    // ==================== SLIDING WINDOW BEHAVIOR TESTS ====================

    @Test
    fun `sliding window allows requests as old ones expire`() = runTest {
        // Given - 5 requests per 100ms
        val limiter = RateLimiter(maxRequests = 5, perMillis = 100)

        // Make 5 requests
        repeat(5) { limiter.tryAcquire() }
        assertThat(limiter.tryAcquire()).isFalse()

        // Wait 50ms - requests should still be in window
        advanceTimeBy(50)
        assertThat(limiter.tryAcquire()).isFalse()

        // Wait another 60ms (total 110ms) - old requests expired
        advanceTimeBy(60)
        assertThat(limiter.tryAcquire()).isTrue()
    }
}
