package com.balaji.smsgateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingWindowRateLimiterTest {

    @Test
    fun `allows the configured number of requests in a minute`() {
        var now = 0L
        val limiter = SlidingWindowRateLimiter(perMinute = 2, perHour = 100, now = { now })

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `allows again after the minute window passes`() {
        var now = 0L
        val limiter = SlidingWindowRateLimiter(perMinute = 1, perHour = 100, now = { now })

        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        now = 59_999L
        assertFalse(limiter.tryAcquire())

        now = 60_000L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun `enforces the hourly limit independently of the minute limit`() {
        var now = 0L
        val limiter = SlidingWindowRateLimiter(perMinute = 100, perHour = 3, now = { now })

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        now = 61_000L
        assertFalse(limiter.tryAcquire())

        now = 3_600_000L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun `rejected requests do not consume the minute quota`() {
        var now = 0L
        val limiter = SlidingWindowRateLimiter(perMinute = 1, perHour = 100, now = { now })

        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        now = 60_000L
        assertTrue(limiter.tryAcquire())
    }
}
